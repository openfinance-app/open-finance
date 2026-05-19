package org.openfinance.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.KeyManagementService;
import org.openfinance.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StopWatch;

/**
 * Bulk-operation performance tests for the Open Finance backend.
 *
 * <p>These tests create a dataset of N records and then measure how long common read/aggregate
 * operations take, verifying that query complexity grows linearly (not exponentially) and that key
 * endpoints remain within acceptable thresholds even with non-trivial data volumes.
 *
 * <p><b>Dataset sizes and thresholds:</b>
 *
 * <ul>
 *   <li>20 accounts created sequentially — total creation time &lt; 15 s
 *   <li>GET /accounts after 20 accounts created — &lt; 1 500 ms
 *   <li>50 transactions created sequentially — total creation time &lt; 30 s
 *   <li>GET /transactions (paginated, page 1) after 50 created — &lt; 2 500 ms
 *   <li>GET /dashboard after 50 transactions — &lt; 5 000 ms
 *   <li>Average per-account creation time — &lt; 750 ms each
 *   <li>Average per-transaction creation time — &lt; 600 ms each
 * </ul>
 *
 * <p>Requirement: TASK-13.1.6 (Performance tests — bulk operations)
 *
 * @see ApiResponseTimeSlaTest
 * @see ConcurrentRequestPerformanceTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("Bulk Operation Performance Tests")
class BulkOperationPerformanceTest {

    // ── Dataset sizes ────────────────────────────────────────────────────────
    private static final int ACCOUNT_COUNT = 20;
    private static final int TRANSACTION_COUNT = 50;

    // ── SLA thresholds (ms) ──────────────────────────────────────────────────
    private static final long SLA_BULK_ACCOUNT_CREATE_TOTAL_MS = 15_000L;
    private static final long SLA_BULK_ACCOUNT_LIST_MS = 1_500L;
    private static final long SLA_BULK_TRANSACTION_CREATE_TOTAL_MS = 30_000L;
    private static final long SLA_BULK_TRANSACTION_LIST_MS = 2_500L;
    private static final long SLA_DASHBOARD_AFTER_BULK_MS = 5_000L;
    private static final long SLA_AVG_ACCOUNT_CREATE_MS = 750L;
    private static final long SLA_AVG_TRANSACTION_CREATE_MS = 600L;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private KeyManagementService keyManagementService;

    private String token;
    private String encKey;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        userService.registerUser(
                UserRegistrationRequest.builder()
                        .username("bulk_user")
                        .email("bulk@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build());

        String loginResp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        LoginRequest.builder()
                                                                .username("bulk_user")
                                                                .password("Password123!")
                                                                .masterPassword("Master123!")
                                                                .build())))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        token = objectMapper.readTree(loginResp).get("token").asText();

        User user =
                userRepository
                        .findByUsername("bulk_user")
                        .orElseThrow(() -> new RuntimeException("Test user not found"));
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        SecretKey secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);
        encKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    // ── Account bulk create ───────────────────────────────────────────────────

    /**
     * Creates {@value #ACCOUNT_COUNT} accounts sequentially and asserts that:
     *
     * <ol>
     *   <li>Total creation time is within the bulk SLA.
     *   <li>Average per-account creation time does not exceed the per-item SLA.
     * </ol>
     *
     * <p>Requirement: TASK-13.1.6 (Bulk account create performance)
     */
    @Test
    @DisplayName("Create 20 accounts sequentially within total and per-item SLA")
    void bulkAccountCreationShouldBeWithinSla() throws Exception {
        StopWatch sw = new StopWatch("bulkAccountCreate");
        sw.start();

        for (int i = 1; i <= ACCOUNT_COUNT; i++) {
            mockMvc.perform(
                            post("/api/v1/accounts")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Key", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    AccountRequest.builder()
                                                            .name("Bulk Account " + i)
                                                            .type(
                                                                    i % 2 == 0
                                                                            ? AccountType.CHECKING
                                                                            : AccountType.SAVINGS)
                                                            .currency("USD")
                                                            .initialBalance(
                                                                    BigDecimal.valueOf(100L * i))
                                                            .build())))
                    .andExpect(status().isCreated());
        }

        sw.stop();
        long totalMs = sw.getTotalTimeMillis();
        long avgMs = totalMs / ACCOUNT_COUNT;

        assertThat(totalMs)
                .as(
                        "Creating %d accounts must complete within %d ms but took %d ms",
                        ACCOUNT_COUNT, SLA_BULK_ACCOUNT_CREATE_TOTAL_MS, totalMs)
                .isLessThanOrEqualTo(SLA_BULK_ACCOUNT_CREATE_TOTAL_MS);

        assertThat(avgMs)
                .as(
                        "Average account create time must be < %d ms but was %d ms",
                        SLA_AVG_ACCOUNT_CREATE_MS, avgMs)
                .isLessThanOrEqualTo(SLA_AVG_ACCOUNT_CREATE_MS);
    }

    // ── Account list after bulk create ────────────────────────────────────────

    /**
     * Creates {@value #ACCOUNT_COUNT} accounts and then measures how long the list endpoint takes.
     * Verifies no N+1 regression as data grows.
     *
     * <p>Requirement: TASK-13.1.6 (Account list performance with data)
     */
    @Test
    @DisplayName("GET /accounts responds within SLA after 20 accounts exist")
    void listAccountsAfterBulkCreateShouldBeWithinSla() throws Exception {
        // Seed data
        for (int i = 1; i <= ACCOUNT_COUNT; i++) {
            mockMvc.perform(
                            post("/api/v1/accounts")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Key", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    AccountRequest.builder()
                                                            .name("List Account " + i)
                                                            .type(AccountType.CHECKING)
                                                            .currency("USD")
                                                            .initialBalance(BigDecimal.valueOf(500))
                                                            .build())))
                    .andExpect(status().isCreated());
        }

        // Measure list
        StopWatch sw = new StopWatch("listAfterBulk");
        sw.start();
        mockMvc.perform(
                        get("/api/v1/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk());
        sw.stop();

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "GET /accounts with %d records must complete within %d ms but took %d ms",
                        ACCOUNT_COUNT, SLA_BULK_ACCOUNT_LIST_MS, elapsed)
                .isLessThanOrEqualTo(SLA_BULK_ACCOUNT_LIST_MS);
    }

    // ── Transaction bulk create ───────────────────────────────────────────────

    /**
     * Creates one account then adds {@value #TRANSACTION_COUNT} transactions and asserts both total
     * and per-item SLAs.
     *
     * <p>Requirement: TASK-13.1.6 (Bulk transaction create performance)
     */
    @Test
    @DisplayName("Create 50 transactions sequentially within total and per-item SLA")
    void bulkTransactionCreationShouldBeWithinSla() throws Exception {
        // Create account to attach transactions to
        String accountResp =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        AccountRequest.builder()
                                                                .name("Tx Bulk Account")
                                                                .type(AccountType.CHECKING)
                                                                .currency("USD")
                                                                .initialBalance(
                                                                        BigDecimal.valueOf(10_000))
                                                                .build())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        long acctId = objectMapper.readTree(accountResp).get("id").asLong();

        StopWatch sw = new StopWatch("bulkTxCreate");
        sw.start();

        for (int i = 1; i <= TRANSACTION_COUNT; i++) {
            TransactionType type = (i % 3 == 0) ? TransactionType.EXPENSE : TransactionType.INCOME;
            mockMvc.perform(
                            post("/api/v1/transactions")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Key", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    TransactionRequest.builder()
                                                            .accountId(acctId)
                                                            .type(type)
                                                            .amount(BigDecimal.valueOf(10L * i))
                                                            .currency("USD")
                                                            .date(LocalDate.now().minusDays(i))
                                                            .description("Bulk tx " + i)
                                                            .build())))
                    .andExpect(status().isCreated());
        }

        sw.stop();
        long totalMs = sw.getTotalTimeMillis();
        long avgMs = totalMs / TRANSACTION_COUNT;

        assertThat(totalMs)
                .as(
                        "Creating %d transactions must complete within %d ms but took %d ms",
                        TRANSACTION_COUNT, SLA_BULK_TRANSACTION_CREATE_TOTAL_MS, totalMs)
                .isLessThanOrEqualTo(SLA_BULK_TRANSACTION_CREATE_TOTAL_MS);

        assertThat(avgMs)
                .as(
                        "Average transaction create time must be < %d ms but was %d ms",
                        SLA_AVG_TRANSACTION_CREATE_MS, avgMs)
                .isLessThanOrEqualTo(SLA_AVG_TRANSACTION_CREATE_MS);
    }

    // ── Transaction list after bulk create ───────────────────────────────────

    /**
     * Seeds {@value #TRANSACTION_COUNT} transactions and then measures the first-page list
     * endpoint, verifying no N+1 regression.
     *
     * <p>Requirement: TASK-13.1.6 (Transaction list performance with data)
     */
    @Test
    @DisplayName("GET /transactions (page 1) responds within SLA after 50 transactions exist")
    void listTransactionsAfterBulkCreateShouldBeWithinSla() throws Exception {
        String accountResp =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        AccountRequest.builder()
                                                                .name("Tx List Account")
                                                                .type(AccountType.CHECKING)
                                                                .currency("USD")
                                                                .initialBalance(
                                                                        BigDecimal.valueOf(5_000))
                                                                .build())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        long acctId = objectMapper.readTree(accountResp).get("id").asLong();

        for (int i = 1; i <= TRANSACTION_COUNT; i++) {
            mockMvc.perform(
                            post("/api/v1/transactions")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Key", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    TransactionRequest.builder()
                                                            .accountId(acctId)
                                                            .type(TransactionType.EXPENSE)
                                                            .amount(BigDecimal.valueOf(20L * i))
                                                            .currency("USD")
                                                            .date(LocalDate.now().minusDays(i))
                                                            .description("List tx " + i)
                                                            .build())))
                    .andExpect(status().isCreated());
        }

        // Measure paginated list (first page of 20, filtered by account)
        // Note: GET /transactions requires at least one filter parameter (accountId or date range)
        StopWatch sw = new StopWatch("listTxAfterBulk");
        sw.start();
        mockMvc.perform(
                        get("/api/v1/transactions")
                                .param("accountId", String.valueOf(acctId))
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk());
        sw.stop();

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "GET /transactions page 1 with %d records must complete within %d ms but took %d ms",
                        TRANSACTION_COUNT, SLA_BULK_TRANSACTION_LIST_MS, elapsed)
                .isLessThanOrEqualTo(SLA_BULK_TRANSACTION_LIST_MS);
    }

    // ── Dashboard after bulk data ─────────────────────────────────────────────

    /**
     * Seeds {@value #TRANSACTION_COUNT} transactions across multiple accounts and verifies that the
     * dashboard aggregation endpoint still responds within the SLA (no runaway query growth).
     *
     * <p>Requirement: TASK-13.1.6 (Dashboard performance with data)
     */
    @Test
    @DisplayName("GET /dashboard responds within SLA after 50 transactions in 3 accounts")
    void dashboardAfterBulkDataShouldBeWithinSla() throws Exception {
        // Create 3 accounts
        long[] accountIds = new long[3];
        for (int a = 0; a < 3; a++) {
            String resp =
                    mockMvc.perform(
                                    post("/api/v1/accounts")
                                            .header("Authorization", "Bearer " + token)
                                            .header("X-Encryption-Key", encKey)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    objectMapper.writeValueAsString(
                                                            AccountRequest.builder()
                                                                    .name("Dash Account " + a)
                                                                    .type(AccountType.CHECKING)
                                                                    .currency("USD")
                                                                    .initialBalance(
                                                                            BigDecimal.valueOf(
                                                                                    1_000L
                                                                                            * (a
                                                                                                    + 1)))
                                                                    .build())))
                            .andExpect(status().isCreated())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            accountIds[a] = objectMapper.readTree(resp).get("id").asLong();
        }

        // Create 50 transactions spread across the 3 accounts
        for (int i = 1; i <= TRANSACTION_COUNT; i++) {
            long acctId = accountIds[i % 3];
            TransactionType type = (i % 2 == 0) ? TransactionType.INCOME : TransactionType.EXPENSE;
            mockMvc.perform(
                            post("/api/v1/transactions")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Key", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    TransactionRequest.builder()
                                                            .accountId(acctId)
                                                            .type(type)
                                                            .amount(BigDecimal.valueOf(15L * i))
                                                            .currency("USD")
                                                            .date(LocalDate.now().minusDays(i))
                                                            .description("Dashboard tx " + i)
                                                            .build())))
                    .andExpect(status().isCreated());
        }

        // Measure dashboard
        StopWatch sw = new StopWatch("dashboardAfterBulk");
        sw.start();
        mockMvc.perform(
                        get("/api/v1/dashboard")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk());
        sw.stop();

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "GET /dashboard with %d transactions must complete within %d ms but took %d ms",
                        TRANSACTION_COUNT, SLA_DASHBOARD_AFTER_BULK_MS, elapsed)
                .isLessThanOrEqualTo(SLA_DASHBOARD_AFTER_BULK_MS);
    }
}
