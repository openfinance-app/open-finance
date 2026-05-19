package org.openfinance.dto.calculator;

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
    private double defaultWithdrawalRate;

    /** Default inflation rate percentage. */
    private double defaultInflationRate;

    /** Minimum allowed withdrawal rate percentage. */
    private double minimumWithdrawalRate;

    /** Maximum allowed withdrawal rate percentage. */
    private double maximumWithdrawalRate;

    /** Minimum allowed return rate percentage. */
    private double minimumReturnRate;

    /** Maximum allowed return rate percentage. */
    private double maximumReturnRate;

    /** Default expected annual return rate. */
    private double defaultReturnRate;

    /** Maximum number of projection years. */
    private int maxProjectionYears;

    /** Default monthly contribution amount. */
    private double defaultMonthlyContribution;

    /** Default number of projection years. */
    private int defaultProjectionYears;
}
