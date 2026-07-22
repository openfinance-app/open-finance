package org.openfinance.dto.calculator;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing default calculation parameters for the financial freedom calculator.
 *
 * <p>Provides clients with valid ranges and default values for calculator inputs, useful for
 * initializing UI components with appropriate defaults.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationDefaults {

    /** Default withdrawal rate percentage (4% rule). */
    private BigDecimal defaultWithdrawalRate;

    /** Default inflation rate percentage. */
    private BigDecimal defaultInflationRate;

    /** Minimum allowed withdrawal rate percentage. */
    private BigDecimal minimumWithdrawalRate;

    /** Maximum allowed withdrawal rate percentage. */
    private BigDecimal maximumWithdrawalRate;

    /** Minimum allowed return rate percentage. */
    private BigDecimal minimumReturnRate;

    /** Maximum allowed return rate percentage. */
    private BigDecimal maximumReturnRate;

    /** Default expected annual return rate. */
    private BigDecimal defaultReturnRate;

    /** Maximum number of projection years. */
    private int maxProjectionYears;

    /** Default monthly contribution amount. */
    private BigDecimal defaultMonthlyContribution;

    /** Default number of projection years. */
    private int defaultProjectionYears;
}
