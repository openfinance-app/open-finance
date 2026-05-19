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
 * Performance tests for critical REST API endpoints.
 *
 * <p>These tests verify that individual API operations complete within defined SLA thresholds under
 * a clean, single-user test environment backed by an H2 in-memory database. They are not load tests
 * — they validate that the code path itself is not abnormally slow due to N+1 queries, missing
 * indices, or synchronous blocking in the service layer.
 *
 * <p><b>SLA Thresholds (H2 in-memory, single request):</b>
 *
 * <ul>
 *   <li>Auth login: &lt; 3 000 ms
 *   <li>Account list (empty): &lt; 1 000 ms
 *   <li>Account create: &lt; 1 500 ms
 *   <li>Transaction create: &lt; 2 000 ms
 *   <li>Transaction list: &lt; 2 000 ms
 *   <li>Dashboard summary: &lt; 3 000 ms
 * </ul>
 *
 * <p>Requirement: TASK-13.1.6 (Performance tests)
 *
 * @see BulkOperationPerformanceTest
 * @see ConcurrentRequestPerformanceTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("API Response-Time SLA Tests")
class ApiResponseTimeSlaTest {

    // ── SLA limits (milliseconds) ────────────────────────────────────────────
    private static final long SLA_AUTH_LOGIN_MS = 3_000L;
    private static final long SLA_ACCOUNT_LIST_MS = 1_000L;
    private static final long SLA_ACCOUNT_CREATE_MS = 1_500L;
    private static final long SLA_TRANSACTION_CREATE_MS = 2_000L;
    private static final long SLA_TRANSACTION_LIST_MS = 2_000L;
    private static final long SLA_DASHBOARD_SUMMARY_MS = 3_000L;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private KeyManagementService keyManagementService;

    private String token;
    private String encKey;
    private Long accountId;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Register test user
        userService.registerUser(
                UserRegistrationRequest.builder()
                        .username("perf_user")
                        .email("perf@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build());

        // Log in and capture token
        String loginResp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        LoginRequest.builder()
                                                                .username("perf_user")
                                                                .password("Password123!")
                                                                .masterPassword("Master123!")
                                                                .build())))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        token = objectMapper.readTree(loginResp).get("token").asText();

        // Derive encryption key
        User user =
                userRepository
                        .findByUsername("perf_user")
                        .orElseThrow(() -> new RuntimeException("Test user not found"));
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        SecretKey secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);
        encKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        // Pre-create one account to use in transaction tests
        String accountResp =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        AccountRequest.builder()
                                                                .name("Perf Checking")
                                                                .type(AccountType.CHECKING)
                                                                .currency("USD")
                                                                .initialBalance(
                                                                        BigDecimal.valueOf(1000))
                                                                .build())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        accountId = objectMapper.readTree(accountResp).get("id").asLong();
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    /**
     * Verifies that the login endpoint responds within the SLA. Requirement: TASK-13.1.6
     * (Authentication performance)
     */
    @Test
    @DisplayName("POST /api/v1/auth/login responds within SLA")
    void loginShouldRespondWithinSla() throws Exception {
        LoginRequest req =
                LoginRequest.builder()
                        .username("perf_user")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        StopWatch sw = new StopWatch("loginSla");
        sw.start();
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        sw.stop();

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "POST /auth/login must complete within %d ms but took %d ms",
                        SLA_AUTH_LOGIN_MS, elapsed)
                .isLessThanOrEqualTo(SLA_AUTH_LOGIN_MS);
    }

    // ── Accounts ─────────────────────────────────────────────────────────────

    /**
     * Verifies that listing accounts responds within the SLA. Requirement: TASK-13.1.6 (Account
     * list performance)
     */
    @Test
    @DisplayName("GET /api/v1/accounts responds within SLA")
    void listAccountsShouldRespondWithinSla() throws Exception {
        StopWatch sw = new StopWatch("listAccountsSla");
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
                        "GET /accounts must complete within %d ms but took %d ms",
                        SLA_ACCOUNT_LIST_MS, elapsed)
                .isLessThanOrEqualTo(SLA_ACCOUNT_LIST_MS);
    }

    /**
     * Verifies that creating an account responds within the SLA. Requirement: TASK-13.1.6 (Account
     * create performance)
     */
    @Test
    @DisplayName("POST /api/v1/accounts responds within SLA")
    void createAccountShouldRespondWithinSla() throws Exception {
        AccountRequest req =
                AccountRequest.builder()
                        .name("Perf Savings")
                        .type(AccountType.SAVINGS)
                        .currency("EUR")
                        .initialBalance(BigDecimal.valueOf(2000))
                        .build();

        StopWatch sw = new StopWatch("createAccountSla");
        sw.start();
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        sw.stop();

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "POST /accounts must complete within %d ms but took %d ms",
                        SLA_ACCOUNT_CREATE_MS, elapsed)
                .isLessThanOrEqualTo(SLA_ACCOUNT_CREATE_MS);
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    /**
     * Verifies that creating a transaction responds within the SLA. Requirement: TASK-13.1.6
     * (Transaction create performance)
     */
    @Test
    @DisplayName("POST /api/v1/transactions responds within SLA")
    void createTransactionShouldRespondWithinSla() throws Exception {
        TransactionRequest req =
                TransactionRequest.builder()
                        .accountId(accountId)
                        .type(TransactionType.INCOME)
                        .amount(BigDecimal.valueOf(500))
                        .currency("USD")
                        .date(LocalDate.now())
                        .description("Salary")
                        .build();

        StopWatch sw = new StopWatch("createTransactionSla");
        sw.start();
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        sw.stop();

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "POST /transactions must complete within %d ms but took %d ms",
                        SLA_TRANSACTION_CREATE_MS, elapsed)
                .isLessThanOrEqualTo(SLA_TRANSACTION_CREATE_MS);
    }

    /**
     * Verifies that listing transactions responds within the SLA. Requirement: TASK-13.1.6
     * (Transaction list performance)
     */
    @Test
    @DisplayName("GET /api/v1/transactions responds within SLA")
    void listTransactionsShouldRespondWithinSla() throws Exception {
        // GET /transactions requires at least one filter; use the pre-created accountId
        StopWatch sw = new StopWatch("listTransactionsSla");
        sw.start();
        mockMvc.perform(
                        get("/api/v1/transactions")
                                .param("accountId", String.valueOf(accountId))
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk());
        sw.stop();

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "GET /transactions must complete within %d ms but took %d ms",
                        SLA_TRANSACTION_LIST_MS, elapsed)
                .isLessThanOrEqualTo(SLA_TRANSACTION_LIST_MS);
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    /**
     * Verifies that the dashboard summary endpoint responds within the SLA. Requirement:
     * TASK-13.1.6 (Dashboard performance)
     */
    @Test
    @DisplayName("GET /api/v1/dashboard responds within SLA")
    void dashboardSummaryShouldRespondWithinSla() throws Exception {
        StopWatch sw = new StopWatch("dashboardSla");
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
                        "GET /dashboard must complete within %d ms but took %d ms",
                        SLA_DASHBOARD_SUMMARY_MS, elapsed)
                .isLessThanOrEqualTo(SLA_DASHBOARD_SUMMARY_MS);
    }
}
