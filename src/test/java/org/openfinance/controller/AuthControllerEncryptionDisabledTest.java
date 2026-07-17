package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "application.encryption.enabled=false")
@DisplayName("AuthController Encryption Disabled Integration Tests")
class AuthControllerEncryptionDisabledTest {

    private static final String ENCRYPTION_DISABLED_SALT = "ENCRYPTION_DISABLED";

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    @BeforeEach
    void setUp() {
        databaseCleanupService.execute();
    }

    @Test
    @DisplayName(
            "POST /auth/login - should login without master password when encryption is disabled")
    void shouldLoginWithoutMasterPasswordWhenEncryptionIsDisabled() throws Exception {
        UserRegistrationRequest registrationRequest =
                UserRegistrationRequest.builder()
                        .username("disabled_user")
                        .email("disabled@example.com")
                        .password("Password123!")
                        .masterPassword(null)
                        .skipSeeding(true)
                        .build();

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registrationRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        User savedUser = userRepository.findByUsername("disabled_user").orElseThrow();
        assertThat(savedUser.getMasterPasswordSalt()).isEqualTo(ENCRYPTION_DISABLED_SALT);

        String loginRequestBody =
                "{" + "\"username\": \"disabled_user\"," + "\"password\": \"Password123!\"" + "}";

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(savedUser.getId()))
                .andExpect(jsonPath("$.username").value("disabled_user"))
                .andExpect(jsonPath("$.encryptionKey").value(nullValue()))
                .andExpect(jsonPath("$.encryptionEnabled").value(false));
    }
}
