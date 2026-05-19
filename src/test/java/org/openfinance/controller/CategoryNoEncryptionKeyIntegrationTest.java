package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Integration tests to verify behavior when X-Encryption-Key header is missing. Verifies that
 * system categories are still accessible while user categories are protected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
class CategoryNoEncryptionKeyIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String authToken;
    private String encryptionKeyHeader;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        UserRegistrationRequest reg = new UserRegistrationRequest();
        reg.setUsername("okeyuser");
        reg.setEmail("okey@example.com");
        reg.setPassword("Password123!");
        reg.setMasterPassword("Master123!");
        reg.setSkipSeeding(false); // Enable seeding to have system categories
        userService.registerUser(reg);

        LoginRequest login = new LoginRequest();
        login.setUsername("okeyuser");
        login.setPassword("Password123!");
        login.setMasterPassword("Master123!");

        String loginResponse =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        authToken = objectMapper.readTree(loginResponse).get("token").asText();
        encryptionKeyHeader = objectMapper.readTree(loginResponse).get("encryptionKey").asText();
    }

    @Test
    void shouldReturnOnlySystemCategoriesWhenHeaderMissing() throws Exception {
        // Create a user category first (with key)
        CategoryRequest userCat =
                CategoryRequest.builder().name("Private Stuff").type(CategoryType.EXPENSE).build();

        mockMvc.perform(
                        post("/api/v1/categories")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Key", encryptionKeyHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userCat)))
                .andExpect(status().isCreated());

        // Now fetch without X-Encryption-Key
        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[*].isSystem", everyItem(is(true))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Private Stuff"))));
    }

    @Test
    void shouldReturnOnlySystemTreeWhenHeaderMissing() throws Exception {
        mockMvc.perform(
                        get("/api/v1/categories/tree")
                                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[*].isSystem", everyItem(is(true))));
    }

    @Test
    void shouldReturn400WhenGettingUserCategoryWithoutHeader() throws Exception {
        // Create user category
        CategoryRequest userCat =
                CategoryRequest.builder().name("Hidden").type(CategoryType.EXPENSE).build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/categories")
                                        .header("Authorization", "Bearer " + authToken)
                                        .header("X-Encryption-Key", encryptionKeyHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(userCat)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        long id = objectMapper.readTree(resp).get("id").asLong();

        // Try to get it without header
        mockMvc.perform(
                        get("/api/v1/categories/" + id)
                                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value("Encryption key required for user categories"));
    }

    @Test
    void shouldReturnSystemCategoryWithoutHeader() throws Exception {
        // Find a system category ID first
        String listResp =
                mockMvc.perform(
                                get("/api/v1/categories")
                                        .header("Authorization", "Bearer " + authToken)
                                        .header("X-Encryption-Key", encryptionKeyHeader))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        long systemId = objectMapper.readTree(listResp).get(0).get("id").asLong();

        // Fetch it without header
        mockMvc.perform(
                        get("/api/v1/categories/" + systemId)
                                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(systemId))
                .andExpect(jsonPath("$.isSystem").value(true));
    }
}
