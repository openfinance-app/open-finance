package org.openfinance.provider;

import java.time.LocalDate;
import java.util.List;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.dto.SymbolSearchResult;

/**
 * Interface for market data providers that fetch real-time and historical financial data.
 * Implementations can integrate with various market data APIs (Yahoo Finance, Alpha Vantage, etc.).
 *
 * <p>This interface defines the contract for retrieving:
 *
 * <ul>
 *   <li>Real-time quotes for stocks, ETFs, cryptocurrencies
 *   <li>Historical price data for performance analysis
 *   <li>Symbol search for instrument discovery
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 * MarketDataProvider provider = new YahooFinanceProvider();
 * MarketQuote quote = provider.getQuote("AAPL");
 * System.out.println("AAPL price: $" + quote.getPrice());
 * </pre>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
public interface MarketDataProvider {

    /**
     * Retrieves a real-time quote for a single symbol.
     *
     * <p>The quote includes current price, change, volume, and other market data. Data freshness
     * depends on the provider and market status (real-time, delayed, or closed).
     *
     * @param symbol the trading symbol (e.g., "AAPL", "BTC-USD", "^GSPC")
     * @return the market quote with current pricing data
     * @throws org.openfinance.exception.MarketDataException if the symbol is invalid or the API
     *     request fails
     * @throws IllegalArgumentException if the symbol is null or empty
     */
    MarketQuote getQuote(String symbol);

    /**
     * Retrieves real-time quotes for multiple symbols in a single batch request.
     *
     * <p>Batch requests are more efficient than individual requests when fetching multiple quotes.
     * The order of results may not match the order of input symbols.
     *
     * @param symbols list of trading symbols (e.g., ["AAPL", "MSFT", "GOOGL"])
     * @return list of market quotes; missing quotes for invalid symbols
     * @throws org.openfinance.exception.MarketDataException if the API request fails
     * @throws IllegalArgumentException if the symbols list is null or empty
     */
    List<MarketQuote> getQuotes(List<String> symbols);

    /**
     * Retrieves historical price data for a symbol within a date range.
     *
     * <p>Historical data includes open, high, low, close (OHLC) prices and volume for each trading
     * day. Useful for charting, calculating returns, and analyzing trends.
     *
     * @param symbol the trading symbol (e.g., "AAPL", "BTC-USD")
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of historical prices ordered by date (oldest first)
     * @throws org.openfinance.exception.MarketDataException if the symbol is invalid or the API
     *     request fails
     * @throws IllegalArgumentException if any parameter is null or if startDate is after endDate
     */
    List<HistoricalPrice> getHistoricalPrices(
            String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * Searches for financial instruments matching a query string.
     *
     * <p>Useful for autocomplete functionality when users add new assets to their portfolio.
     * Returns matching stocks, ETFs, cryptocurrencies, and other instruments.
     *
     * @param query the search term (e.g., "apple", "bitcoin", "SPY")
     * @return list of matching symbols with metadata (name, type, exchange)
     * @throws org.openfinance.exception.MarketDataException if the API request fails
     * @throws IllegalArgumentException if the query is null or empty
     */
    List<SymbolSearchResult> searchSymbol(String query);
}
