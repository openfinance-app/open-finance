package org.openfinance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.PayeeRequest;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.TransactionType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the full category-to-transaction flow.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>TASK-CAT-3.3.1: Create category → use in transaction → verify auto-fill from payee
 * </ul>
 *
 * <p>Requirements:
 *
 * <ul>
 *   <li>REQ-CAT-2.4: Payee-to-category auto-fill when creating transactions
 *   <li>REQ-2.4.1: Category creation
 *   <li>REQ-2.3.1: Transaction creation
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
class CategoryTransactionFlowIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private KeyManagementService keyManagementService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    /** JWT bearer token for the test user */
    private String token;

    /** Base64-encoded AES encryption key */
    private String encKey;

    /** Account ID created in setUp */
    private Long accountId;

    @BeforeEach
    void setUp() throws Exception {
        // Clean state
        databaseCleanupService.execute();

        // Register user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("testuser")
                        .email("testuser@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        // Log in and capture token
        LoginRequest login =
                LoginRequest.builder()
                        .username("testuser")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        String loginResp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        token = objectMapper.readTree(loginResp).get("token").asText();
        encKey = objectMapper.readTree(loginResp).get("encryptionKey").asText();

        // Create a checking account for transactions
        AccountRequest accountReq =
                AccountRequest.builder()
                        .name("Test Checking")
                        .type(AccountType.CHECKING)
                        .currency("EUR")
                        .initialBalance(BigDecimal.ZERO)
                        .build();

        String accountResp =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(accountReq)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        accountId = objectMapper.readTree(accountResp).get("id").asLong();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a custom category via the API and returns its ID.
     *
     * @param name category name
     * @param type INCOME or EXPENSE
     * @param parentId optional parent category ID (null for root)
     * @return the newly created category's ID
     */
    private Long createCategory(String name, CategoryType type, Long parentId) throws Exception {
        CategoryRequest req =
                CategoryRequest.builder().name(name).type(type).parentId(parentId).build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/categories")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(resp).get("id").asLong();
    }

    /**
     * Creates a payee with a default category via the API and returns its ID.
     *
     * @param name payee name
     * @param categoryId default category ID (may be null)
     * @return the newly created payee's ID
     */
    private Long createPayee(String name, Long categoryId) throws Exception {
        PayeeRequest req = PayeeRequest.builder().name(name).categoryId(categoryId).build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/payees")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(resp).get("id").asLong();
    }

    // ── TASK-CAT-3.3.1 Tests ─────────────────────────────────────────────────

    /**
     * Full flow: create category → create transaction with that category → verify transaction
     * stores the correct category.
     *
     * <p>Requirement REQ-2.4.1, REQ-2.3.1
     */
    @Test
    void shouldCreateCategoryAndUseItInTransaction() throws Exception {
        // Step 1: Create a custom EXPENSE category
        Long categoryId = createCategory("Online Shopping", CategoryType.EXPENSE, null);

        // Step 2: Create an expense transaction using the new category
        TransactionRequest txReq =
                TransactionRequest.builder()
                        .accountId(accountId)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("49.99"))
                        .currency("EUR")
                        .categoryId(categoryId)
                        .date(java.time.LocalDate.of(2026, 1, 15))
                        .description("Purchase from store")
                        .build();

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(txReq)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.amount").value(49.99));
    }

    /**
     * Full auto-fill flow: create category → create payee with that category → create transaction
     * with that payee but NO explicit categoryId → service should auto-fill from payee's default
     * category.
     *
     * <p>Requirement REQ-CAT-2.4
     */
    @Test
    void shouldAutoFillCategoryFromPayeeWhenCreatingTransaction() throws Exception {
        // Step 1: Create an EXPENSE category
        Long categoryId = createCategory("Groceries", CategoryType.EXPENSE, null);

        // Step 2: Create a payee associated with that category
        createPayee("SuperMarket", categoryId);

        // Step 3: Create a transaction with the payee name but NO categoryId
        TransactionRequest txReq =
                TransactionRequest.builder()
                        .accountId(accountId)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("35.00"))
                        .currency("EUR")
                        .categoryId(null) // ← no explicit category
                        .payee("SuperMarket") // ← payee has default category
                        .date(java.time.LocalDate.of(2026, 1, 20))
                        .build();

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(txReq)))
                .andDo(print())
                .andExpect(status().isCreated())
                // The service should have auto-filled the category from the payee
                .andExpect(jsonPath("$.categoryId").value(categoryId));
    }

    /**
     * Verify that an explicit categoryId on the transaction takes precedence over the payee's
     * default category (no override of user's choice).
     *
     * <p>Requirement REQ-CAT-2.5
     */
    @Test
    void shouldNotOverrideExplicitCategoryWithPayeeDefault() throws Exception {
        // Step 1: Create two categories
        Long payeeDefaultCategoryId = createCategory("Groceries", CategoryType.EXPENSE, null);
        Long userChosenCategoryId = createCategory("Electronics", CategoryType.EXPENSE, null);

        // Step 2: Create a payee with the first category
        createPayee("BestBuy", payeeDefaultCategoryId);

        // Step 3: Create a transaction explicitly setting the second category
        TransactionRequest txReq =
                TransactionRequest.builder()
                        .accountId(accountId)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("299.00"))
                        .currency("EUR")
                        .categoryId(userChosenCategoryId) // ← user explicitly set this
                        .payee("BestBuy")
                        .date(java.time.LocalDate.of(2026, 1, 22))
                        .build();

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(txReq)))
                .andDo(print())
                .andExpect(status().isCreated())
                // Should keep the user's explicit category, NOT the payee's default
                .andExpect(jsonPath("$.categoryId").value(userChosenCategoryId));
    }

    /**
     * Transaction with a payee that has no default category should be created without any category
     * (categoryId remains null).
     *
     * <p>Requirement REQ-CAT-2.4
     */
    @Test
    void shouldCreateTransactionWithNullCategoryWhenPayeeHasNoDefault() throws Exception {
        // Create a payee with no default category
        createPayee("UnknownShop", null);

        TransactionRequest txReq =
                TransactionRequest.builder()
                        .accountId(accountId)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("10.00"))
                        .currency("EUR")
                        .payee("UnknownShop")
                        .date(java.time.LocalDate.of(2026, 1, 25))
                        .build();

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(txReq)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    /**
     * Category tree endpoint should include newly created categories.
     *
     * <p>Requirement REQ-CAT-3.1
     */
    @Test
    void shouldReturnNewCategoryInCategoryTree() throws Exception {
        Long categoryId = createCategory("My Custom Expenses", CategoryType.EXPENSE, null);

        mockMvc.perform(
                        get("/api/v1/categories/tree")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.id == " + categoryId + ")].name")
                                .value("My Custom Expenses"));
    }
}
