package org.openfinance.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a summary of estimated interest across all accounts. Used by the Dashboard API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimatedInterestSummary {

    /** List of interest details per account. */
    private List<AccountInterest> accounts;

    /** The total actual net interest earned over the selected period across all accounts. */
    private BigDecimal totalEarned;

    /** The total projected 1-year net interest across all accounts. */
    private BigDecimal totalProjected;

    /** The currency used for the totals. */
    private String currency;
}
