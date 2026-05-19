package org.openfinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a search result for financial instrument symbols. Used when
 * users search for stocks, ETFs, cryptocurrencies to add to their portfolio.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolSearchResult {

    /** Trading symbol (e.g., "AAPL", "MSFT", "BTC-USD"). */
    private String symbol;

    /** Full name of the instrument (e.g., "Apple Inc."). */
    private String name;

    /** Type of instrument (e.g., "EQUITY", "ETF", "CRYPTOCURRENCY", "MUTUALFUND"). */
    private String type;

    /** Stock exchange where the instrument is traded (e.g., "NASDAQ", "NYSE"). */
    private String exchange;

    /** Exchange display name (e.g., "NASDAQ Stock Market"). */
    private String exchangeDisplay;

    /** Industry sector (e.g., "Technology", "Healthcare"). */
    private String sector;

    /** Industry category (e.g., "Consumer Electronics", "Biotechnology"). */
    private String industry;
}
