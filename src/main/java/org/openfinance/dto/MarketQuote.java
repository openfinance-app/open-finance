package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a market quote for a financial instrument. Contains real-time
 * pricing information fetched from external market data providers.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketQuote {

    /** Trading symbol (e.g., "AAPL", "MSFT", "BTC-USD"). */
    private String symbol;

    /** Full name of the instrument (e.g., "Apple Inc."). */
    private String name;

    /** Current market price. */
    private BigDecimal price;

    /** Absolute price change from previous close. */
    private BigDecimal change;

    /** Percentage change from previous close. */
    private BigDecimal changePercent;

    /** Previous closing price. */
    private BigDecimal previousClose;

    /** Market opening price for the day. */
    private BigDecimal open;

    /** Day's high price. */
    private BigDecimal dayHigh;

    /** Day's low price. */
    private BigDecimal dayLow;

    /** Trading volume. */
    private Long volume;

    /** Market capitalization. */
    private Long marketCap;

    /** Currency of the quote (e.g., "USD", "EUR"). */
    private String currency;

    /** Stock exchange where the instrument is traded (e.g., "NASDAQ", "NYSE"). */
    private String exchange;

    /** Timestamp when the quote was fetched. */
    private LocalDateTime timestamp;

    /** Market status (e.g., "REGULAR", "PRE", "POST", "CLOSED"). */
    private String marketState;

    /** Type of instrument (e.g., "EQUITY", "CRYPTOCURRENCY", "ETF"). */
    private String quoteType;
}
