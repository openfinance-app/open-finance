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

/**
 * Deterministic end-to-end test for the Skrooge QIF import flow. Drives the real {@link
 * ImportService} pipeline (store file → async parse → {@code confirmImport} → account creation →
 * transaction persistence → balance recalculation) against a small synthetic QIF export and asserts
 * the persisted account balances, currencies, and transaction count.
 *
 * <p>Unlike the parser-level regression test, this exercises the full {@code ImportService} +
 * {@code AccountService} path that produces the balance a user actually sees, including:
 *
 * <ul>
 *   <li>EUR accounts with plain {@code T} amounts (opening balance, expense, intra-EUR transfer).
 *   <li>A CFA foreign-currency account with {@code Q×I} valuation (opening + income).
 *   <li>An investment (Oth A) account with {@code Q×I} share valuation and an unresolvable opening
 *       ({@code Q0}/{@code Inan}) that produces a validation error and is correctly excluded.
 *   <li>Transfer deduplication: both legs of the EUR→EUR transfer are present; only the first is
 *       processed and the second is skipped via the canonical-key dedup guard.
 * </ul>
 *
 * <p>{@link ExchangeRateService} is mocked so the flow is hermetic and offline (production fetches
 * live FX rates when creating foreign-currency accounts).
 *
 * <p>QIF openings (T-based or Q×I-based) are persisted as regular transactions in the QIF path —
 * there is no separate {@code opening_balance} field mechanism as in the Skrooge JSON path.
 *
 * <p><b>Known QIF limitation (not exercised here):</b> same-currency (CFA↔CFA) transfers cannot
 * have their direction recovered from unsigned {@code Q} values. This synthetic fixture avoids
 * same-currency transfers; see docs/wiki/import-export.md for the full discussion.
 */
@SpringBootTest
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("QIF import flow — deterministic end-to-end")
class QifImportFlowE2ETest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    @MockBean private ExchangeRateService exchangeRateService;
    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private UserService userService;
    @Autowired private AuthService authService;
    @Autowired private EncryptionKeyCache encryptionKeyCache;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private ImportService importService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DatabaseCleanupService databaseCleanupService;

    private Long userId;

    @BeforeEach
    void setUp() {
        databaseCleanupService.execute();

        // Offline FX: account creation asks for a rate even for same-currency accounts.
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
                        .username("qif_importer")
                        .email("qif_importer@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build());

        User user = userRepository.findByUsername("qif_importer").orElseThrow();
        userId = user.getId();
        // QIF files have no explicit currency; ImportService falls back to the user's base
        // currency, which defaults to USD. Set it to EUR so the synthetic fixture's EUR accounts
        // get the correct currency.
        userService.updateBaseCurrency(userId, "EUR");

        LoginResponse login =
                authService.login(
                        LoginRequest.builder()
                                .username("qif_importer")
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
    @DisplayName("Should persist expected balances, currencies, and transaction count")
    void shouldImportSyntheticQifEndToEnd() throws Exception {
        // 1. Store the synthetic QIF and drive the async parse to completion.
        String uploadId =
                fileStorageService.storeFile(
                        new MockMultipartFile(
                                "file",
                                "synthetic.qif",
                                "application/octet-stream",
                                syntheticQif().getBytes(StandardCharsets.UTF_8)));

        ImportSession session = importService.startImport(uploadId, userId, null, "synthetic.qif");
        awaitParsed(session.getId());

        // 2. Confirm: creates accounts, persists transactions, recalculates balances.
        importService.confirmImport(session.getId(), userId, null, Map.of(), true);

        // 3. Assert the persisted transaction count.
        //
        //    Transactions saved to the DB:
        //      Checking:  T+1000 opening (INCOME)  +  T-50 grocery (EXPENSE)
        //                 + T-200 transfer-out leg (EXPENSE, from createTransfer)     = 3 rows
        //      Savings:   T+200 transfer-in  (INCOME, created as the other half of
        //                 the Checking→Savings createTransfer call; the Savings-side
        //                 QIF leg is deduplicated and skipped)                         = 1 row
        //      CFA Wallet: Q6000×0.0015=9.00 opening (INCOME)
        //                 + Q10000×0.0015=15.00 salary income (INCOME)                = 2 rows
        //      Crypto:    Q0/Inan opening → validation error → excluded
        //                 + Q0.01×50000=500.00 buy (INCOME)                           = 1 row
        //
        //    Total = 7 rows.
        assertThat(transactionRepository.countByUserId(userId)).isEqualTo(7L);

        // 4. Assert each account's persisted currency and balance.
        Map<String, Account> byName = new LinkedHashMap<>();
        List<Account> accounts = accountRepository.findByUserId(userId);
        for (Account account : accounts) {
            byName.put(account.getName(), account);
        }

        Map<String, ExpectedAccount> expected = new LinkedHashMap<>();
        // Opening (T+1000) is a regular transaction in the QIF path (no separate opening_balance
        // field). Balance = 1000 (opening) − 50 (grocery) − 200 (transfer out) = 750.
        expected.put("Checking", new ExpectedAccount("750.00", "EUR"));
        // Receives the +200 transfer created by Checking's leg; Savings' own QIF leg is skipped.
        expected.put("Savings", new ExpectedAccount("200.00", "EUR"));
        // Q×I: 6000×0.0015 (opening) + 10000×0.0015 (salary) = 9.00 + 15.00 = 24.00 EUR.
        // Account currency is home currency (EUR) — QIF collapses all foreign units to Q×I.
        expected.put("CFA Wallet", new ExpectedAccount("24.00", "EUR"));
        // Q×I: 0.01 shares × 50000 EUR/share = 500.00 EUR.
        expected.put("Crypto", new ExpectedAccount("500.00", "EUR"));

        assertThat(byName.keySet())
                .as("persisted account names")
                .containsExactlyInAnyOrderElementsOf(expected.keySet());

        expected.forEach(
                (name, exp) -> {
                    Account account = byName.get(name);
                    assertThat(account.getCurrency())
                            .as("currency of %s", name)
                            .isEqualTo(exp.currency());
                    assertThat(account.getBalance().setScale(2, RoundingMode.HALF_UP))
                            .as("balance of %s", name)
                            .isCloseTo(new BigDecimal(exp.balance()), within(TOLERANCE));
                });
    }

    /** Waits for the async parse to reach PARSED, failing fast on FAILED or timeout. */
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
     * Minimal but representative Skrooge-style QIF covering all parser paths:
     *
     * <ul>
     *   <li>EUR Bank account: T-based opening, expense, and outgoing transfer.
     *   <li>EUR Bank account (Savings): T-based incoming transfer (both legs present; ImportService
     *       deduplicates and processes only the Checking leg).
     *   <li>CFA Cash account: foreign-unit opening ({@code Q×I}, now counted after the Cause-2 fix)
     *       and a CFA income transaction.
     *   <li>Investment (Oth A) account: unresolvable opening ({@code Q=0, I=nan} → error) and a
     *       valid share purchase ({@code Q×I}).
     * </ul>
     *
     * <p>No same-currency (CFA↔CFA) transfers are included — direction recovery is not possible
     * from unsigned QIF {@code Q} values and is documented as a known limitation.
     */
    private String syntheticQif() {
        return """
                !Type:Cat
                NGroceries
                E
                ^
                NSalary
                I
                ^
                !Account
                NChecking
                TBank
                D
                ^
                !Type:Bank
                D2026-07-12
                T1000.00
                CR
                ^
                D2024-01-10
                T-50.00
                PMarket
                LGroceries
                ^
                D2024-01-12
                T-200.00
                PTransferDesk
                L[Savings]
                ^
                !Account
                NSavings
                TBank
                D
                ^
                !Type:Bank
                D2024-01-12
                T200.00
                PTransferDesk
                L[Checking]
                ^
                !Account
                NCFA Wallet
                TCash
                D
                ^
                !Type:Cash
                D2026-07-12
                YCFA
                CR
                Q6000
                I0.001500000000
                ^
                D2024-01-15
                YCFA
                PMarket
                CR
                Q10000
                I0.001500000000
                LSalary
                ^
                !Account
                NCrypto
                TOth A
                D
                ^
                !Type:Oth A
                D2026-07-12
                YTST
                CR
                Q0
                Inan
                ^
                D2024-06-15
                YTST
                PBroker
                CR
                Q0.01
                I50000.00
                ^
                """;
    }

    private record ExpectedAccount(String balance, String currency) {}
}
