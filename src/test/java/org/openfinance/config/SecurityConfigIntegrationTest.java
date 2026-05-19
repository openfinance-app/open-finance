package org.openfinance.config;

// no static assert imports needed here
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.dto.UserResponse;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.AuthService;
import org.openfinance.service.JwtService;
import org.openfinance.service.UserService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "jwt.secret=01234567890123456789012345678901",
            "spring.main.allow-bean-definition-overriding=true"
        })
@Import(TestDatabaseConfig.class)
class SecurityConfigIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private JwtService jwtService;

    @Autowired private UserRepository userRepository;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanupService databaseCleanupService;

    @MockBean private UserService userService; // prevent real registration logic
    @MockBean private AuthService authService; // prevent real login logic

    @Test
    @DisplayName("Should allow public auth register endpoint without authentication")
    void shouldAllowPublicAuthRegisterWithoutAuth() throws Exception {
        // Arrange - mock return value from UserService
        UserResponse resp = new UserResponse();
        resp.setId(1L);
        resp.setUsername("testuser");
        resp.setEmail("test@example.com");

        org.mockito.Mockito.when(userService.registerUser(org.mockito.Mockito.any()))
                .thenReturn(resp);

        UserRegistrationRequest req = new UserRegistrationRequest();
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("Password123!");
        req.setMasterPassword("Master123!");

        // Act & Assert: call register endpoint without Authorization header
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should deny access to protected endpoint without authentication")
    void shouldDenyHealthWhenUnauthenticated() throws Exception {
        // Protected API endpoints require authentication.
        // /api/v1/auth/profile is a protected endpoint, expect 403 Access Denied.
        mockMvc.perform(get("/api/v1/auth/profile")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow access to protected endpoint /health with valid JWT")
    @org.springframework.transaction.annotation.Transactional
    void shouldAllowHealthWhenAuthenticatedWithValidJwt() throws Exception {
        // Arrange: ensure repository is clean to avoid unique constraint collisions
        databaseCleanupService.execute();

        String random = java.util.UUID.randomUUID().toString();
        User u =
                User.builder()
                        .username("intuser_" + random)
                        .email(random + "@example.com")
                        .passwordHash("$2a$10$hash")
                        .masterPasswordSalt("salt")
                        .build();

        u = userRepository.save(u);

        // Generate token for saved user
        String token = jwtService.generateToken(u);

        // Act & Assert
        mockMvc.perform(get("/api/v1/health").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SecurityConfig exposes PasswordEncoder and CORS configuration")
    void shouldExposePasswordEncoderAndCors() throws Exception {
        // Arrange - ensure PasswordEncoder bean is available
        org.junit.jupiter.api.Assertions.assertNotNull(
                passwordEncoder, "PasswordEncoder bean should be present");

        // Act - perform CORS preflight
        org.springframework.test.web.servlet.MvcResult result =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .options("/health")
                                        .header("Origin", "http://localhost:3000")
                                        .header("Access-Control-Request-Method", "GET"))
                        .andExpect(status().isOk())
                        .andReturn();

        // Assert - CORS response contains expected headers
        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
        String allowMethods = result.getResponse().getHeader("Access-Control-Allow-Methods");

        org.assertj.core.api.Assertions.assertThat(allowOrigin).isEqualTo("http://localhost:3000");
        org.assertj.core.api.Assertions.assertThat(allowMethods).contains("GET");
    }
}
