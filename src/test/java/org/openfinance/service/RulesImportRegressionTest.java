package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.ImportReviewRequest;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

/** Real persistence boundaries from the browser audit, with external AI/FX kept offline. */
@SpringBootTest(properties = "application.encryption.enabled=false")
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
class RulesImportRegressionTest {
    @Autowired private ImportService imports;
    @Autowired private ImportSessionRepository sessions;
    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private TransactionRuleService rules;
    @Autowired private FileStorageService files;
    @Autowired private UserService users;
    @Autowired private UserRepository userRepository;
    @Autowired private DatabaseCleanupService cleanup;
    @Autowired private org.openfinance.repository.CurrencyRepository currencyRepository;
    @MockBean private AICategorizationService ai;
    @MockBean private ExchangeRateService fx;
    private Long userId;
    private Long everyday;
    private Long savings;

    @Test
    void reviewResolvesImplicitCurrenciesWithoutChangingAmountsWhenSelectingAnotherAccount()
            throws Exception {
        currencyRepository.save(
                org.openfinance.entity.Currency.builder()
                        .code("USD")
                        .name("US Dollar")
                        .symbol("$")
                        .build());
        Long dollars =
                accountService
                        .createAccount(
                                userId,
                                AccountRequest.builder()
                                        .name("Dollar wallet")
                                        .type(AccountType.CHECKING)
                                        .currency("USD")
                                        .initialBalance(new BigDecimal("100"))
                                        .build())
                        .getId();
        ImportSession session = csv("-12.34", "Coffee", dollars);
        List<ImportedTransaction> reviewed = imports.reviewTransactions(session.getId(), userId);
        assertThat(reviewed.getFirst().getReviewCurrency()).isEqualTo("USD");
        assertThat(reviewed.getFirst().getCurrency()).isNull();
        imports.updateParsedTransactions(session.getId(), reviewed, userId);
        imports.updateAccount(session.getId(), everyday, userId);
        ImportedTransaction switched =
                imports.reviewTransactions(session.getId(), userId).getFirst();
        assertThat(switched.getReviewCurrency()).isEqualTo("EUR");
        assertThat(switched.getCurrency()).isNull();
        assertThat(switched.getAmount()).isEqualByComparingTo("-12.34");

        ImportSession explicit =
                upload(
                        "explicit.csv",
                        "date,amount,payee,currency\n2026-09-02,-12.34,Coffee,USD\n",
                        everyday);
        assertThat(
                        imports.reviewTransactions(explicit.getId(), userId)
                                .getFirst()
                                .getReviewCurrency())
                .isEqualTo("USD");

        ImportSession multi =
                upload(
                        "multiple.qif",
                        "!Account\nNEveryday audit\nTBank\n^\n!Type:Bank\nD09/02/2026\nT-1\nPCoffee\n^\n"
                                + "!Account\nNDollar wallet\nTBank\n^\n!Type:Bank\nD09/02/2026\nT-2\nPTea\n^\n",
                        everyday);
        assertThat(imports.reviewTransactions(multi.getId(), userId))
                .extracting(ImportedTransaction::getReviewCurrency)
                .containsExactly("EUR", "USD");
    }

    @Test
    void savesReviewChoicesWithRowsAndPreservesThemThroughLegacyUpdates() throws Exception {
        ImportSession session = csv("-12.34", "Reviewed purchase", everyday);
        List<ImportedTransaction> reviewed = imports.reviewTransactions(session.getId(), userId);
        reviewed.getFirst().setMemo("Receipt verified");
        ImportReviewRequest request = new ImportReviewRequest();
        request.setTransactions(reviewed);
        request.setSkipDuplicates(false);
        ImportSession saved = imports.updateReview(session.getId(), request, userId);
        assertThat(saved.getStatus()).isEqualTo(ImportStatus.REVIEWING);
        assertThat(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(saved.getMetadata())
                                .path("reviewOptions")
                                .path("skipDuplicates")
                                .booleanValue())
                .isFalse();
        ImportSession legacy = imports.updateParsedTransactions(session.getId(), reviewed, userId);
        assertThat(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(legacy.getMetadata())
                                .path("reviewOptions")
                                .path("skipDuplicates")
                                .booleanValue())
                .isFalse();
        assertThat(imports.reviewTransactions(session.getId(), userId).getFirst().getMemo())
                .isEqualTo("Receipt verified");
        confirm(session);
        assertThatThrownBy(() -> imports.updateReview(session.getId(), request, userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @BeforeEach
    void setUp() {
        cleanup.execute();
        currencyRepository.save(
                org.openfinance.entity.Currency.builder()
                        .code("EUR")
                        .name("Euro")
                        .symbol("€")
                        .build());
        lenient()
                .when(fx.getExchangeRate(anyString(), anyString(), any()))
                .thenReturn(BigDecimal.ONE);
        lenient()
                .when(fx.convert(any(), anyString(), anyString()))
                .thenAnswer(i -> i.getArgument(0));
        lenient()
                .when(fx.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(i -> i.getArgument(0));
        users.registerUser(
                UserRegistrationRequest.builder()
                        .username("rules_import_regression")
                        .email("rules-import@example.invalid")
                        .password("Password123!")
                        .skipSeeding(true)
                        .build());
        userId = userRepository.findByUsername("rules_import_regression").orElseThrow().getId();
        users.updateBaseCurrency(userId, "EUR");
        everyday = createAccount("Everyday audit", "1000");
        savings = createAccount("Savings audit", "500");
    }

    private Long createAccount(String name, String opening) {
        return accountService
                .createAccount(
                        userId,
                        AccountRequest.builder()
                                .name(name)
                                .type(AccountType.CHECKING)
                                .currency("EUR")
                                .initialBalance(new BigDecimal(opening))
                                .openingDate(LocalDate.of(2026, 1, 1))
                                .build())
                .getId();
    }

    private ImportSession upload(String name, String text, Long accountId) throws Exception {
        String uploadId =
                files.storeFile(
                        new MockMultipartFile(
                                "file",
                                name,
                                "application/octet-stream",
                                text.getBytes(StandardCharsets.UTF_8)),
                        userId);
        return imports.startImport(uploadId, userId, accountId, name);
    }

    private ImportSession fixture(String name) throws Exception {
        try (java.io.InputStream input = getClass().getResourceAsStream("/import-audit/" + name)) {
            assertThat(input).isNotNull();
            return upload(name, new String(input.readAllBytes(), StandardCharsets.UTF_8), null);
        }
    }

    private ImportSession csv(String amount, String merchant, Long accountId) throws Exception {
        return upload(
                "regression.csv",
                "date,amount,payee,memo\n2026-09-02,"
                        + amount
                        + ","
                        + merchant
                        + ",Original memo\n",
                accountId);
    }

    private ImportSession confirm(ImportSession session) {
        return imports.confirmImport(
                session.getId(), userId, session.getAccountId(), Map.of(), true);
    }

    private void rule(String merchant, List<TransactionRuleRequest.ActionRequest> actions) {
        rules.createRule(
                userId,
                TransactionRuleRequest.builder()
                        .name("Rule " + merchant)
                        .conditions(
                                List.of(
                                        TransactionRuleRequest.ConditionRequest.builder()
                                                .field(RuleConditionField.DESCRIPTION)
                                                .operator(RuleConditionOperator.CONTAINS)
                                                .value(merchant)
                                                .build()))
                        .actions(actions)
                        .build());
    }

    @Test
    void foreignCurrencyRepeatsUseSourceAmountsAndRetainConversionDetails() throws Exception {
        org.mockito.Mockito.when(
                        fx.convert(
                                any(),
                                org.mockito.ArgumentMatchers.eq("USD"),
                                org.mockito.ArgumentMatchers.eq("EUR"),
                                any()))
                .thenAnswer(
                        i ->
                                ((BigDecimal) i.getArgument(0))
                                        .multiply(new BigDecimal("0.87711604")));
        String csv = "date,amount,currency,payee\n2026-09-29,-10.00,USD,Foreign purchase\n";
        ImportSession first = upload("foreign.csv", csv, everyday);
        assertThat(confirm(first).getImportedCount()).isEqualTo(1);
        org.openfinance.entity.Transaction saved =
                transactions.findByAccountId(everyday).getFirst();
        assertThat(saved.getOriginalAmount()).isEqualByComparingTo("10");
        assertThat(saved.getOriginalCurrency()).isEqualTo("USD");
        assertThat(saved.getConversionRate()).isEqualByComparingTo("0.87711604");
        ImportSession repeat = upload("foreign.csv", csv, everyday);
        assertThat(
                        imports.reviewTransactions(repeat.getId(), userId)
                                .getFirst()
                                .isPotentialDuplicate())
                .isTrue();
        assertThat(confirm(repeat).getImportedCount()).isZero();
        assertThat(accounts.findById(everyday).orElseThrow().getBalance())
                .isEqualByComparingTo("991.22883960");
        // Previously imported rows have no source monetary fields. Historical comparison must
        // still protect them, and the same row in another account must remain importable.
        saved.setOriginalAmount(null);
        saved.setOriginalCurrency(null);
        saved.setConversionRate(null);
        transactions.save(saved);
        ImportSession legacy = upload("foreign.csv", csv, everyday);
        assertThat(
                        imports.reviewTransactions(legacy.getId(), userId)
                                .getFirst()
                                .isPotentialDuplicate())
                .isTrue();
        assertThat(confirm(legacy).getImportedCount()).isZero();
        assertThat(confirm(upload("foreign.csv", csv, savings)).getImportedCount()).isEqualTo(1);
    }

    @Test
    void invalidSplitRowsAreExcludedWithoutRollingBackValidRows() throws Exception {
        rule("Over split", List.of(splitAction("20"), splitAction("20")));
        ImportSession session =
                upload(
                        "mixed.csv",
                        "date,amount,payee\n2026-09-29,-5.55,Before\n"
                                + "2026-09-29,-30,Over split\n2026-09-29,-6.66,After\n",
                        everyday);
        List<ImportedTransaction> review = imports.reviewTransactions(session.getId(), userId);
        assertThat(review.get(1).hasErrors()).isTrue();
        assertThat(review.get(1).getValidationErrors())
                .anyMatch(e -> e.startsWith("SPLIT_INVALID:"));
        // Bypassing review must run the same validation at confirmation, through real proxies.
        ImportSession completed = confirm(session);
        assertThat(completed.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(completed.getImportedCount()).isEqualTo(2);
        assertThat(completed.getErrorCount()).isEqualTo(1);
        assertThat(completed.getSkippedCount()).isEqualTo(1);
        assertThat(transactions.findByAccountId(everyday)).hasSize(2);
        assertThat(accounts.findById(everyday).orElseThrow().getBalance())
                .isEqualByComparingTo("987.79");
    }

    @Test
    void correctedSplitTotalClearsTheReviewErrorBeforeConfirmation() throws Exception {
        rule("Over split", List.of(splitAction("20"), splitAction("20")));
        ImportSession session = csv("-30", "Over split", everyday);
        List<ImportedTransaction> review = imports.reviewTransactions(session.getId(), userId);
        assertThat(review.getFirst().hasErrors()).isTrue();
        review.getFirst().setAmount(new BigDecimal("-40"));
        imports.updateParsedTransactions(session.getId(), review, userId);
        assertThat(imports.reviewTransactions(session.getId(), userId).getFirst().hasErrors())
                .isFalse();
        assertThat(confirm(session).getImportedCount()).isEqualTo(1);
    }

    @Test
    void malformedSavedSplitActionProducesABlockingReviewError() throws Exception {
        // Historical rules can predate the controller's parameter validation.
        rule("Incomplete split", List.of(splitAction(null)));
        ImportSession session = csv("-22", "Incomplete split", everyday);
        assertThat(imports.reviewTransactions(session.getId(), userId).getFirst().hasErrors())
                .isTrue();
        assertThat(confirm(session).getImportedCount()).isZero();
        assertThat(transactions.findByAccountId(everyday)).isEmpty();
    }

    private TransactionRuleRequest.ActionRequest splitAction(String amount) {
        return TransactionRuleRequest.ActionRequest.builder()
                .actionType(RuleActionType.ADD_SPLIT)
                .actionValue("Groceries")
                .actionValue2(amount)
                .build();
    }

    @Test
    void reviewIsReadOnlyAndPreservesExplicitEditsThroughConfirmation() throws Exception {
        rule(
                "Grocery",
                List.of(
                        TransactionRuleRequest.ActionRequest.builder()
                                .actionType(RuleActionType.SET_DESCRIPTION)
                                .actionValue("Rule memo")
                                .build()));
        ImportSession session = csv("-43.27", "Grocery", everyday);
        String parsed = sessions.findById(session.getId()).orElseThrow().getMetadata();
        List<ImportedTransaction> first = imports.reviewTransactions(session.getId(), userId);
        assertThat(first.getFirst().getMemo()).isEqualTo("Rule memo");
        assertThat(imports.reviewTransactions(session.getId(), userId).getFirst().getMemo())
                .isEqualTo("Rule memo");
        assertThat(sessions.findById(session.getId()).orElseThrow().getMetadata())
                .isEqualTo(parsed);
        first.getFirst().setMemo("User correction");
        imports.updateParsedTransactions(session.getId(), first, userId);
        assertThat(imports.reviewTransactions(session.getId(), userId).getFirst().getMemo())
                .isEqualTo("User correction");
        assertThat(confirm(session).getImportedCount()).isEqualTo(1);
        assertThat(transactions.findByAccountId(everyday))
                .singleElement()
                .satisfies(tx -> assertThat(tx.getNotes()).isEqualTo("User correction"));
    }

    @Test
    void ruleSplitsDoNotAccumulateAcrossReviewAndSave() throws Exception {
        rule(
                "Split shop",
                List.of(
                        TransactionRuleRequest.ActionRequest.builder()
                                .actionType(RuleActionType.ADD_SPLIT)
                                .actionValue("Groceries")
                                .actionValue2("60")
                                .build(),
                        TransactionRuleRequest.ActionRequest.builder()
                                .actionType(RuleActionType.ADD_SPLIT)
                                .actionValue("Dining")
                                .actionValue2("40")
                                .build()));
        ImportSession session = csv("-100", "Split shop", everyday);
        List<ImportedTransaction> preview = imports.reviewTransactions(session.getId(), userId);
        assertThat(preview.getFirst().getSplits()).hasSize(2);
        imports.updateParsedTransactions(session.getId(), preview, userId);
        assertThat(imports.reviewTransactions(session.getId(), userId).getFirst().getSplits())
                .hasSize(2);
        assertThat(confirm(session).getImportedCount()).isEqualTo(1);
        assertThat(accounts.findById(everyday).orElseThrow().getBalance())
                .isEqualByComparingTo("900");
    }

    @Test
    void lateReviewCannotUndoCompletionOrReopenTheConfirmationGuard() throws Exception {
        ImportSession session = csv("-5", "Late review", everyday);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            if (Thread.currentThread().getName().equals("late-review")) {
                                entered.countDown();
                                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                            }
                            return null;
                        })
                .when(ai)
                .categorizeWithAI(anyList(), anyList());
        try (java.util.concurrent.ExecutorService executor =
                Executors.newSingleThreadExecutor(task -> new Thread(task, "late-review"))) {
            java.util.concurrent.Future<List<ImportedTransaction>> pending =
                    executor.submit(() -> imports.reviewTransactions(session.getId(), userId));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(confirm(session).getStatus()).isEqualTo(ImportStatus.COMPLETED);
            } finally {
                release.countDown();
            }
            pending.get(10, TimeUnit.SECONDS);
        }
        ImportSession saved = sessions.findById(session.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(saved.getConfirmationStarted()).isTrue();
        assertThat(saved.getImportedCount()).isEqualTo(1);
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThatThrownBy(
                        () -> imports.updateParsedTransactions(session.getId(), List.of(), userId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sessions.claimConfirmation(session.getId(), userId)).isZero();
        assertThat(transactions.findByAccountId(everyday)).hasSize(1);
    }

    @Test
    void refundsAndDifferentAccountsAreNotDuplicatesAndOldFlagsAreCleared() throws Exception {
        confirm(csv("-43.27", "Grocery Corner", everyday));
        ImportSession refund = csv("43.27", "Grocery Corner", everyday);
        assertThat(
                        imports.reviewTransactions(refund.getId(), userId)
                                .getFirst()
                                .isPotentialDuplicate())
                .isFalse();
        confirm(refund);
        assertThat(accounts.findById(everyday).orElseThrow().getBalance())
                .isEqualByComparingTo("1000");
        ImportSession secondAccount = csv("-43.27", "Grocery Corner", everyday);
        List<ImportedTransaction> stalePreview =
                imports.reviewTransactions(secondAccount.getId(), userId);
        assertThat(stalePreview.getFirst().isPotentialDuplicate()).isTrue();
        imports.updateAccount(secondAccount.getId(), savings, userId);
        imports.updateParsedTransactions(secondAccount.getId(), stalePreview, userId);
        assertThat(
                        imports.reviewTransactions(secondAccount.getId(), userId)
                                .getFirst()
                                .isPotentialDuplicate())
                .isFalse();
        ImportSession completed =
                imports.confirmImport(secondAccount.getId(), userId, savings, Map.of(), true);
        assertThat(completed.getImportedCount()).isEqualTo(1);
        assertThat(accounts.findById(savings).orElseThrow().getBalance())
                .isEqualByComparingTo("456.73");
    }

    @Test
    void bankIdsAreScopedPerAccountAndChequeNumbersDoNotReplaceFitid() throws Exception {
        assertThat(confirm(fixture("two-banks-same-fitid.ofx")).getImportedCount()).isEqualTo(2);
        assertThat(account("AUDIT-BANK-A").getBalance()).isEqualByComparingTo("989");
        assertThat(account("AUDIT-BANK-B").getBalance()).isEqualByComparingTo("978");
        assertThat(confirm(fixture("distinct-fitid-same-check.ofx")).getImportedCount())
                .isEqualTo(2);
        Account cheque = account("AUDIT-CHEQUE-4001");
        assertThat(cheque.getOpeningBalance()).isEqualByComparingTo("1000");
        assertThat(cheque.getBalance()).isEqualByComparingTo("940");
        assertThat(transactions.findByAccountId(cheque.getId())).hasSize(2);
    }

    @Test
    void standardCardStatementIsImportable() throws Exception {
        ImportSession session = fixture("credit-card.ofx");
        assertThat(imports.reviewTransactions(session.getId(), userId))
                .singleElement()
                .satisfies(tx -> assertThat(tx.getQifAccountType()).isEqualTo("CCard"));
        assertThat(confirm(session).getImportedCount()).isEqualTo(1);
    }

    @Test
    void removingAReviewedStatementRowDoesNotRewriteItsOpeningBalance() throws Exception {
        ImportSession session = fixture("distinct-fitid-same-check.ofx");
        List<ImportedTransaction> reviewed = imports.reviewTransactions(session.getId(), userId);
        imports.updateParsedTransactions(session.getId(), List.of(reviewed.getFirst()), userId);
        assertThat(confirm(session).getImportedCount()).isEqualTo(1);
        Account cheque = account("AUDIT-CHEQUE-4001");
        assertThat(cheque.getOpeningBalance()).isEqualByComparingTo("1000");
        assertThat(cheque.getBalance()).isEqualByComparingTo("975");
    }

    @Test
    void pairsComplementarySidesButPreservesIndependentReverseTransfers() throws Exception {
        assertThat(confirm(fixture("transfer.qif")).getImportedCount()).isEqualTo(1);
        assertThat(confirm(fixture("independent-transfers.qif")).getImportedCount()).isEqualTo(2);
        assertThat(accounts.findById(everyday).orElseThrow().getBalance())
                .isEqualByComparingTo("865");
        assertThat(accounts.findById(savings).orElseThrow().getBalance())
                .isEqualByComparingTo("635");
        assertThat(
                        transactions.findByAccountId(everyday).stream()
                                .filter(tx -> tx.getAccountId().equals(everyday)))
                .hasSize(3);
        assertThat(
                        transactions.findByAccountId(savings).stream()
                                .filter(tx -> tx.getAccountId().equals(savings)))
                .hasSize(3);
    }

    @Test
    void deliberateRuleSkipIsNotAnImportError() throws Exception {
        rule(
                "Ignore pending",
                List.of(
                        TransactionRuleRequest.ActionRequest.builder()
                                .actionType(RuleActionType.SKIP_TRANSACTION)
                                .build()));
        ImportSession result = confirm(csv("-20", "Ignore pending", everyday));
        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isZero();
        assertThat(accounts.findById(everyday).orElseThrow().getBalance())
                .isEqualByComparingTo("1000");
    }

    private Account account(String name) {
        return accounts.findByUserId(userId).stream()
                .filter(account -> account.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
