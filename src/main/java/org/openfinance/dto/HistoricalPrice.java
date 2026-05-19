package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a historical price point for a financial instrument. Used for
 * charting price history, calculating returns, and analyzing performance.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalPrice {

    /** Trading symbol (e.g., "AAPL", "MSFT", "BTC-USD"). */
    private String symbol;

    /** Date of the price data. */
    private LocalDate date;

    /** Opening price for the period. */
    private BigDecimal open;

    /** Highest price during the period. */
    private BigDecimal high;

    /** Lowest price during the period. */
    private BigDecimal low;

    /** Closing price for the period. */
    private BigDecimal close;

    /** Adjusted closing price (accounts for splits, dividends). */
    private BigDecimal adjustedClose;

    /** Trading volume for the period. */
    private Long volume;
}
