package org.openfinance.dto.calculator;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single year's projection in the financial freedom calculation.
 *
 * <p>Contains detailed breakdown of savings growth for a specific year including starting balance,
 * contributions, investment returns, withdrawals, and progress.
 *
 * <p>Requirement 4.2: Visual Representations
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionResult {

    /** Year number in the projection (0 = current year). */
    private int year;

    /** Starting balance at the beginning of this year. */
    private BigDecimal startingBalance;

    /** Ending balance after contributions, returns, and withdrawals. */
    private BigDecimal endingBalance;

    /** Total contributions made during this year. */
    private BigDecimal contributions;

    /** Investment returns earned during this year. */
    private BigDecimal investmentReturns;

    /** Withdrawals made during this year (for longevity calculations). */
    private BigDecimal withdrawals;

    /** Cumulative progress toward target as percentage (0-100). */
    private BigDecimal progressTowardTarget;

    /** Flag indicating if target was reached this year. */
    private boolean targetReached;
}
