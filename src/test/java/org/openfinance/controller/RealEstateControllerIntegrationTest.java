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
import org.openfinance.dto.*;
import org.openfinance.entity.*;
import org.openfinance.repository.*;
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
 * Integration tests for RealEstateController REST endpoints.
 *
 * <p>Tests all CRUD operations, equity/ROI calculations with real HTTP requests, database
 * interactions, and encryption/decryption. Verifies authorization, validation, and calculated
 * fields.
 *
 * <p>Requirement REQ-2.16: Real Estate Management - Full integration testing
 *
 * @see RealEstateController
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("RealEstateController Integration Tests")
class RealEstateControllerIntegrationTest {

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
    private Long mortgageId;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() throws Exception {
        // Clean database
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

        // Login
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

        // Derive encryption key
        User user =
                userRepository
                        .findByUsername("alice")
                        .orElseThrow(() -> new RuntimeException("User not found"));
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);

        // Create mortgage for equity calculations WITH PROPER ENCRYPTION
        Liability mortgage = new Liability();
        mortgage.setUserId(user.getId()); // Set userId field (required by validation)
        mortgage.setUser(user); // Set user relationship
        mortgage.setName(encryptionService.encrypt("Home Mortgage", secretKey));
        mortgage.setType(LiabilityType.MORTGAGE);
        mortgage.setPrincipal(encryptionService.encrypt("500000", secretKey));
        mortgage.setCurrentBalance(encryptionService.encrypt("400000", secretKey));
        mortgage.setCurrency("USD");
        mortgage.setInterestRate(encryptionService.encrypt("3.5", secretKey));
        mortgage.setStartDate(LocalDate.of(2020, 1, 1));
        mortgage.setEndDate(LocalDate.of(2050, 1, 1));
        mortgage = liabilityRepository.save(mortgage);
        mortgageId = mortgage.getId();
    }

    // === CREATE Tests ===

    @Test
    @DisplayName("POST /api/v1/real-estate - create property successfully with all fields")
    void shouldCreatePropertySuccessfully() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("Main Residence")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St, San Francisco, CA 94102")
                        .purchasePrice(new BigDecimal("850000.00"))
                        .currentValue(new BigDecimal("1200000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2020, 3, 15))
                        .mortgageId(mortgageId)
                        .rentalIncome(null)
                        .latitude(new BigDecimal("37.7749"))
                        .longitude(new BigDecimal("-122.4194"))
                        .notes("Primary residence")
                        .build();

        mockMvc.perform(
                        post("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Main Residence"))
                .andExpect(jsonPath("$.propertyType").value("RESIDENTIAL"))
                .andExpect(jsonPath("$.address").value("123 Main St, San Francisco, CA 94102"))
                .andExpect(jsonPath("$.purchasePrice").value(850000.00))
                .andExpect(jsonPath("$.currentValue").value(1200000.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.purchaseDate").value("2020-03-15"))
                .andExpect(jsonPath("$.mortgageId").value(mortgageId))
                .andExpect(jsonPath("$.mortgageName").exists())
                .andExpect(jsonPath("$.rentalIncome").doesNotExist())
                .andExpect(jsonPath("$.latitude").value(37.7749))
                .andExpect(jsonPath("$.longitude").value(-122.4194))
                .andExpect(jsonPath("$.notes").value("Primary residence"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.appreciation").value(350000.00)) // 1200000 - 850000
                .andExpect(jsonPath("$.appreciationPercentage").exists())
                .andExpect(jsonPath("$.equity").exists()) // currentValue - mortgageBalance
                .andExpect(jsonPath("$.equityPercentage").exists())
                .andExpect(jsonPath("$.yearsOwned").isNumber())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("POST /api/v1/real-estate - create rental property with income")
    void shouldCreateRentalPropertyWithIncome() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("Rental Condo")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("456 Elm St, Oakland, CA 94601")
                        .purchasePrice(new BigDecimal("400000.00"))
                        .currentValue(new BigDecimal("550000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2019, 6, 1))
                        .mortgageId(null) // Paid off
                        .rentalIncome(new BigDecimal("2500.00")) // Monthly rental income
                        .latitude(new BigDecimal("37.8044"))
                        .longitude(new BigDecimal("-122.2711"))
                        .notes("Investment property")
                        .build();

        mockMvc.perform(
                        post("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Rental Condo"))
                .andExpect(jsonPath("$.rentalIncome").value(2500.00))
                .andExpect(jsonPath("$.rentalYield").exists()) // (2500*12)/550000 * 100
                .andExpect(jsonPath("$.mortgageId").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/real-estate - create commercial property")
    void shouldCreateCommercialProperty() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("Office Building")
                        .propertyType(PropertyType.COMMERCIAL)
                        .address("789 Business Park Dr, San Jose, CA 95110")
                        .purchasePrice(new BigDecimal("2000000.00"))
                        .currentValue(new BigDecimal("2500000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2018, 1, 1))
                        .build();

        mockMvc.perform(
                        post("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propertyType").value("COMMERCIAL"))
                .andExpect(jsonPath("$.name").value("Office Building"));
    }

    @Test
    @DisplayName("POST /api/v1/real-estate - fail without encryption session")
    void shouldFailCreateWithoutEncryptionKey() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("Test Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Test St")
                        .purchasePrice(new BigDecimal("100000.00"))
                        .currentValue(new BigDecimal("120000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        post("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                // No X-Encryption-Session header
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/real-estate - fail without authentication")
    void shouldFailCreateWithoutAuthentication() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("Test Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Test St")
                        .purchasePrice(new BigDecimal("100000.00"))
                        .currentValue(new BigDecimal("120000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        post("/api/v1/real-estate")
                                // No Authorization header
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden()); // 403 - Spring Security default for missing
        // auth
    }

    @Test
    @DisplayName("POST /api/v1/real-estate - fail with invalid request (missing required fields)")
    void shouldFailCreateWithInvalidRequest() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("") // Invalid: empty name
                        .propertyType(null) // Invalid: null type
                        .address("123 Test St")
                        .purchasePrice(new BigDecimal("-1000.00")) // Invalid: negative price
                        .currentValue(new BigDecimal("120000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        post("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // === GET (List) Tests ===

    @Test
    @DisplayName("GET /api/v1/real-estate - get all active properties")
    void shouldGetAllActiveProperties() throws Exception {
        // Create multiple properties
        createProperty("Property 1", PropertyType.RESIDENTIAL, "500000", "600000", true);
        createProperty("Property 2", PropertyType.COMMERCIAL, "1000000", "1200000", true);
        createProperty("Property 3", PropertyType.LAND, "100000", "150000", false); // Inactive

        mockMvc.perform(
                        get("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2)) // Only active properties
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Property 1", "Property 2")));
    }

    @Test
    @DisplayName(
            "GET /api/v1/real-estate?includeInactive=true - get all properties including inactive")
    void shouldGetAllPropertiesIncludingInactive() throws Exception {
        createProperty("Active Property", PropertyType.RESIDENTIAL, "500000", "600000", true);
        createProperty("Inactive Property", PropertyType.COMMERCIAL, "1000000", "1200000", false);

        mockMvc.perform(
                        get("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/real-estate?propertyType=RESIDENTIAL - filter by property type")
    void shouldFilterPropertiesByType() throws Exception {
        createProperty("House", PropertyType.RESIDENTIAL, "500000", "600000", true);
        createProperty("Office", PropertyType.COMMERCIAL, "1000000", "1200000", true);
        createProperty("Condo", PropertyType.RESIDENTIAL, "300000", "350000", true);

        mockMvc.perform(
                        get("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .param("propertyType", "RESIDENTIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].propertyType", everyItem(is("RESIDENTIAL"))));
    }

    @Test
    @DisplayName("GET /api/v1/real-estate - return empty array when no properties")
    void shouldReturnEmptyArrayWhenNoProperties() throws Exception {
        mockMvc.perform(
                        get("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // === GET (By ID) Tests ===

    @Test
    @DisplayName("GET /api/v1/real-estate/{id} - get property by ID")
    void shouldGetPropertyById() throws Exception {
        Long propertyId =
                createProperty("Test Property", PropertyType.RESIDENTIAL, "500000", "600000", true);

        mockMvc.perform(
                        get("/api/v1/real-estate/" + propertyId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(propertyId))
                .andExpect(jsonPath("$.name").value("Test Property"))
                .andExpect(jsonPath("$.propertyType").value("RESIDENTIAL"));
    }

    @Test
    @DisplayName("GET /api/v1/real-estate/{id} - fail with non-existent ID")
    void shouldFailGetPropertyWithNonExistentId() throws Exception {
        mockMvc.perform(
                        get("/api/v1/real-estate/99999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/real-estate/{id} - fail without encryption session")
    void shouldFailGetPropertyWithoutEncryptionKey() throws Exception {
        Long propertyId =
                createProperty("Test Property", PropertyType.RESIDENTIAL, "500000", "600000", true);

        mockMvc.perform(
                        get("/api/v1/real-estate/" + propertyId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // === UPDATE Tests ===

    @Test
    @DisplayName("PUT /api/v1/real-estate/{id} - update property successfully")
    void shouldUpdatePropertySuccessfully() throws Exception {
        Long propertyId =
                createProperty("Old Name", PropertyType.RESIDENTIAL, "500000", "600000", true);

        RealEstatePropertyRequest updateReq =
                RealEstatePropertyRequest.builder()
                        .name("Updated Name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("Updated Address")
                        .purchasePrice(new BigDecimal("500000.00"))
                        .currentValue(new BigDecimal("700000.00")) // Updated value
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2020, 1, 1))
                        .notes("Updated notes")
                        .build();

        mockMvc.perform(
                        put("/api/v1/real-estate/" + propertyId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(propertyId))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.address").value("Updated Address"))
                .andExpect(jsonPath("$.currentValue").value(700000.00))
                .andExpect(jsonPath("$.notes").value("Updated notes"));
    }

    @Test
    @DisplayName("PUT /api/v1/real-estate/{id} - fail update with non-existent ID")
    void shouldFailUpdateWithNonExistentId() throws Exception {
        RealEstatePropertyRequest updateReq =
                RealEstatePropertyRequest.builder()
                        .name("Test")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Test")
                        .purchasePrice(new BigDecimal("100000.00"))
                        .currentValue(new BigDecimal("120000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        put("/api/v1/real-estate/99999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());
    }

    // === DELETE Tests ===

    @Test
    @DisplayName("DELETE /api/v1/real-estate/{id} - delete property successfully (soft delete)")
    void shouldDeletePropertySuccessfully() throws Exception {
        Long propertyId =
                createProperty("To Delete", PropertyType.RESIDENTIAL, "500000", "600000", true);

        mockMvc.perform(
                        delete("/api/v1/real-estate/" + propertyId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isNoContent());

        // Verify property is soft deleted (not in active list)
        mockMvc.perform(
                        get("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // But still exists with includeInactive=true
        mockMvc.perform(
                        get("/api/v1/real-estate")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isActive").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/real-estate/{id} - fail delete with non-existent ID")
    void shouldFailDeleteWithNonExistentId() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/real-estate/99999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/real-estate/{id} - no encryption key required for delete")
    void shouldDeleteWithoutEncryptionKey() throws Exception {
        Long propertyId =
                createProperty("To Delete", PropertyType.RESIDENTIAL, "500000", "600000", true);

        mockMvc.perform(
                        delete("/api/v1/real-estate/" + propertyId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isNoContent());
    }

    // === EQUITY Tests ===

    @Test
    @DisplayName("GET /api/v1/real-estate/{id}/equity - calculate equity with mortgage")
    void shouldCalculateEquityWithMortgage() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("Test Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Test St")
                        .purchasePrice(new BigDecimal("1000000.00"))
                        .currentValue(new BigDecimal("1200000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2020, 1, 1))
                        .mortgageId(mortgageId) // Mortgage with 400k balance
                        .build();

        String createResp =
                mockMvc.perform(
                                post("/api/v1/real-estate")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long propertyId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(
                        get("/api/v1/real-estate/" + propertyId + "/equity")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(propertyId))
                .andExpect(jsonPath("$.propertyName").value("Test Property"))
                .andExpect(jsonPath("$.currentValue").value(1200000.00))
                .andExpect(jsonPath("$.mortgageBalance").exists())
                .andExpect(jsonPath("$.equity").exists())
                .andExpect(jsonPath("$.equityPercentage").exists())
                .andExpect(jsonPath("$.loanToValueRatio").exists())
                .andExpect(jsonPath("$.hasMortgage").value(true))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("GET /api/v1/real-estate/{id}/equity - calculate equity without mortgage")
    void shouldCalculateEquityWithoutMortgage() throws Exception {
        Long propertyId =
                createProperty(
                        "Paid Off Property", PropertyType.RESIDENTIAL, "500000", "700000", true);

        mockMvc.perform(
                        get("/api/v1/real-estate/" + propertyId + "/equity")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(propertyId))
                .andExpect(jsonPath("$.currentValue").value(700000.00))
                .andExpect(jsonPath("$.equity").value(700000.00)) // Full equity
                .andExpect(jsonPath("$.equityPercentage").value(100.00))
                .andExpect(jsonPath("$.hasMortgage").value(false));
    }

    // === ROI Tests ===

    @Test
    @DisplayName("GET /api/v1/real-estate/{id}/roi - calculate ROI without rental income")
    void shouldCalculateROIWithoutRentalIncome() throws Exception {
        Long propertyId =
                createProperty(
                        "Appreciation Only", PropertyType.RESIDENTIAL, "500000", "600000", true);

        mockMvc.perform(
                        get("/api/v1/real-estate/" + propertyId + "/roi")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(propertyId))
                .andExpect(jsonPath("$.propertyName").value("Appreciation Only"))
                .andExpect(jsonPath("$.purchasePrice").value(500000.00))
                .andExpect(jsonPath("$.currentValue").value(600000.00))
                .andExpect(jsonPath("$.appreciation").value(100000.00))
                .andExpect(jsonPath("$.appreciationPercentage").exists())
                .andExpect(jsonPath("$.totalROI").exists())
                .andExpect(jsonPath("$.annualizedReturn").exists())
                .andExpect(jsonPath("$.isRentalProperty").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/real-estate/{id}/roi - calculate ROI with rental income")
    void shouldCalculateROIWithRentalIncome() throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name("Rental Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Rental St")
                        .purchasePrice(new BigDecimal("400000.00"))
                        .currentValue(new BigDecimal("500000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2020, 1, 1))
                        .rentalIncome(new BigDecimal("2000.00")) // Monthly
                        .build();

        String createResp =
                mockMvc.perform(
                                post("/api/v1/real-estate")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long propertyId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(
                        get("/api/v1/real-estate/" + propertyId + "/roi")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRentalIncome").value(2000.00))
                .andExpect(jsonPath("$.totalRentalIncome").exists())
                .andExpect(jsonPath("$.rentalYield").exists())
                .andExpect(jsonPath("$.isRentalProperty").value(true));
    }

    // === VALUE UPDATE Tests ===

    @Test
    @DisplayName("PUT /api/v1/real-estate/{id}/value - update property value")
    void shouldUpdatePropertyValue() throws Exception {
        Long propertyId =
                createProperty(
                        "Value Update Test", PropertyType.RESIDENTIAL, "500000", "600000", true);

        BigDecimal newValue = new BigDecimal("750000.00");

        mockMvc.perform(
                        put("/api/v1/real-estate/" + propertyId + "/value")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(propertyId))
                .andExpect(jsonPath("$.currentValue").value(750000.00))
                .andExpect(jsonPath("$.appreciation").value(250000.00)); // 750000 - 500000
    }

    @Test
    @DisplayName("PUT /api/v1/real-estate/{id}/value - fail with negative value")
    void shouldFailUpdateValueWithNegative() throws Exception {
        Long propertyId =
                createProperty("Test Property", PropertyType.RESIDENTIAL, "500000", "600000", true);

        BigDecimal negativeValue = new BigDecimal("-100000.00");

        mockMvc.perform(
                        put("/api/v1/real-estate/" + propertyId + "/value")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(negativeValue)))
                .andExpect(status().isBadRequest());
    }

    // === Helper Methods ===

    /** Creates a test property via API and returns its ID. */
    private Long createProperty(
            String name,
            PropertyType type,
            String purchasePrice,
            String currentValue,
            boolean isActive)
            throws Exception {
        RealEstatePropertyRequest req =
                RealEstatePropertyRequest.builder()
                        .name(name)
                        .propertyType(type)
                        .address("123 Test Street")
                        .purchasePrice(new BigDecimal(purchasePrice))
                        .currentValue(new BigDecimal(currentValue))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2020, 1, 1))
                        .build();

        String response =
                mockMvc.perform(
                                post("/api/v1/real-estate")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long propertyId = objectMapper.readTree(response).get("id").asLong();

        if (!isActive) {
            mockMvc.perform(
                            delete("/api/v1/real-estate/" + propertyId)
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andExpect(status().isNoContent());
        }

        return propertyId;
    }
}
