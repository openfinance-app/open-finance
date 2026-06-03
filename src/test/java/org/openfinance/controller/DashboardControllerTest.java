package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.*;
import org.openfinance.entity.*;
import org.openfinance.repository.NetWorthRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for DashboardController focusing on Asset Allocation and
 * Portfolio Performance
 * endpoints.
 *
 * <p>
 * Tests cover: - GET /api/v1/dashboard/asset-allocation - GET
 * /api/v1/dashboard/portfolio-performance - Authentication and authorization -
 * Various data
 * scenarios (empty assets, multiple types, zero values, etc.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("DashboardController Integration Tests")
class DashboardControllerTest {

        @MockBean
        private OperationHistoryService operationHistoryService;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserService userService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private NetWorthRepository netWorthRepository;

        @Autowired
        private KeyManagementService keyManagementService;

        @Autowired
        private DatabaseCleanupService databaseCleanupService;

        private String token;
        private String encKey;
        private User testUser;
        private Long accountId;

        @BeforeEach
        void setUp() throws Exception {
                // Clean up
                databaseCleanupService.execute();

                // Create user
                UserRegistrationRequest reg = UserRegistrationRequest.builder()
                                .username("john_doe")
                                .email("john@example.com")
                                .password("securePass123")
                                .masterPassword("masterPass123")
                                .skipSeeding(true)
                                .build();
                userService.registerUser(reg);

                // Update user's base currency to EUR
                testUser = userRepository
                                .findByUsername("john_doe")
                                .orElseThrow(() -> new RuntimeException("User not found"));
                testUser.setBaseCurrency("EUR");
                userRepository.save(testUser);

                // Login
                LoginRequest login = LoginRequest.builder()
                                .username("john_doe")
                                .password("securePass123")
                                .masterPassword("masterPass123")
                                .build();

                String resp = mockMvc.perform(
                                post("/api/v1/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(login)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                token = objectMapper.readTree(resp).get("token").asText();
                encKey = objectMapper.readTree(resp).get("encryptionKey").asText();

                // Set security context for MockMvc
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(testUser, null,
                                testUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Create a brokerage account
                AccountRequest accountReq = AccountRequest.builder()
                                .name("Investment Account")
                                .type(AccountType.INVESTMENT)
                                .currency("EUR")
                                .initialBalance(new BigDecimal("10000.00"))
                                .build();

                String accountResp = mockMvc.perform(
                                post("/api/v1/accounts")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(accountReq)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                accountId = objectMapper.readTree(accountResp).get("id").asLong();
        }

        @Nested
        @DisplayName("GET /api/v1/dashboard/asset-allocation")
        class AssetAllocationEndpointTests {

                @Test
                @DisplayName("Should return asset allocation with multiple asset types")
                void shouldReturnAssetAllocationWithMultipleTypes() throws Exception {
                        // Create assets of different types
                        createAsset(
                                        "Apple Stock",
                                        AssetType.STOCK,
                                        "AAPL",
                                        new BigDecimal("100"),
                                        new BigDecimal("150"),
                                        new BigDecimal("180")); // Value: 18000
                        createAsset(
                                        "Bitcoin",
                                        AssetType.CRYPTO,
                                        "BTC-USD",
                                        new BigDecimal("0.5"),
                                        new BigDecimal("30000"),
                                        new BigDecimal("40000")); // Value: 20000
                        createAsset(
                                        "Corporate Bond",
                                        AssetType.BOND,
                                        "BOND123",
                                        new BigDecimal("10"),
                                        new BigDecimal("1000"),
                                        new BigDecimal("1050")); // Value: 10500
                        // Total: 48500

                        mockMvc.perform(
                                        get("/api/v1/dashboard/asset-allocation")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(3)))
                                        // CRYPTO should be first (highest value)
                                        .andExpect(jsonPath("$[0].type").value("CRYPTO"))
                                        .andExpect(jsonPath("$[0].typeName").value("Cryptocurrency"))
                                        .andExpect(jsonPath("$[0].totalValue").value(20000))
                                        .andExpect(jsonPath("$[0].percentage").value(closeTo(41.24, 0.1)))
                                        .andExpect(jsonPath("$[0].assetCount").value(1))
                                        .andExpect(jsonPath("$[0].currency").value("EUR"))
                                        // STOCK should be second
                                        .andExpect(jsonPath("$[1].type").value("STOCK"))
                                        .andExpect(jsonPath("$[1].totalValue").value(18000))
                                        .andExpect(jsonPath("$[1].percentage").value(closeTo(37.11, 0.1)))
                                        .andExpect(jsonPath("$[1].assetCount").value(1))
                                        // BOND should be third (lowest value)
                                        .andExpect(jsonPath("$[2].type").value("BOND"))
                                        .andExpect(jsonPath("$[2].totalValue").value(10500))
                                        .andExpect(jsonPath("$[2].percentage").value(closeTo(21.65, 0.1)))
                                        .andExpect(jsonPath("$[2].assetCount").value(1));
                }

                @Test
                @DisplayName("Should return empty array when no assets exist")
                void shouldReturnEmptyArrayWhenNoAssets() throws Exception {
                        mockMvc.perform(
                                        get("/api/v1/dashboard/asset-allocation")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                }

                @Test
                @DisplayName("Should group multiple assets of same type")
                void shouldGroupMultipleAssetsOfSameType() throws Exception {
                        // Create multiple stocks
                        createAsset(
                                        "Apple Stock",
                                        AssetType.STOCK,
                                        "AAPL",
                                        new BigDecimal("50"),
                                        new BigDecimal("150"),
                                        new BigDecimal("180")); // Value: 9000
                        createAsset(
                                        "Google Stock",
                                        AssetType.STOCK,
                                        "GOOGL",
                                        new BigDecimal("20"),
                                        new BigDecimal("2000"),
                                        new BigDecimal("2500")); // Value: 50000
                        createAsset(
                                        "Microsoft Stock",
                                        AssetType.STOCK,
                                        "MSFT",
                                        new BigDecimal("100"),
                                        new BigDecimal("200"),
                                        new BigDecimal("250")); // Value: 25000
                        // Total stock value: 84000

                        mockMvc.perform(
                                        get("/api/v1/dashboard/asset-allocation")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(1)))
                                        .andExpect(jsonPath("$[0].type").value("STOCK"))
                                        .andExpect(jsonPath("$[0].totalValue").value(84000))
                                        .andExpect(jsonPath("$[0].percentage").value(100.00))
                                        .andExpect(jsonPath("$[0].assetCount").value(3));
                }

                @Test
                @DisplayName("Should return empty list when total portfolio value is very small")
                void shouldHandleVerySmallPortfolioValue() throws Exception {
                        // Create asset with minimal value (using 4 decimal places max per validation)
                        createAsset(
                                        "Penny Stock",
                                        AssetType.STOCK,
                                        "PENNY",
                                        new BigDecimal("0.0001"),
                                        new BigDecimal("0.0001"),
                                        new BigDecimal("0.0001"));

                        // Small values should still appear, just with very small allocations
                        mockMvc.perform(
                                        get("/api/v1/dashboard/asset-allocation")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(1)))
                                        .andExpect(jsonPath("$[0].percentage").value(100.00));
                }

                @Test
                @DisplayName("Should return 403 when not authenticated")
                void shouldReturn403WhenNotAuthenticated() throws Exception {
                        mockMvc.perform(get("/api/v1/dashboard/asset-allocation"))
                                        .andDo(print())
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("Should return 403 when invalid token")
                void shouldReturn403WhenInvalidToken() throws Exception {
                        mockMvc.perform(
                                        get("/api/v1/dashboard/asset-allocation")
                                                        .header("Authorization", "Bearer invalid_token"))
                                        .andDo(print())
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("Should handle all asset types correctly")
                void shouldHandleAllAssetTypesCorrectly() throws Exception {
                        createAsset(
                                        "Stock",
                                        AssetType.STOCK,
                                        "AAPL",
                                        new BigDecimal("10"),
                                        new BigDecimal("100"),
                                        new BigDecimal("110"));
                        createAsset(
                                        "ETF",
                                        AssetType.ETF,
                                        "SPY",
                                        new BigDecimal("10"),
                                        new BigDecimal("400"),
                                        new BigDecimal("420"));
                        createAsset(
                                        "Crypto",
                                        AssetType.CRYPTO,
                                        "BTC",
                                        new BigDecimal("0.1"),
                                        new BigDecimal("40000"),
                                        new BigDecimal("45000"));
                        createAsset(
                                        "Bond",
                                        AssetType.BOND,
                                        "BOND",
                                        new BigDecimal("5"),
                                        new BigDecimal("1000"),
                                        new BigDecimal("1020"));
                        createAsset(
                                        "Real Estate",
                                        AssetType.REAL_ESTATE,
                                        "REIT",
                                        new BigDecimal("10"),
                                        new BigDecimal("500"),
                                        new BigDecimal("550"));

                        mockMvc.perform(
                                        get("/api/v1/dashboard/asset-allocation")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(5)))
                                        .andExpect(
                                                        jsonPath(
                                                                        "$[*].type",
                                                                        containsInAnyOrder(
                                                                                        "STOCK", "ETF", "CRYPTO",
                                                                                        "BOND", "REAL_ESTATE")));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/dashboard/portfolio-performance")
        class PortfolioPerformanceEndpointTests {

                @Test
                @DisplayName("Should return portfolio performance with historical data")
                void shouldReturnPortfolioPerformanceWithHistoricalData() throws Exception {
                        // Create assets
                        createAsset(
                                        "Apple Stock",
                                        AssetType.STOCK,
                                        "AAPL",
                                        new BigDecimal("100"),
                                        new BigDecimal("150"),
                                        new BigDecimal("180")); // Cost: 15000, Value: 18000
                        createAsset(
                                        "Bitcoin",
                                        AssetType.CRYPTO,
                                        "BTC-USD",
                                        new BigDecimal("1"),
                                        new BigDecimal("30000"),
                                        new BigDecimal("35000")); // Cost: 30000, Value: 35000
                        // Total cost: 45000, Total value: 53000, Gain: 8000 (17.78%)

                        // Create net worth history
                        createNetWorthSnapshot(LocalDate.now().minusDays(30), new BigDecimal("50000"));
                        createNetWorthSnapshot(LocalDate.now().minusDays(15), new BigDecimal("51000"));
                        createNetWorthSnapshot(LocalDate.now(), new BigDecimal("53000"));

                        mockMvc.perform(
                                        get("/api/v1/dashboard/portfolio-performance")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(3)))
                                        // Total Value metric
                                        .andExpect(jsonPath("$[0].label").value("Total Value"))
                                        .andExpect(jsonPath("$[0].currentValue").value(53000))
                                        .andExpect(jsonPath("$[0].changeAmount").value(3000)) // 53000 - 50000
                                        .andExpect(jsonPath("$[0].changePercentage").value(closeTo(6.00, 0.1)))
                                        .andExpect(jsonPath("$[0].currency").value("EUR"))
                                        .andExpect(jsonPath("$[0].sparklineData", hasSize(3)))
                                        .andExpect(jsonPath("$[0].sparklineData[0].value").value(50000))
                                        .andExpect(jsonPath("$[0].sparklineData[2].value").value(53000))
                                        // Unrealized Gain metric
                                        .andExpect(jsonPath("$[1].label").value("Unrealized Gain"))
                                        .andExpect(jsonPath("$[1].currentValue").value(8000))
                                        .andExpect(jsonPath("$[1].changeAmount").value(8000))
                                        .andExpect(jsonPath("$[1].changePercentage").value(closeTo(17.78, 0.1)))
                                        .andExpect(jsonPath("$[1].currency").value("EUR"))
                                        // Cost Basis metric
                                        .andExpect(jsonPath("$[2].label").value("Cost Basis"))
                                        .andExpect(jsonPath("$[2].currentValue").value(45000))
                                        .andExpect(jsonPath("$[2].changeAmount").value(0))
                                        .andExpect(jsonPath("$[2].changePercentage").value(0.00));
                }

                @Test
                @DisplayName("Should return empty array when no assets exist")
                void shouldReturnEmptyArrayWhenNoAssets() throws Exception {
                        mockMvc.perform(
                                        get("/api/v1/dashboard/portfolio-performance")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                }

                @Test
                @DisplayName("Should handle negative performance (losses)")
                void shouldHandleNegativePerformance() throws Exception {
                        // Create assets with losses
                        createAsset(
                                        "Losing Stock",
                                        AssetType.STOCK,
                                        "LOSE",
                                        new BigDecimal("100"),
                                        new BigDecimal("100"),
                                        new BigDecimal("80")); // Cost: 10000, Value: 8000, Loss: -2000

                        createNetWorthSnapshot(LocalDate.now().minusDays(30), new BigDecimal("10000"));
                        createNetWorthSnapshot(LocalDate.now(), new BigDecimal("8000"));

                        mockMvc.perform(
                                        get("/api/v1/dashboard/portfolio-performance")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$[0].currentValue").value(8000))
                                        .andExpect(jsonPath("$[0].changeAmount").value(-2000))
                                        .andExpect(jsonPath("$[0].changePercentage").value(closeTo(-20.00, 0.1)))
                                        .andExpect(jsonPath("$[1].currentValue").value(-2000))
                                        .andExpect(jsonPath("$[1].changePercentage").value(closeTo(-20.00, 0.1)));
                }

                @Test
                @DisplayName("Should handle empty historical data")
                void shouldHandleEmptyHistoricalData() throws Exception {
                        createAsset(
                                        "Stock",
                                        AssetType.STOCK,
                                        "AAPL",
                                        new BigDecimal("10"),
                                        new BigDecimal("100"),
                                        new BigDecimal("110"));

                        mockMvc.perform(
                                        get("/api/v1/dashboard/portfolio-performance")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(3)))
                                        .andExpect(jsonPath("$[0].sparklineData", hasSize(0)))
                                        .andExpect(jsonPath("$[0].changeAmount").value(0))
                                        .andExpect(jsonPath("$[0].changePercentage").value(0.00));
                }

                @Test
                @DisplayName("Should handle single historical data point")
                void shouldHandleSingleHistoricalDataPoint() throws Exception {
                        createAsset(
                                        "Stock",
                                        AssetType.STOCK,
                                        "AAPL",
                                        new BigDecimal("10"),
                                        new BigDecimal("100"),
                                        new BigDecimal("110"));
                        createNetWorthSnapshot(LocalDate.now(), new BigDecimal("1100"));

                        mockMvc.perform(
                                        get("/api/v1/dashboard/portfolio-performance")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$[0].sparklineData", hasSize(1)))
                                        .andExpect(jsonPath("$[0].changeAmount").value(0)) // Need at least 2 points
                                        .andExpect(jsonPath("$[0].changePercentage").value(0.00));
                }

                @Test
                @DisplayName("Should return 403 when not authenticated")
                void shouldReturn403WhenNotAuthenticated() throws Exception {
                        mockMvc.perform(get("/api/v1/dashboard/portfolio-performance"))
                                        .andDo(print())
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("Should return 403 when invalid token")
                void shouldReturn403WhenInvalidToken() throws Exception {
                        mockMvc.perform(
                                        get("/api/v1/dashboard/portfolio-performance")
                                                        .header("Authorization", "Bearer invalid_token"))
                                        .andDo(print())
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("Should calculate performance with mixed asset types")
                void shouldCalculatePerformanceWithMixedAssetTypes() throws Exception {
                        createAsset(
                                        "Stock",
                                        AssetType.STOCK,
                                        "AAPL",
                                        new BigDecimal("50"),
                                        new BigDecimal("100"),
                                        new BigDecimal("120"));
                        createAsset(
                                        "Crypto",
                                        AssetType.CRYPTO,
                                        "BTC",
                                        new BigDecimal("0.5"),
                                        new BigDecimal("30000"),
                                        new BigDecimal("35000"));
                        createAsset(
                                        "Bond",
                                        AssetType.BOND,
                                        "BOND",
                                        new BigDecimal("10"),
                                        new BigDecimal("1000"),
                                        new BigDecimal("1020"));
                        // Total cost: 5000 + 15000 + 10000 = 30000
                        // Total value: 6000 + 17500 + 10200 = 33700
                        // Gain: 3700 (12.33%)

                        mockMvc.perform(
                                        get("/api/v1/dashboard/portfolio-performance")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$[0].currentValue").value(33700))
                                        .andExpect(jsonPath("$[1].currentValue").value(3700))
                                        .andExpect(jsonPath("$[1].changePercentage").value(closeTo(12.33, 0.1)))
                                        .andExpect(jsonPath("$[2].currentValue").value(30000));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/dashboard/daily-cashflow")
        class DailyCashFlowEndpointTests {
                @Test
                @DisplayName("Should return 403 when not authenticated")
                void shouldReturn403WhenNotAuthenticated() throws Exception {
                        mockMvc.perform(get("/api/v1/dashboard/daily-cashflow?year=2024&month=2"))
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("Should return daily cash flow data")
                void shouldReturnDailyCashFlow() throws Exception {
                        LocalDate date1 = LocalDate.of(2024, 2, 1);
                        LocalDate date2 = LocalDate.of(2024, 2, 15);
                        LocalDate date3 = LocalDate.of(2024, 2, 28);

                        createTransaction(new BigDecimal("1000.00"), TransactionType.INCOME, date1, "Salary");
                        createTransaction(new BigDecimal("50.00"), TransactionType.EXPENSE, date1, "Groceries");
                        createTransaction(
                                        new BigDecimal("200.00"), TransactionType.EXPENSE, date2, "Utilities");
                        createTransaction(new BigDecimal("500.00"), TransactionType.INCOME, date3, "Bonus");

                        mockMvc.perform(
                                        get("/api/v1/dashboard/daily-cashflow?year=2024&month=2")
                                                        .header("Authorization", "Bearer " + token)
                                                        .header("X-Encryption-Session", encKey))
                                        .andDo(print())
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$").isArray())
                                        .andExpect(jsonPath("$.length()").value(29)) // 2024 is a leap year
                                        .andExpect(jsonPath("$[0].date").value("2024-02-01"))
                                        .andExpect(jsonPath("$[0].income").value(1000.0))
                                        .andExpect(jsonPath("$[0].expense").value(50.0))
                                        .andExpect(jsonPath("$[14].date").value("2024-02-15"))
                                        .andExpect(jsonPath("$[14].income").value(0))
                                        .andExpect(jsonPath("$[14].expense").value(200.0))
                                        .andExpect(jsonPath("$[27].date").value("2024-02-28"))
                                        .andExpect(jsonPath("$[27].income").value(500.0))
                                        .andExpect(jsonPath("$[27].expense").value(0));
                }
        }

        // ==================== Helper Methods ====================
        private void createTransaction(
                        BigDecimal amount, TransactionType type, LocalDate date, String description)
                        throws Exception {
                TransactionRequest tx = TransactionRequest.builder()
                                .accountId(accountId)
                                .amount(amount)
                                .currency("EUR")
                                .type(type)
                                .date(date)
                                .description(description)
                                .payee("Test Payee")
                                .build();
                mockMvc.perform(
                                post("/api/v1/transactions")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(tx)))
                                .andExpect(status().isCreated());
        }

        /**
         * Creates an asset using the API endpoint to ensure proper encryption handling.
         * This method
         * makes a real HTTP POST request to /api/v1/assets.
         */
        private void createAsset(
                        String name,
                        AssetType type,
                        String symbol,
                        BigDecimal quantity,
                        BigDecimal purchasePrice,
                        BigDecimal currentPrice)
                        throws Exception {
                AssetRequest assetRequest = AssetRequest.builder()
                                .accountId(accountId)
                                .name(name)
                                .type(type)
                                .symbol(symbol)
                                .quantity(quantity)
                                .purchasePrice(purchasePrice)
                                .currentPrice(currentPrice)
                                .currency("EUR")
                                .purchaseDate(LocalDate.now().minusMonths(6))
                                .notes("Test asset for dashboard")
                                .build();

                mockMvc.perform(
                                post("/api/v1/assets")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(assetRequest)))
                                .andExpect(status().isCreated());
        }

        /**
         * Creates a net worth snapshot for historical data testing. Direct repository
         * save is
         * acceptable here as NetWorth doesn't have encrypted fields.
         */
        private void createNetWorthSnapshot(LocalDate date, BigDecimal netWorth) {
                NetWorth nw = NetWorth.builder()
                                .userId(testUser.getId())
                                .snapshotDate(date)
                                .netWorth(netWorth)
                                .totalAssets(netWorth) // simplified
                                .totalLiabilities(BigDecimal.ZERO)
                                .currency("EUR")
                                .build();
                netWorthRepository.save(nw);
        }
}
