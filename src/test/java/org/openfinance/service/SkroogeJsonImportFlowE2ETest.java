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
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DatabaseCleanupService databaseCleanupService;

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
                                syntheticSkroogeJson().getBytes(StandardCharsets.UTF_8)));

        ImportSession session =
                importService.startImport(uploadId, userId, null, "synthetic_skrooge.json");
        awaitParsed(session.getId());

        // 2. Run the real confirm path: account creation + transaction persistence + recalculation.
        importService.confirmImport(session.getId(), userId, null, Map.of(), true);

        // 3. Assert the persisted transaction count:
        //    groceries(1) + transfer(2 rows) + XOF income(1) + share buy(1) = 5.
        //    (Synthetic opening-balance rows are not persisted as transactions.)
        assertThat(transactionRepository.countByUserId(userId)).isEqualTo(5L);

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
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 100, "f_value": "-50.00" },
                    { "id": 31, "rd_operation_id": 3, "r_category_id": 200, "f_value": "-200.00" },
                    { "id": 41, "rd_operation_id": 4, "r_category_id": 200, "f_value": "200.00" },
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
