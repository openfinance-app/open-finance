package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.Currency;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.exception.MarketDataException;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;

/**
 * Unit tests for the ExchangeRateService.
 *
 * <p>Tests business logic for exchange rate management:
 *
 * <ul>
 *   <li>getExchangeRate() - retrieve rates with fallback to inverse
 *   <li>convert() - currency conversion with validation
 *   <li>updateExchangeRates() - fetch rates from Yahoo Finance
 *   <li>clearCache() - cache management
 *   <li>validateCurrency() - currency validation logic
 *   <li>findRate() - database lookup with date handling
 *   <li>parseQuoteToExchangeRate() - Yahoo Finance quote parsing
 * </ul>
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRate Service Tests")
class ExchangeRateServiceTest {

    @Mock private ExchangeRateRepository exchangeRateRepository;

    @Mock private CurrencyRepository currencyRepository;

    @Mock private MarketDataProvider marketDataProvider;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private ExchangeRateService exchangeRateService;

    // ==================== getExchangeRate() Tests ====================

    @Test
    @DisplayName("Should return 1.0 for same currency conversion")
    void shouldReturnOneForSameCurrencyConversion() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);

        // Act
        BigDecimal rate = exchangeRateService.getExchangeRate("USD", "USD", null);

        // Assert
        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
        verify(exchangeRateRepository, never())
                .findLatestByBaseCurrencyAndTargetCurrency(anyString(), anyString());
    }

    @Test
    @DisplayName("Should return direct rate when available")
    void shouldReturnDirectRateWhenAvailable() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);

        ExchangeRate rate = createRate("USD", "EUR", new BigDecimal("0.85"), LocalDate.now());
        when(exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(List.of(rate));

        // Act
        BigDecimal result = exchangeRateService.getExchangeRate("USD", "EUR", null);

        // Assert
        assertThat(result).isEqualByComparingTo("0.85");
        verify(exchangeRateRepository).findLatestByBaseCurrencyAndTargetCurrency("USD", "EUR");
    }

    @Test
    @DisplayName("Should calculate inverse rate when only inverse available")
    void shouldCalculateInverseRateWhenOnlyInverseAvailable() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);

        ExchangeRate inverseRate =
                createRate("EUR", "USD", new BigDecimal("1.17647059"), LocalDate.now());
        when(exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(List.of());
        when(exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("EUR", "USD"))
                .thenReturn(List.of(inverseRate));

        // Act
        BigDecimal result = exchangeRateService.getExchangeRate("USD", "EUR", null);

        // Assert
        // Inverse of 1.17647059 ≈ 0.85
        assertThat(result)
                .isEqualByComparingTo(
                        BigDecimal.ONE.divide(
                                new BigDecimal("1.17647059"), 8, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Should throw exception when no rate found")
    void shouldThrowExceptionWhenNoRateFound() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("XXX")).thenReturn(true);

        when(exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency(
                        anyString(), anyString()))
                .thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> exchangeRateService.getExchangeRate("USD", "XXX", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No exchange rate available");
    }

    @Test
    @DisplayName("Should throw exception when currency does not exist")
    void shouldThrowExceptionWhenCurrencyDoesNotExist() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("INVALID")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> exchangeRateService.getExchangeRate("USD", "INVALID", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency not found: INVALID");
    }

    @Test
    @DisplayName("Should return rate for specific date")
    void shouldReturnRateForSpecificDate() {
        // Arrange
        LocalDate date = LocalDate.of(2024, 3, 15);
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("GBP")).thenReturn(true);

        ExchangeRate rate = createRate("USD", "GBP", new BigDecimal("0.79"), date);
        when(exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        "USD", "GBP", date))
                .thenReturn(Optional.of(rate));

        // Act
        BigDecimal result = exchangeRateService.getExchangeRate("USD", "GBP", date);

        // Assert
        assertThat(result).isEqualByComparingTo("0.79");
    }

    @Test
    @DisplayName("Should fall back to historical rate when exact date not found")
    void shouldFallBackToHistoricalRateWhenExactDateNotFound() {
        // Arrange
        LocalDate requestDate = LocalDate.of(2024, 3, 15);
        LocalDate availableDate = LocalDate.of(2024, 3, 10);

        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("JPY")).thenReturn(true);

        ExchangeRate rate = createRate("USD", "JPY", new BigDecimal("150.50"), availableDate);
        when(exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        "USD", "JPY", requestDate))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository
                        .findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                                "USD", "JPY", requestDate))
                .thenReturn(List.of(rate));

        // Act
        BigDecimal result = exchangeRateService.getExchangeRate("USD", "JPY", requestDate);

        // Assert
        assertThat(result).isEqualByComparingTo("150.50");
    }

    // ==================== convert() Tests ====================

    @Test
    @DisplayName("Should convert amount between currencies")
    void shouldConvertAmountBetweenCurrencies() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);

        ExchangeRate rate = createRate("USD", "EUR", new BigDecimal("0.85"), LocalDate.now());
        when(exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(List.of(rate));

        // Act
        BigDecimal result = exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR");

        // Assert
        assertThat(result).isEqualByComparingTo("85.00000000"); // 100 * 0.85 with 8 decimal scale
    }

    @Test
    @DisplayName("Should convert with historical date")
    void shouldConvertWithHistoricalDate() {
        // Arrange
        LocalDate date = LocalDate.of(2024, 1, 1);
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("GBP")).thenReturn(true);

        ExchangeRate rate = createRate("USD", "GBP", new BigDecimal("0.80"), date);
        when(exchangeRateRepository.findByBaseCurrencyAndTargetCurrencyAndRateDate(
                        "USD", "GBP", date))
                .thenReturn(Optional.of(rate));

        // Act
        BigDecimal result = exchangeRateService.convert(new BigDecimal("50"), "USD", "GBP", date);

        // Assert
        assertThat(result).isEqualByComparingTo("40.00000000"); // 50 * 0.80
    }

    @Test
    @DisplayName("Should throw exception for null amount")
    void shouldThrowExceptionForNullAmount() {
        // Act & Assert
        assertThatThrownBy(() -> exchangeRateService.convert(null, "USD", "EUR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount cannot be null");
    }

    @Test
    @DisplayName("Should allow and convert negative amount")
    void shouldAllowNegativeAmount() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);

        ExchangeRate rate = createRate("USD", "EUR", new BigDecimal("0.85"), LocalDate.now());
        when(exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(List.of(rate));

        // Act
        BigDecimal result = exchangeRateService.convert(new BigDecimal("-100"), "USD", "EUR");

        // Assert
        assertThat(result).isEqualByComparingTo("-85.00000000");
    }

    @Test
    @DisplayName("Should round result to 8 decimal places")
    void shouldRoundResultTo8DecimalPlaces() {
        // Arrange
        when(currencyRepository.existsByCode("USD")).thenReturn(true);
        when(currencyRepository.existsByCode("BTC")).thenReturn(true);

        ExchangeRate rate = createRate("USD", "BTC", new BigDecimal("0.00001053"), LocalDate.now());
        when(exchangeRateRepository.findLatestByBaseCurrencyAndTargetCurrency("USD", "BTC"))
                .thenReturn(List.of(rate));

        // Act
        BigDecimal result = exchangeRateService.convert(new BigDecimal("1000"), "USD", "BTC");

        // Assert
        assertThat(result.scale()).isEqualTo(8);
    }

    // ==================== updateExchangeRates() Tests ====================

    @Test
    @DisplayName("Should fetch and store exchange rates from Yahoo Finance")
    void shouldFetchAndStoreExchangeRatesFromYahooFinance() {
        // Arrange
        List<Currency> currencies =
                List.of(
                        createCurrency("USD", "US Dollar", true),
                        createCurrency("EUR", "Euro", true),
                        createCurrency("BTC", "Bitcoin", true));
        when(currencyRepository.findByIsActiveTrueOrderByCodeAsc()).thenReturn(currencies);

        MarketQuote eurQuote = createMarketQuote("EURUSD=X", 1.08);
        MarketQuote btcQuote = createMarketQuote("BTC-USD", 95000.00);
        when(marketDataProvider.getQuotes(anyList())).thenReturn(List.of(eurQuote, btcQuote));

        // Act
        int count = exchangeRateService.updateExchangeRates();

        // Assert
        assertThat(count).isEqualTo(4); // 2 direct quotes + 2 inverse rates
        verify(currencyRepository).findByIsActiveTrueOrderByCodeAsc();
        verify(marketDataProvider).getQuotes(anyList());
        verify(exchangeRateRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Should return 0 when no active currencies")
    void shouldReturnZeroWhenNoActiveCurrencies() {
        // Arrange
        when(currencyRepository.findByIsActiveTrueOrderByCodeAsc()).thenReturn(List.of());

        // Act
        int count = exchangeRateService.updateExchangeRates();

        // Assert
        assertThat(count).isZero();
        verify(marketDataProvider, never()).getQuotes(anyList());
    }

    @Test
    @DisplayName("Should throw exception when Yahoo Finance API fails")
    void shouldThrowExceptionWhenYahooFinanceApiFails() {
        // Arrange
        List<Currency> currencies = List.of(createCurrency("EUR", "Euro", true));
        when(currencyRepository.findByIsActiveTrueOrderByCodeAsc()).thenReturn(currencies);
        when(marketDataProvider.getQuotes(anyList()))
                .thenThrow(new MarketDataException("API unavailable"));

        // Act & Assert
        assertThatThrownBy(() -> exchangeRateService.updateExchangeRates())
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("API unavailable");
    }

    @Test
    @DisplayName("Should parse fiat currency quote correctly")
    void shouldParseFiatCurrencyQuoteCorrectly() {
        // Arrange
        List<Currency> currencies =
                List.of(
                        createCurrency("USD", "US Dollar", true),
                        createCurrency("EUR", "Euro", true));
        when(currencyRepository.findByIsActiveTrueOrderByCodeAsc()).thenReturn(currencies);

        MarketQuote quote = createMarketQuote("EURUSD=X", 1.08);
        when(marketDataProvider.getQuotes(anyList())).thenReturn(List.of(quote));

        // Act
        exchangeRateService.updateExchangeRates();

        // Assert
        verify(exchangeRateRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Should parse crypto currency quote correctly with inverse")
    void shouldParseCryptoCurrencyQuoteCorrectlyWithInverse() {
        // Arrange
        List<Currency> currencies =
                List.of(
                        createCurrency("USD", "US Dollar", true),
                        createCurrency("BTC", "Bitcoin", true));
        when(currencyRepository.findByIsActiveTrueOrderByCodeAsc()).thenReturn(currencies);

        MarketQuote quote = createMarketQuote("BTC-USD", 95000.00);
        when(marketDataProvider.getQuotes(anyList())).thenReturn(List.of(quote));

        // Act
        exchangeRateService.updateExchangeRates();

        // Assert
        verify(exchangeRateRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Should skip invalid quotes with zero or negative prices")
    void shouldSkipInvalidQuotesWithZeroOrNegativePrices() {
        // Arrange
        List<Currency> currencies =
                List.of(
                        createCurrency("USD", "US Dollar", true),
                        createCurrency("EUR", "Euro", true),
                        createCurrency("GBP", "British Pound", true));
        when(currencyRepository.findByIsActiveTrueOrderByCodeAsc()).thenReturn(currencies);

        MarketQuote validQuote = createMarketQuote("EURUSD=X", 1.08);
        MarketQuote invalidQuote1 = createMarketQuote("GBPUSD=X", 0.0);
        MarketQuote invalidQuote2 = createMarketQuote("JPYUSD=X", -1.0);
        when(marketDataProvider.getQuotes(anyList()))
                .thenReturn(List.of(validQuote, invalidQuote1, invalidQuote2));

        // Act
        int count = exchangeRateService.updateExchangeRates();

        // Assert
        assertThat(count).isEqualTo(2); // 1 valid quote + 1 inverse rate
        verify(exchangeRateRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Should build correct Yahoo Finance symbols")
    void shouldBuildCorrectYahooFinanceSymbols() {
        // Arrange
        List<Currency> currencies =
                List.of(
                        createCurrency("USD", "US Dollar", true),
                        createCurrency("EUR", "Euro", true),
                        createCurrency("GBP", "British Pound", true),
                        createCurrency("BTC", "Bitcoin", true),
                        createCurrency("ETH", "Ethereum", true));
        when(currencyRepository.findByIsActiveTrueOrderByCodeAsc()).thenReturn(currencies);
        when(marketDataProvider.getQuotes(anyList())).thenReturn(List.of());

        // Act
        exchangeRateService.updateExchangeRates();

        // Assert
        verify(marketDataProvider)
                .getQuotes(
                        argThat(
                                symbols ->
                                        symbols.size() == 4
                                                && // USD excluded
                                                symbols.contains("EURUSD=X")
                                                && symbols.contains("GBPUSD=X")
                                                && symbols.contains("BTC-USD")
                                                && symbols.contains("ETH-USD")));
    }

    // ==================== updateExchangeRatesForDate() Tests ====================

    @Test
    @DisplayName("Should reject future dates for historical rate updates")
    void shouldRejectFutureDatesForHistoricalRateUpdates() {
        // Arrange
        LocalDate futureDate = LocalDate.now().plusDays(7);

        // Act & Assert
        assertThatThrownBy(() -> exchangeRateService.updateExchangeRatesForDate(futureDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot fetch exchange rates for future date");
    }

    @Test
    @DisplayName("Should accept past dates for historical rate updates")
    void shouldAcceptPastDatesForHistoricalRateUpdates() {
        // Arrange
        LocalDate pastDate = LocalDate.of(2023, 1, 1);

        // Act & Assert - should not throw
        int result = exchangeRateService.updateExchangeRatesForDate(pastDate);

        // Currently returns 0 as historical fetching is not implemented
        assertThat(result).isZero();
    }

    // ==================== clearCache() Tests ====================

    @Test
    @DisplayName("Should clear exchange rate cache")
    void shouldClearExchangeRateCache() {
        // Act & Assert - should not throw
        exchangeRateService.clearCache();

        // Cache eviction is handled by Spring @CacheEvict annotation
        // This test verifies the method can be called without errors
    }

    // ==================== Helper Methods ====================

    private ExchangeRate createRate(String base, String target, BigDecimal rate, LocalDate date) {
        return ExchangeRate.builder()
                .baseCurrency(base)
                .targetCurrency(target)
                .rate(rate)
                .rateDate(date)
                .source("test")
                .build();
    }

    private Currency createCurrency(String code, String name, boolean isActive) {
        return Currency.builder().code(code).name(name).symbol(code).isActive(isActive).build();
    }

    private MarketQuote createMarketQuote(String symbol, double price) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(symbol);
        quote.setPrice(BigDecimal.valueOf(price));
        return quote;
    }
}
