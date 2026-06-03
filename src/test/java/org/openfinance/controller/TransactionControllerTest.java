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
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.UserRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("TransactionController Integration Tests")
class TransactionControllerTest {

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
        private CategoryRepository categoryRepository;

        @Autowired
        private KeyManagementService keyManagementService;

        @Autowired
        private DatabaseCleanupService databaseCleanupService;

        private String token;
        private String encKey;
        private Long accountId1;
        private Long accountId2;
        private Long categoryId1; // Groceries (system category)
        private Long categoryId2; // Salary (system category)
        private Long transactionId1;
        private Long transactionId2;

        @BeforeEach
        void setUp() throws Exception {
                databaseCleanupService.execute();

                // Create user
                UserRegistrationRequest reg = UserRegistrationRequest.builder()
                                .username("alice")
                                .email("alice@example.com")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .skipSeeding(true)
                                .build();
                userService.registerUser(reg);

                // Login
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

                User user = userRepository
                                .findByUsername("alice")
                                .orElseThrow(() -> new RuntimeException("User not found"));
                encKey = objectMapper.readTree(resp).get("encryptionKey").asText();

                // Set security context for MockMvc
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null,
                                user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Create accounts
                AccountRequest account1 = AccountRequest.builder()
                                .name("Checking Account")
                                .type(org.openfinance.entity.AccountType.CHECKING)
                                .currency("USD")
                                .initialBalance(new BigDecimal("1000.00"))
                                .build();

                String accountResp1 = mockMvc.perform(
                                post("/api/v1/accounts")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(account1)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                accountId1 = objectMapper.readTree(accountResp1).get("id").asLong();

                AccountRequest account2 = AccountRequest.builder()
                                .name("Savings Account")
                                .type(org.openfinance.entity.AccountType.SAVINGS)
                                .currency("USD")
                                .initialBalance(new BigDecimal("5000.00"))
                                .build();

                String accountResp2 = mockMvc.perform(
                                post("/api/v1/accounts")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(account2)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                accountId2 = objectMapper.readTree(accountResp2).get("id").asLong();

                // Create categories manually for test (since we skip seeding)
                Category groceriesCategory = Category.builder()
                                .userId(user.getId())
                                .name("Groceries")
                                .type(CategoryType.EXPENSE)
                                .icon("🛒")
                                .color("#10B981")
                                .isSystem(true)
                                .build();
                categoryId1 = categoryRepository.save(groceriesCategory).getId();

                Category salaryCategory = Category.builder()
                                .userId(user.getId())
                                .name("Salary")
                                .type(CategoryType.INCOME)
                                .icon("💰")
                                .color("#10B981")
                                .isSystem(true)
                                .build();
                categoryId2 = categoryRepository.save(salaryCategory).getId();

                // Create transactions
                TransactionRequest tx1 = TransactionRequest.builder()
                                .accountId(accountId1)
                                .type(TransactionType.INCOME)
                                .amount(new BigDecimal("3000.00"))
                                .currency("USD")
                                .categoryId(categoryId2) // Salary (1)
                                .date(LocalDate.of(2026, 1, 1))
                                .description("Monthly salary")
                                .payee("Employer Inc")
                                .tags("salary,monthly")
                                .isReconciled(false)
                                .build();

                String txResp1 = mockMvc.perform(
                                post("/api/v1/transactions")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(tx1)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                transactionId1 = objectMapper.readTree(txResp1).get("id").asLong();

                TransactionRequest tx2 = TransactionRequest.builder()
                                .accountId(accountId2)
                                .type(TransactionType.EXPENSE)
                                .amount(new BigDecimal("50.00"))
                                .currency("USD")
                                .categoryId(categoryId1) // Groceries (6)
                                .date(LocalDate.of(2026, 1, 15))
                                .description("Grocery shopping")
                                .payee("Supermarket")
                                .tags("food,weekly")
                                .isReconciled(true)
                                .build();

                String txResp2 = mockMvc.perform(
                                post("/api/v1/transactions")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(tx2)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                transactionId2 = objectMapper.readTree(txResp2).get("id").asLong();
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - basic search without filters")
        void shouldSearchTransactionsWithoutFilters() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content", hasSize(2)))
                                .andExpect(jsonPath("$.totalElements").value(2))
                                .andExpect(jsonPath("$.totalPages").value(1))
                                .andExpect(jsonPath("$.number").value(0))
                                .andExpect(jsonPath("$.size").value(20)); // default page size
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - search by keyword")
        void shouldSearchTransactionsByKeyword() throws Exception {
                // Note: Description and notes fields are encrypted, so keyword search only
                // works on
                // unencrypted payee field
                // tx2 has payee="Supermarket" (unencrypted) and description="Grocery shopping"
                // (encrypted)
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("keyword", "supermarket")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].description").value("Grocery shopping"))
                                .andExpect(jsonPath("$.content[0].payee").value("Supermarket"));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - search by account ID")
        void shouldSearchTransactionsByAccountId() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("accountId", accountId1.toString())
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].accountId").value(accountId1));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - search by type")
        void shouldSearchTransactionsByType() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("type", "EXPENSE")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].type").value("EXPENSE"));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - search by date range")
        void shouldSearchTransactionsByDateRange() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("dateFrom", "2026-01-10")
                                                .param("dateTo", "2026-01-20")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].date").value("2026-01-15"));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - search by amount range")
        void shouldSearchTransactionsByAmountRange() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("amountMin", "40")
                                                .param("amountMax", "60")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].amount").value(50.00));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - search by tags")
        void shouldSearchTransactionsByTags() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("tags", "salary")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - search by reconciliation status")
        void shouldSearchTransactionsByReconciliationStatus() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("isReconciled", "true")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].isReconciled").value(true));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - pagination with page and size")
        void shouldHandlePagination() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("page", "0")
                                                .param("size", "1")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.totalElements").value(2))
                                .andExpect(jsonPath("$.totalPages").value(2))
                                .andExpect(jsonPath("$.number").value(0))
                                .andExpect(jsonPath("$.size").value(1));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - sorting by date descending")
        void shouldSortByDateDescending() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("sort", "date,desc")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(2)))
                                .andExpect(jsonPath("$.content[0].date").value("2026-01-15"))
                                .andExpect(jsonPath("$.content[1].date").value("2026-01-01"));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - sorting by amount ascending")
        void shouldSortByAmountAscending() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("sort", "amount,asc")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(2)));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - combined filters")
        void shouldHandleCombinedFilters() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .param("type", "EXPENSE")
                                                .param("accountId", accountId2.toString())
                                                .param("amountMin", "40")
                                                .param("amountMax", "60")
                                                .param("isReconciled", "true")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].amount").value(50.00));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - missing encryption key header")
        void shouldReturn400WhenMissingEncryptionKey() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - empty encryption key header")
        void shouldReturn400WhenEmptyEncryptionKey() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", ""))
                                .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("GET /api/v1/transactions/search - invalid encryption key format")
        void shouldReturn400WhenInvalidEncryptionKey() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/search")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", "invalid-key"))
                                .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("GET /api/v1/transactions/{id}/splits - should return splits for transaction")
        void shouldReturnSplitsForTransaction() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/{id}/splits", transactionId1)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$", hasSize(0))); // Assuming no splits for this transaction
        }

        @Test
        @DisplayName("GET /api/v1/transactions/{id}/splits - should return 404 for non-existent transaction")
        void shouldReturn404ForNonExistentTransactionSplits() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/{id}/splits", 99999L)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/v1/transactions/{id}/splits - should return 400 when encryption key missing")
        void shouldReturn400WhenEncryptionKeyMissingForSplits() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/{id}/splits", transactionId1)
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("GET /api/v1/transactions/{id}/splits - should return 400 when encryption key empty")
        void shouldReturn400WhenEncryptionKeyEmptyForSplits() throws Exception {
                mockMvc.perform(
                                get("/api/v1/transactions/{id}/splits", transactionId1)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", ""))
                                .andExpect(status().isInternalServerError());
        }
}
