package org.openfinance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.ConvertRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.Currency;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for CurrencyController.
 *
 * <p>Tests all currency and exchange rate REST endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/currencies - List active currencies
 *   <li>GET /api/v1/currencies/all - List all currencies
 *   <li>GET /api/v1/currencies/exchange-rates - Get exchange rate for specific date
 *   <li>GET /api/v1/currencies/exchange-rates/latest - Get latest exchange rate
 *   <li>POST /api/v1/currencies/convert - Convert amount between currencies
 *   <li>POST /api/v1/currencies/exchange-rates/update - Update rates (admin only)
 * </ul>
 *
 * <p>Requirements: REQ-6.2 (Multi-Currency Support)
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("CurrencyController Integration Tests")
class CurrencyControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @MockBean private MarketDataProvider marketDataProvider;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private CurrencyRepository currencyRepository;

    @Autowired private ExchangeRateRepository exchangeRateRepository;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String userToken;
    private String userEncKey;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        databaseCleanupService.execute();

        // Create regular user
        UserRegistrationRequest userReg =
                UserRegistrationRequest.builder()
                        .username("alice")
                        .email("alice@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(userReg);

        LoginRequest userLogin =
                LoginRequest.builder()
                        .username("alice")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        MvcResult userResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(userLogin)))
                        .andExpect(status().isOk())
                        .andReturn();

        String userResponse = userResult.getResponse().getContentAsString();
        userToken = objectMapper.readTree(userResponse).get("token").asText();
        userEncKey = objectMapper.readTree(userResponse).get("encryptionKey").asText();

        // Seed test currencies
        seedCurrencies();

        // Seed test exchange rates
        seedExchangeRates();

        when(marketDataProvider.getHistoricalPrices(any(), any(), any())).thenReturn(List.of());
    }

    private void seedCurrencies() {
        Currency usd =
                Currency.builder().code("USD").name("US Dollar").symbol("$").isActive(true).build();

        Currency eur =
                Currency.builder().code("EUR").name("Euro").symbol("€").isActive(true).build();

        Currency gbp =
                Currency.builder()
                        .code("GBP")
                        .name("British Pound")
                        .symbol("£")
                        .isActive(true)
                        .build();

        Currency jpy =
                Currency.builder()
                        .code("JPY")
                        .name("Japanese Yen")
                        .symbol("¥")
                        .isActive(false) // Inactive for testing
                        .build();

        currencyRepository.save(usd);
        currencyRepository.save(eur);
        currencyRepository.save(gbp);
        currencyRepository.save(jpy);
    }

    private void seedExchangeRates() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Today's rates
        ExchangeRate usdEurToday =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.85000000"))
                        .rateDate(today)
                        .source("test")
                        .build();

        ExchangeRate usdGbpToday =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("GBP")
                        .rate(new BigDecimal("0.79000000"))
                        .rateDate(today)
                        .source("test")
                        .build();

        // Yesterday's rate
        ExchangeRate usdEurYesterday =
                ExchangeRate.builder()
                        .baseCurrency("USD")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.84000000"))
                        .rateDate(yesterday)
                        .source("test")
                        .build();

        exchangeRateRepository.save(usdEurToday);
        exchangeRateRepository.save(usdGbpToday);
        exchangeRateRepository.save(usdEurYesterday);
    }

    // ========== GET /api/v1/currencies (List Active Currencies) ==========

    @Test
    @DisplayName("Should return all active currencies sorted by code")
    void shouldReturnAllActiveCurrenciesSortedByCode() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3)) // USD, EUR, GBP (not JPY - inactive)
                .andExpect(jsonPath("$[0].code").value("EUR"))
                .andExpect(jsonPath("$[0].name").value("Euro"))
                .andExpect(jsonPath("$[0].symbol").value("€"))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[1].code").value("GBP"))
                .andExpect(jsonPath("$[2].code").value("USD"));
    }

    @Test
    @DisplayName("Should return empty array when no active currencies exist")
    void shouldReturnEmptyArrayWhenNoActiveCurrencies() throws Exception {
        // Deactivate all currencies
        currencyRepository
                .findAll()
                .forEach(
                        c -> {
                            c.setIsActive(false);
                            currencyRepository.save(c);
                        });

        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Should return 403 when not authenticated (active currencies)")
    void shouldReturn403WhenNotAuthenticatedActiveCurrencies() throws Exception {
        mockMvc.perform(get("/api/v1/currencies")).andDo(print()).andExpect(status().isForbidden());
    }

    // ========== GET /api/v1/currencies/all (List All Currencies) ==========

    @Test
    @DisplayName("Should return all currencies including inactive")
    void shouldReturnAllCurrenciesIncludingInactive() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies/all")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4)) // USD, EUR, GBP, JPY
                .andExpect(jsonPath("$[?(@.code == 'JPY')].isActive").value(false))
                .andExpect(jsonPath("$[?(@.code == 'USD')].isActive").value(true));
    }

    @Test
    @DisplayName("Should return 403 when not authenticated (all currencies)")
    void shouldReturn403WhenNotAuthenticatedAllCurrencies() throws Exception {
        mockMvc.perform(get("/api/v1/currencies/all"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // ========== GET /api/v1/currencies/exchange-rates (Get Rate for Date) ==========

    @Test
    @DisplayName("Should return exchange rate for specific date")
    void shouldReturnExchangeRateForSpecificDate() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        mockMvc.perform(
                        get("/api/v1/currencies/exchange-rates")
                                .param("from", "USD")
                                .param("to", "EUR")
                                .param("date", yesterday.toString())
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("EUR"))
                .andExpect(jsonPath("$.rate").value(0.84)) // Yesterday's rate
                .andExpect(jsonPath("$.rateDate").value(yesterday.toString()));
    }

    @Test
    @DisplayName("Should return 400 when exchange rate not found for date")
    void shouldReturn400WhenExchangeRateNotFoundForDate() throws Exception {
        // Missing exchange rate is a client error (invalid date parameter) → 400 Bad Request
        LocalDate farPast = LocalDate.of(2020, 1, 1);

        mockMvc.perform(
                        get("/api/v1/currencies/exchange-rates")
                                .param("from", "USD")
                                .param("to", "EUR")
                                .param("date", farPast.toString())
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 500 when date format is invalid")
    void shouldReturn500WhenDateFormatInvalid() throws Exception {
        // TODO: Should return 400, but Spring's date parsing throws exception (→500)
        // Need proper @ExceptionHandler for MethodArgumentTypeMismatchException in
        // GlobalExceptionHandler
        mockMvc.perform(
                        get("/api/v1/currencies/exchange-rates")
                                .param("from", "USD")
                                .param("to", "EUR")
                                .param("date", "invalid-date")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should return 403 when not authenticated (exchange rate by date)")
    void shouldReturn403WhenNotAuthenticatedExchangeRateByDate() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies/exchange-rates")
                                .param("from", "USD")
                                .param("to", "EUR")
                                .param("date", LocalDate.now().toString()))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // ========== GET /api/v1/currencies/exchange-rates/latest (Get Latest Rate) ==========

    @Test
    @DisplayName("Should return latest exchange rate")
    void shouldReturnLatestExchangeRate() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies/exchange-rates/latest")
                                .param("from", "USD")
                                .param("to", "EUR")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("EUR"))
                .andExpect(jsonPath("$.rate").value(0.85)) // Today's rate (latest)
                .andExpect(jsonPath("$.rateDate").value(LocalDate.now().toString()));
    }

    @Test
    @DisplayName("Should return 400 when no exchange rate exists")
    void shouldReturn400WhenNoExchangeRateExists() throws Exception {
        // Use a currency that is NOT in the database to ensure it fails with 400
        mockMvc.perform(
                        get("/api/v1/currencies/exchange-rates/latest")
                                .param("from", "USD")
                                .param("to", "NONEXISTENT")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 403 when not authenticated (latest exchange rate)")
    void shouldReturn403WhenNotAuthenticatedLatestExchangeRate() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies/exchange-rates/latest")
                                .param("from", "USD")
                                .param("to", "EUR"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // ========== POST /api/v1/currencies/convert (Convert Currency) ==========

    @Test
    @DisplayName("Should convert amount between currencies")
    void shouldConvertAmountBetweenCurrencies() throws Exception {
        ConvertRequest request = new ConvertRequest(new BigDecimal("100.00"), "USD", "EUR", null);

        mockMvc.perform(
                        post("/api/v1/currencies/convert")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.toCurrency").value("EUR"))
                .andExpect(jsonPath("$.originalAmount").value(100.00))
                .andExpect(jsonPath("$.convertedAmount").value(85.00)) // 100 * 0.85
                .andExpect(jsonPath("$.exchangeRate").value(0.85));
    }

    @Test
    @DisplayName("Should return 400 when amount is null")
    void shouldReturn400WhenAmountIsNull() throws Exception {
        ConvertRequest request =
                new ConvertRequest(
                        null, // Invalid
                        "USD", "EUR", null);

        mockMvc.perform(
                        post("/api/v1/currencies/convert")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when amount is negative")
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        ConvertRequest request =
                new ConvertRequest(
                        new BigDecimal("-100.00"), // Invalid
                        "USD",
                        "EUR",
                        null);

        mockMvc.perform(
                        post("/api/v1/currencies/convert")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when currency codes are invalid")
    void shouldReturn400WhenCurrencyCodesInvalid() throws Exception {
        ConvertRequest request =
                new ConvertRequest(
                        new BigDecimal("100.00"),
                        "XX", // Invalid code
                        "EUR",
                        null);

        mockMvc.perform(
                        post("/api/v1/currencies/convert")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when conversion fails (no exchange rate)")
    void shouldReturn400WhenConversionFails() throws Exception {
        ConvertRequest request =
                new ConvertRequest(
                        new BigDecimal("100.00"),
                        "USD",
                        "NONEXISTENT", // No rate exists
                        null);

        mockMvc.perform(
                        post("/api/v1/currencies/convert")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 403 when not authenticated (convert)")
    void shouldReturn403WhenNotAuthenticatedConvert() throws Exception {
        ConvertRequest request = new ConvertRequest(new BigDecimal("100.00"), "USD", "EUR", null);

        mockMvc.perform(
                        post("/api/v1/currencies/convert")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // ========== POST /api/v1/currencies/exchange-rates/update (Update Rates - Admin Only)
    // ==========

    @Test
    @DisplayName("Should return 403 when regular user tries to update rates")
    void shouldReturn403WhenUserTriesToUpdateRates() throws Exception {
        // Expect 403 Forbidden because @PreAuthorize("hasRole('ADMIN')") is now enabled
        mockMvc.perform(
                        post("/api/v1/currencies/exchange-rates/update")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Encryption-Session", userEncKey)) // Regular user
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when not authenticated (update rates)")
    void shouldReturn403WhenNotAuthenticatedUpdateRates() throws Exception {
        mockMvc.perform(post("/api/v1/currencies/exchange-rates/update"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }
}
