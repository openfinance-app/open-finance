package org.openfinance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.PasswordUpdateRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.dto.UserResponse;
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
 * Integration tests for UserController password change endpoint.
 *
 * <p>Tests password update functionality with authentication and validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class UserControllerPasswordIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String jwtToken;
    private UserResponse testUser;
    private static final String TEST_PASSWORD = "TestPassword123!";
    private static final String TEST_MASTER_PASSWORD = "MasterPassword456!";
    private static final String TEST_USERNAME = "passworduser";

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        // Register test user
        UserRegistrationRequest registrationRequest =
                UserRegistrationRequest.builder()
                        .username(TEST_USERNAME)
                        .email("password@test.com")
                        .password(TEST_PASSWORD)
                        .masterPassword(TEST_MASTER_PASSWORD)
                        .skipSeeding(true)
                        .build();

        testUser = userService.registerUser(registrationRequest);

        // Login to get JWT token
        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username(TEST_USERNAME)
                        .password(TEST_PASSWORD)
                        .masterPassword(TEST_MASTER_PASSWORD)
                        .build();

        String loginResponse =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequest)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Extract token from response
        jwtToken = objectMapper.readTree(loginResponse).get("token").asText();
    }

    @Test
    @DisplayName("Should update password with valid credentials")
    void shouldUpdatePasswordWithValidCredentials() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest(TEST_PASSWORD, "NewPassword789!");

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));
    }

    @Test
    @DisplayName("Should fail with incorrect current password")
    void shouldFailWithIncorrectCurrentPassword() throws Exception {
        // Arrange
        PasswordUpdateRequest request =
                new PasswordUpdateRequest("WrongPassword123!", "NewPassword789!");

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should fail with password less than 8 characters")
    void shouldFailWithShortPassword() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest(TEST_PASSWORD, "Short1");

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail without authentication")
    void shouldFailWithoutAuthentication() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest(TEST_PASSWORD, "NewPassword789!");

        // Act & Assert - Spring Security returns 403 for unauthenticated requests
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should fail with invalid JWT token")
    void shouldFailWithInvalidToken() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest(TEST_PASSWORD, "NewPassword789!");

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer invalid_token_12345")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should fail with blank current password")
    void shouldFailWithBlankCurrentPassword() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest("", "NewPassword789!");

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail with blank new password")
    void shouldFailWithBlankNewPassword() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest(TEST_PASSWORD, "");

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should successfully change password twice in sequence")
    void shouldChangePasswordTwiceInSequence() throws Exception {
        // Arrange - First password change
        PasswordUpdateRequest firstChange =
                new PasswordUpdateRequest(TEST_PASSWORD, "SecondPassword123!");

        // Act - First change
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstChange)))
                .andExpect(status().isOk());

        // Arrange - Second password change
        PasswordUpdateRequest secondChange =
                new PasswordUpdateRequest("SecondPassword123!", "ThirdPassword456!");

        // Act - Second change
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(secondChange)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));
    }
}
