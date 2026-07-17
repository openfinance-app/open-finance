package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.*;
import org.openfinance.entity.*;
import org.openfinance.repository.*;
import org.openfinance.security.EncryptionService;
import org.openfinance.security.KeyManagementService;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.service.UserService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for DashboardController.
 *
 * <p>Tests cover: - GET /api/v1/dashboard - Complete dashboard summary - GET
 * /api/v1/dashboard/accounts - Account summaries - GET /api/v1/dashboard/cashflow - Cash flow
 * analysis - GET /api/v1/dashboard/spending - Spending by category - Authentication and
 * authorization - Encryption key validation - Error handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("DashboardController Integration Tests")
class DashboardControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private AccountRepository accountRepository;

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private CategoryRepository categoryRepository;

    @Autowired private KeyManagementService keyManagementService;

    @Autowired private EncryptionService encryptionService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;
    private String encKey;
    private User testUser;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up database
        databaseCleanupService.execute();

        // Register user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("alice")
                        .email("alice@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        // Login to get token
        LoginRequest login =
                LoginRequest.builder()
                        .username("alice")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        token = objectMapper.readTree(resp).get("token").asText();
        encKey = objectMapper.readTree(resp).get("encryptionKey").asText();

        // Derive the encryption key
        testUser =
                userRepository
                        .findByUsername("alice")
                        .orElseThrow(() -> new RuntimeException("User not found"));
        byte[] salt = Base64.getDecoder().decode(testUser.getMasterPasswordSalt());
        secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);
    }

    // ==================== GET /api/v1/dashboard Tests ====================

    @Test
    @DisplayName("GET /api/v1/dashboard - should return dashboard summary successfully")
    void shouldGetDashboardSummarySuccessfully() throws Exception {
        // Create test accounts
        createAccount("Checking", AccountType.CHECKING, new BigDecimal("5000"));
        createAccount("Savings", AccountType.SAVINGS, new BigDecimal("10000"));

        // Create test transactions
        Account checking = accountRepository.findByUserIdAndIsActive(testUser.getId(), true).get(0);
        createTransaction(
                checking.getId(),
                TransactionType.INCOME,
                new BigDecimal("3000"),
                LocalDate.now().minusDays(5));
        createTransaction(
                checking.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("500"),
                LocalDate.now().minusDays(3));

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/dashboard")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netWorth").exists())
                .andExpect(jsonPath("$.netWorth.netWorth").isNumber())
                .andExpect(jsonPath("$.netWorth.totalAssets").isNumber())
                .andExpect(jsonPath("$.netWorth.totalLiabilities").isNumber())
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.recentTransactions").isArray())
                .andExpect(jsonPath("$.totalAccounts").value(2))
                .andExpect(jsonPath("$.totalTransactions", greaterThanOrEqualTo(2)))
                .andExpect(
                        jsonPath("$.baseCurrency")
                                .value("USD")); // User.baseCurrency defaults to USD
    }

    @Test
    @DisplayName("GET /api/v1/dashboard - should return 403 when not authenticated")
    void shouldReturn403WhenNotAuthenticatedForDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard - should return 401 when encryption session is missing")
    void shouldReturn500WhenEncryptionKeyMissingForDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard - should return 401 when encryption session is invalid")
    void shouldReturn400WhenEncryptionKeyInvalidForDashboard() throws Exception {
        mockMvc.perform(
                        get("/api/v1/dashboard")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", "invalid_key"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/v1/dashboard/accounts Tests
    // ====================

    @Test
    @DisplayName("GET /api/v1/dashboard/accounts - should return account summaries successfully")
    void shouldGetAccountSummariesSuccessfully() throws Exception {
        // Create test accounts
        createAccount("Checking Account", AccountType.CHECKING, new BigDecimal("2500"));
        createAccount("Savings Account", AccountType.SAVINGS, new BigDecimal("15000"));
        createAccount("Investment Account", AccountType.INVESTMENT, new BigDecimal("50000"));

        // Act & Assert
        // Note: Accounts are sorted by balance descending, so Investment (50000) comes
        // first
        mockMvc.perform(
                        get("/api/v1/dashboard/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Investment Account")) // Highest balance
                // first
                .andExpect(jsonPath("$[0].balance").value(50000))
                .andExpect(jsonPath("$[1].name").value("Savings Account"))
                .andExpect(jsonPath("$[1].balance").value(15000))
                .andExpect(jsonPath("$[2].name").value("Checking Account")) // Lowest balance last
                .andExpect(jsonPath("$[2].balance").value(2500))
                .andExpect(jsonPath("$[0].type").exists())
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/accounts - should return empty list when no accounts exist")
    void shouldReturnEmptyListWhenNoAccounts() throws Exception {
        mockMvc.perform(
                        get("/api/v1/dashboard/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName(
            "GET /api/v1/dashboard/accounts - should return 401 when encryption session is missing")
    void shouldReturn500WhenEncryptionKeyMissingForAccounts() throws Exception {
        mockMvc.perform(
                        get("/api/v1/dashboard/accounts")
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/v1/dashboard/cashflow Tests
    // ====================

    @Test
    @DisplayName("GET /api/v1/dashboard/cashflow - should return cash flow with default period")
    void shouldGetCashFlowWithDefaultPeriod() throws Exception {
        // Create test account and transactions
        Account account = createAccount("Checking", AccountType.CHECKING, new BigDecimal("5000"));
        createTransaction(
                account.getId(),
                TransactionType.INCOME,
                new BigDecimal("4000"),
                LocalDate.now().minusDays(10));
        createTransaction(
                account.getId(),
                TransactionType.INCOME,
                new BigDecimal("500"),
                LocalDate.now().minusDays(5));
        createTransaction(
                account.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("1200"),
                LocalDate.now().minusDays(8));
        createTransaction(
                account.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("300"),
                LocalDate.now().minusDays(2));

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/dashboard/cashflow")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").isNumber())
                .andExpect(jsonPath("$.expenses").isNumber())
                .andExpect(jsonPath("$.netCashFlow").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/cashflow - should return cash flow with custom period")
    void shouldGetCashFlowWithCustomPeriod() throws Exception {
        // Create test account and transactions
        Account account = createAccount("Checking", AccountType.CHECKING, new BigDecimal("5000"));
        createTransaction(
                account.getId(),
                TransactionType.INCOME,
                new BigDecimal("2000"),
                LocalDate.now().minusDays(5));
        createTransaction(
                account.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("800"),
                LocalDate.now().minusDays(3));

        // Act & Assert - query for last 7 days
        mockMvc.perform(
                        get("/api/v1/dashboard/cashflow")
                                .param("period", "7")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").isNumber())
                .andExpect(jsonPath("$.expenses").isNumber())
                .andExpect(jsonPath("$.netCashFlow").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/cashflow - should return 400 when period is zero")
    void shouldReturn400WhenPeriodIsZeroForCashFlow() throws Exception {
        mockMvc.perform(
                        get("/api/v1/dashboard/cashflow")
                                .param("period", "0")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/cashflow - should return 400 when period is negative")
    void shouldReturn400WhenPeriodIsNegativeForCashFlow() throws Exception {
        mockMvc.perform(
                        get("/api/v1/dashboard/cashflow")
                                .param("period", "-10")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /api/v1/dashboard/spending Tests
    // ====================

    @Test
    @DisplayName(
            "GET /api/v1/dashboard/spending - should return spending by category with default period")
    void shouldGetSpendingByCategoryWithDefaultPeriod() throws Exception {
        // Create test account, categories, and transactions
        Account account = createAccount("Checking", AccountType.CHECKING, new BigDecimal("10000"));
        Category food = createCategory("Food", CategoryType.EXPENSE);
        Category transport = createCategory("Transport", CategoryType.EXPENSE);

        createTransaction(
                account.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("500"),
                LocalDate.now().minusDays(5),
                food.getId());
        createTransaction(
                account.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("200"),
                LocalDate.now().minusDays(3),
                transport.getId());
        createTransaction(
                account.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("300"),
                LocalDate.now().minusDays(1),
                food.getId());

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/dashboard/spending")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    @DisplayName(
            "GET /api/v1/dashboard/spending - should return spending by category with custom period")
    void shouldGetSpendingByCategoryWithCustomPeriod() throws Exception {
        // Create test account and transactions
        Account account = createAccount("Checking", AccountType.CHECKING, new BigDecimal("5000"));
        createTransaction(
                account.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("400"),
                LocalDate.now().minusDays(2));

        // Act & Assert - query for last 7 days
        mockMvc.perform(
                        get("/api/v1/dashboard/spending")
                                .param("period", "7")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/spending - should return 400 when period is zero")
    void shouldReturn400WhenPeriodIsZeroForSpending() throws Exception {
        mockMvc.perform(
                        get("/api/v1/dashboard/spending")
                                .param("period", "0")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/spending - should return empty map when no expenses exist")
    void shouldReturnEmptyMapWhenNoExpenses() throws Exception {
        mockMvc.perform(
                        get("/api/v1/dashboard/spending")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ==================== Helper Methods ====================

    private Account createAccount(String name, AccountType type, BigDecimal balance) {
        String encryptedName = encryptionService.encrypt(name, secretKey);
        Account account =
                Account.builder()
                        .userId(testUser.getId())
                        .name(encryptedName)
                        .type(type)
                        .balance(balance)
                        .currency("EUR")
                        .isActive(true)
                        .build();
        return accountRepository.save(account);
    }

    private Category createCategory(String name, CategoryType type) {
        String encryptedName = encryptionService.encrypt(name, secretKey);
        Category category =
                Category.builder()
                        .userId(testUser.getId())
                        .name(encryptedName)
                        .type(type)
                        .icon("icon")
                        .color("#000000")
                        .isSystem(false)
                        .build();
        return categoryRepository.save(category);
    }

    private Transaction createTransaction(
            Long accountId, TransactionType type, BigDecimal amount, LocalDate date) {
        return createTransaction(accountId, type, amount, date, null);
    }

    private Transaction createTransaction(
            Long accountId,
            TransactionType type,
            BigDecimal amount,
            LocalDate date,
            Long categoryId) {
        String encryptedDescription =
                encryptionService.encrypt("Transaction description", secretKey);
        Transaction transaction =
                Transaction.builder()
                        .userId(testUser.getId())
                        .accountId(accountId)
                        .type(type)
                        .amount(amount)
                        .date(date)
                        .description(encryptedDescription)
                        .categoryId(categoryId)
                        .currency("EUR")
                        .isDeleted(false)
                        .build();
        return transactionRepository.save(transaction);
    }
}
