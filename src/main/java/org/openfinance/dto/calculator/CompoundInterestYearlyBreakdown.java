package org.openfinance.dto.calculator;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Year-by-year breakdown entry for a compound interest calculation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompoundInterestYearlyBreakdown {

    /** Year number (1-based). */
    private int year;

    /** Balance at the start of this year. */
    private BigDecimal startingBalance;

    /** Total contributions made during this year. */
    private BigDecimal contributions;

    /** Interest earned during this year. */
    private BigDecimal interestEarned;

    /** Balance at the end of this year. */
    private BigDecimal endingBalance;

    /** Cumulative interest earned from year 1 through this year. */
    private BigDecimal cumulativeInterest;

    /** Cumulative contributions from year 1 through this year (including initial principal). */
    private BigDecimal cumulativePrincipal;
}
