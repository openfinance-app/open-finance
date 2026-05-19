package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing portfolio performance metrics.
 *
 * <p>Used for dashboard performance cards with sparkline charts.
 *
 * <p><b>Task 4.3.8:</b> Portfolio performance cards component data
 *
 * <p><b>Requirement REQ-2.6.3:</b> Portfolio performance analytics
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPerformance {

    /** Performance metric label (e.g., "Total Value", "Unrealized Gain", "30-Day Return") */
    private String label;

    /** Current value of the metric */
    private BigDecimal currentValue;

    /** Change in value over the period */
    private BigDecimal changeAmount;

    /** Percentage change over the period */
    private BigDecimal changePercentage;

    /** Currency code (e.g., "EUR", "USD") */
    private String currency;

    /** Historical data points for sparkline chart (date -> value) */
    private List<HistoricalDataPoint> sparklineData;

    /** Data point for sparkline chart */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalDataPoint {
        private LocalDate date;
        private BigDecimal value;
    }
}
