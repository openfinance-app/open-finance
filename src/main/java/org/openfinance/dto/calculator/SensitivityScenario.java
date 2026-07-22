package org.openfinance.dto.calculator;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a sensitivity analysis scenario.
 *
 * <p>Shows how different return rates affect the timeline to financial freedom, allowing users to
 * understand the impact of market conditions.
 *
 * <p><strong>Scenario Types:</strong>
 *
 * <ul>
 *   <li>baseline - The expected scenario based on user input
 *   <li>optimistic - Better than expected market conditions (+2% return)
 *   <li>pessimistic - Worse than expected market conditions (-2% return)
 * </ul>
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
public class SensitivityScenario {

    /**
     * Description of the scenario. Example: "Optimistic (+2%)", "Pessimistic (-2%)", "Base Case"
     */
    private String label;

    /** Return rate used for this scenario as percentage. */
    private BigDecimal returnRate;

    /** Years to financial freedom with this scenario. */
    private int yearsToFreedom;

    /** Months to financial freedom with this scenario (0-11). */
    private double monthsToFreedom;

    /** Type of scenario: baseline, optimistic, or pessimistic. */
    private ScenarioType scenarioType;

    /** Enum for scenario types. */
    public enum ScenarioType {
        /** Baseline scenario using user's expected return rate. */
        BASELINE,

        /** Optimistic scenario with higher return rate. */
        OPTIMISTIC,

        /** Pessimistic scenario with lower return rate. */
        PESSIMISTIC
    }
}
