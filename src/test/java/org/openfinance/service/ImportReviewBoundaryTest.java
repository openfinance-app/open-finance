package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.ImportProperties;
import org.openfinance.dto.ImportReviewRequest;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Account;
import org.openfinance.entity.Category;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.User;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.parser.QifParser;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("Import review preserves explicit assignments, metadata and ownership")
class ImportReviewBoundaryTest {
    @Mock ImportSessionRepository sessions;
    @Mock AccountRepository accounts;
    @Mock CategoryRepository categories;
    @Mock TransactionRepository transactions;
    @Mock UserRepository users;
    @Mock UserSettingsRepository settings;
    @Mock FileStorageService files;
    @Mock QifParser qif;
    @Mock org.openfinance.service.parser.OfxParser ofx;
    @Mock TransactionRuleService rules;
    @Mock AutoCategorizationService predictions;
    @Mock AICategorizationService ai;
    @Mock ImportedTransferService transfers;
    @Mock DefaultCurrencyProvider currencies;
    @Mock MessageSource messages;

    @Spy
    ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .enable(
                            com.fasterxml.jackson.databind.DeserializationFeature
                                    .USE_BIG_DECIMAL_FOR_FLOATS);

    @Spy ImportProgressTracker progress = new ImportProgressTracker();
    @Spy ImportProperties properties = new ImportProperties();
    @InjectMocks ImportService service;
    private ImportSession session;

    @BeforeEach
    void setup() {
        session =
                ImportSession.builder()
                        .id(7L)
                        .userId(3L)
                        .uploadId("upload")
                        .fileName("statement.qif")
                        .fileFormat("QIF")
                        .status(ImportStatus.PARSED)
                        .build();
        lenient().when(sessions.findById(7L)).thenAnswer(call -> Optional.of(session));
        lenient().when(currencies.getDefaultCurrency()).thenReturn("EUR");
        lenient().when(currencies.resolveForUser(3L)).thenReturn("EUR");
    }

    @ParameterizedTest
    @CsvSource({"QIF,QIF", "qif,QIF"})
    @ResourceLock(Resources.LOCALE)
    void fileTypeDetectionDoesNotDependOnServerLocale(String extension, String expected)
            throws Exception {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            when(files.fileExists("upload", 3L)).thenReturn(true);
            when(files.getOriginalFileName("upload", 3L)).thenReturn("upload." + extension);
            when(files.getFileContent("upload", 3L))
                    .thenReturn(new ByteArrayInputStream(new byte[0]));
            when(sessions.save(any(ImportSession.class)))
                    .thenAnswer(
                            call -> {
                                session = call.getArgument(0);
                                session.setId(7L);
                                return session;
                            });
            ImportSession result = service.startImport("upload", 3L, null, "Bank statement.QIF");
            assertThat(result.getFileFormat()).isEqualTo(expected);
            assertThat(result.getStatus()).isEqualTo(ImportStatus.PARSED);
            assertThat(result.getFileName()).isEqualTo("Bank statement.QIF");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    void accountAutodetectionMatchesAsciiNamesRegardlessOfServerLocale() throws Exception {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            ImportedTransaction row = row();
            row.setAccountName("ISIK BANK");
            when(files.getFileContent("upload", 3L))
                    .thenReturn(new ByteArrayInputStream(new byte[0]));
            when(qif.parseFile(any(), eq("statement.qif"), any())).thenReturn(List.of(row));
            when(accounts.findByUserId(3L))
                    .thenReturn(
                            List.of(
                                    Account.builder()
                                            .id(11L)
                                            .name("isik bank")
                                            .isActive(true)
                                            .build()));
            service.parseFileAsync(7L);
            assertThat(session.getStatus()).isEqualTo(ImportStatus.PARSED);
            assertThat(session.getAccountId()).isEqualTo(11L);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RULE_MATCH: previous assignment", "RULE_SKIP: previous exclusion"})
    void previouslyAppliedRulesAreNotAppliedAgain(String marker) throws Exception {
        ImportedTransaction row = row();
        row.setCategory("Source choice");
        row.addValidationError(marker);
        givenRows(List.of(row), Map.of());
        assertThat(service.reviewTransactions(7L, 3L).getFirst().getCategory())
                .isEqualTo("Source choice");
        verify(rules).applyRules(eq(List.of()), eq(3L));
        verifyNoInteractions(predictions);
    }

    @Test
    void explicitlySavedReviewsBypassAllCategorization() throws Exception {
        ImportedTransaction row = row();
        row.setCategory("User choice");
        givenRows(List.of(row), Map.of("reviewSaved", true));
        assertThat(service.reviewTransactions(7L, 3L).getFirst().getCategory())
                .isEqualTo("User choice");
        verifyNoInteractions(rules, predictions, ai);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onlyUnreviewedLegacySkroogeRowsRecoverTheirStatementPayee(boolean saved) throws Exception {
        session.setFileFormat("JSON");
        ImportedTransaction row = row();
        row.setOriginalPayee("FACTURE CARTE");
        row.setMemo("DU 071026 UBER");
        row.setPayee(saved ? "User choice" : row.getMemo());
        givenRows(List.of(row), Map.of("reviewSaved", saved));
        ImportedTransaction reviewed = service.reviewTransactions(7L, 3L).getFirst();
        assertThat(reviewed.getPayee()).isEqualTo(saved ? "User choice" : "FACTURE CARTE");
        assertThat(reviewed.getLegacyRuleText())
                .isEqualTo(
                        saved
                                ? null
                                : new ImportedTransaction.RuleText(
                                        "DU 071026 UBER", "DU 071026 UBER"));
        assertThat(reviewed.statementDescription()).isEqualTo("FACTURE CARTE");
    }

    @Test
    void ruleActionsPreserveTheOriginalMerchantAndOverridePredictions() throws Exception {
        givenRows(List.of(row()), Map.of());
        when(rules.applyRules(any(), eq(3L)))
                .thenAnswer(
                        call -> {
                            List<ImportedTransaction> rows = call.getArgument(0);
                            rows.getFirst().setPayee("Normalized merchant");
                            rows.getFirst().setCategory("Rule category");
                            return Set.of(0);
                        });
        ImportedTransaction result = service.reviewTransactions(7L, 3L).getFirst();
        assertThat(result.getOriginalPayee()).isEqualTo("Imported merchant");
        assertThat(result.getPayee()).isEqualTo("Normalized merchant");
        assertThat(result.getCategory()).isEqualTo("Rule category");
        verifyNoInteractions(predictions);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Original merchant"})
    void historySuggestionsOnlyFillMissingPayees(String payee) throws Exception {
        ImportedTransaction row = row();
        row.setPayee(payee);
        row.setOriginalPayee("Raw description");
        givenRows(List.of(row), Map.of());
        when(predictions.predictCategoryAndPayee(any(), eq(3L)))
                .thenReturn(
                        Optional.of(
                                new AutoCategorizationService.Prediction(
                                        "Groceries", "Predicted merchant", 0.91)));
        ImportedTransaction result = service.reviewTransactions(7L, 3L).getFirst();
        assertThat(result.getCategory()).isEqualTo("Groceries");
        assertThat(result.getCategorizationConfidence()).isEqualTo(0.91);
        assertThat(result.getOriginalPayee()).isEqualTo("Raw description");
        assertThat(result.getPayee())
                .isEqualTo(payee == null || payee.isBlank() ? "Predicted merchant" : payee);
    }

    @Test
    void categoryPredictionsNeedNotInventAMerchant() throws Exception {
        ImportedTransaction row = row();
        row.setPayee(null);
        givenRows(List.of(row), Map.of());
        when(predictions.predictCategoryAndPayee(any(), eq(3L)))
                .thenReturn(
                        Optional.of(
                                new AutoCategorizationService.Prediction("Groceries", null, 0.9)));
        ImportedTransaction result = service.reviewTransactions(7L, 3L).getFirst();
        assertThat(result.getCategory()).isEqualTo("Groceries");
        assertThat(result.getPayee()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"' groceries ',Groceries,false", "Grocerie,Groceries,true", "Unknown,Unknown,true"})
    void fileCategoriesUseExactOrFuzzyMatchesAndRetainUnrecognizedNames(
            String source, String expected, boolean warning) throws Exception {
        ImportedTransaction row = row();
        row.setCategory(source);
        givenRows(List.of(row), Map.of());
        when(categories.findByUserId(3L))
                .thenReturn(
                        List.of(
                                Category.builder().id(4L).name("Groceries").build(),
                                Category.builder().id(5L).name("Transport").build()));
        ImportedTransaction result = service.reviewTransactions(7L, 3L).getFirst();
        assertThat(result.getCategory()).isEqualTo(expected);
        assertThat(!result.getValidationErrors().isEmpty()).isEqualTo(warning);
        verifyNoInteractions(predictions);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 0L, -1L})
    void onlyPositiveSourceCategoryIdentitiesBypassNameMatching(long sourceId) throws Exception {
        ImportedTransaction row = row();
        row.setCategory(" groCERIES ");
        row.setSourceCategoryId(sourceId);
        givenRows(List.of(row), Map.of());
        when(categories.findByUserId(3L))
                .thenReturn(List.of(Category.builder().id(4L).name("Groceries").build()));
        ImportedTransaction result = service.reviewTransactions(7L, 3L).getFirst();
        assertThat(result.getCategory()).isEqualTo(sourceId > 0 ? " groCERIES " : "Groceries");
    }

    @ParameterizedTest
    @EnumSource(
            value = ImportStatus.class,
            names = {"PENDING", "PARSING", "IMPORTING", "COMPLETED", "CANCELLED", "FAILED"})
    void reviewCannotMutateAnUnavailableSession(ImportStatus status) {
        session.setStatus(status);
        assertThatThrownBy(() -> service.updateParsedTransactions(7L, List.of(), 3L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.reviewTransactions(7L, 3L))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(transfers, rules, transactions);
    }

    @Test
    void savingReviewDetectsConcurrentConfirmationInsteadOfReportingSuccess() {
        assertThatThrownBy(() -> service.updateParsedTransactions(7L, List.of(), 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void savingReviewPreservesExactLedgerAndSourceMetadata() throws Exception {
        givenRows(
                List.of(row()),
                Map.of(
                        "ledgerBalance",
                        new BigDecimal("9007199254740993.12345678"),
                        "fileCurrency",
                        "BTC",
                        "sourceVersion",
                        "bank-v3"));
        when(sessions.saveReview(eq(7L), eq(3L), anyString(), eq(1), any(LocalDateTime.class)))
                .thenReturn(1);
        ImportReviewRequest request = new ImportReviewRequest();
        request.setTransactions(List.of(row()));
        request.setSkipDuplicates(false);
        service.updateReview(7L, request, 3L);
        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(sessions)
                .saveReview(eq(7L), eq(3L), saved.capture(), eq(1), any(LocalDateTime.class));
        JsonNode value = mapper.readTree(saved.getValue());
        assertThat(value.path("ledgerBalance").asText()).isEqualTo("9007199254740993.12345678");
        assertThat(value.path("fileCurrency").asText()).isEqualTo("BTC");
        assertThat(value.path("sourceVersion").asText()).isEqualTo("bank-v3");
        assertThat(value.path("reviewSaved").asBoolean()).isTrue();
        assertThat(value.at("/reviewOptions/skipDuplicates").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void missingLedgerMetadataUsesTheUsersCurrency(String metadata) throws Exception {
        session.setMetadata(metadata);
        when(users.findById(3L))
                .thenReturn(Optional.of(User.builder().id(3L).baseCurrency("CHF").build()));
        when(sessions.saveReview(eq(7L), eq(3L), anyString(), eq(0), any(LocalDateTime.class)))
                .thenReturn(1);
        service.updateParsedTransactions(7L, List.of(), 3L);
        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(sessions)
                .saveReview(eq(7L), eq(3L), saved.capture(), eq(0), any(LocalDateTime.class));
        assertThat(mapper.readTree(saved.getValue()).path("fileCurrency").asText())
                .isEqualTo("CHF");
    }

    @Test
    void foreignCategoryMappingsCannotBeSaved() {
        ImportReviewRequest request = new ImportReviewRequest();
        request.setTransactions(List.of());
        request.setCategoryMappings(Map.of("Imported", 99L));
        assertThatThrownBy(() -> service.updateReview(7L, request, 3L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sessions, never())
                .saveReview(
                        any(), any(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void aiProgressAndFailuresDoNotDiscardSourceRows() throws Exception {
        givenRows(List.of(row()), Map.of());
        doAnswer(
                        call -> {
                            java.util.function.BiConsumer<Integer, Integer> callback =
                                    call.getArgument(2);
                            callback.accept(1, 1);
                            throw new IllegalStateException("provider unavailable");
                        })
                .when(ai)
                .categorizeWithAI(any(), any(), any());
        assertThat(service.reviewTransactions(7L, 3L)).hasSize(1);
        verify(sessions, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = ImportStatus.class,
            names = {"PENDING", "PARSING", "IMPORTING", "COMPLETED", "CANCELLED", "FAILED"})
    void cannotChangeAccountAfterTheReviewPhase(ImportStatus status) {
        session.setStatus(status);
        assertThatThrownBy(() -> service.updateAccount(7L, 11L, 3L))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void clearingAnExplicitAccountKeepsTheSessionReviewable() {
        session.setAccountId(11L);
        when(sessions.selectReviewAccount(eq(7L), eq(3L), eq(null), any()))
                .thenAnswer(
                        call -> {
                            session.setAccountId(null);
                            return 1;
                        });
        assertThat(service.updateAccount(7L, null, 3L).getAccountId()).isNull();
        verifyNoInteractions(accounts);
    }

    @Test
    void staleAccountChangesFailInsteadOfAppearingSaved() {
        when(accounts.findByIdAndUserId(11L, 3L))
                .thenReturn(Optional.of(Account.builder().id(11L).isActive(true).build()));
        assertThatThrownBy(() -> service.updateAccount(7L, 11L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer");
    }

    @Test
    void inactiveAccountsCannotBeSelectedForImport() {
        when(accounts.findByIdAndUserId(11L, 3L))
                .thenReturn(Optional.of(Account.builder().id(11L).isActive(false).build()));
        assertThatThrownBy(() -> service.updateAccount(7L, 11L, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive");
        verify(sessions, never()).selectReviewAccount(any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
        "USD,GBP,CHF,USD",
        "'',GBP,CHF,GBP",
        ",GBP,CHF,GBP",
        "' ',GBP,CHF,GBP",
        ",'',CHF,CHF",
        ",,CHF,CHF",
        ",' ',CHF,CHF"
    })
    void reviewCurrencyUsesExplicitRowThenAccountThenFile(
            String rowCurrency, String accountCurrency, String fileCurrency, String expected)
            throws Exception {
        ImportedTransaction row = row();
        row.setCurrency(rowCurrency);
        session.setAccountId(11L);
        givenRows(List.of(row), Map.of("reviewSaved", true, "fileCurrency", fileCurrency));
        when(accounts.findByUserId(3L))
                .thenReturn(
                        List.of(
                                Account.builder()
                                        .id(11L)
                                        .name("Bank")
                                        .currency(accountCurrency)
                                        .build()));
        assertThat(service.reviewTransactions(7L, 3L).getFirst().getReviewCurrency())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"true,category.food", "true,''", "true,", "false,category.food", ",category.food"})
    void categoryMatchingUsesTranslationsOnlyForUsableSystemKeys(Boolean system, String key)
            throws Exception {
        Category category =
                Category.builder().id(4L).name("Food").isSystem(system).nameKey(key).build();
        ImportedTransaction row = row();
        row.setCategory("Food");
        givenRows(List.of(row), Map.of());
        when(categories.findByUserId(3L)).thenReturn(List.of(category));
        if (Boolean.TRUE.equals(system) && key != null && !key.isBlank())
            when(messages.getMessage(eq(key), any(), eq("Food"), any())).thenReturn("Food");
        assertThat(service.reviewTransactions(7L, 3L).getFirst().getCategory()).isEqualTo("Food");
        verifyNoInteractions(predictions);
    }

    @ParameterizedTest
    @CsvSource({
        "123456789,Account •••6789",
        "123 456 789,Account ••• 789",
        "BANK123456789,BANK123456789",
        ",statement",
        "' ',statement"
    })
    void parsedAccountSuggestionsAbbreviateOnlyBankNumbers(String name, String expected)
            throws Exception {
        ImportedTransaction row = row();
        row.setAccountName(name);
        session.setFileName("statement");
        when(files.getFileContent("upload", 3L)).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(qif.parseFile(any(), eq("statement"), any())).thenReturn(List.of(row));
        when(users.findById(3L))
                .thenReturn(Optional.of(User.builder().id(3L).baseCurrency(null).build()));
        service.parseFileAsync(7L);
        assertThat(session.getStatus()).isEqualTo(ImportStatus.PARSED);
        assertThat(session.getSuggestedAccountName()).isEqualTo(expected);
        assertThat(mapper.readTree(session.getMetadata()).path("fileCurrency").asText())
                .isEqualTo("EUR");
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", ",false"})
    void accountAutodetectionNeverSelectsInactiveOrDeletedAccounts(Boolean active, boolean deleted)
            throws Exception {
        ImportedTransaction row = row();
        row.setAccountName("Checking");
        when(files.getFileContent("upload", 3L)).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(qif.parseFile(any(), eq("statement.qif"), any())).thenReturn(List.of(row));
        when(accounts.findByUserId(3L))
                .thenReturn(
                        List.of(
                                Account.builder()
                                        .id(11L)
                                        .name("Checking")
                                        .isActive(active)
                                        .deletedDate(deleted ? LocalDate.now() : null)
                                        .build()));
        service.parseFileAsync(7L);
        assertThat(session.getAccountId()).isNull();
        assertThat(session.getSuggestedAccountName()).isEqualTo("Checking");
        assertThat(session.getStatus()).isEqualTo(ImportStatus.PARSED);
    }

    @ParameterizedTest
    @CsvSource({"OFX,,EUR", "QFX,GBP,GBP"})
    void ofxCurrencyFallsBackWithoutDiscardingExactLedgerBalances(
            String format, String currency, String expected) throws Exception {
        session.setFileFormat(format);
        when(files.getFileContent("upload", 3L)).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(ofx.parseFileToResult(any(), eq("statement.qif"), any()))
                .thenReturn(
                        org.openfinance.dto.ImportParseResult.builder()
                                .transactions(List.of(row()))
                                .currency(currency)
                                .ledgerBalance(new BigDecimal("10.000000000000000001"))
                                .ledgerBalances(null)
                                .build());
        service.parseFileAsync(7L);
        JsonNode metadata = mapper.readTree(session.getMetadata());
        assertThat(session.getStatus()).isEqualTo(ImportStatus.PARSED);
        assertThat(metadata.path("fileCurrency").asText()).isEqualTo(expected);
        assertThat(metadata.path("ledgerBalance").asText()).isEqualTo("10.000000000000000001");
        assertThat(metadata.path("ledgerBalances").size()).isZero();
    }

    @Test
    void unknownFormatsFailTheSessionWithoutPretendingToHaveParsedEmptyData() throws Exception {
        session.setFileFormat("UNKNOWN");
        when(files.getFileContent("upload", 3L)).thenReturn(new ByteArrayInputStream(new byte[0]));
        service.parseFileAsync(7L);
        assertThat(session.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(session.getErrorMessage()).contains("Unsupported file format");
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "amount"})
    void incompleteRowsCannotBeFlaggedAsDuplicateByTheirReference(String missing) throws Exception {
        ImportedTransaction invalid = row();
        invalid.setReferenceNumber("bank-1");
        invalid.setPotentialDuplicate(true);
        invalid.addValidationError("DUPLICATE: stale review");
        if (missing.equals("date")) invalid.setTransactionDate(null);
        else invalid.setAmount(null);
        ImportedTransaction valid = row();
        valid.setReferenceNumber("bank-1");
        session.setFileFormat("OFX");
        givenRows(List.of(invalid, valid), Map.of("reviewSaved", true));
        List<ImportedTransaction> reviewed = service.reviewTransactions(7L, 3L);
        assertThat(reviewed).allSatisfy(tx -> assertThat(tx.isPotentialDuplicate()).isFalse());
        assertThat(reviewed.getFirst().getValidationErrors())
                .doesNotContain("DUPLICATE: stale review");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitlyNonauthoritativeReferencesDoNotSuppressDistinctOfxPayments(boolean authoritative)
            throws Exception {
        ImportedTransaction first = row();
        first.setReferenceNumber("check-7");
        first.setAuthoritativeReference(authoritative);
        ImportedTransaction second = row();
        second.setReferenceNumber("check-7");
        second.setAuthoritativeReference(authoritative);
        second.setAmount(new BigDecimal("-20"));
        session.setFileFormat("OFX");
        givenRows(List.of(first, second), Map.of("reviewSaved", true));
        List<ImportedTransaction> reviewed = service.reviewTransactions(7L, 3L);
        assertThat(reviewed.getFirst().isPotentialDuplicate()).isFalse();
        assertThat(reviewed.get(1).isPotentialDuplicate()).isEqualTo(authoritative);
    }

    private void givenRows(List<ImportedTransaction> rows, Map<String, Object> extra)
            throws Exception {
        Map<String, Object> metadata = new java.util.HashMap<>(extra);
        metadata.put("transactions", rows);
        session.setMetadata(mapper.writeValueAsString(metadata));
    }

    private ImportedTransaction row() {
        return ImportedTransaction.builder()
                .transactionDate(LocalDate.of(2024, 1, 10))
                .amount(new BigDecimal("-10"))
                .payee("Imported merchant")
                .build();
    }
}
