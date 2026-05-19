package org.openfinance.provider;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.exception.MarketDataException;

/**
 * Unit tests for YahooFinanceProvider.
 *
 * <p>Note: Most tests are disabled by default as they require network access to Yahoo Finance API.
 * Enable them manually for integration testing or set up WireMock for proper unit tests.
 */
class YahooFinanceProviderTest {

    private YahooFinanceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new YahooFinanceProvider(new ObjectMapper());
    }

    @Test
    void shouldRejectNullSymbol() {
        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.getQuote(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbol cannot be null or empty");
    }

    @Test
    void shouldRejectEmptySymbol() {
        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.getQuote(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbol cannot be null or empty");
    }

    @Test
    void shouldRejectBlankSymbol() {
        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.getQuote("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbol cannot be null or empty");
    }

    @Test
    void shouldRejectNullSymbols() {
        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.getQuotes(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbols list cannot be null or empty");
    }

    @Test
    void shouldRejectEmptySymbols() {
        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.getQuotes(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbols list cannot be null or empty");
    }

    @Test
    void shouldRejectNullQuery() {
        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.searchSymbol(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query cannot be null or empty");
    }

    @Test
    void shouldRejectEmptyQuery() {
        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.searchSymbol(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query cannot be null or empty");
    }

    @Test
    void shouldRejectNullDates() {
        // When/Then: Should throw IllegalArgumentException for null startDate
        assertThatThrownBy(() -> provider.getHistoricalPrices("AAPL", null, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");

        // When/Then: Should throw IllegalArgumentException for null endDate
        assertThatThrownBy(() -> provider.getHistoricalPrices("AAPL", LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectInvalidDateRange() {
        // Given: End date before start date
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 1);

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> provider.getHistoricalPrices("AAPL", startDate, endDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date cannot be after end date");
    }

    /**
     * Integration test - requires network access. Enable manually to test real Yahoo Finance API.
     */
    @Test
    @Disabled("Requires network access - enable manually for integration testing")
    void shouldGetQuoteForApple() {
        // When: Get quote for AAPL
        MarketQuote quote = provider.getQuote("AAPL");

        // Then: Verify quote structure
        assertThat(quote).isNotNull();
        assertThat(quote.getSymbol()).isEqualTo("AAPL");
        assertThat(quote.getName()).contains("Apple");
        assertThat(quote.getPrice()).isNotNull().isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(quote.getCurrency()).isEqualTo("USD");
        assertThat(quote.getExchange()).isNotBlank();
        assertThat(quote.getTimestamp()).isNotNull();
    }

    /**
     * Integration test - requires network access. Enable manually to test real Yahoo Finance API.
     */
    @Test
    @Disabled("Requires network access - enable manually for integration testing")
    void shouldGetQuotesForMultipleSymbols() {
        // When: Get quotes for multiple symbols
        List<MarketQuote> quotes = provider.getQuotes(List.of("AAPL", "MSFT", "GOOGL"));

        // Then: Verify all quotes returned
        assertThat(quotes).hasSize(3);
        assertThat(quotes)
                .extracting(MarketQuote::getSymbol)
                .containsExactlyInAnyOrder("AAPL", "MSFT", "GOOGL");
    }

    /**
     * Integration test - requires network access. Enable manually to test real Yahoo Finance API.
     */
    @Test
    @Disabled("Requires network access - enable manually for integration testing")
    void shouldSearchSymbols() {
        // When: Search for "apple"
        List<SymbolSearchResult> results = provider.searchSymbol("apple");

        // Then: Should find AAPL in results
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> r.getSymbol().equals("AAPL"));
        assertThat(results).anyMatch(r -> r.getName().contains("Apple"));
    }

    /**
     * Integration test - requires network access. Enable manually to test real Yahoo Finance API.
     */
    @Test
    @Disabled("Requires network access - enable manually for integration testing")
    void shouldGetHistoricalPrices() {
        // Given: Date range
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // When: Get historical prices
        List<HistoricalPrice> prices = provider.getHistoricalPrices("AAPL", startDate, endDate);

        // Then: Verify data structure
        assertThat(prices).isNotEmpty();
        assertThat(prices.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(prices.get(0).getDate()).isNotNull();
        assertThat(prices.get(0).getOpen()).isNotNull();
        assertThat(prices.get(0).getClose()).isNotNull();
    }

    /**
     * Integration test - requires network access. Enable manually to test real Yahoo Finance API.
     */
    @Test
    @Disabled("Requires network access - enable manually for integration testing")
    void shouldThrowExceptionForInvalidSymbol() {
        // When/Then: Should throw MarketDataException for obviously invalid symbol
        assertThatThrownBy(() -> provider.getQuote("INVALID_SYMBOL_123456"))
                .isInstanceOf(MarketDataException.class);
    }
}
