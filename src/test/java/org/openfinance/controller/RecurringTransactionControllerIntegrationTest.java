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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.RecurringTransactionRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.*;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
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
 * Integration tests for RecurringTransactionController.
 *
 * <p>
 * Tests cover:
 *
 * <ul>
 * <li>Create recurring transaction (POST /api/v1/recurring-transactions)
 * <li>List all recurring transactions (GET /api/v1/recurring-transactions)
 * <li>List active recurring transactions (GET
 * /api/v1/recurring-transactions/active)
 * <li>List due recurring transactions (GET /api/v1/recurring-transactions/due)
 * <li>Get recurring transaction by ID (GET /api/v1/recurring-transactions/{id})
 * <li>Update recurring transaction (PUT /api/v1/recurring-transactions/{id})
 * <li>Delete recurring transaction (DELETE /api/v1/recurring-transactions/{id})
 * <li>Pause recurring transaction (POST
 * /api/v1/recurring-transactions/{id}/pause)
 * <li>Resume recurring transaction (POST
 * /api/v1/recurring-transactions/{id}/resume)
 * <li>Manual processing trigger (POST /api/v1/recurring-transactions/process)
 * <li>Authentication and authorization
 * <li>Validation and error handling
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("RecurringTransactionController Integration Tests")
class RecurringTransactionControllerIntegrationTest {

        @MockBean
        private OperationHistoryService operationHistoryService;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserService userService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private AccountRepository accountRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private RecurringTransactionRepository recurringTransactionRepository;

        @Autowired
        private KeyManagementService keyManagementService;

        @Autowired
        private DatabaseCleanupService databaseCleanupService;

        @Autowired
        private EncryptionService encryptionService;

        private String token;
        private String encKey;
        private Long userId;
        private SecretKey secretKey;
        private Account checkingAccount;
        private Account savingsAccount;
        private Category rentCategory;
        private Category salaryCategory;

        @BeforeEach
        void setUp() throws Exception {
                // Clean up everything to avoid referential integrity issues
                databaseCleanupService.execute();

                // Register user
                UserRegistrationRequest reg = UserRegistrationRequest.builder()
                                .username("alice")
                                .email("alice@example.com")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .skipSeeding(true)
                                .build();
                userService.registerUser(reg);

                // Login and get token
                LoginRequest login = LoginRequest.builder()
                                .username("alice")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .build();

                String resp = mockMvc.perform(
                                post("/api/v1/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(login)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                token = objectMapper.readTree(resp).get("token").asText();
                encKey = objectMapper.readTree(resp).get("encryptionKey").asText();

                // Derive encryption key manually (same as AttachmentControllerIntegrationTest)
                User user = userRepository
                                .findByUsername("alice")
                                .orElseThrow(() -> new RuntimeException("User not found"));
                userId = user.getId();
                byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
                secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);

                // Create test accounts through API (so they get properly encrypted)
                String checkingAccountJson = """
                                {
                                    "name": "Checking Account",
                                    "type": "CHECKING",
                                    "initialBalance": 5000.00,
                                    "currency": "USD"
                                }
                                """;
                String checkingResp = mockMvc.perform(
                                post("/api/v1/accounts")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(checkingAccountJson))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                Long checkingAccountId = objectMapper.readTree(checkingResp).get("id").asLong();
                EncryptionContext.setKey(secretKey);
                try {
                        checkingAccount = accountRepository.findById(checkingAccountId).orElseThrow();
                } finally {
                        EncryptionContext.clear();
                }

                String savingsAccountJson = """
                                {
                                    "name": "Savings Account",
                                    "type": "SAVINGS",
                                    "initialBalance": 10000.00,
                                    "currency": "USD"
                                }
                                """;
                String savingsResp = mockMvc.perform(
                                post("/api/v1/accounts")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(savingsAccountJson))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                Long savingsAccountId = objectMapper.readTree(savingsResp).get("id").asLong();
                EncryptionContext.setKey(secretKey);
                try {
                        savingsAccount = accountRepository.findById(savingsAccountId).orElseThrow();
                } finally {
                        EncryptionContext.clear();
                }

                // Create test categories through API (so they get properly encrypted)
                String rentCategoryJson = """
                                {
                                    "name": "Housing",
                                    "type": "EXPENSE",
                                    "icon": "home",
                                    "color": "#FF6B6B"
                                }
                                """;
                String rentResp = mockMvc.perform(
                                post("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(rentCategoryJson))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                Long rentCategoryId = objectMapper.readTree(rentResp).get("id").asLong();
                rentCategory = categoryRepository.findById(rentCategoryId).orElseThrow();

                String salaryCategoryJson = """
                                {
                                    "name": "Salary",
                                    "type": "INCOME",
                                    "icon": "dollar-sign",
                                    "color": "#4ECDC4"
                                }
                                """;
                String salaryResp = mockMvc.perform(
                                post("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(salaryCategoryJson))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                Long salaryCategoryId = objectMapper.readTree(salaryResp).get("id").asLong();
                salaryCategory = categoryRepository.findById(salaryCategoryId).orElseThrow();
        }

        // ========== CREATE RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("POST /api/v1/recurring-transactions - Create Recurring Transaction")
        class CreateRecurringTransactionTests {

                @Test
                @DisplayName("Should create recurring transaction successfully")
                void shouldCreateRecurringTransaction() throws Exception {
                        // Given
                        RecurringTransactionRequest request = RecurringTransactionRequest.builder()
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1200.00"))
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .description("Monthly rent payment")
                                        .notes("Due on the 1st of each month")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(1))
                                        .build();

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(objectMapper.writeValueAsString(request)))
                                        .andDo(print())
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.id").isNumber())
                                        .andExpect(jsonPath("$.accountId").value(checkingAccount.getId()))
                                        .andExpect(jsonPath("$.accountName").value("Checking Account"))
                                        .andExpect(jsonPath("$.type").value("EXPENSE"))
                                        .andExpect(jsonPath("$.amount").value(1200.00))
                                        .andExpect(jsonPath("$.currency").value("USD"))
                                        .andExpect(jsonPath("$.categoryId").value(rentCategory.getId()))
                                        .andExpect(jsonPath("$.categoryName").value("Housing"))
                                        .andExpect(jsonPath("$.categoryIcon").value("home"))
                                        .andExpect(jsonPath("$.categoryColor").value("#FF6B6B"))
                                        .andExpect(jsonPath("$.description").value("Monthly rent payment"))
                                        .andExpect(jsonPath("$.notes").value("Due on the 1st of each month"))
                                        .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                                        .andExpect(jsonPath("$.frequencyDisplayName").value("Monthly"))
                                        .andExpect(jsonPath("$.isActive").value(true))
                                        .andExpect(jsonPath("$.createdAt").isString())
                                        .andExpect(jsonPath("$.updatedAt").isString());
                }

                @Test
                @DisplayName("Should create TRANSFER recurring transaction successfully")
                void shouldCreateTransferRecurringTransaction() throws Exception {
                        // Given - Transfer from checking to savings
                        RecurringTransactionRequest request = RecurringTransactionRequest.builder()
                                        .accountId(checkingAccount.getId())
                                        .toAccountId(savingsAccount.getId())
                                        .type(TransactionType.TRANSFER)
                                        .amount(new BigDecimal("500.00"))
                                        .currency("USD")
                                        .description("Monthly savings transfer")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(15))
                                        .build();

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(objectMapper.writeValueAsString(request)))
                                        .andDo(print())
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.type").value("TRANSFER"))
                                        .andExpect(jsonPath("$.accountId").value(checkingAccount.getId()))
                                        .andExpect(jsonPath("$.toAccountId").value(savingsAccount.getId()))
                                        .andExpect(jsonPath("$.toAccountName").value("Savings Account"))
                                        .andExpect(jsonPath("$.categoryId").doesNotExist());
                }

                @Test
                @DisplayName("Should return 400 when missing encryption key")
                void shouldReturn400WhenMissingEncryptionKey() throws Exception {
                        // Given
                        RecurringTransactionRequest request = RecurringTransactionRequest.builder()
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("100.00"))
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(1))
                                        .build();

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions")
                                                        .header("Authorization", "Bearer " + token)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(objectMapper.writeValueAsString(request)))
                                        .andDo(print())
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("Should return 400 when invalid request body")
                void shouldReturn400WhenInvalidRequestBody() throws Exception {
                        // Given - missing required fields (accountId, amount)
                        String invalidRequest = "{\"type\":\"EXPENSE\",\"frequency\":\"MONTHLY\"}";

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(invalidRequest))
                                        .andDo(print())
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("Should return 403 when missing JWT token")
                void shouldReturn403WhenMissingToken() throws Exception {
                        // Given
                        RecurringTransactionRequest request = RecurringTransactionRequest.builder()
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("100.00"))
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(1))
                                        .build();

                        // When/Then - Spring Security returns 403 for missing JWT
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions")
                                                        .header("X-Encryption-Session", encKey)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(objectMapper.writeValueAsString(request)))
                                        .andDo(print())
                                        .andExpect(status().isForbidden());
                }
        }

        // ========== LIST ALL RECURRING TRANSACTIONS TESTS ==========

        @Nested
        @DisplayName("GET /api/v1/recurring-transactions - List All Recurring Transactions")
        class ListAllRecurringTransactionsTests {

                @Test
                @DisplayName("Should list all recurring transactions")
                void shouldListAllRecurringTransactions() throws Exception {
                        // Given - create 2 recurring transactions
                        createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);
                        createRecurringTransaction(
                                        TransactionType.INCOME,
                                        salaryCategory.getId(),
                                        RecurringFrequency.BIWEEKLY,
                                        true);

                        // When/Then
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(2)))
                                        .andExpect(jsonPath("$[0].id").isNumber())
                                        .andExpect(jsonPath("$[1].id").isNumber());
                }

                @Test
                @DisplayName("Should filter recurring transactions by frequency")
                void shouldFilterByFrequency() throws Exception {
                        // Given - create 3 recurring transactions with different frequencies
                        createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);
                        createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);
                        createRecurringTransaction(
                                        TransactionType.INCOME,
                                        salaryCategory.getId(),
                                        RecurringFrequency.WEEKLY,
                                        true);

                        // When/Then - filter for MONTHLY only
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions")
                                                        .param("frequency", "MONTHLY")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(2)))
                                        .andExpect(jsonPath("$[0].frequency").value("MONTHLY"))
                                        .andExpect(jsonPath("$[1].frequency").value("MONTHLY"));
                }

                @Test
                @DisplayName("Should return empty list when no recurring transactions exist")
                void shouldReturnEmptyListWhenNoRecurringTransactions() throws Exception {
                        // Given - no recurring transactions

                        // When/Then
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                }
        }

        // ========== LIST ACTIVE RECURRING TRANSACTIONS TESTS ==========

        @Nested
        @DisplayName("GET /api/v1/recurring-transactions/active - List Active Recurring Transactions")
        class ListActiveRecurringTransactionsTests {

                @Test
                @DisplayName("Should list only active recurring transactions")
                void shouldListOnlyActiveRecurringTransactions() throws Exception {
                        // Given - create 1 active and 1 paused recurring transaction
                        createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);
                        createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.WEEKLY,
                                        false);

                        // When/Then
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/active")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(1)))
                                        .andExpect(jsonPath("$[0].isActive").value(true));
                }

                @Test
                @DisplayName("Should return empty list when no active recurring transactions")
                void shouldReturnEmptyListWhenNoActiveRecurringTransactions() throws Exception {
                        // Given - create only paused recurring transactions
                        createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        false);

                        // When/Then
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/active")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                }
        }

        // ========== LIST DUE RECURRING TRANSACTIONS TESTS ==========

        @Nested
        @DisplayName("GET /api/v1/recurring-transactions/due - List Due Recurring Transactions")
        class ListDueRecurringTransactionsTests {

                @Test
                @DisplayName("Should list due recurring transactions")
                void shouldListDueRecurringTransactions() throws Exception {
                        // Given - create 1 due (nextOccurrence = today) and 1 future recurring
                        // transaction
                        RecurringTransaction dueTransaction = RecurringTransaction.builder()
                                        .userId(userId)
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("100.00"))
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .description(encryptionService.encrypt("Due transaction", secretKey))
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now())
                                        .isActive(true)
                                        .build();
                        recurringTransactionRepository.save(dueTransaction);

                        RecurringTransaction futureTransaction = RecurringTransaction.builder()
                                        .userId(userId)
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("50.00"))
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .description(encryptionService.encrypt("Future transaction", secretKey))
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(10))
                                        .isActive(true)
                                        .build();
                        recurringTransactionRepository.save(futureTransaction);

                        // When/Then - should return only the due transaction
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/due")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(1)))
                                        .andExpect(jsonPath("$[0].isDue").value(true));
                }

                @Test
                @DisplayName("Should return empty list when no due recurring transactions")
                void shouldReturnEmptyListWhenNoDueRecurringTransactions() throws Exception {
                        // Given - create only future recurring transactions
                        createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);

                        // When/Then
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/due")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                }
        }

        // ========== GET RECURRING TRANSACTION BY ID TESTS ==========

        @Nested
        @DisplayName("GET /api/v1/recurring-transactions/{id} - Get Recurring Transaction by ID")
        class GetRecurringTransactionByIdTests {

                @Test
                @DisplayName("Should get recurring transaction by ID")
                void shouldGetRecurringTransactionById() throws Exception {
                        // Given
                        RecurringTransaction saved = createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);

                        // When/Then
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/{id}", saved.getId())
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id").value(saved.getId()))
                                        .andExpect(jsonPath("$.accountId").value(checkingAccount.getId()))
                                        .andExpect(jsonPath("$.type").value("EXPENSE"))
                                        .andExpect(jsonPath("$.frequency").value("MONTHLY"));
                }

                @Test
                @DisplayName("Should return 404 when recurring transaction not found")
                void shouldReturn404WhenNotFound() throws Exception {
                        // Given - non-existent ID
                        Long nonExistentId = 99999L;

                        // When/Then
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/{id}", nonExistentId)
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isNotFound());
                }

                @Test
                @DisplayName("Should return 404 when accessing another user's recurring transaction")
                void shouldReturn404WhenUnauthorized() throws Exception {
                        // Given - create another user and their recurring transaction
                        UserRegistrationRequest reg2 = UserRegistrationRequest.builder()
                                        .username("bob")
                                        .email("bob@example.com")
                                        .password("password456")
                                        .masterPassword("master456")
                                        .build();
                        userService.registerUser(reg2);

                        User user2 = userRepository
                                        .findByUsername("bob")
                                        .orElseThrow(() -> new RuntimeException("User2 not found"));

                        Account user2Account = Account.builder()
                                        .userId(user2.getId())
                                        .name("Bob's Account")
                                        .type(AccountType.CHECKING)
                                        .balance(BigDecimal.ZERO)
                                        .currency("USD")
                                        .isActive(true)
                                        .build();
                        user2Account = accountRepository.save(user2Account);

                        Category user2Category = Category.builder()
                                        .userId(user2.getId())
                                        .name("Bob's Category")
                                        .type(CategoryType.EXPENSE)
                                        .icon("tag")
                                        .color("#000000")
                                        .build();
                        user2Category = categoryRepository.save(user2Category);

                        RecurringTransaction user2Transaction = RecurringTransaction.builder()
                                        .userId(user2.getId())
                                        .accountId(user2Account.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("200.00"))
                                        .currency("USD")
                                        .categoryId(user2Category.getId())
                                        .description("encrypted-user2-transaction")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(5))
                                        .isActive(true)
                                        .build();
                        user2Transaction = recurringTransactionRepository.save(user2Transaction);

                        // When/Then - User1 (alice) tries to access User2 (bob)'s recurring transaction
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/{id}", user2Transaction.getId())
                                                        .header("Authorization", "Bearer " + token) // alice's token
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isNotFound()); // 404 not 403
                }
        }

        // ========== UPDATE RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("PUT /api/v1/recurring-transactions/{id} - Update Recurring Transaction")
        class UpdateRecurringTransactionTests {

                @Test
                @DisplayName("Should update recurring transaction successfully")
                void shouldUpdateRecurringTransaction() throws Exception {
                        // Given
                        RecurringTransaction saved = createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);

                        RecurringTransactionRequest updateRequest = RecurringTransactionRequest.builder()
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("1300.00")) // Updated amount
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .description("Updated monthly rent") // Updated description
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(2))
                                        .build();

                        // When/Then
                        mockMvc.perform(
                                        put("/api/v1/recurring-transactions/{id}", saved.getId())
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(objectMapper.writeValueAsString(updateRequest)))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id").value(saved.getId()))
                                        .andExpect(jsonPath("$.amount").value(1300.00))
                                        .andExpect(jsonPath("$.description").value("Updated monthly rent"));
                }

                @Test
                @DisplayName("Should return 404 when updating non-existent recurring transaction")
                void shouldReturn404WhenUpdatingNonExistent() throws Exception {
                        // Given
                        Long nonExistentId = 99999L;
                        RecurringTransactionRequest updateRequest = RecurringTransactionRequest.builder()
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("100.00"))
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .description("Updated description")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now().plusDays(1))
                                        .build();

                        // When/Then
                        mockMvc.perform(
                                        put("/api/v1/recurring-transactions/{id}", nonExistentId)
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(objectMapper.writeValueAsString(updateRequest)))
                                        .andDo(print())
                                        .andExpect(status().isNotFound());
                }
        }

        // ========== DELETE RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("DELETE /api/v1/recurring-transactions/{id} - Delete Recurring Transaction")
        class DeleteRecurringTransactionTests {

                @Test
                @DisplayName("Should delete recurring transaction successfully")
                void shouldDeleteRecurringTransaction() throws Exception {
                        // Given
                        RecurringTransaction saved = createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);

                        // When/Then
                        mockMvc.perform(
                                        delete("/api/v1/recurring-transactions/{id}", saved.getId())
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isNoContent());

                        // Verify deletion
                        mockMvc.perform(
                                        get("/api/v1/recurring-transactions/{id}", saved.getId())
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andExpect(status().isNotFound());
                }

                @Test
                @DisplayName("Should return 404 when deleting non-existent recurring transaction")
                void shouldReturn404WhenDeletingNonExistent() throws Exception {
                        // Given
                        Long nonExistentId = 99999L;

                        // When/Then
                        mockMvc.perform(
                                        delete("/api/v1/recurring-transactions/{id}", nonExistentId)
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isNotFound());
                }
        }

        // ========== PAUSE RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("POST /api/v1/recurring-transactions/{id}/pause - Pause Recurring Transaction")
        class PauseRecurringTransactionTests {

                @Test
                @DisplayName("Should pause recurring transaction successfully")
                void shouldPauseRecurringTransaction() throws Exception {
                        // Given - active recurring transaction
                        RecurringTransaction saved = createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        true);

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions/{id}/pause", saved.getId())
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id").value(saved.getId()))
                                        .andExpect(jsonPath("$.isActive").value(false));
                }

                @Test
                @DisplayName("Should return 404 when pausing non-existent recurring transaction")
                void shouldReturn404WhenPausingNonExistent() throws Exception {
                        // Given
                        Long nonExistentId = 99999L;

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions/{id}/pause", nonExistentId)
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isNotFound());
                }
        }

        // ========== RESUME RECURRING TRANSACTION TESTS ==========

        @Nested
        @DisplayName("POST /api/v1/recurring-transactions/{id}/resume - Resume Recurring Transaction")
        class ResumeRecurringTransactionTests {

                @Test
                @DisplayName("Should resume recurring transaction successfully")
                void shouldResumeRecurringTransaction() throws Exception {
                        // Given - paused recurring transaction
                        RecurringTransaction saved = createRecurringTransaction(
                                        TransactionType.EXPENSE,
                                        rentCategory.getId(),
                                        RecurringFrequency.MONTHLY,
                                        false);

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions/{id}/resume", saved.getId())
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id").value(saved.getId()))
                                        .andExpect(jsonPath("$.isActive").value(true));
                }

                @Test
                @DisplayName("Should return 404 when resuming non-existent recurring transaction")
                void shouldReturn404WhenResumingNonExistent() throws Exception {
                        // Given
                        Long nonExistentId = 99999L;

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions/{id}/resume", nonExistentId)
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isNotFound());
                }
        }

        // ========== MANUAL PROCESSING TRIGGER TESTS ==========

        @Nested
        @DisplayName("POST /api/v1/recurring-transactions/process - Manual Processing Trigger")
        class ManualProcessingTests {

                @Test
                @DisplayName("Should process due recurring transactions manually")
                void shouldProcessDueRecurringTransactionsManually() throws Exception {
                        // Given - create 1 due recurring transaction
                        RecurringTransaction dueTransaction = RecurringTransaction.builder()
                                        .userId(userId)
                                        .accountId(checkingAccount.getId())
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("100.00"))
                                        .currency("USD")
                                        .categoryId(rentCategory.getId())
                                        .description("encrypted-due-transaction")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(LocalDate.now())
                                        .isActive(true)
                                        .build();
                        recurringTransactionRepository.save(dueTransaction);

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions/process")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.processedCount").isNumber())
                                        .andExpect(jsonPath("$.failedCount").isNumber())
                                        .andExpect(jsonPath("$.errors").isArray());
                }

                @Test
                @DisplayName("Should return empty result when no due recurring transactions")
                void shouldReturnEmptyResultWhenNoDueRecurringTransactions() throws Exception {
                        // Given - no due recurring transactions

                        // When/Then
                        mockMvc.perform(
                                        post("/api/v1/recurring-transactions/process")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.processedCount").value(0))
                                        .andExpect(jsonPath("$.failedCount").value(0))
                                        .andExpect(jsonPath("$.errors", hasSize(0)));
                }
        }

        // ========== HELPER METHODS ==========

        /**
         * Helper method to create a recurring transaction for testing.
         *
         * @param type       transaction type
         * @param categoryId category ID
         * @param frequency  recurring frequency
         * @param isActive   whether the recurring transaction is active
         * @return saved recurring transaction
         */
        private RecurringTransaction createRecurringTransaction(
                        TransactionType type, Long categoryId, RecurringFrequency frequency, boolean isActive) {

                RecurringTransaction recurringTransaction = RecurringTransaction.builder()
                                .userId(userId)
                                .accountId(checkingAccount.getId())
                                .type(type)
                                .amount(new BigDecimal("100.00"))
                                .currency("USD")
                                .categoryId(categoryId)
                                .description(
                                                encryptionService.encrypt("Test recurring transaction", secretKey))
                                .frequency(frequency)
                                .nextOccurrence(LocalDate.now().plusDays(30))
                                .isActive(isActive)
                                .build();

                return recurringTransactionRepository.save(recurringTransaction);
        }
}
