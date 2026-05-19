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
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.AssetCondition;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.User;
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
 * Integration tests for AssetController REST endpoints.
 *
 * <p>Tests all CRUD operations with real HTTP requests, database interactions, and
 * encryption/decryption. Verifies authorization, validation, and calculated fields.
 *
 * @see AssetController
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("AssetController Integration Tests")
class AssetControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private KeyManagementService keyManagementService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;
    private String encKey;
    private Long accountId;

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

        // Derive encryption key
        User user =
                userRepository
                        .findByUsername("alice")
                        .orElseThrow(() -> new RuntimeException("User not found"));
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        SecretKey secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);
        encKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        // Create account for assets
        AccountRequest accountReq =
                AccountRequest.builder()
                        .name("Fidelity Brokerage")
                        .type(AccountType.INVESTMENT)
                        .currency("USD")
                        .initialBalance(new BigDecimal("10000.00"))
                        .description("Brokerage account")
                        .build();

        String accountResp =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(accountReq)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        accountId = objectMapper.readTree(accountResp).get("id").asLong();
    }

    @Test
    @DisplayName("POST /api/v1/assets - create asset successfully with all fields")
    void shouldCreateAssetSuccessfully() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name("Apple Inc.")
                        .type(AssetType.STOCK)
                        .symbol("AAPL")
                        .quantity(new BigDecimal("10.0"))
                        .purchasePrice(new BigDecimal("150.00"))
                        .currentPrice(new BigDecimal("175.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2025, 1, 15))
                        .notes("Tech portfolio allocation")
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.type").value("STOCK"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quantity").value(10.0))
                .andExpect(jsonPath("$.purchasePrice").value(150.00))
                .andExpect(jsonPath("$.currentPrice").value(175.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.purchaseDate").value("2025-01-15"))
                .andExpect(jsonPath("$.notes").value("Tech portfolio allocation"))
                .andExpect(jsonPath("$.accountName").value("Fidelity Brokerage"))
                .andExpect(jsonPath("$.totalValue").value(1750.00)) // 10 * 175
                .andExpect(jsonPath("$.totalCost").value(1500.00)) // 10 * 150
                .andExpect(jsonPath("$.unrealizedGain").value(250.00)) // 1750 - 1500
                .andExpect(jsonPath("$.gainPercentage").exists())
                .andExpect(jsonPath("$.holdingDays").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.lastUpdated").exists());
    }

    @Test
    @DisplayName("POST /api/v1/assets - create asset without account (null accountId)")
    void shouldCreateAssetWithoutAccount() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(null) // No account association
                        .name("Bitcoin")
                        .type(AssetType.CRYPTO)
                        .symbol("BTC")
                        .quantity(new BigDecimal("0.5"))
                        .purchasePrice(new BigDecimal("40000.00"))
                        .currentPrice(new BigDecimal("45000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2025, 2, 1))
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Bitcoin"))
                .andExpect(jsonPath("$.accountId").isEmpty())
                .andExpect(jsonPath("$.accountName").isEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/assets - fail without encryption key")
    void shouldReturn400WhenMissingEncryptionKey() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name("Test Asset")
                        .type(AssetType.STOCK)
                        .symbol("TEST")
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("100.00"))
                        .currentPrice(new BigDecimal("100.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                // Missing X-Encryption-Key header
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/assets - fail with invalid account (doesn't exist)")
    void shouldReturn404WhenAccountDoesNotExist() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(99999L) // Non-existent account
                        .name("Test Asset")
                        .type(AssetType.STOCK)
                        .symbol("TEST")
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("100.00"))
                        .currentPrice(new BigDecimal("100.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/assets - validation errors on invalid fields")
    void shouldReturn400OnValidationErrors() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name("") // Blank name
                        .type(null) // Null type
                        .symbol("") // Blank symbol
                        .quantity(new BigDecimal("-1.0")) // Negative quantity
                        .purchasePrice(new BigDecimal("-10.00")) // Negative price
                        .currentPrice(null) // Null current price
                        .currency("INVALID") // Invalid currency code
                        .purchaseDate(LocalDate.now().plusDays(1)) // Future date
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/assets - list all assets")
    void shouldListAllAssets() throws Exception {
        // Create two assets
        createAsset("Apple Inc.", AssetType.STOCK, "AAPL");
        createAsset("Microsoft Corp.", AssetType.STOCK, "MSFT");

        mockMvc.perform(
                        get("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[1].name").exists());
    }

    @Test
    @DisplayName("GET /api/v1/assets?accountId=X - filter by account")
    void shouldFilterAssetsByAccount() throws Exception {
        // Create assets
        createAsset("Apple Inc.", AssetType.STOCK, "AAPL");
        createAsset("Bitcoin", AssetType.CRYPTO, "BTC");

        mockMvc.perform(
                        get("/api/v1/assets")
                                .param("accountId", accountId.toString())
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/assets?type=STOCK - filter by type")
    void shouldFilterAssetsByType() throws Exception {
        // Create mixed asset types
        createAsset("Apple Inc.", AssetType.STOCK, "AAPL");
        createAsset("Bitcoin", AssetType.CRYPTO, "BTC");
        createAsset("Gold ETF", AssetType.ETF, "GLD");

        mockMvc.perform(
                        get("/api/v1/assets")
                                .param("type", "STOCK")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("STOCK"))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    @DisplayName("GET /api/v1/assets/{id} - get specific asset")
    void shouldGetAssetById() throws Exception {
        Long assetId = createAsset("Apple Inc.", AssetType.STOCK, "AAPL");

        mockMvc.perform(
                        get("/api/v1/assets/" + assetId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assetId))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    @DisplayName("GET /api/v1/assets/{id} - 404 for non-existent asset")
    void shouldReturn404ForNonExistentAsset() throws Exception {
        mockMvc.perform(
                        get("/api/v1/assets/99999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/assets/{id} - update asset successfully")
    void shouldUpdateAsset() throws Exception {
        Long assetId = createAsset("Apple Inc.", AssetType.STOCK, "AAPL");

        AssetRequest updateReq =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name("Apple Inc. (Updated)")
                        .type(AssetType.STOCK)
                        .symbol("AAPL")
                        .quantity(new BigDecimal("15.0")) // Increased quantity
                        .purchasePrice(new BigDecimal("150.00"))
                        .currentPrice(new BigDecimal("180.00")) // Updated price
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2025, 1, 15))
                        .notes("Updated notes")
                        .build();

        mockMvc.perform(
                        put("/api/v1/assets/" + assetId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assetId))
                .andExpect(jsonPath("$.name").value("Apple Inc. (Updated)"))
                .andExpect(jsonPath("$.quantity").value(15.0))
                .andExpect(jsonPath("$.currentPrice").value(180.00))
                .andExpect(jsonPath("$.notes").value("Updated notes"))
                .andExpect(jsonPath("$.totalValue").value(2700.00)) // 15 * 180
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.lastUpdated").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/assets/{id} - update price updates lastUpdated timestamp")
    void shouldUpdateLastUpdatedWhenPriceChanges() throws Exception {
        Long assetId = createAsset("Apple Inc.", AssetType.STOCK, "AAPL");

        // Wait a bit to ensure timestamp difference
        Thread.sleep(100);

        AssetRequest updateReq =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name("Apple Inc.")
                        .type(AssetType.STOCK)
                        .symbol("AAPL")
                        .quantity(new BigDecimal("10.0"))
                        .purchasePrice(new BigDecimal("150.00"))
                        .currentPrice(new BigDecimal("185.00")) // Price changed
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2025, 1, 15))
                        .build();

        mockMvc.perform(
                        put("/api/v1/assets/" + assetId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice").value(185.00))
                .andExpect(jsonPath("$.lastUpdated").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/assets/{id} - delete asset successfully")
    void shouldDeleteAsset() throws Exception {
        Long assetId = createAsset("Apple Inc.", AssetType.STOCK, "AAPL");

        mockMvc.perform(
                        delete("/api/v1/assets/" + assetId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verify asset is deleted
        mockMvc.perform(
                        get("/api/v1/assets/" + assetId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/assets/{id} - 404 for non-existent asset")
    void shouldReturn404WhenDeletingNonExistentAsset() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/assets/99999")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Verify calculated fields: totalValue, totalCost, unrealizedGain, gainPercentage")
    void shouldCalculateFieldsCorrectly() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name("Test Asset")
                        .type(AssetType.STOCK)
                        .symbol("TEST")
                        .quantity(new BigDecimal("20.0"))
                        .purchasePrice(new BigDecimal("50.00"))
                        .currentPrice(new BigDecimal("60.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2025, 1, 1))
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalValue").value(1200.00)) // 20 * 60
                .andExpect(jsonPath("$.totalCost").value(1000.00)) // 20 * 50
                .andExpect(jsonPath("$.unrealizedGain").value(200.00)) // 1200 - 1000
                .andExpect(
                        jsonPath("$.gainPercentage")
                                .value(closeTo(0.20, 0.01))) // (60-50)/50 = 0.20
                .andExpect(jsonPath("$.holdingDays").isNumber());
    }

    @Test
    @DisplayName("Authorization - users cannot access other users' assets")
    void shouldEnforceUserIsolation() throws Exception {
        // Create asset for Alice
        Long aliceAssetId = createAsset("Apple Inc.", AssetType.STOCK, "AAPL");

        // Create second user (Bob)
        UserRegistrationRequest bobReg =
                UserRegistrationRequest.builder()
                        .username("bob")
                        .email("bob@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();
        userService.registerUser(bobReg);

        // Login Bob
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

        // Derive Bob's encryption key
        User bob =
                userRepository
                        .findByUsername("bob")
                        .orElseThrow(() -> new RuntimeException("User not found"));
        byte[] bobSalt = Base64.getDecoder().decode(bob.getMasterPasswordSalt());
        SecretKey bobSecretKey =
                keyManagementService.deriveKey("Master123!".toCharArray(), bobSalt);
        String bobEncKey = Base64.getEncoder().encodeToString(bobSecretKey.getEncoded());

        // Bob tries to access Alice's asset
        mockMvc.perform(
                        get("/api/v1/assets/" + aliceAssetId)
                                .header("Authorization", "Bearer " + bobToken)
                                .header("X-Encryption-Key", bobEncKey))
                .andDo(print())
                .andExpect(status().isNotFound());

        // Bob tries to update Alice's asset
        AssetRequest updateReq =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name("Hacked")
                        .type(AssetType.STOCK)
                        .symbol("HACK")
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("1.00"))
                        .currentPrice(new BigDecimal("1.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        put("/api/v1/assets/" + aliceAssetId)
                                .header("Authorization", "Bearer " + bobToken)
                                .header("X-Encryption-Key", bobEncKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        // Bob tries to delete Alice's asset
        mockMvc.perform(
                        delete("/api/v1/assets/" + aliceAssetId)
                                .header("Authorization", "Bearer " + bobToken)
                                .header("X-Encryption-Key", bobEncKey))
                .andExpect(status().isNotFound());
    }

    // === Physical Asset Integration Tests ===

    @Test
    @DisplayName("POST /api/v1/assets - create physical asset (VEHICLE) with all fields")
    void shouldCreatePhysicalAssetVehicleWithAllFields() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(null)
                        .name("Tesla Model 3")
                        .type(AssetType.VEHICLE)
                        .serialNumber("5YJ3E1EB9KF123456")
                        .brand("Tesla")
                        .model("Model 3 Long Range")
                        .condition(AssetCondition.EXCELLENT)
                        .warrantyExpiration(LocalDate.of(2028, 6, 1))
                        .usefulLifeYears(15)
                        .photoPath("/photos/tesla-model3.jpg")
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("50000.00"))
                        .currentPrice(new BigDecimal("45000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2023, 6, 1))
                        .notes("Personal vehicle")
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Tesla Model 3"))
                .andExpect(jsonPath("$.type").value("VEHICLE"))
                .andExpect(jsonPath("$.serialNumber").value("5YJ3E1EB9KF123456"))
                .andExpect(jsonPath("$.brand").value("Tesla"))
                .andExpect(jsonPath("$.model").value("Model 3 Long Range"))
                .andExpect(jsonPath("$.condition").value("EXCELLENT"))
                .andExpect(jsonPath("$.warrantyExpiration").value("2028-06-01"))
                .andExpect(jsonPath("$.usefulLifeYears").value(15))
                .andExpect(jsonPath("$.photoPath").value("/photos/tesla-model3.jpg"))
                .andExpect(jsonPath("$.isPhysical").value(true))
                .andExpect(jsonPath("$.isWarrantyValid").value(true))
                .andExpect(jsonPath("$.depreciatedValue").exists())
                .andExpect(jsonPath("$.conditionAdjustedValue").exists())
                .andExpect(jsonPath("$.totalValue").value(45000.00))
                .andExpect(jsonPath("$.totalCost").value(50000.00))
                .andExpect(jsonPath("$.unrealizedGain").value(-5000.00));
    }

    @Test
    @DisplayName("POST /api/v1/assets - create physical asset (ELECTRONICS) with minimal fields")
    void shouldCreatePhysicalAssetElectronicsWithMinimalFields() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .name("MacBook Pro")
                        .type(AssetType.ELECTRONICS)
                        // All physical fields are optional
                        .serialNumber(null)
                        .brand(null)
                        .model(null)
                        .condition(null)
                        .warrantyExpiration(null)
                        .usefulLifeYears(null)
                        .photoPath(null)
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("2500.00"))
                        .currentPrice(new BigDecimal("2000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("MacBook Pro"))
                .andExpect(jsonPath("$.type").value("ELECTRONICS"))
                .andExpect(jsonPath("$.isPhysical").value(true))
                .andExpect(jsonPath("$.serialNumber").isEmpty())
                .andExpect(jsonPath("$.brand").isEmpty())
                .andExpect(jsonPath("$.model").isEmpty())
                .andExpect(jsonPath("$.condition").isEmpty())
                .andExpect(jsonPath("$.warrantyExpiration").isEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/assets - create all five physical asset types")
    void shouldCreateAllFivePhysicalAssetTypes() throws Exception {
        AssetType[] physicalTypes = {
            AssetType.VEHICLE,
            AssetType.JEWELRY,
            AssetType.COLLECTIBLE,
            AssetType.ELECTRONICS,
            AssetType.FURNITURE
        };

        for (AssetType type : physicalTypes) {
            AssetRequest req =
                    AssetRequest.builder()
                            .name("Test " + type.name())
                            .type(type)
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("1000.00"))
                            .currentPrice(new BigDecimal("900.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now())
                            .build();

            mockMvc.perform(
                            post("/api/v1/assets")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Key", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value(type.name()))
                    .andExpect(jsonPath("$.isPhysical").value(true));
        }
    }

    @Test
    @DisplayName("GET /api/v1/assets - filter physical assets by type")
    void shouldFilterPhysicalAssetsByType() throws Exception {
        // Create physical assets of different types
        createPhysicalAsset("Tesla Model 3", AssetType.VEHICLE);
        createPhysicalAsset("MacBook Pro", AssetType.ELECTRONICS);
        createPhysicalAsset("Rolex Submariner", AssetType.JEWELRY);

        // Filter by VEHICLE
        mockMvc.perform(
                        get("/api/v1/assets")
                                .param("type", "VEHICLE")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("VEHICLE"))
                .andExpect(jsonPath("$[0].name").value("Tesla Model 3"));
    }

    @Test
    @DisplayName("PUT /api/v1/assets/{id} - update physical asset condition")
    void shouldUpdatePhysicalAssetCondition() throws Exception {
        Long assetId = createPhysicalAsset("iPhone 14", AssetType.ELECTRONICS);

        AssetRequest updateReq =
                AssetRequest.builder()
                        .name("iPhone 14")
                        .type(AssetType.ELECTRONICS)
                        .condition(AssetCondition.FAIR) // Downgraded from GOOD
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("1099.00"))
                        .currentPrice(new BigDecimal("700.00")) // Price dropped
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2023, 9, 1))
                        .build();

        mockMvc.perform(
                        put("/api/v1/assets/" + assetId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("FAIR"))
                .andExpect(jsonPath("$.currentPrice").value(700.00))
                .andExpect(jsonPath("$.conditionAdjustedValue").exists());
    }

    @Test
    @DisplayName("Verify depreciated value calculation for physical assets")
    void shouldCalculateDepreciatedValueForPhysicalAssets() throws Exception {
        // Create vehicle purchased 2 years ago with 15-year useful life
        AssetRequest req =
                AssetRequest.builder()
                        .name("Car")
                        .type(AssetType.VEHICLE)
                        .usefulLifeYears(15)
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("30000.00"))
                        .currentPrice(new BigDecimal("30000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now().minusYears(2))
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depreciatedValue").exists())
                // Depreciation calculation: 10% salvage, 90% depreciates over 15 years
                // After ~2 years (730 days): actual calculation based on days owned
                // Expected value approximately $26,000 with tolerance for date calculation
                .andExpect(jsonPath("$.depreciatedValue").value(closeTo(26000.0, 500.0)))
                .andExpect(jsonPath("$.isPhysical").value(true));
    }

    @Test
    @DisplayName("Verify condition-adjusted value calculation")
    void shouldCalculateConditionAdjustedValue() throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .name("Laptop")
                        .type(AssetType.ELECTRONICS)
                        .condition(AssetCondition.GOOD) // Retention factor 0.62
                        .usefulLifeYears(5)
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("2000.00"))
                        .currentPrice(new BigDecimal("2000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now().minusYears(1))
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depreciatedValue").exists())
                .andExpect(jsonPath("$.conditionAdjustedValue").exists())
                .andExpect(jsonPath("$.condition").value("GOOD"));
    }

    @Test
    @DisplayName("Verify warranty validation")
    void shouldValidateWarrantyStatus() throws Exception {
        // Create asset with valid warranty (future date)
        AssetRequest validWarranty =
                AssetRequest.builder()
                        .name("New Phone")
                        .type(AssetType.ELECTRONICS)
                        .warrantyExpiration(LocalDate.now().plusYears(1)) // Valid future date
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("1000.00"))
                        .currentPrice(new BigDecimal("1000.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validWarranty)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warrantyExpiration").exists())
                .andExpect(jsonPath("$.isWarrantyValid").value(true));

        // Create asset with no warranty (null warranty expiration)
        AssetRequest noWarranty =
                AssetRequest.builder()
                        .name("Old Phone")
                        .type(AssetType.ELECTRONICS)
                        .warrantyExpiration(null) // No warranty specified
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("1000.00"))
                        .currentPrice(new BigDecimal("500.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.now().minusYears(3))
                        .build();

        mockMvc.perform(
                        post("/api/v1/assets")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(noWarranty)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warrantyExpiration").isEmpty())
                .andExpect(jsonPath("$.isWarrantyValid").value(false));
    }

    /** Helper method to create an asset and return its ID. */
    private Long createAsset(String name, AssetType type, String symbol) throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(accountId)
                        .name(name)
                        .type(type)
                        .symbol(symbol)
                        .quantity(new BigDecimal("10.0"))
                        .purchasePrice(new BigDecimal("100.00"))
                        .currentPrice(new BigDecimal("110.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2025, 1, 15))
                        .build();

        String response =
                mockMvc.perform(
                                post("/api/v1/assets")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    /** Helper method to create a physical asset and return its ID. */
    private Long createPhysicalAsset(String name, AssetType type) throws Exception {
        AssetRequest req =
                AssetRequest.builder()
                        .accountId(null)
                        .name(name)
                        .type(type)
                        .condition(AssetCondition.GOOD)
                        .usefulLifeYears(5)
                        .quantity(new BigDecimal("1.0"))
                        .purchasePrice(new BigDecimal("1000.00"))
                        .currentPrice(new BigDecimal("900.00"))
                        .currency("USD")
                        .purchaseDate(LocalDate.of(2023, 9, 1))
                        .build();

        String response =
                mockMvc.perform(
                                post("/api/v1/assets")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
