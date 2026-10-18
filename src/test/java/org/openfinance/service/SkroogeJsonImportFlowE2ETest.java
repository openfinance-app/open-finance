package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.LoginResponse;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Deterministic end-to-end test for the Skrooge JSON import flow. Drives the real {@link
 * ImportService} pipeline (store file → async parse → {@code confirmImport} → account creation →
 * transaction persistence → balance recalculation) against a small synthetic export and asserts the
 * persisted account balances, native currencies, and transaction count.
 *
 * <p>Unlike the parser-level regression test, this exercises the {@code ImportService} + {@code
 * AccountService} path that produces the balance a user actually sees, including native currency
 * handling (EUR vs XOF), the foreign-currency peg conversion, investment valuation, and transfer
 * persistence. {@link ExchangeRateService} is mocked so the flow is hermetic and offline
 * (production performs a live Yahoo Finance fetch when creating foreign-currency accounts).
 */
@SpringBootTest
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "application.import.skrooge-json-enabled=true")
@DisplayName("Skrooge JSON import flow — deterministic end-to-end")
class SkroogeJsonImportFlowE2ETest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    @MockBean private ExchangeRateService exchangeRateService;
    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private UserService userService;
    @Autowired private AuthService authService;
    @Autowired private EncryptionKeyCache encryptionKeyCache;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private ImportService importService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private org.openfinance.repository.TransactionSplitRepository splitRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DatabaseCleanupService databaseCleanupService;
    @Autowired private TransactionRuleService rules;
    @Autowired private TransactionService transactions;

    private Long userId;

    @BeforeEach
    void setUp() {
        databaseCleanupService.execute();

        // Offline FX: same-currency rows need no conversion; account creation still asks for a
        // rate.
        lenient()
                .when(exchangeRateService.getExchangeRate(anyString(), anyString(), any()))
                .thenReturn(BigDecimal.ONE);
        lenient()
                .when(exchangeRateService.convert(any(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(exchangeRateService.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(
                UserRegistrationRequest.builder()
                        .username("importer")
                        .email("importer@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build());

        User user = userRepository.findByUsername("importer").orElseThrow();
        userId = user.getId();

        LoginResponse login =
                authService.login(
                        LoginRequest.builder()
                                .username("importer")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .build());
        SecretKey key =
                encryptionKeyCache
                        .getKeyBySessionToken(login.getEncryptionKey())
                        .orElseThrow(
                                () -> new IllegalStateException("No encryption key for session"));
        EncryptionContext.setKey(key);
    }

    @AfterEach
    void tearDown() {
        EncryptionContext.clear();
    }

    @Test
    @DisplayName("Should persist expected balances, native currencies, and transaction count")
    void shouldImportSyntheticExportEndToEnd() throws Exception {
        // 1. Store the synthetic export and run the real async parse to completion.
        String uploadId =
                fileStorageService.storeFile(
                        new MockMultipartFile(
                                "file",
                                "synthetic_skrooge.json",
                                "application/json",
                                syntheticSkroogeJson().getBytes(StandardCharsets.UTF_8)),
                        userId);

        ImportSession session =
                importService.startImport(uploadId, userId, null, "synthetic_skrooge.json");
        awaitParsed(session.getId());

        // 2. Run the real confirm path: account creation + transaction persistence + recalculation.
        importService.confirmImport(session.getId(), userId, null, Map.of(), true);

        // 3. Assert the persisted transaction count:
        //    groceries(1) + transfer(2 rows) + XOF income(1) + share buy(1) = 5.
        //    (Synthetic opening-balance rows are not persisted as transactions.)
        assertThat(transactionRepository.countByUserId(userId)).isEqualTo(5L);

        assertThat(transactionRepository.findByUserId(userId))
                .filteredOn(tx -> "skrooge:operation:2".equals(tx.getExternalReference()))
                .singleElement()
                .satisfies(
                        tx -> {
                            assertThat(tx.getTags()).isNullOrEmpty();
                            assertThat(splitRepository.findByTransactionIdOrderById(tx.getId()))
                                    .extracting(org.openfinance.entity.TransactionSplit::getTags)
                                    .containsExactly("Holiday", "Work trip");
                        });
        assertThat(transactionRepository.findByUserId(userId))
                .filteredOn(tx -> tx.getTransferId() != null)
                .extracting(org.openfinance.entity.Transaction::getTags)
                .containsExactlyInAnyOrder("Holiday", "Work trip");
        assertThat(categoryRepository.findByUserId(userId))
                .isNotEmpty()
                .allSatisfy(category -> assertThat(category.getIcon()).isEqualTo("📁"));

        // 4. Assert each persisted account's native currency and reconciled balance.
        Map<String, Account> byName = new LinkedHashMap<>();
        List<Account> accounts = accountRepository.findByUserId(userId);
        for (Account account : accounts) {
            byName.put(account.getName(), account);
        }

        Map<String, ExpectedAccount> expected = new LinkedHashMap<>();
        expected.put("Checking", new ExpectedAccount("750.00", "EUR")); // 1000 opening − 50 − 200
        expected.put("Savings", new ExpectedAccount("200.00", "EUR")); // +200 transfer in
        expected.put("CFA Wallet", new ExpectedAccount("10000.00", "XOF")); // native XOF income
        expected.put("Crypto", new ExpectedAccount("500.00", "EUR")); // 0.01 share × 50000 EUR

        assertThat(byName.keySet())
                .as("persisted account names")
                .containsExactlyInAnyOrderElementsOf(expected.keySet());

        expected.forEach(
                (name, exp) -> {
                    Account account = byName.get(name);
                    assertThat(account.getCurrency())
                            .as("native currency of %s", name)
                            .isEqualTo(exp.currency());
                    assertThat(account.getBalance().setScale(2, RoundingMode.HALF_UP))
                            .as("persisted balance of %s", name)
                            .isCloseTo(new BigDecimal(exp.balance()), within(TOLERANCE));
                });
    }

    @Test
    void retainsSourceAmountsThroughImportAndMetadataOnlyTransferEdit() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper json =
                new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode source =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.readTree(syntheticSkroogeJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) source.path("operation").get(3))
                .put("rd_account_id", 30);
        ((com.fasterxml.jackson.databind.node.ObjectNode) source.path("suboperation").get(3))
                .put("f_value", "-500");
        ((com.fasterxml.jackson.databind.node.ObjectNode) source.path("suboperation").get(4))
                .put("f_value", "500");
        com.fasterxml.jackson.databind.node.ArrayNode operations =
                (com.fasterxml.jackson.databind.node.ArrayNode) source.path("operation");
        operations
                .addObject()
                .put("id", 7)
                .put("rd_account_id", 30)
                .put("d_date", "0000-00-00")
                .put("rc_unit_id", 4);
        operations
                .addObject()
                .put("id", 8)
                .put("rd_account_id", 30)
                .put("d_date", "2024-01-15")
                .put("rc_unit_id", 1);
        com.fasterxml.jackson.databind.node.ArrayNode parts =
                (com.fasterxml.jackson.databind.node.ArrayNode) source.path("suboperation");
        parts.addObject().put("id", 71).put("rd_operation_id", 7).put("f_value", "1");
        parts.addObject()
                .put("id", 81)
                .put("rd_operation_id", 8)
                .put("r_category_id", 101)
                .put("f_value", "2");
        ((com.fasterxml.jackson.databind.node.ArrayNode) source.path("unitvalue"))
                .addObject()
                .put("id", 4)
                .put("rd_unit_id", 4)
                .put("d_date", "2026-10-17")
                .put("f_quantity", "0.001524490197");
        String uploadId =
                fileStorageService.storeFile(
                        new MockMultipartFile(
                                "file",
                                "source_amounts.json",
                                "application/json",
                                json.writeValueAsBytes(source)),
                        userId);
        ImportSession session =
                importService.startImport(uploadId, userId, null, "source_amounts.json");
        awaitParsed(session.getId());
        importService.confirmImport(session.getId(), userId, null, Map.of(), true);

        List<org.openfinance.entity.Transaction> rows = transactionRepository.findByUserId(userId);
        assertThat(rows).hasSize(6);
        org.openfinance.entity.Transaction debit =
                rows.stream()
                        .filter(tx -> "skrooge:operation:3".equals(tx.getExternalReference()))
                        .findFirst()
                        .orElseThrow();
        org.openfinance.entity.Transaction credit =
                rows.stream()
                        .filter(tx -> "skrooge:operation:4".equals(tx.getExternalReference()))
                        .findFirst()
                        .orElseThrow();
        assertThat(credit.getCurrency()).isEqualTo("XOF");
        assertThat(credit.getAmount()).isEqualByComparingTo("327978.4947019899");
        assertThat(credit.getOriginalAmount()).isEqualByComparingTo("500");
        assertThat(credit.getOriginalCurrency()).isEqualTo("EUR");
        assertThat(credit.getConversionRate()).isEqualByComparingTo("655.9569894039798");
        assertThat(rows)
                .filteredOn(tx -> "skrooge:operation:8".equals(tx.getExternalReference()))
                .singleElement()
                .satisfies(
                        tx -> {
                            assertThat(tx.getOriginalAmount()).isEqualByComparingTo("2");
                            assertThat(tx.getOriginalCurrency()).isEqualTo("EUR");
                        });
        assertThat(rows)
                .filteredOn(tx -> "skrooge:operation:6".equals(tx.getExternalReference()))
                .singleElement()
                .satisfies(tx -> assertThat(tx.getOriginalAmount()).isNull());
        // Unchanged reimports remain duplicates despite now carrying additional FX metadata.
        ImportSession repeat =
                importService.startImport(uploadId, userId, null, "source_amounts.json");
        awaitParsed(repeat.getId());
        importService.confirmImport(repeat.getId(), userId, null, Map.of(), true);
        assertThat(transactionRepository.countByUserId(userId)).isEqualTo(6);
        BigDecimal beforeBalance =
                accountRepository.findById(credit.getAccountId()).orElseThrow().getBalance();
        org.openfinance.dto.TransactionResponse response =
                transactions.updateTransfer(
                        debit.getTransferId(),
                        userId,
                        org.openfinance.dto.TransferUpdateRequest.builder()
                                .fromAccountId(debit.getAccountId())
                                .toAccountId(credit.getAccountId())
                                .amount(debit.getAmount())
                                .currency(debit.getCurrency())
                                .date(debit.getDate())
                                .description("Renamed imported transfer")
                                .build());
        assertThat(response.getTransferAmount()).isEqualByComparingTo("327978.4947019899");
        org.openfinance.entity.Transaction saved =
                transactionRepository.findById(credit.getId()).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo("327978.4947019899");
        assertThat(saved.getOriginalAmount()).isEqualByComparingTo("500");
        assertThat(accountRepository.findById(credit.getAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo(beforeBalance);
        assertThat(transactions.getTransactionsByAccount(userId, debit.getAccountId()))
                .filteredOn(tx -> tx.getId().equals(debit.getId()))
                .singleElement()
                .satisfies(
                        tx ->
                                assertThat(tx.getTransferAmount())
                                        .isEqualByComparingTo("327978.4947019899"));
    }

    /** Waits for the async parse to reach PARSED, failing fast on FAILED or timeout. */
    @Test
    void persistsRulesOnlyOnConfirmationAndReimportPreservesRulesAndAccountNotes()
            throws Exception {
        org.openfinance.entity.Category parent =
                categoryRepository.save(
                        org.openfinance.entity.Category.builder()
                                .userId(userId)
                                .name("Maison")
                                .isSystem(false)
                                .build());
        org.openfinance.entity.Category mapped =
                categoryRepository.save(
                        org.openfinance.entity.Category.builder()
                                .userId(userId)
                                .name("Ménage")
                                .parentId(parent.getId())
                                .isSystem(false)
                                .build());
        com.fasterxml.jackson.databind.ObjectMapper json =
                new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode source =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.readTree(syntheticSkroogeJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) source.path("account").get(0))
                .put("t_comment", "Original account notes")
                .put("t_agency_address", "12 High Street");
        ((com.fasterxml.jackson.databind.node.ArrayNode) source.path("category"))
                .addObject()
                .put("id", 999)
                .put("rd_category_id", 0)
                .put("t_name", "Rule only")
                .put("t_fullname", "Rule only");
        source.putArray("rule")
                .addObject()
                .put("id", 7)
                .put("t_action_type", "U")
                .put(
                        "t_definition",
                        "<element><!--OR--><element><!--AND--><element attribute=\"t_PAYEE\" operator=\"#ATT#='#V1S#'\" value=\"Coffee shop\"/></element></element>")
                .put(
                        "t_action_definition",
                        "<element><!--OR--><element><!--AND--><element attribute=\"t_REALCATEGORY\" operator=\"#ATT#='#V1S#'\" value=\"Rule only\"/></element></element>");
        for (int attempt = 0; attempt < 2; attempt++) {
            String upload =
                    fileStorageService.storeFile(
                            new MockMultipartFile(
                                    "file",
                                    "rules.json",
                                    "application/json",
                                    json.writeValueAsBytes(source)),
                            userId);
            ImportSession session = importService.startImport(upload, userId, null, "rules.json");
            awaitParsed(session.getId());
            assertThat(rules.getRulesForUser(userId)).hasSize(attempt);
            importService.confirmImport(
                    session.getId(), userId, null, Map.of("Rule only", mapped.getId()), true);
            assertThat(rules.getRulesForUser(userId)).hasSize(1);
        }
        Account checking =
                accountRepository.findByUserId(userId).stream()
                        .filter(a -> a.getName().equals("Checking"))
                        .findFirst()
                        .orElseThrow();
        assertThat(checking.getDescription()).isEqualTo("Original account notes\n12 High Street");
        assertThat(transactionRepository.countByUserId(userId)).isEqualTo(5);
        org.openfinance.dto.ImportedTransaction matching =
                org.openfinance.dto.ImportedTransaction.builder()
                        .payee("Coffee shop")
                        .memo("Receipt")
                        .amount(new BigDecimal("-5"))
                        .build();
        org.openfinance.dto.ImportedTransaction nonmatching =
                org.openfinance.dto.ImportedTransaction.builder()
                        .payee("Other shop")
                        .memo("Coffee shop")
                        .amount(new BigDecimal("-5"))
                        .build();
        assertThat(rules.applyRules(List.of(matching, nonmatching), userId)).containsExactly(0);
        assertThat(matching.getCategory()).isEqualTo("Maison:Ménage");
        assertThat(nonmatching.getCategory()).isNull();
    }

    @Test
    void importsRealFixtureIncludingMappedRulesAndAgencyAddresses() throws Exception {
        byte[] source =
                java.nio.file.Files.readAllBytes(
                        java.nio.file.Path.of("docs/sample/json/export_anonymized.json"));
        String upload =
                fileStorageService.storeFile(
                        new MockMultipartFile("file", "export.json", "application/json", source),
                        userId);
        ImportSession session = importService.startImport(upload, userId, null, "export.json");
        awaitParsed(session.getId());
        ImportSession completed =
                importService.confirmImport(session.getId(), userId, null, Map.of(), true);
        assertThat(completed.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(completed.getErrorCount()).isZero();
        assertThat(rules.getRulesForUser(userId)).hasSize(29);
        com.fasterxml.jackson.databind.JsonNode metadata =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(completed.getMetadata())
                        .path("skroogeMetadata");
        assertThat(metadata.path("sourceRuleCount").asInt()).isEqualTo(31);
        assertThat(metadata.path("importedRuleCount").asInt()).isEqualTo(29);
        assertThat(metadata.path("skippedRuleCount").asInt()).isEqualTo(2);
        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(source);
        for (com.fasterxml.jackson.databind.JsonNode account : root.path("account")) {
            String address = account.path("t_agency_address").asText().trim();
            if (!address.isEmpty()) {
                assertThat(accountRepository.findByUserId(userId))
                        .filteredOn(a -> a.getName().equals(account.path("t_name").asText()))
                        .singleElement()
                        .satisfies(a -> assertThat(a.getDescription()).contains(address));
            }
        }
    }

    private void awaitParsed(Long sessionId) throws InterruptedException {
        for (int attempt = 0; attempt < 80; attempt++) {
            ImportStatus status = importService.getSession(sessionId, userId).getStatus();
            if (status == ImportStatus.PARSED || status == ImportStatus.REVIEWING) {
                return;
            }
            if (status == ImportStatus.FAILED) {
                throw new IllegalStateException("Import parsing failed for session " + sessionId);
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Timed out waiting for parse of session " + sessionId);
    }

    /**
     * A minimal but representative Skrooge export covering: an EUR checking account with an opening
     * balance and an expense, an intra-EUR transfer to a savings account, a native-XOF income row,
     * and a share (investment) purchase priced in EUR.
     */
    private String syntheticSkroogeJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Checking", "t_type": "C", "t_number": "CHK", "t_close": false },
                    { "id": 20, "rd_bank_id": 1, "t_name": "Savings", "t_type": "S", "t_number": "SAV", "t_close": false },
                    { "id": 30, "rd_bank_id": 1, "t_name": "CFA Wallet", "t_type": "C", "t_number": "CFA", "t_close": false },
                    { "id": 40, "rd_bank_id": 1, "t_name": "Crypto", "t_type": "A", "t_number": "", "t_close": false }
                  ],
                  "category": [
                    { "id": 100, "rd_category_id": 0, "t_name": "Groceries", "t_fullname": "Food:Groceries" },
                    { "id": 200, "rd_category_id": 0, "t_name": "Transfer", "t_fullname": "Transfer" },
                    { "id": 101, "rd_category_id": 0, "t_name": "Salary", "t_fullname": "Salary" },
                    { "id": 102, "rd_category_id": 0, "t_name": "Investment", "t_fullname": "Investment" }
                  ],
                  "payee": [
                    { "id": 1, "t_name": "Market" },
                    { "id": 2, "t_name": "TransferDesk" },
                    { "id": 3, "t_name": "Broker" }
                  ],
                  "refund": [
                    { "id": 7, "t_name": "Holiday" },
                    { "id": 8, "t_name": "Work trip" }
                  ],
                  "unit": [
                    { "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 },
                    { "id": 4, "t_name": "Franc CFA (XOF)", "t_symbol": "CFA", "t_internet_code": "XOF/EUR", "t_type": "2", "rd_unit_id": 1 },
                    { "id": 5, "t_name": "Test Share", "t_symbol": "TST", "t_internet_code": "", "t_type": "S", "rd_unit_id": 1 }
                  ],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 1 },
                    { "id": 2, "rd_account_id": 10, "d_date": "2024-01-10", "i_group_id": 0, "r_payee_id": 1, "t_mode": "CARD", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 3, "rd_account_id": 10, "d_date": "2024-01-12", "i_group_id": 99, "r_payee_id": 2, "t_mode": "TRANSFER", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 4, "rd_account_id": 20, "d_date": "2024-01-12", "i_group_id": 99, "r_payee_id": 2, "t_mode": "TRANSFER", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 5, "rd_account_id": 30, "d_date": "2024-01-15", "i_group_id": 0, "r_payee_id": 1, "t_mode": "Deposit", "t_status": "Y", "rc_unit_id": 4 },
                    { "id": 6, "rd_account_id": 40, "d_date": "2024-06-15", "i_group_id": 0, "r_payee_id": 3, "t_mode": "Buy", "t_status": "Y", "rc_unit_id": 5 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 0, "f_value": "1000.00" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 100, "f_value": "-30.00", "r_refund_id": 7 },
                    { "id": 22, "rd_operation_id": 2, "r_category_id": 100, "f_value": "-20.00", "r_refund_id": 8 },
                    { "id": 31, "rd_operation_id": 3, "r_category_id": 200, "f_value": "-200.00", "r_refund_id": 7 },
                    { "id": 41, "rd_operation_id": 4, "r_category_id": 200, "f_value": "200.00", "r_refund_id": 8 },
                    { "id": 51, "rd_operation_id": 5, "r_category_id": 101, "f_value": "10000.00" },
                    { "id": 61, "rd_operation_id": 6, "r_category_id": 102, "f_value": "0.01" }
                  ],
                  "unitvalue": [
                    { "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 },
                    { "id": 2, "rd_unit_id": 4, "d_date": "2024-01-01", "f_quantity": 0.0015 },
                    { "id": 3, "rd_unit_id": 5, "d_date": "2024-06-01", "f_quantity": 50000 }
                  ]
                }
                """;
    }

    private record ExpectedAccount(String balance, String currency) {}
}
