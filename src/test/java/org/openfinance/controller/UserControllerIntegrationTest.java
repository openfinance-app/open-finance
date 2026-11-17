package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
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
 * Integration tests for UserController.
 *
 * <p>Tests user profile and settings endpoints including base currency management. Sprint 6 - Task
 * 6.2.13: Base currency user setting
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private org.openfinance.repository.CurrencyRepository currencyRepository;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;
    private String encryptionSession;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();
        for (String code : new String[] {"EUR", "USD", "GBP", "JPY", "CHF", "BTC", "ETH", "USDT"}) {
            currencyRepository.save(
                    org.openfinance.entity.Currency.builder()
                            .code(code)
                            .name(code)
                            .symbol(code)
                            .build());
        }

        // Register test user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("testuser")
                        .email("testuser@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        // Login to get JWT token
        LoginRequest login =
                LoginRequest.builder()
                        .username("testuser")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        token = objectMapper.readTree(resp).get("token").asText();
        encryptionSession = objectMapper.readTree(resp).get("encryptionKey").asText();
    }

    @Test
    @DisplayName("GET /api/v1/users/me - Should return current user profile")
    void testGetCurrentUser() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("testuser")))
                .andExpect(jsonPath("$.email", is("testuser@example.com")))
                .andExpect(jsonPath("$.baseCurrency", is("EUR"))) // Application default (EUR)
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.createdAt", endsWith("Z")));
    }

    @Test
    @DisplayName("Timezone preference defaults to device time and persists an account override")
    void persistsDisplayTimezoneMode() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useDeviceTimezone", is(true)))
                .andExpect(jsonPath("$.createdAt", endsWith("Z")));
        mockMvc.perform(
                        put("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"timezone\":\"America/New_York\",\"useDeviceTimezone\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone", is("America/New_York")))
                .andExpect(jsonPath("$.useDeviceTimezone", is(false)));
        mockMvc.perform(
                        put("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"useDeviceTimezone\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone", is("America/New_York")))
                .andExpect(jsonPath("$.useDeviceTimezone", is(true)));
    }

    @Test
    @DisplayName("Unknown timezone identifiers are rejected at the API boundary")
    void rejectsInvalidTimezone() throws Exception {
        mockMvc.perform(
                        put("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"timezone\":\"Invalid/Timezone\",\"useDeviceTimezone\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useDeviceTimezone", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/users/me - Should return 401 without token")
    void testGetCurrentUserUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me/base-currency - Should return default base currency")
    void testGetBaseCurrency() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me/base-currency")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("EUR")));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/base-currency - Should update base currency")
    void testUpdateBaseCurrency() throws Exception {
        Map<String, String> request = Map.of("baseCurrency", "EUR");

        mockMvc.perform(
                        put("/api/v1/users/me/base-currency")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("EUR")))
                .andExpect(jsonPath("$.username", is("testuser")));

        // Verify the change persisted
        mockMvc.perform(
                        get("/api/v1/users/me/base-currency")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("EUR")));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/base-currency - Should accept valid currency codes")
    void testUpdateBaseCurrencyWithVariousCodes() throws Exception {
        String[] validCurrencies = {"USD", "EUR", "GBP", "JPY", "CHF", "BTC", "ETH", "USDT"};

        for (String currency : validCurrencies) {
            Map<String, String> request = Map.of("baseCurrency", currency);

            mockMvc.perform(
                            put("/api/v1/users/me/base-currency")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encryptionSession)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseCurrency", is(currency)));
        }
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/base-currency - Should reject invalid currency format")
    void testUpdateBaseCurrencyInvalidFormat() throws Exception {
        // Test too short - will be rejected by UserService
        Map<String, String> request2 = Map.of("baseCurrency", "US");
        mockMvc.perform(
                        put("/api/v1/users/me/base-currency")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest());

        // Test too long - will be rejected by UserService
        Map<String, String> request3 = Map.of("baseCurrency", "USDD");
        mockMvc.perform(
                        put("/api/v1/users/me/base-currency")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/base-currency - Should return 401 without token")
    void testUpdateBaseCurrencyUnauthorized() throws Exception {
        Map<String, String> request = Map.of("baseCurrency", "EUR");

        mockMvc.perform(
                        put("/api/v1/users/me/base-currency")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/base-currency - Should handle null currency")
    void testUpdateBaseCurrencyNull() throws Exception {
        String requestJson = "{\"baseCurrency\": null}";

        mockMvc.perform(
                        put("/api/v1/users/me/base-currency")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void remembersDeviceTimezoneWithoutOverwritingAccountPreference() throws Exception {
        mockMvc.perform(
                        put("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .header("X-Device-Timezone", "Europe/Paris")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "timezone",
                                                        "America/New_York",
                                                        "useDeviceTimezone",
                                                        false))))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/users/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encryptionSession)
                                .header("X-Device-Timezone", "Asia/Tokyo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.useDeviceTimezone").value(false));
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT last_device_timezone FROM user_settings WHERE user_id = (SELECT id FROM users WHERE username = 'testuser')",
                                String.class))
                .isEqualTo("Asia/Tokyo");
    }
}
