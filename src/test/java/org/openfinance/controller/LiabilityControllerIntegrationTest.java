package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.User;
import org.openfinance.repository.LiabilityRepository;
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
 * Integration tests for LiabilityController. Tests all 8 REST endpoints with authentication,
 * authorization, and encryption.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("LiabilityController Integration Tests")
class LiabilityControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private LiabilityRepository liabilityRepository;

    @Autowired private KeyManagementService keyManagementService;

    @Autowired private org.openfinance.security.EncryptionService encryptionService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;
    private String encKey;
    private Long userId;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        // Register user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("alice")
                        .email("alice@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        // Login to get JWT token
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

        // Derive encryption key
        User user =
                userRepository
                        .findByUsername("alice")
                        .orElseThrow(() -> new RuntimeException("User not found"));
        userId = user.getId();
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);
        encKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    private LiabilityRequest createValidRequest() {
        LiabilityRequest request = new LiabilityRequest();
        request.setName("Home Mortgage");
        request.setType(LiabilityType.MORTGAGE);
        request.setPrincipal(new BigDecimal("300000.00"));
        request.setCurrentBalance(new BigDecimal("250000.00"));
        request.setInterestRate(new BigDecimal("3.5"));
        request.setStartDate(LocalDate.now().minusYears(2));
        request.setEndDate(LocalDate.now().plusYears(28));
        request.setMinimumPayment(new BigDecimal("1500.00"));
        request.setCurrency("USD");
        request.setNotes("Fixed rate mortgage");
        return request;
    }

    /**
     * Helper method to create liability directly via repository (for tests that need existing
     * data). Follows the pattern from DashboardControllerIntegrationTest.
     */
    private org.openfinance.entity.Liability createLiabilityDirectly(
            String name,
            LiabilityType type,
            BigDecimal principal,
            BigDecimal currentBalance,
            BigDecimal interestRate,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal minimumPayment,
            String currency,
            String notes) {

        // Encrypt sensitive fields
        String encryptedName = encryptionService.encrypt(name, secretKey);
        String encryptedPrincipal = encryptionService.encrypt(principal.toString(), secretKey);
        String encryptedCurrentBalance =
                encryptionService.encrypt(currentBalance.toString(), secretKey);
        String encryptedInterestRate =
                interestRate != null
                        ? encryptionService.encrypt(interestRate.toString(), secretKey)
                        : null;
        String encryptedMinimumPayment =
                minimumPayment != null
                        ? encryptionService.encrypt(minimumPayment.toString(), secretKey)
                        : null;
        String encryptedNotes = notes != null ? encryptionService.encrypt(notes, secretKey) : null;

        org.openfinance.entity.Liability liability = new org.openfinance.entity.Liability();
        liability.setUserId(userId);
        liability.setName(encryptedName);
        liability.setType(type);
        liability.setPrincipal(encryptedPrincipal);
        liability.setCurrentBalance(encryptedCurrentBalance);
        liability.setInterestRate(encryptedInterestRate);
        liability.setStartDate(startDate);
        liability.setEndDate(endDate);
        liability.setMinimumPayment(encryptedMinimumPayment);
        liability.setCurrency(currency);
        liability.setNotes(encryptedNotes);

        return liabilityRepository.save(liability);
    }

    // ============ Create Liability Tests ============

    @Test
    @DisplayName("POST /api/v1/liabilities - Should create liability with valid request")
    void shouldCreateLiability_WhenValidRequest() throws Exception {
        // Given
        LiabilityRequest request = createValidRequest();

        // When/Then
        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Home Mortgage"))
                .andExpect(jsonPath("$.type").value("MORTGAGE"))
                .andExpect(jsonPath("$.principal").value(300000.00))
                .andExpect(jsonPath("$.currentBalance").value(250000.00))
                .andExpect(jsonPath("$.interestRate").value(3.5))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.notes").value("Fixed rate mortgage"))
                .andExpect(jsonPath("$.totalPaid").exists())
                .andExpect(jsonPath("$.payoffPercentage").exists());
    }

    @Test
    @DisplayName("POST /api/v1/liabilities - Should fail without authentication")
    void shouldFailToCreateLiability_WhenNotAuthenticated() throws Exception {
        // Given
        LiabilityRequest request = createValidRequest();

        // When/Then
        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/liabilities - Should fail without encryption key")
    void shouldFailToCreateLiability_WhenNoEncryptionKey() throws Exception {
        // Given
        LiabilityRequest request = createValidRequest();

        // When/Then
        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Encryption key header is required"));
    }

    @Test
    @DisplayName("POST /api/v1/liabilities - Should fail with invalid data")
    void shouldFailToCreateLiability_WhenInvalidData() throws Exception {
        // Given - Missing required fields
        LiabilityRequest request = new LiabilityRequest();
        request.setName(""); // Blank name
        request.setCurrency("US"); // Invalid currency format

        // When/Then
        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/liabilities - Should create liability with optional fields null")
    void shouldCreateLiability_WithOptionalFieldsNull() throws Exception {
        // Given - Minimal required fields
        LiabilityRequest request = new LiabilityRequest();
        request.setName("Simple Loan");
        request.setType(LiabilityType.LOAN);
        request.setPrincipal(new BigDecimal("5000.00"));
        request.setCurrentBalance(new BigDecimal("5000.00"));
        request.setStartDate(LocalDate.now());
        request.setCurrency("USD");
        // No interestRate, endDate, minimumPayment, notes

        // When/Then
        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Simple Loan"))
                .andExpect(jsonPath("$.interestRate").doesNotExist())
                .andExpect(jsonPath("$.endDate").doesNotExist());
    }

    // ============ Get Liabilities Tests ============

    @Test
    @DisplayName("GET /api/v1/liabilities - Should get all user liabilities")
    void shouldGetAllLiabilities() throws Exception {
        // Given - Create two liabilities via REST API
        LiabilityRequest mortgage = createValidRequest();
        mortgage.setName("Mortgage");
        mortgage.setType(LiabilityType.MORTGAGE);

        LiabilityRequest loan = createValidRequest();
        loan.setName("Car Loan");
        loan.setType(LiabilityType.LOAN);

        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(mortgage)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loan)))
                .andExpect(status().isCreated());

        // When/Then - Get all liabilities
        mockMvc.perform(
                        get("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Mortgage", "Car Loan")));
    }

    @Test
    @DisplayName("GET /api/v1/liabilities?type=MORTGAGE - Should get liabilities by type")
    void shouldGetLiabilitiesByType() throws Exception {
        // Given - Create mortgage and loan
        LiabilityRequest mortgage = createValidRequest();
        mortgage.setName("Mortgage");
        mortgage.setType(LiabilityType.MORTGAGE);

        LiabilityRequest loan = createValidRequest();
        loan.setName("Car Loan");
        loan.setType(LiabilityType.LOAN);

        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(mortgage)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loan)))
                .andExpect(status().isCreated());

        // When/Then - Filter by MORTGAGE
        mockMvc.perform(
                        get("/api/v1/liabilities?type=MORTGAGE")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mortgage"))
                .andExpect(jsonPath("$[0].type").value("MORTGAGE"));
    }

    @Test
    @DisplayName("GET /api/v1/liabilities - Should return empty list when no liabilities")
    void shouldReturnEmptyList_WhenNoLiabilities() throws Exception {
        // When/Then
        mockMvc.perform(
                        get("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ============ Get Single Liability Tests ============

    @Test
    @DisplayName("GET /api/v1/liabilities/{id} - Should get liability by ID")
    void shouldGetLiabilityById() throws Exception {
        // Given - Create liability
        LiabilityRequest request = createValidRequest();
        String createResp =
                mockMvc.perform(
                                post("/api/v1/liabilities")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long liabilityId = objectMapper.readTree(createResp).get("id").asLong();

        // When/Then
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(liabilityId))
                .andExpect(jsonPath("$.name").value("Home Mortgage"));
    }

    @Test
    @DisplayName("GET /api/v1/liabilities/{id} - Should return 404 when liability not found")
    void shouldReturn404_WhenLiabilityNotFound() throws Exception {
        // When/Then
        mockMvc.perform(
                        get("/api/v1/liabilities/999999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isNotFound());
    }

    // ============ Update Liability Tests ============

    @Test
    @DisplayName("PUT /api/v1/liabilities/{id} - Should update liability")
    void shouldUpdateLiability() throws Exception {
        // Given - Create liability
        LiabilityRequest request = createValidRequest();
        String createResp =
                mockMvc.perform(
                                post("/api/v1/liabilities")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long liabilityId = objectMapper.readTree(createResp).get("id").asLong();

        // Update request
        LiabilityRequest updateRequest = createValidRequest();
        updateRequest.setName("Updated Mortgage");
        updateRequest.setCurrentBalance(new BigDecimal("240000.00"));

        // When/Then
        mockMvc.perform(
                        put("/api/v1/liabilities/" + liabilityId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(liabilityId))
                .andExpect(jsonPath("$.name").value("Updated Mortgage"))
                .andExpect(jsonPath("$.currentBalance").value(240000.00));
    }

    @Test
    @DisplayName(
            "PUT /api/v1/liabilities/{id} - Should return 404 when updating non-existent liability")
    void shouldReturn404_WhenUpdatingNonExistentLiability() throws Exception {
        // Given
        LiabilityRequest request = createValidRequest();

        // When/Then
        mockMvc.perform(
                        put("/api/v1/liabilities/999999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ============ Delete Liability Tests ============

    @Test
    @DisplayName("DELETE /api/v1/liabilities/{id} - Should delete liability")
    void shouldDeleteLiability() throws Exception {
        // Given - Create liability
        LiabilityRequest request = createValidRequest();
        String createResp =
                mockMvc.perform(
                                post("/api/v1/liabilities")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long liabilityId = objectMapper.readTree(createResp).get("id").asLong();

        // When/Then - Delete
        mockMvc.perform(
                        delete("/api/v1/liabilities/" + liabilityId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isNoContent());

        // Verify it's deleted
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/liabilities/{id} - Should return 404 when deleting non-existent liability")
    void shouldReturn404_WhenDeletingNonExistentLiability() throws Exception {
        // When/Then
        mockMvc.perform(
                        delete("/api/v1/liabilities/999999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isNotFound());
    }

    // ============ Amortization Schedule Tests ============

    @Test
    @DisplayName("GET /api/v1/liabilities/{id}/amortization - Should get amortization schedule")
    void shouldGetAmortizationSchedule() throws Exception {
        // Given - Create liability with reasonable parameters
        LiabilityRequest request = createValidRequest();
        request.setCurrentBalance(new BigDecimal("10000.00"));
        request.setInterestRate(new BigDecimal("12.0"));
        request.setMinimumPayment(new BigDecimal("500.00"));

        String createResp =
                mockMvc.perform(
                                post("/api/v1/liabilities")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long liabilityId = objectMapper.readTree(createResp).get("id").asLong();

        // When/Then
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/amortization")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].paymentNumber").value(1))
                .andExpect(jsonPath("$[0].paymentAmount").exists())
                .andExpect(jsonPath("$[0].principalPortion").exists())
                .andExpect(jsonPath("$[0].interestPortion").exists())
                .andExpect(jsonPath("$[0].remainingBalance").exists());
    }

    // ============ Total Interest Tests ============

    @Test
    @DisplayName("GET /api/v1/liabilities/{id}/total-interest - Should get total interest")
    void shouldGetTotalInterest() throws Exception {
        // Given
        LiabilityRequest request = createValidRequest();
        request.setCurrentBalance(new BigDecimal("10000.00"));
        request.setInterestRate(new BigDecimal("12.0"));
        request.setMinimumPayment(new BigDecimal("500.00"));

        String createResp =
                mockMvc.perform(
                                post("/api/v1/liabilities")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long liabilityId = objectMapper.readTree(createResp).get("id").asLong();

        // When/Then
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/total-interest")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInterest").exists())
                .andExpect(jsonPath("$.totalInterest").value(greaterThan(0.0)));
    }

    // ============ Total Liabilities Tests ============

    @Test
    @DisplayName("GET /api/v1/liabilities/total - Should get total liabilities by currency")
    void shouldGetTotalLiabilities() throws Exception {
        // Given - Create USD and EUR liabilities
        LiabilityRequest usdRequest = createValidRequest();
        usdRequest.setName("USD Mortgage");
        usdRequest.setCurrentBalance(new BigDecimal("50000.00"));
        usdRequest.setCurrency("USD");

        LiabilityRequest eurRequest = createValidRequest();
        eurRequest.setName("EUR Loan");
        eurRequest.setCurrentBalance(new BigDecimal("30000.00"));
        eurRequest.setCurrency("EUR");

        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(usdRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/liabilities")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(eurRequest)))
                .andExpect(status().isCreated());

        // When/Then
        mockMvc.perform(
                        get("/api/v1/liabilities/total")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.USD").value(50000.00))
                .andExpect(jsonPath("$.EUR").value(30000.00));
    }

    @Test
    @DisplayName("GET /api/v1/liabilities/total - Should return empty map when no liabilities")
    void shouldReturnEmptyMap_WhenNoLiabilitiesForTotal() throws Exception {
        // When/Then
        mockMvc.perform(
                        get("/api/v1/liabilities/total")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
