package org.openfinance.dto.calculator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for financial freedom calculation.
 *
 * <p>Contains all calculated results including timeline to financial freedom, target savings
 * amount, progress metrics, and projections.
 *
 * <p><strong>Key Metrics:</strong>
 *
 * <ul>
 *   <li>yearsToFreedom - Years until financial freedom is achieved
 *   <li>targetSavingsAmount - Total savings needed for financial freedom
 *   <li>progressPercentage - Current progress toward goal as percentage
 *   <li>annualPassiveIncome - Expected annual income from investments at target
 * </ul>
 *
 * <p><strong>Additional Data:</strong>
 *
 * <ul>
 *   <li>yearlyProjections - Year-by-year breakdown of savings growth
 *   <li>sensitivityScenarios - Comparison of different return rate scenarios
 * </ul>
 *
 * <p>Requirement 4.1: Primary Results
 *
 * <p>Requirement 4.2: Visual Representations
 *
 * <p>Requirement 4.3: Sensitivity Analysis
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreedomCalculatorResponse {

    /**
     * Whole years until financial freedom is achieved. Calculated based on monthly progress toward
     * target.
     */
    private int yearsToFreedom;

    /**
     * Additional months beyond whole years (0-11). Combined with yearsToFreedom for precise
     * timeline.
     */
    private double monthsToFreedom;

    /**
     * Target savings amount needed to achieve financial freedom. Calculated as: Annual Expenses /
     * (Withdrawal Rate / 100)
     */
    private BigDecimal targetSavingsAmount;

    /**
     * Current progress toward target as a percentage. Calculated as: (Current Savings / Target
     * Amount) * 100
     */
    private double progressPercentage;

    /** Current savings amount (same as input). Included for reference in UI display. */
    private BigDecimal currentProgress;

    /**
     * Estimated annual passive income from investments at target amount. Calculated as: Target
     * Amount * (Withdrawal Rate / 100) This represents the sustainable annual income.
     */
    private BigDecimal annualPassiveIncome;

    /**
     * Flag indicating if savings will last indefinitely. True when investment returns exceed
     * withdrawal rate.
     */
    @JsonProperty("isSustainableIndefinitely")
    private boolean sustainableIndefinitely;

    /**
     * Flag indicating if financial freedom is achievable. False when savings and contributions are
     * insufficient to reach target.
     */
    @JsonProperty("isAchievable")
    private boolean achievable;

    /**
     * Year-by-year projections of savings growth. Includes balance, contributions, returns, and
     * progress for each year.
     */
    @JsonProperty("projections")
    private List<ProjectionResult> yearlyProjections;

    /** Sensitivity analysis scenarios. Shows impact of different return rates on timeline. */
    private List<SensitivityScenario> sensitivityScenarios;

    /**
     * Human-readable message about the calculation result. Provides context or warnings about the
     * calculation.
     */
    private String message;
}
