package org.openfinance.dto.calculator;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for financial freedom calculation.
 *
 * <p>Contains all input parameters needed to calculate the timeline for achieving financial freedom
 * based on current savings, expected expenses, and investment returns.
 *
 * <p><strong>Required Fields:</strong>
 *
 * <ul>
 *   <li>currentSavings - Current total savings and investments
 *   <li>monthlyExpenses - Expected monthly expenses in retirement
 *   <li>expectedAnnualReturn - Expected annual return rate on investments
 * </ul>
 *
 * <p><strong>Optional Fields:</strong>
 *
 * <ul>
 *   <li>monthlyContribution - Monthly savings contribution (default: 0)
 *   <li>withdrawalRate - Safe withdrawal rate (default: 4%)
 *   <li>inflationRate - Expected annual inflation rate (default: 2.5%)
 *   <li>adjustForInflation - Whether to adjust calculations for inflation
 * </ul>
 *
 * <p>Requirement 3.1: Parameter Input
 *
 * <p>Requirement 3.2: Input Validation
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreedomCalculatorRequest {

    /**
     * Current total savings and investments in EUR. This includes all liquid assets, investments,
     * and savings accounts.
     */
    @NotNull(message = "{calc.savings.required}")
    @DecimalMin(value = "0.0", message = "{calc.savings.min}")
    @Digits(integer = 15, fraction = 2, message = "{calc.savings.digits}")
    private BigDecimal currentSavings;

    /**
     * Expected monthly expenses in EUR during retirement. Used to calculate the target savings
     * amount needed for financial freedom.
     */
    @NotNull(message = "{calc.expenses.required}")
    @DecimalMin(value = "0.0", message = "{calc.expenses.min}")
    @Digits(integer = 12, fraction = 2, message = "{calc.expenses.digits}")
    private BigDecimal monthlyExpenses;

    /**
     * Expected annual return rate as a percentage. Represents the average annual return expected
     * from investments. Reasonable range: -10% (poor market) to 30% (exceptional market).
     */
    @NotNull(message = "{calc.return.required}")
    @DecimalMin(value = "-10.0", message = "{calc.return.min}")
    @DecimalMax(value = "30.0", message = "{calc.return.max}")
    private BigDecimal expectedAnnualReturn;

    /**
     * Monthly contribution to savings in EUR. Additional amount added to savings each month from
     * income. Default value: 0 (no monthly contributions).
     */
    @Builder.Default
    @DecimalMin(value = "0.0", message = "{calc.contribution.min}")
    @Digits(integer = 12, fraction = 2, message = "{calc.contribution.digits}")
    private BigDecimal monthlyContribution = BigDecimal.ZERO;

    /**
     * Safe withdrawal rate as a percentage for calculating target savings. Default follows the "4%
     * rule" - withdraw 4% of portfolio annually. Reasonable range: 0.5% (conservative) to 10%
     * (aggressive).
     */
    @Builder.Default
    @DecimalMin(value = "0.5", message = "{calc.withdrawal.min}")
    @DecimalMax(value = "10.0", message = "{calc.withdrawal.max}")
    private BigDecimal withdrawalRate = new BigDecimal("4.0");

    /**
     * Expected annual inflation rate as a percentage. Used when adjustForInflation is true to
     * calculate real returns. Default: 2.5% (historical average).
     */
    @Builder.Default
    @DecimalMin(value = "0.0", message = "{calc.inflation.min}")
    private BigDecimal inflationRate = new BigDecimal("2.5");

    /**
     * Whether to adjust calculations for inflation. When true, uses real return rate (nominal
     * return - inflation). When false, uses nominal return rate as-is.
     */
    @Builder.Default private Boolean adjustForInflation = false;

    /**
     * Number of years to project into the future. Used for calculating year-by-year projections.
     * Default: 30 years.
     */
    @Builder.Default
    @DecimalMin(value = "1", message = "{calc.projection.min}")
    @DecimalMax(value = "100", message = "{calc.projection.max}")
    private Integer projectionYears = 30;
}
