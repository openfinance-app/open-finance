package org.openfinance.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AccountType;
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
 * Concurrent request performance tests for the Open Finance backend.
 *
 * <p>These tests simulate multiple threads issuing requests at the same time to verify that the
 * application handles concurrent access without deadlocks, excessive latency, or data corruption.
 *
 * <p><b>Thresholds:</b>
 *
 * <ul>
 *   <li>10 concurrent reads (accounts list): all succeed, total wall-time &lt; 5 s
 *   <li>10 concurrent reads (dashboard): all succeed, total wall-time &lt; 8 s
 *   <li>5 concurrent creates (accounts): all succeed, no 5xx errors
 * </ul>
 *
 * <p>Note: MockMvc dispatches on the calling thread, so the executor threads each create their own
 * MockMvc request. The shared Spring application context is thread-safe for read operations; write
 * tests are kept at a lower concurrency to avoid H2 connection-pool exhaustion (pool size = 5 in
 * {@code TestDatabaseConfig}).
 *
 * <p>Requirement: TASK-13.1.6 (Performance tests — concurrent load)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("Concurrent Request Performance Tests")
class ConcurrentRequestPerformanceTest {

    // ── Thresholds ──────────────────────────────────────────────────────────
    private static final int CONCURRENT_READERS = 10;
    private static final int CONCURRENT_WRITERS = 5;
    private static final long CONCURRENT_READ_WALL_MS = 5_000L;
    private static final long CONCURRENT_DASHBOARD_WALL_MS = 8_000L;
    private static final long EXECUTOR_AWAIT_SECONDS = 30L;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private KeyManagementService keyManagementService;

    private String token;
    private String encKey;

    /**
     * Sets up a test user and authentication token. Note: NOT annotated with {@code @Transactional}
     * so that data is visible across threads spawned by the test methods.
     */
    @BeforeEach
    void setUp() throws Exception {
        // Clean up any leftover data
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        userService.registerUser(
                UserRegistrationRequest.builder()
                        .username("conc_user")
                        .email("conc@example.com")
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
                                                                .username("conc_user")
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
                        .findByUsername("conc_user")
                        .orElseThrow(() -> new RuntimeException("Test user not found"));
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        SecretKey secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);
        encKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        // Pre-create a couple of accounts so that list/dashboard have data to return
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(
                            post("/api/v1/accounts")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Key", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    AccountRequest.builder()
                                                            .name("Conc Account " + i)
                                                            .type(AccountType.CHECKING)
                                                            .currency("USD")
                                                            .initialBalance(
                                                                    BigDecimal.valueOf(100L * i))
                                                            .build())))
                    .andExpect(status().isCreated());
        }
    }

    // ── Concurrent reads ─────────────────────────────────────────────────────

    /**
     * Issues {@value #CONCURRENT_READERS} simultaneous GET /accounts requests and verifies all
     * complete successfully within the wall-time threshold.
     *
     * <p>Requirement: TASK-13.1.6 (Concurrent reads)
     */
    @Test
    @DisplayName("10 concurrent GET /accounts all succeed within wall-time SLA")
    void concurrentAccountReadsShouldAllSucceed() throws Exception {
        final String capturedToken = token;
        final String capturedEncKey = encKey;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_READERS; i++) {
            tasks.add(
                    () -> {
                        try {
                            mockMvc.perform(
                                            get("/api/v1/accounts")
                                                    .header(
                                                            "Authorization",
                                                            "Bearer " + capturedToken)
                                                    .header("X-Encryption-Key", capturedEncKey))
                                    .andExpect(status().isOk());
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                        return null;
                    });
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_READERS);
        StopWatch sw = new StopWatch("concurrentAccountReads");
        sw.start();
        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        boolean finished = executor.awaitTermination(EXECUTOR_AWAIT_SECONDS, TimeUnit.SECONDS);
        sw.stop();

        // Verify all futures completed without throwing
        for (Future<Void> f : futures) {
            f.get(); // rethrows ExecutionException if a task threw
        }

        assertThat(finished)
                .as("Executor did not finish within %d s", EXECUTOR_AWAIT_SECONDS)
                .isTrue();

        assertThat(failureCount.get())
                .as("All concurrent account reads should succeed but %d failed", failureCount.get())
                .isZero();

        assertThat(successCount.get())
                .as("Expected %d successful reads", CONCURRENT_READERS)
                .isEqualTo(CONCURRENT_READERS);

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "%d concurrent GET /accounts must complete within %d ms but took %d ms",
                        CONCURRENT_READERS, CONCURRENT_READ_WALL_MS, elapsed)
                .isLessThanOrEqualTo(CONCURRENT_READ_WALL_MS);
    }

    /**
     * Issues {@value #CONCURRENT_READERS} simultaneous GET /dashboard requests and verifies all
     * complete successfully within the wall-time threshold.
     *
     * <p>A single warm-up request is issued first so that the net-worth snapshot for today already
     * exists in the database before concurrent threads start. Without the warm-up, all threads
     * would race to INSERT the same (user_id, snapshot_date) row, causing unique-constraint
     * violations (HTTP 500).
     *
     * <p>Requirement: TASK-13.1.6 (Concurrent dashboard reads)
     */
    @Test
    @DisplayName("10 concurrent GET /dashboard all succeed within wall-time SLA")
    void concurrentDashboardReadsShouldAllSucceed() throws Exception {
        final String capturedToken = token;
        final String capturedEncKey = encKey;

        // Warm-up: seed today's net-worth snapshot so concurrent threads only UPDATE, not INSERT
        mockMvc.perform(
                        get("/api/v1/dashboard")
                                .header("Authorization", "Bearer " + capturedToken)
                                .header("X-Encryption-Key", capturedEncKey))
                .andExpect(status().isOk());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_READERS; i++) {
            tasks.add(
                    () -> {
                        try {
                            mockMvc.perform(
                                            get("/api/v1/dashboard")
                                                    .header(
                                                            "Authorization",
                                                            "Bearer " + capturedToken)
                                                    .header("X-Encryption-Key", capturedEncKey))
                                    .andExpect(status().isOk());
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                        return null;
                    });
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_READERS);
        StopWatch sw = new StopWatch("concurrentDashboard");
        sw.start();
        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        boolean finished = executor.awaitTermination(EXECUTOR_AWAIT_SECONDS, TimeUnit.SECONDS);
        sw.stop();

        for (Future<Void> f : futures) {
            f.get();
        }

        assertThat(finished).as("Executor timed out").isTrue();
        assertThat(failureCount.get())
                .as(
                        "All concurrent dashboard reads should succeed but %d failed",
                        failureCount.get())
                .isZero();
        assertThat(successCount.get()).isEqualTo(CONCURRENT_READERS);

        long elapsed = sw.getTotalTimeMillis();
        assertThat(elapsed)
                .as(
                        "%d concurrent GET /dashboard must complete within %d ms but took %d ms",
                        CONCURRENT_READERS, CONCURRENT_DASHBOARD_WALL_MS, elapsed)
                .isLessThanOrEqualTo(CONCURRENT_DASHBOARD_WALL_MS);
    }

    // ── Concurrent writes ────────────────────────────────────────────────────

    /**
     * Issues {@value #CONCURRENT_WRITERS} simultaneous POST /accounts requests and verifies all
     * complete without any 5xx server errors.
     *
     * <p>Uses a lower concurrency than reads to stay within the H2 connection-pool size configured
     * in {@code TestDatabaseConfig} (pool size = 5).
     *
     * <p>Requirement: TASK-13.1.6 (Concurrent writes)
     */
    @Test
    @DisplayName("5 concurrent POST /accounts all complete without 5xx errors")
    void concurrentAccountCreatesShouldNotFail() throws Exception {
        final String capturedToken = token;
        final String capturedEncKey = encKey;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger serverErrorCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_WRITERS; i++) {
            final int idx = i;
            tasks.add(
                    () -> {
                        try {
                            int statusCode =
                                    mockMvc.perform(
                                                    post("/api/v1/accounts")
                                                            .header(
                                                                    "Authorization",
                                                                    "Bearer " + capturedToken)
                                                            .header(
                                                                    "X-Encryption-Key",
                                                                    capturedEncKey)
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    objectMapper.writeValueAsString(
                                                                            AccountRequest.builder()
                                                                                    .name(
                                                                                            "Concurrent Account "
                                                                                                    + idx)
                                                                                    .type(
                                                                                            AccountType
                                                                                                    .SAVINGS)
                                                                                    .currency("USD")
                                                                                    .initialBalance(
                                                                                            BigDecimal
                                                                                                    .valueOf(
                                                                                                            50L
                                                                                                                    * (idx
                                                                                                                            + 1)))
                                                                                    .build())))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();

                            if (statusCode >= 500) {
                                serverErrorCount.incrementAndGet();
                            } else {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            serverErrorCount.incrementAndGet();
                        }
                        return null;
                    });
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WRITERS);
        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        boolean finished = executor.awaitTermination(EXECUTOR_AWAIT_SECONDS, TimeUnit.SECONDS);

        for (Future<Void> f : futures) {
            f.get();
        }

        assertThat(finished).as("Executor timed out").isTrue();
        assertThat(serverErrorCount.get())
                .as(
                        "No concurrent account creates should return 5xx but %d did",
                        serverErrorCount.get())
                .isZero();
        assertThat(successCount.get())
                .as("Expected %d successful creates", CONCURRENT_WRITERS)
                .isEqualTo(CONCURRENT_WRITERS);
    }
}
