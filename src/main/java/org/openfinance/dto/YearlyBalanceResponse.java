package org.openfinance.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing yearly balance variations for dashboard visualization.
 *
 * <p>
 * Returns year-end balances and year-over-year variation percentages for total
 * net worth,
 * individual accounts, and institution groups.
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YearlyBalanceResponse {

    /** Ordered list of years covered (from first transaction to last). */
    private List<Integer> years;

    /** Year-end net worth data points. */
    private List<YearlyDataPoint> netWorth;

    /** Year-end balance per individual account. */
    private List<YearlyBalanceEntry> accounts;

    /** Year-end balance per institution (sum of all accounts in institution). */
    private List<YearlyBalanceEntry> institutions;

    /** Base currency for all amounts. */
    private String currency;

    /** Year-end data point with amount and variation. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearlyDataPoint {
        private int year;
        private BigDecimal amount;
        /** Percentage change vs previous year; null for the first year. */
        private BigDecimal variationPercentage;
    }

    /** Named entry (account or institution) with yearly data. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearlyBalanceEntry {
        private Long id;
        private String name;
        private List<YearlyDataPoint> data;
    }
}
