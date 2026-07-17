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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AccountRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountController Integration Tests")
class AccountControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;
    private String encKey;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("alice")
                        .email("alice@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        LoginRequest login =
                LoginRequest.builder()
                        .username("alice")
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
        encKey = objectMapper.readTree(resp).get("encryptionKey").asText();
    }

    @Test
    @DisplayName("POST /api/v1/accounts - create account successfully")
    void shouldCreateAccount() throws Exception {
        AccountRequest req =
                AccountRequest.builder()
                        .name("Chase Checking")
                        .type(org.openfinance.entity.AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("500.00"))
                        .description("Primary")
                        .build();

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Chase Checking"));
    }

    @Test
    @DisplayName("POST /api/v1/accounts - validation errors")
    void shouldReturn400OnValidationErrors() throws Exception {
        AccountRequest req =
                AccountRequest.builder()
                        .name("")
                        .type(null)
                        .currency("")
                        .initialBalance(null)
                        .build();

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.type").exists())
                .andExpect(jsonPath("$.validationErrors.currency").exists())
                .andExpect(jsonPath("$.validationErrors.initialBalance").exists());
    }

    @Test
    @DisplayName("POST /api/v1/accounts - missing encryption session header")
    void shouldReturn400WhenMissingEncryptionKey() throws Exception {
        AccountRequest req =
                AccountRequest.builder()
                        .name("Name")
                        .type(org.openfinance.entity.AccountType.CASH)
                        .currency("USD")
                        .initialBalance(new BigDecimal("1.00"))
                        .build();

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/accounts - list all accounts")
    void shouldListAccounts() throws Exception {
        // create one account
        AccountRequest req =
                AccountRequest.builder()
                        .name("ListMe")
                        .type(org.openfinance.entity.AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("10.00"))
                        .build();

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/accounts")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("ListMe"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - get account by ID and 404 when not found")
    void shouldGetAccountByIdAndHandleNotFound() throws Exception {
        // create
        AccountRequest req =
                AccountRequest.builder()
                        .name("FindMe")
                        .type(org.openfinance.entity.AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("20.00"))
                        .build();

        String created =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(
                        get("/api/v1/accounts/" + id)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("FindMe"));

        // non-existent
        mockMvc.perform(
                        get("/api/v1/accounts/99999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/accounts/{id} - update and partial update handling")
    void shouldUpdateAccount() throws Exception {
        AccountRequest create =
                AccountRequest.builder()
                        .name("ToUpdate")
                        .type(org.openfinance.entity.AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("30.00"))
                        .build();

        String created =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(create)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long id = objectMapper.readTree(created).get("id").asLong();

        AccountRequest update =
                AccountRequest.builder()
                        .name("UpdatedName")
                        .type(org.openfinance.entity.AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("300.00"))
                        .description(null) // explicit null should be ignored
                        .build();

        mockMvc.perform(
                        put("/api/v1/accounts/" + id)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UpdatedName"));
    }

    @Test
    @DisplayName("DELETE /api/v1/accounts/{id} - soft delete and handle not found")
    void shouldSoftDeleteAndHandleNotFound() throws Exception {
        AccountRequest req =
                AccountRequest.builder()
                        .name("ToDelete")
                        .type(org.openfinance.entity.AccountType.CASH)
                        .currency("USD")
                        .initialBalance(new BigDecimal("5.00"))
                        .build();

        String created =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(
                        delete("/api/v1/accounts/" + id)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        delete("/api/v1/accounts/99999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance - get balance")
    void shouldGetBalance() throws Exception {
        AccountRequest req =
                AccountRequest.builder()
                        .name("BalAcc")
                        .type(org.openfinance.entity.AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("77.77"))
                        .build();

        String created =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(
                        get("/api/v1/accounts/" + id + "/balance")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(77.77));
    }

    @Test
    @DisplayName("Authorization - users cannot access others' accounts")
    void authorizationCheck() throws Exception {
        // Create second user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("bob")
                        .email("bob@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(reg);

        // Login bob
        LoginRequest bobLogin =
                LoginRequest.builder()
                        .username("bob")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        String bobResp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(bobLogin)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String bobToken = objectMapper.readTree(bobResp).get("token").asText();
        String bobEnc = objectMapper.readTree(bobResp).get("encryptionKey").asText();

        // Alice creates account
        AccountRequest req =
                AccountRequest.builder()
                        .name("AliceOnly")
                        .type(org.openfinance.entity.AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("12.00"))
                        .build();

        String created =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long id = objectMapper.readTree(created).get("id").asLong();

        // Bob tries to access Alice's account
        mockMvc.perform(
                        get("/api/v1/accounts/" + id)
                                .header("Authorization", "Bearer " + bobToken)
                                .header("X-Encryption-Session", bobEnc))
                .andExpect(status().isNotFound());
    }
}
