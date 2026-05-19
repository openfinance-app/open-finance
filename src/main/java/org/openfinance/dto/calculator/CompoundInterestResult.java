package org.openfinance.dto.calculator;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for compound interest calculation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompoundInterestResult {

    /** Final balance at the end of the investment period. */
    private BigDecimal finalBalance;

    /** Initial principal amount. */
    private BigDecimal principal;

    /** Total additional contributions made over the period. */
    private BigDecimal totalContributions;

    /** Total interest earned over the period. */
    private BigDecimal totalInterest;

    /** Total amount invested (principal + contributions). */
    private BigDecimal totalInvested;

    /** Effective annual rate (EAR) reflecting the true annual yield after compounding. */
    private BigDecimal effectiveAnnualRate;

    /** Year-by-year breakdown. */
    private List<CompoundInterestYearlyBreakdown> yearlyBreakdown;
}
