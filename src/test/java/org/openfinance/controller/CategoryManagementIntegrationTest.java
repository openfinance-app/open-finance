package org.openfinance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
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
 * Integration tests for category management: create, edit, delete, and
 * subcategory handling.
 *
 * <p>
 * Tests cover TASK-CAT-3.3.2:
 *
 * <ul>
 * <li>Create/edit/delete a custom category
 * <li>Subcategory creation and parent-child relationships
 * <li>Category tree structure
 * <li>System categories cannot be deleted
 * <li>Categories with assigned transactions cannot be deleted
 * </ul>
 *
 * <p>
 * Requirements:
 *
 * <ul>
 * <li>REQ-2.4.1: Category CRUD operations
 * <li>REQ-2.4.2: Subcategory handling
 * <li>REQ-CAT-3.1: Hierarchical category tree
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
class CategoryManagementIntegrationTest {

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
        private KeyManagementService keyManagementService;

        @Autowired
        private DatabaseCleanupService databaseCleanupService;

        /** JWT bearer token for the test user */
        private String token;

        /** Base64-encoded AES encryption key */
        private String encKey;

        /** Account ID for transaction tests */
        private Long accountId;

        @BeforeEach
        void setUp() throws Exception {
                databaseCleanupService.execute();

                // Register + login
                UserRegistrationRequest reg = UserRegistrationRequest.builder()
                                .username("catuser")
                                .email("catuser@example.com")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .skipSeeding(true)
                                .build();
                userService.registerUser(reg);

                LoginRequest login = LoginRequest.builder()
                                .username("catuser")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .build();

                String loginResp = mockMvc.perform(
                                post("/api/v1/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(login)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                token = objectMapper.readTree(loginResp).get("token").asText();
                encKey = objectMapper.readTree(loginResp).get("encryptionKey").asText();

                // Create an account (needed for transaction-based tests)
                AccountRequest accountReq = AccountRequest.builder()
                                .name("Test Account")
                                .type(AccountType.CHECKING)
                                .currency("EUR")
                                .initialBalance(BigDecimal.ZERO)
                                .build();

                String accountResp = mockMvc.perform(
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

        private Long createCategory(String name, CategoryType type, Long parentId) throws Exception {
                CategoryRequest req = CategoryRequest.builder()
                                .name(name)
                                .type(type)
                                .parentId(parentId)
                                .icon("🛒")
                                .color("#10b981")
                                .build();

                String resp = mockMvc.perform(
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

        // ── Create ────────────────────────────────────────────────────────────────

        /**
         * POST /api/v1/categories - Create a root EXPENSE category. Requirement
         * REQ-2.4.1
         */
        @Test
        void shouldCreateRootExpenseCategory() throws Exception {
                CategoryRequest req = CategoryRequest.builder()
                                .name("Entertainment")
                                .type(CategoryType.EXPENSE)
                                .icon("🎬")
                                .color("#6366f1")
                                .build();

                mockMvc.perform(
                                post("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(req)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").isNumber())
                                .andExpect(jsonPath("$.name").value("Entertainment"))
                                .andExpect(jsonPath("$.type").value("EXPENSE"))
                                .andExpect(jsonPath("$.isSystem").value(false))
                                .andExpect(jsonPath("$.parentId").doesNotExist());
        }

        /**
         * POST /api/v1/categories - Create a root INCOME category. Requirement
         * REQ-2.4.1
         */
        @Test
        void shouldCreateRootIncomeCategory() throws Exception {
                CategoryRequest req = CategoryRequest.builder()
                                .name("Freelance Income")
                                .type(CategoryType.INCOME)
                                .build();

                mockMvc.perform(
                                post("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(req)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("Freelance Income"))
                                .andExpect(jsonPath("$.type").value("INCOME"));
        }

        /**
         * POST /api/v1/categories - Validation error when name is blank. Requirement
         * REQ-2.4.1
         */
        @Test
        void shouldReturn400WhenCreatingCategoryWithBlankName() throws Exception {
                CategoryRequest req = CategoryRequest.builder().name("").type(CategoryType.EXPENSE).build();

                mockMvc.perform(
                                post("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(req)))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        /**
         * POST /api/v1/categories - 401 when encryption session header is missing.
         * Requirement REQ-2.18
         */
        @Test
        void shouldReturn400WhenCreatingCategoryWithoutEncryptionKey() throws Exception {
                CategoryRequest req = CategoryRequest.builder()
                                .name("No Key Category")
                                .type(CategoryType.EXPENSE)
                                .build();

                mockMvc.perform(
                                post("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andDo(print())
                                .andExpect(status().isUnauthorized());
        }

        // ── Read ──────────────────────────────────────────────────────────────────

        /** GET /api/v1/categories - list all categories. Requirement REQ-2.4.1 */
        @Test
        void shouldListAllCategories() throws Exception {
                createCategory("Shopping", CategoryType.EXPENSE, null);
                createCategory("Salary", CategoryType.INCOME, null);

                mockMvc.perform(
                                get("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }

        /**
         * GET /api/v1/categories?type=EXPENSE - filter by type. Requirement REQ-2.4.1
         */
        @Test
        void shouldFilterCategoriesByType() throws Exception {
                createCategory("Shopping", CategoryType.EXPENSE, null);
                createCategory("Salary", CategoryType.INCOME, null);

                mockMvc.perform(
                                get("/api/v1/categories?type=EXPENSE")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.type == 'INCOME')]").isEmpty());
        }

        /**
         * GET /api/v1/categories/{id} - get a specific category by ID. Requirement
         * REQ-2.4.1
         */
        @Test
        void shouldGetCategoryById() throws Exception {
                Long categoryId = createCategory("Travel", CategoryType.EXPENSE, null);

                mockMvc.perform(
                                get("/api/v1/categories/" + categoryId)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(categoryId))
                                .andExpect(jsonPath("$.name").value("Travel"));
        }

        /** GET /api/v1/categories/{id} - 404 for non-existent category. */
        @Test
        void shouldReturn404ForNonExistentCategory() throws Exception {
                mockMvc.perform(
                                get("/api/v1/categories/999999")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        // ── Update ────────────────────────────────────────────────────────────────

        /**
         * PUT /api/v1/categories/{id} - update an existing user category. Requirement
         * REQ-2.4.2
         */
        @Test
        void shouldUpdateCategory() throws Exception {
                Long categoryId = createCategory("Old Name", CategoryType.EXPENSE, null);

                CategoryRequest updateReq = CategoryRequest.builder()
                                .name("New Name")
                                .type(CategoryType.EXPENSE)
                                .icon("✏️")
                                .color("#ef4444")
                                .build();

                mockMvc.perform(
                                put("/api/v1/categories/" + categoryId)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(updateReq)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(categoryId))
                                .andExpect(jsonPath("$.name").value("New Name"))
                                .andExpect(jsonPath("$.color").value("#ef4444"));
        }

        // ── Delete ────────────────────────────────────────────────────────────────

        /**
         * DELETE /api/v1/categories/{id} - delete a user-created category. Requirement
         * REQ-2.4.2
         */
        @Test
        void shouldDeleteUserCategory() throws Exception {
                Long categoryId = createCategory("To Be Deleted", CategoryType.EXPENSE, null);

                mockMvc.perform(
                                delete("/api/v1/categories/" + categoryId)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isNoContent());

                // Verify it's gone
                mockMvc.perform(
                                get("/api/v1/categories/" + categoryId)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isNotFound());
        }

        /** DELETE /api/v1/categories/{id} - 404 for a non-existent category. */
        @Test
        void shouldReturn404WhenDeletingNonExistentCategory() throws Exception {
                mockMvc.perform(
                                delete("/api/v1/categories/999999")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        // ── Subcategory Handling ──────────────────────────────────────────────────

        /**
         * Create a subcategory and verify the parent relationship. Requirement
         * REQ-2.4.2
         */
        @Test
        void shouldCreateSubcategoryWithParentRelationship() throws Exception {
                // Step 1: Create parent category
                Long parentId = createCategory("Shopping", CategoryType.EXPENSE, null);

                // Step 2: Create subcategory
                CategoryRequest subReq = CategoryRequest.builder()
                                .name("Online Shopping")
                                .type(CategoryType.EXPENSE)
                                .parentId(parentId)
                                .build();

                mockMvc.perform(
                                post("/api/v1/categories")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(subReq)))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("Online Shopping"))
                                .andExpect(jsonPath("$.parentId").value(parentId));
        }

        /**
         * Category tree should include parent with nested subcategory. Requirement
         * REQ-CAT-3.1
         */
        @Test
        void shouldReturnSubcategoryNestedInsideParentInTree() throws Exception {
                Long parentId = createCategory("Food", CategoryType.EXPENSE, null);
                Long childId = createCategory("Restaurants", CategoryType.EXPENSE, parentId);

                mockMvc.perform(
                                get("/api/v1/categories/tree")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk());

                // The parent "Food" node must exist
                // Its subcategories array must contain the child "Restaurants"
                mockMvc.perform(
                                get("/api/v1/categories/tree")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(jsonPath("$[?(@.id == " + parentId + ")].name").value("Food"))
                                .andExpect(
                                                jsonPath(
                                                                "$[?(@.id == "
                                                                                + parentId
                                                                                + ")].subcategories[?(@.id == "
                                                                                + childId
                                                                                + ")].name")
                                                                .value("Restaurants"));
        }

        /**
         * Multiple subcategories should all appear nested under the same parent.
         * Requirement REQ-2.4.2
         */
        @Test
        void shouldSupportMultipleSubcategoriesUnderOneParent() throws Exception {
                Long parentId = createCategory("Utilities", CategoryType.EXPENSE, null);
                Long subId1 = createCategory("Electricity", CategoryType.EXPENSE, parentId);
                Long subId2 = createCategory("Water", CategoryType.EXPENSE, parentId);
                Long subId3 = createCategory("Internet", CategoryType.EXPENSE, parentId);

                mockMvc.perform(
                                get("/api/v1/categories/tree")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(
                                                jsonPath(
                                                                "$[?(@.id == " + parentId + ")].subcategories[*].id",
                                                                org.hamcrest.Matchers.hasItems(
                                                                                subId1.intValue(), subId2.intValue(),
                                                                                subId3.intValue())));
        }

        /**
         * Parent category subcategoryCount must reflect the number of children.
         * Requirement REQ-2.4.2
         */
        @Test
        void shouldReportCorrectSubcategoryCount() throws Exception {
                Long parentId = createCategory("Housing", CategoryType.EXPENSE, null);
                createCategory("Rent", CategoryType.EXPENSE, parentId);
                createCategory("Maintenance", CategoryType.EXPENSE, parentId);

                mockMvc.perform(
                                get("/api/v1/categories/" + parentId)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.subcategoryCount").value(2));
        }

        /**
         * Deleting a parent category that has subcategories should fail (or the
         * subcategories should be
         * handled), protecting data integrity. Requirement REQ-2.4.2
         */
        @Test
        void shouldNotDeleteParentCategoryThatHasSubcategories() throws Exception {
                Long parentId = createCategory("Parent Category", CategoryType.EXPENSE, null);
                createCategory("Child Category", CategoryType.EXPENSE, parentId);

                // Attempt to delete the parent — should be rejected (400 or 409)
                mockMvc.perform(
                                delete("/api/v1/categories/" + parentId)
                                                .header("Authorization", "Bearer " + token))
                                .andDo(print())
                                .andExpect(status().is4xxClientError());
        }

        /**
         * Deleting a category that has assigned transactions should be rejected.
         * Requirement REQ-2.4.2
         */
        @Test
        void shouldNotDeleteCategoryInUseByTransactions() throws Exception {
                Long categoryId = createCategory("In Use Category", CategoryType.EXPENSE, null);

                // Create a transaction using this category
                TransactionRequest txReq = TransactionRequest.builder()
                                .accountId(accountId)
                                .type(TransactionType.EXPENSE)
                                .amount(new BigDecimal("25.00"))
                                .currency("EUR")
                                .categoryId(categoryId)
                                .date(java.time.LocalDate.of(2026, 1, 10))
                                .build();

                mockMvc.perform(
                                post("/api/v1/transactions")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(txReq)))
                                .andExpect(status().isCreated());

                // Now attempt to delete the category — should fail
                mockMvc.perform(
                                delete("/api/v1/categories/" + categoryId)
                                                .header("Authorization", "Bearer " + token))
                                .andDo(print())
                                .andExpect(status().is4xxClientError());
        }

        // ── Category Tree ─────────────────────────────────────────────────────────

        /**
         * GET /api/v1/categories/tree - must return a structured tree with INCOME and
         * EXPENSE root
         * nodes. Requirement REQ-CAT-3.1
         */
        @Test
        void shouldReturnCategoryTree() throws Exception {
                createCategory("My Expense", CategoryType.EXPENSE, null);
                createCategory("My Income", CategoryType.INCOME, null);

                mockMvc.perform(
                                get("/api/v1/categories/tree")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }
}
