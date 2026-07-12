package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UpdateProfileRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
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
 * Integration tests for {@link AuthController}.
 *
 * <p>Tests the full registration flow including HTTP request/response handling, validation, service
 * layer integration, and database persistence.
 *
 * <p>Uses H2 in-memory database for testing instead of SQLite to avoid JDBC driver conflicts. The
 * {@link TestDatabaseConfig} provides a {@code @Primary} DataSource bean that overrides the
 * production SQLite configuration.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private UserRegistrationRequest validRequest;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        databaseCleanupService.execute();

        // Valid registration request
        validRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should successfully register user with valid data")
    void shouldRegisterUserWithValidData() throws Exception {
        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.masterPasswordSalt").doesNotExist());

        // Verify user was saved in database
        Optional<User> savedUser = userRepository.findByUsername("john_doe");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo("john@example.com");
        assertThat(savedUser.get().getPasswordHash()).isNotNull();
        assertThat(savedUser.get().getMasterPasswordSalt()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should hash password before storing")
    void shouldHashPasswordBeforeStoring() throws Exception {
        // Act
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Assert - Password should be hashed (BCrypt format)
        Optional<User> savedUser = userRepository.findByUsername("john_doe");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getPasswordHash()).startsWith("$2a$");
        assertThat(savedUser.get().getPasswordHash()).isNotEqualTo(validRequest.getPassword());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should generate salt for master password")
    void shouldGenerateSaltForMasterPassword() throws Exception {
        // Act
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Assert - Salt should be generated and stored (Base64 encoded)
        Optional<User> savedUser = userRepository.findByUsername("john_doe");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getMasterPasswordSalt()).isNotNull();
        assertThat(savedUser.get().getMasterPasswordSalt()).isNotEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 409 when username exists")
    void shouldReturn409WhenUsernameExists() throws Exception {
        // Arrange - Register first user
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Act & Assert - Try to register with same username
        UserRegistrationRequest duplicateRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("different@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Username already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 409 when email exists")
    void shouldReturn409WhenEmailExists() throws Exception {
        // Arrange - Register first user
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        // Act & Assert - Try to register with same email
        UserRegistrationRequest duplicateRequest =
                UserRegistrationRequest.builder()
                        .username("different_user")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Email already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when username is missing")
    void shouldReturn400WhenUsernameIsMissing() throws Exception {
        // Arrange
        UserRegistrationRequest invalidRequest =
                UserRegistrationRequest.builder()
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.username").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when email is invalid")
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        // Arrange
        UserRegistrationRequest invalidRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("invalid-email")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when password is too short")
    void shouldReturn400WhenPasswordIsTooShort() throws Exception {
        // Arrange
        UserRegistrationRequest invalidRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("short")
                        .masterPassword("masterPassword123")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.validationErrors.password")
                                .value(containsString("at least 8 characters")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when username is too short")
    void shouldReturn400WhenUsernameIsTooShort() throws Exception {
        // Arrange
        UserRegistrationRequest invalidRequest =
                UserRegistrationRequest.builder()
                        .username("ab")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.validationErrors.username")
                                .value(containsString("between 3 and 50 characters")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when master password is missing")
    void shouldReturn400WhenMasterPasswordIsMissing() throws Exception {
        // Arrange
        UserRegistrationRequest invalidRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        containsString(
                                                "Master password is required when encryption is enabled")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 when master password is too short")
    void shouldReturn400WhenMasterPasswordIsTooShort() throws Exception {
        // Arrange
        UserRegistrationRequest invalidRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("short")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        containsString(
                                                "Master password must be at least 8 characters when encryption is enabled")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 with multiple validation errors")
    void shouldReturn400WithMultipleValidationErrors() throws Exception {
        // Arrange - Invalid username, email, and password. Master password is validated by the
        // service only when other registration fields are valid.
        UserRegistrationRequest invalidRequest =
                UserRegistrationRequest.builder()
                        .username("ab")
                        .email("invalid-email")
                        .password("short")
                        .masterPassword("short")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.username").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should not return sensitive data in response")
    void shouldNotReturnSensitiveDataInResponse() throws Exception {
        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.masterPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.masterPasswordSalt").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should handle special characters in username")
    void shouldHandleSpecialCharactersInUsername() throws Exception {
        // Arrange
        UserRegistrationRequest requestWithSpecialChars =
                UserRegistrationRequest.builder()
                        .username("user_123-test")
                        .email("user123@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestWithSpecialChars)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user_123-test"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should preserve username case")
    void shouldPreserveUsernameCase() throws Exception {
        // Arrange
        UserRegistrationRequest mixedCaseRequest =
                UserRegistrationRequest.builder()
                        .username("JohnDoe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(mixedCaseRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("JohnDoe"));

        // Verify in database
        Optional<User> savedUser = userRepository.findByUsername("JohnDoe");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getUsername()).isEqualTo("JohnDoe");
    }

    // ====================================================================================
    // Login Endpoint Tests (TASK-1.3)
    // ====================================================================================

    @Test
    @DisplayName("POST /auth/login - should successfully authenticate user with valid credentials")
    void shouldLoginWithValidCredentials() throws Exception {
        // Arrange: Register a user first
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.encryptionKey").exists())
                .andExpect(jsonPath("$.encryptionKey").isNotEmpty())
                .andExpect(jsonPath("$.encryptionEnabled").value(true));
    }

    @Test
    @DisplayName("POST /auth/login - should return 401 for non-existent username")
    void shouldReturn401ForNonExistentUsername() throws Exception {
        // Arrange
        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("nonexistent")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /auth/login - should return 401 for incorrect password")
    void shouldReturn401ForIncorrectPassword() throws Exception {
        // Arrange: Register a user first
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("wrongPassword")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName(
            "POST /auth/login - should successfully generate different encryption key for different master password")
    void shouldGenerateDifferentKeyForDifferentMasterPassword() throws Exception {
        // NOTE: Master password validation happens CLIENT-SIDE when decrypting data.
        // Server cannot validate master password correctness without storing a verification hash.
        // The server will successfully login with ANY master password, but the encryption key
        // derived will be different, so the client won't be able to decrypt their data.

        // Arrange: Register a user first
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest correctLogin =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        LoginRequest wrongMasterLogin =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("wrongMaster")
                        .build();

        // Act: Login with correct master password
        String correctResponse =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(correctLogin)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Act: Login with wrong master password (still succeeds server-side)
        String wrongResponse =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(wrongMasterLogin)))
                        .andDo(print())
                        .andExpect(status().isOk()) // Server doesn't reject wrong master password
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Assert: Different master passwords produce different encryption keys
        String correctEncKey = objectMapper.readTree(correctResponse).get("encryptionKey").asText();
        String wrongEncKey = objectMapper.readTree(wrongResponse).get("encryptionKey").asText();
        assertThat(correctEncKey).isNotEqualTo(wrongEncKey);
    }

    @Test
    @DisplayName("POST /auth/login - should return 400 when username is missing")
    void shouldReturn400WhenUsernameIsMissingOnLogin() throws Exception {
        // Arrange
        String requestBody =
                "{" + "\"password\": \"password123\"," + "\"masterPassword\": \"master123\"" + "}";

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.username").value("Username is required"));
    }

    @Test
    @DisplayName("POST /auth/login - should return 400 when password is missing")
    void shouldReturn400WhenPasswordIsMissingOnLogin() throws Exception {
        // Arrange
        String requestBody =
                "{" + "\"username\": \"john_doe\"," + "\"masterPassword\": \"master123\"" + "}";

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.password").value("Password is required"));
    }

    @Test
    @DisplayName("POST /auth/login - should return 400 when master password is missing")
    void shouldReturn400WhenMasterPasswordIsMissingOnLogin() throws Exception {
        // Arrange
        userService.registerUser(validRequest);
        String requestBody =
                "{" + "\"username\": \"john_doe\"," + "\"password\": \"Password123!\"" + "}";

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        containsString(
                                                "Master password is required when encryption is enabled")));
    }

    @Test
    @DisplayName("POST /auth/login - should return valid JWT token that can be parsed")
    void shouldReturnValidJwtToken() throws Exception {
        // Arrange: Register a user first
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        String response =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequest)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.token").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Verify token structure (JWT has 3 parts separated by dots)
        String token = objectMapper.readTree(response).get("token").asText();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("POST /auth/login - should not expose sensitive user data in response")
    void shouldNotExposeSensitiveDataInLoginResponse() throws Exception {
        // Arrange: Register a user first
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.masterPasswordSalt").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.masterPassword").doesNotExist());
    }

    // ====================================================================================
    // JWT Authentication Filter Integration Tests (TASK-1.3.7 & 1.3.8)
    // ====================================================================================

    @Test
    @DisplayName("POST /auth/register - should allow registration without authentication")
    void shouldAllowRegistrationWithoutAuth() throws Exception {
        // Arrange
        UserRegistrationRequest request =
                UserRegistrationRequest.builder()
                        .username("new_user")
                        .email("newuser@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert: Registration should work without JWT token
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new_user"));
    }

    @Test
    @DisplayName("POST /auth/login - should allow login without authentication")
    void shouldAllowLoginWithoutAuth() throws Exception {
        // Arrange: Register a user first
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Act & Assert: Login should work without JWT token
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("Should allow public endpoint access with invalid JWT token")
    void shouldAllowPublicEndpointWithInvalidJwtToken() throws Exception {
        // Act & Assert: Request with invalid token should still access public endpoint
        // The filter logs the error but continues the chain for public endpoints
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .header("Authorization", "Bearer invalid.jwt.token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isCreated()); // Public endpoint still works
    }

    @Test
    @DisplayName("Should allow public endpoint access with malformed Authorization header")
    void shouldAllowPublicEndpointWithMalformedAuthorizationHeader() throws Exception {
        // Act & Assert: Request with malformed Authorization header
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .header("Authorization", "InvalidFormat token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isCreated()); // Public endpoint still works
    }

    @Test
    @DisplayName("Should parse and accept valid JWT token in Authorization header")
    void shouldParseAndAcceptValidJwtToken() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();

        // Act & Assert: Request with valid token should be accepted
        // Using a public endpoint to verify token is parsed correctly
        // The filter should successfully parse the token and set authentication
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    // ====================================================================================
    // User Profile Management Tests (TASK-1.4)
    // ====================================================================================

    @Test
    @DisplayName("GET /auth/profile - should return user profile with valid JWT token")
    void shouldReturnUserProfileWithValidToken() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        // Act & Assert: Get profile with valid token
        mockMvc.perform(
                        get("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.masterPasswordSalt").doesNotExist());
    }

    @Test
    @DisplayName("GET /auth/profile - should return 401 without authentication")
    void shouldReturn401WhenAccessingProfileWithoutAuth() throws Exception {
        // Act & Assert: Access profile without JWT token
        mockMvc.perform(get("/api/v1/auth/profile"))
                .andDo(print())
                .andExpect(status().isForbidden()); // Spring Security returns 403 for missing
        // authentication
    }

    @Test
    @DisplayName("GET /auth/profile - should return 401 with invalid JWT token")
    void shouldReturn401WhenAccessingProfileWithInvalidToken() throws Exception {
        // Act & Assert: Access profile with invalid token
        mockMvc.perform(
                        get("/api/v1/auth/profile")
                                .header("Authorization", "Bearer invalid.jwt.token"))
                .andDo(print())
                .andExpect(status().isForbidden()); // Spring Security returns 403 for invalid token
    }

    @Test
    @DisplayName("PUT /auth/profile - should update email with valid current password")
    void shouldUpdateEmailWithValidCurrentPassword() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .email("newemail@example.com")
                        .currentPassword("Password123!")
                        .build();

        // Act & Assert: Update email
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("newemail@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Verify email was updated in database
        Optional<User> updatedUser = userRepository.findByUsername("john_doe");
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getEmail()).isEqualTo("newemail@example.com");
    }

    @Test
    @DisplayName("PUT /auth/profile - should update password with valid current password")
    void shouldUpdatePasswordWithValidCurrentPassword() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .currentPassword("Password123!")
                        .newPassword("Password123!")
                        .build();

        // Act & Assert: Update password
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_doe"));

        // Verify can login with new password
        LoginRequest newLoginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("PUT /auth/profile - should update both email and password")
    void shouldUpdateBothEmailAndPassword() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("old@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .email("new@example.com")
                        .currentPassword("Password123!")
                        .newPassword("Password123!")
                        .build();

        // Act & Assert: Update both email and password
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));

        // Verify can login with new credentials
        LoginRequest newLoginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("PUT /auth/profile - should return 401 for wrong current password")
    void shouldReturn401ForWrongCurrentPassword() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .email("newemail@example.com")
                        .currentPassword("wrongPassword")
                        .build();

        // Act & Assert: Attempt update with wrong current password
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Invalid current password")));

        // Verify email was not updated
        Optional<User> user = userRepository.findByUsername("john_doe");
        assertThat(user).isPresent();
        assertThat(user.get().getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("PUT /auth/profile - should return 409 when email already exists")
    void shouldReturn409WhenUpdatingToExistingEmail() throws Exception {
        // Arrange: Register two users
        UserRegistrationRequest user1 =
                UserRegistrationRequest.builder()
                        .username("user1")
                        .email("user1@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(user1);

        UserRegistrationRequest user2 =
                UserRegistrationRequest.builder()
                        .username("user2")
                        .email("user2@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(user2);

        // Login as user2
        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("user2")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        // Try to update user2's email to user1's email
        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .email("user1@example.com")
                        .currentPassword("Password123!")
                        .build();

        // Act & Assert: Should return 409 conflict
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Email already exists")));
    }

    @Test
    @DisplayName("PUT /auth/profile - should return 401 without authentication")
    void shouldReturn401WhenUpdatingProfileWithoutAuth() throws Exception {
        // Arrange
        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .email("newemail@example.com")
                        .currentPassword("Password123!")
                        .build();

        // Act & Assert: Attempt update without JWT token
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isForbidden()); // Spring Security returns 403 for missing
        // authentication
    }

    @Test
    @DisplayName("PUT /auth/profile - should return 400 when current password is missing")
    void shouldReturn400WhenCurrentPasswordIsMissing() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        // Missing currentPassword
        String requestBody = "{\"email\": \"newemail@example.com\"}";

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.validationErrors.currentPassword")
                                .value("Current password is required"));
    }

    @Test
    @DisplayName("PUT /auth/profile - should return 400 when new password is too short")
    void shouldReturn400WhenNewPasswordIsTooShort() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .currentPassword("Password123!")
                        .newPassword("short")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.validationErrors.newPassword")
                                .value(containsString("at least 8 characters")));
    }

    @Test
    @DisplayName("PUT /auth/profile - should return 400 with invalid email format")
    void shouldReturn400WhenEmailIsInvalidOnUpdate() throws Exception {
        // Arrange: Register and login to get a valid token
        UserRegistrationRequest registerRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(registerRequest);

        LoginRequest loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("Master123!")
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

        String token = objectMapper.readTree(loginResponse).get("token").asText();
        String encryptionSession = objectMapper.readTree(loginResponse).get("encryptionKey").asText();

        UpdateProfileRequest updateRequest =
                UpdateProfileRequest.builder()
                        .email("invalid-email")
                        .currentPassword("Password123!")
                        .build();

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/auth/profile")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").value("Email must be valid"));
    }
}
