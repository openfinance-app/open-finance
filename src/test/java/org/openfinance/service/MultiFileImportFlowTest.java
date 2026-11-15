package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.ImportProcessRequest;
import org.openfinance.dto.ImportReviewRequest;
import org.openfinance.dto.ImportSourceFile;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "application.import.skrooge-json-enabled=true")
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("Multiple files share account selection, review and confirmation")
class MultiFileImportFlowTest {
    @Autowired ImportService imports;
    @Autowired FileStorageService storage;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;
    @Autowired CurrencyRepository currencies;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired EncryptionKeyCache keys;
    @Autowired ObjectMapper mapper;
    @Autowired DatabaseCleanupService cleanup;
    @MockBean ExchangeRateService exchange;
    @MockBean AICategorizationService ai;
    @MockBean OperationHistoryService history;
    private Long userId;
    private Long checking;
    private Long savings;

    @BeforeEach
    void setup() {
        cleanup.execute();
        currencies.save(
                org.openfinance.entity.Currency.builder()
                        .code("EUR")
                        .name("Euro")
                        .symbol("€")
                        .build());
        lenient()
                .when(exchange.getExchangeRate(anyString(), anyString(), any()))
                .thenReturn(BigDecimal.ONE);
        lenient()
                .when(exchange.convert(any(), anyString(), anyString()))
                .thenAnswer(call -> call.getArgument(0));
        lenient()
                .when(exchange.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(call -> call.getArgument(0));
        userService.registerUser(
                UserRegistrationRequest.builder()
                        .username("multi_import")
                        .email("multi@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build());
        userId = users.findByUsername("multi_import").orElseThrow().getId();
        userService.updateBaseCurrency(userId, "EUR");
        String token =
                auth.login(
                                LoginRequest.builder()
                                        .username("multi_import")
                                        .password("Password123!")
                                        .masterPassword("Master123!")
                                        .build())
                        .getEncryptionKey();
        EncryptionContext.setKey(keys.getKeyBySessionToken(token).orElseThrow());
        checking = account("Checking");
        savings = account("Savings");
    }

    @AfterEach
    void clearKey() {
        EncryptionContext.clear();
    }

    @Test
    void mixedFilesMatchAccountsAndConfirmTogetherWithAnOverride() throws Exception {
        ImportProcessRequest csv = upload("checking.csv", csv("Checking", "Coffee", "-10"));
        ImportProcessRequest qif =
                upload(
                        "savings.qif",
                        "!Account\nNSavings\nTBank\n^\n!Type:Bank\nD1/2/2026\nT25\nPDeposit\n^\n");
        ImportSession session = imports.startMultiFileImport(List.of(csv, qif), userId);
        assertThat(session.getStatus()).isEqualTo(ImportSession.ImportStatus.PARSED);
        assertThat(sources(session))
                .extracting(ImportSourceFile::getAccountId)
                .containsExactly(checking, savings);
        assertThat(imports.getUserSessions(userId)).hasSize(1);
        assertThat(transactions.findByUserId(userId)).isEmpty();

        imports.updateFileAccounts(session.getId(), Map.of(qif.getUploadId(), checking), userId);
        List<ImportedTransaction> rows = imports.reviewTransactions(session.getId(), userId);
        assertThat(rows)
                .extracting(ImportedTransaction::getPayee)
                .containsExactly("Coffee", "Deposit");
        assertThat(rows)
                .extracting(ImportedTransaction::getSourceUploadId)
                .containsExactly(csv.getUploadId(), qif.getUploadId());
        saveReview(session, rows);
        ImportSession completed =
                imports.confirmImport(session.getId(), userId, null, Map.of(), true);
        assertThat(completed.getStatus()).isEqualTo(ImportSession.ImportStatus.COMPLETED);
        assertThat(completed.getImportedCount()).isEqualTo(2);
        assertThat(transactions.findByUserId(userId))
                .extracting(Transaction::getAccountId)
                .containsOnly(checking);
        assertThat(accounts.findById(checking).orElseThrow().getBalance())
                .isEqualByComparingTo("15");
        assertThatThrownBy(
                        () -> imports.confirmImport(session.getId(), userId, null, Map.of(), true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void identicalFilenamesKeepIndependentEditsAndPersistOneCombinedDraft() throws Exception {
        ImportProcessRequest first = upload("statement.csv", csv("Unselected", "First", "-10"));
        ImportProcessRequest second = upload("statement.csv", csv("Savings", "Second", "20"));
        ImportSession session = imports.startMultiFileImport(List.of(first, second), userId);
        List<ImportedTransaction> rows = imports.reviewTransactions(session.getId(), userId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getLineNumber()).isEqualTo(rows.get(1).getLineNumber());
        ImportedTransaction kept = rows.get(1);
        kept.setPayee("Reviewed deposit");
        saveReview(session, List.of(kept));
        List<ImportedTransaction> restored = imports.reviewTransactions(session.getId(), userId);
        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().getSourceUploadId()).isEqualTo(second.getUploadId());
        assertThat(restored.getFirst().getPayee()).isEqualTo("Reviewed deposit");
        imports.confirmImport(session.getId(), userId, null, Map.of(), true);
        assertThat(transactions.findByUserId(userId))
                .extracting(Transaction::getAccountId)
                .containsExactly(savings);
        assertThat(accounts.findByUserId(userId)).hasSize(2);
    }

    @Test
    void overlappingStatementsShowDuplicatesAcrossFilesOnlyWithinTheSameAccount() throws Exception {
        ImportProcessRequest first = upload("first.ofx", ofx("Checking"));
        ImportProcessRequest overlap = upload("overlap.ofx", ofx("Checking"));
        ImportProcessRequest separate = upload("separate.ofx", ofx("Savings"));
        ImportSession session =
                imports.startMultiFileImport(List.of(first, overlap, separate), userId);
        List<ImportedTransaction> rows = imports.reviewTransactions(session.getId(), userId);
        assertThat(rows)
                .extracting(ImportedTransaction::isPotentialDuplicate)
                .containsExactly(false, true, false);
        saveReview(session, rows);
        ImportSession completed =
                imports.confirmImport(session.getId(), userId, null, Map.of(), true);
        assertThat(completed.getImportedCount()).isEqualTo(2);
        assertThat(completed.getDuplicateCount()).isEqualTo(1);
        assertThat(transactions.findByUserId(userId)).hasSize(2);
    }

    @Test
    void preservesSeparateSkroogeSourceIdsAndMetadataAcrossFiles() throws Exception {
        String template =
                """
                {"account":[{"id":1,"t_name":"%s","t_type":"C","t_close":false}],
                 "category":[{"id":1,"t_name":"%s","t_fullname":"%s"}],
                 "unit":[{"id":1,"t_name":"Euro (EUR)","t_symbol":"EUR","t_type":"1"}],
                 "payee":[{"id":1,"t_name":"%s"}],
                 "operation":[{"id":1,"rd_account_id":1,"rc_unit_id":1,"d_date":"2026-01-02","r_payee_id":1}],
                 "suboperation":[{"id":1,"rd_operation_id":1,"r_category_id":1,"f_value":"-10"}]}
                """;
        ImportProcessRequest first =
                upload("first.json", template.formatted("Checking", "Food", "Food", "Market"));
        ImportProcessRequest second =
                upload("second.json", template.formatted("Savings", "Travel", "Travel", "Train"));
        ImportSession session = imports.startMultiFileImport(List.of(first, second), userId);
        assertThat(session.getStatus()).isEqualTo(ImportSession.ImportStatus.PARSED);
        List<ImportedTransaction> rows = imports.reviewTransactions(session.getId(), userId);
        assertThat(rows)
                .extracting(ImportedTransaction::getCategory)
                .containsExactly("Food", "Travel");
        saveReview(session, rows);
        ImportSession completed =
                imports.confirmImport(session.getId(), userId, null, Map.of(), true);
        assertThat(completed.getImportedCount()).isEqualTo(2);
        assertThat(transactions.findByUserId(userId))
                .extracting(Transaction::getAccountId)
                .containsExactlyInAnyOrder(checking, savings);
        assertThat(sources(completed))
                .allSatisfy(file -> assertThat(file.getMetadata()).contains("skroogeMetadata"));
    }

    @Test
    void validatesEveryFileAndAccountBeforeSavingAndRollsBackAConfirmationFailure()
            throws Exception {
        ImportProcessRequest first = upload("first.csv", csv("Checking", "First", "-10"));
        ImportProcessRequest second = upload("second.csv", csv("Savings", "Second", "20"));
        ImportProcessRequest missing = ImportProcessRequest.builder().uploadId("missing").build();
        assertThatThrownBy(() -> imports.startMultiFileImport(List.of(first, missing), userId))
                .isInstanceOf(org.openfinance.exception.ResourceNotFoundException.class);
        assertThat(imports.getUserSessions(userId)).isEmpty();
        ImportSession session = imports.startMultiFileImport(List.of(first, second), userId);
        assertThatThrownBy(
                        () ->
                                imports.updateFileAccounts(
                                        session.getId(), Map.of("other-upload", checking), userId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                imports.updateFileAccounts(
                                        session.getId(), Map.of(first.getUploadId(), -1L), userId))
                .isInstanceOf(org.openfinance.exception.ResourceNotFoundException.class);
        List<ImportedTransaction> rows = imports.reviewTransactions(session.getId(), userId);
        saveReview(session, rows);
        org.openfinance.entity.Account closed = accounts.findById(savings).orElseThrow();
        closed.setIsActive(false);
        accounts.save(closed);
        assertThatThrownBy(
                        () -> imports.confirmImport(session.getId(), userId, null, Map.of(), true))
                .isInstanceOf(RuntimeException.class);
        assertThat(transactions.findByUserId(userId)).isEmpty();
        imports.cancelImport(session.getId(), userId);
        assertThat(imports.getSession(session.getId(), userId).getStatus())
                .isEqualTo(ImportSession.ImportStatus.CANCELLED);
    }

    private Long account(String name) {
        return accountService
                .createAccount(
                        userId,
                        AccountRequest.builder()
                                .name(name)
                                .type(AccountType.CHECKING)
                                .currency("EUR")
                                .initialBalance(BigDecimal.ZERO)
                                .openingDate(LocalDate.of(2026, 1, 1))
                                .build())
                .getId();
    }

    private ImportProcessRequest upload(String name, String content) throws Exception {
        String id =
                storage.storeFile(
                        new MockMultipartFile(
                                "file",
                                name,
                                "application/octet-stream",
                                content.getBytes(StandardCharsets.UTF_8)),
                        userId);
        return ImportProcessRequest.builder().uploadId(id).originalFileName(name).build();
    }

    private List<ImportSourceFile> sources(ImportSession session) {
        return new ImportSessionMetadata(mapper).sourceFiles(session);
    }

    private void saveReview(ImportSession session, List<ImportedTransaction> rows) {
        ImportReviewRequest review = new ImportReviewRequest();
        review.setTransactions(rows);
        imports.updateReview(session.getId(), review, userId);
    }

    private String csv(String account, String payee, String amount) {
        return "date,amount,payee,account\n2026-01-02,"
                + amount
                + ","
                + payee
                + ","
                + account
                + "\n";
    }

    private String ofx(String account) {
        return """
                OFXHEADER:100
                DATA:OFXSGML
                VERSION:102
                SECURITY:NONE
                ENCODING:USASCII
                CHARSET:1252
                COMPRESSION:NONE
                OLDFILEUID:NONE
                NEWFILEUID:NONE

                <OFX><BANKMSGSRSV1><STMTTRNRS><TRNUID>1
                <STATUS><CODE>0<SEVERITY>INFO</STATUS><STMTRS><CURDEF>EUR
                <BANKACCTFROM><BANKID>123<ACCTID>%s<ACCTTYPE>CHECKING</BANKACCTFROM>
                <BANKTRANLIST><DTSTART>20260101000000<DTEND>20260131000000
                <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260102000000<TRNAMT>-10<FITID>shared-id<NAME>Coffee</STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
                """
                .formatted(account);
    }
}
