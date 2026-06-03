package org.openfinance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.CategoryType;
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
 * Integration tests for category localization via Accept-Language header.
 *
 * <p>Tests verify:
 *
 * <ul>
 *   <li>System category names are localized based on Accept-Language header
 *   <li>User-created category names are decrypted correctly regardless of locale
 *   <li>French translations are returned when Accept-Language: fr header is present
 *   <li>English translations are returned by default or with Accept-Language: en
 * </ul>
 *
 * <p>Part of Phase 4 (Quality & CI) of i18n implementation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
class CategoryLocalizationIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    /** JWT bearer token for the test user */
    private String authToken;

    /** Encryption key from login response */
    private String encryptionKeyHeader;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up any existing test data
        databaseCleanupService.execute();

        // Register a test user directly via service
        UserRegistrationRequest registrationRequest = new UserRegistrationRequest();
        registrationRequest.setUsername("testuser");
        registrationRequest.setEmail("test@example.com");
        registrationRequest.setPassword("Password123!");

        userService.registerUser(registrationRequest);

        // Login to get token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("Password123!");
        loginRequest.setMasterPassword(
                "Password123!"); // Master password same as password for this test

        String loginJson = objectMapper.writeValueAsString(loginRequest);

        String loginResponse =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Extract token and encryption key from response
        authToken = objectMapper.readTree(loginResponse).get("token").asText();
        encryptionKeyHeader = objectMapper.readTree(loginResponse).get("encryptionKey").asText();
    }

    @Test
    void shouldReturnSystemCategoryInEnglishByDefault() throws Exception {
        mockMvc.perform(
                        get("/api/v1/categories")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'INCOME' && @.name == 'Employment Income')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'EXPENSE' && @.name == 'Groceries')]")
                                .exists());
    }

    @Test
    void shouldReturnSystemCategoryInEnglishWithExplicitHeader() throws Exception {
        mockMvc.perform(
                        get("/api/v1/categories")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "en"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'INCOME' && @.name == 'Employment Income')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'EXPENSE' && @.name == 'Groceries')]")
                                .exists());
    }

    @Test
    void shouldReturnSystemCategoryInFrenchWithFrenchHeader() throws Exception {
        mockMvc.perform(
                        get("/api/v1/categories")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "fr"))
                .andDo(print())
                .andExpect(status().isOk())
                // Should have French translations
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'INCOME' && @.name == 'Salaire')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'EXPENSE' && @.name == 'Épiceries')]")
                                .exists())
                // Should NOT contain English names for translated categories
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'INCOME' && @.name == 'Salary')]")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'EXPENSE' && @.name == 'Groceries')]")
                                .doesNotExist());
    }

    @Test
    void shouldReturnCategoryByIdInFrench() throws Exception {
        // First get all categories to find a system category ID
        String response =
                mockMvc.perform(
                                get("/api/v1/categories")
                                        .header("Authorization", "Bearer " + authToken)
                                        .header("X-Encryption-Session", encryptionKeyHeader))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Parse response to get Salary category ID (which should translate to "Salaire")
        var categories = objectMapper.readTree(response);
        Long systemCategoryId = null;
        for (var category : categories) {
            if (category.get("isSystem").asBoolean()
                    && "INCOME".equals(category.get("type").asText())
                    && "Salary".equals(category.get("name").asText())) {
                systemCategoryId = category.get("id").asLong();
                break;
            }
        }

        // Now fetch that specific category with French locale
        mockMvc.perform(
                        get("/api/v1/categories/" + systemCategoryId)
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "fr"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSystem").value(true))
                .andExpect(jsonPath("$.name").value("Salaire"));
    }

    @Test
    void shouldReturnCategoryTreeInFrench() throws Exception {
        mockMvc.perform(
                        get("/api/v1/categories/tree")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "fr"))
                .andDo(print())
                .andExpect(status().isOk())
                // Check that the tree contains French category names
                .andExpect(jsonPath("$[?(@.name == 'Investissements')]").exists())
                .andExpect(jsonPath("$..subcategories[?(@.name == 'Salaire')]").exists());
    }

    @Test
    void shouldReturnIncomeCategoriesInFrench() throws Exception {
        mockMvc.perform(
                        get("/api/v1/categories")
                                .param("type", "INCOME")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "fr"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Salaire')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Salary')]").doesNotExist());
    }

    @Test
    void shouldReturnExpenseCategoriesInFrench() throws Exception {
        mockMvc.perform(
                        get("/api/v1/categories")
                                .param("type", "EXPENSE")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "fr"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Épiceries')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Groceries')]").doesNotExist());
    }

    @Test
    void shouldDecryptUserCategoryRegardlessOfLocale() throws Exception {
        // Create a user-created category
        CategoryRequest categoryRequest = new CategoryRequest();
        categoryRequest.setName("My Custom Category");
        categoryRequest.setType(CategoryType.EXPENSE);

        String categoryJson = objectMapper.writeValueAsString(categoryRequest);

        String createResponse =
                mockMvc.perform(
                                post("/api/v1/categories")
                                        .header("Authorization", "Bearer " + authToken)
                                        .header("X-Encryption-Session", encryptionKeyHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(categoryJson))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long customCategoryId = objectMapper.readTree(createResponse).get("id").asLong();

        // Fetch with English header - should still decrypt properly
        mockMvc.perform(
                        get("/api/v1/categories/" + customCategoryId)
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "en"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Custom Category"))
                .andExpect(jsonPath("$.isSystem").value(false));

        // Fetch with French header - should still decrypt properly
        mockMvc.perform(
                        get("/api/v1/categories/" + customCategoryId)
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "fr"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Custom Category"))
                .andExpect(jsonPath("$.isSystem").value(false));
    }

    @Test
    void shouldHandleFallbackLocale() throws Exception {
        // Use an unsupported locale like German - should fallback to English
        mockMvc.perform(
                        get("/api/v1/categories")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "de"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'INCOME' && @.name == 'Employment Income')]")
                                .exists());
    }

    @Test
    void shouldHandleMultipleAcceptLanguageValues() throws Exception {
        // Client can send multiple locales with quality values: "fr, en-US;q=0.9, en;q=0.8"
        // Should use first available (fr)
        mockMvc.perform(
                        get("/api/v1/categories")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionKeyHeader)
                                .header("Accept-Language", "fr, en-US;q=0.9, en;q=0.8"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$[?(@.isSystem == true && @.type == 'INCOME' && @.name == 'Salaire')]")
                                .exists());
    }
}
