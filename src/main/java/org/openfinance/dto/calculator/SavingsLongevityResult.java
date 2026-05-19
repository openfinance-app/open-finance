package org.openfinance.dto.calculator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for savings longevity calculation.
 *
 * <p>Contains results showing how long current savings will last given ongoing expenses and
 * expected investment returns.
 *
 * <p><strong>Key Metrics:</strong>
 *
 * <ul>
 *   <li>yearsUntilDepletion - Years until savings are depleted
 *   <li>isInfinite - Whether savings will last indefinitely
 *   <li>depletionYear - Calendar year when savings will be depleted
 *   <li>finalBalance - Balance at depletion (null if infinite)
 * </ul>
 *
 * <p>Requirement 2.1: Savings Duration
 *
 * <p>Requirement 2.2: Depletion Analysis
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsLongevityResult {

    /** Years until savings are fully depleted. 0 if savings are already depleted. */
    private int yearsUntilDepletion;

    /** Total months until depletion (including fractional month). */
    private double totalMonthsUntilDepletion;

    /**
     * Flag indicating if savings will last indefinitely. True when investment returns exceed
     * monthly expenses.
     */
    @JsonProperty("isInfinite")
    private boolean infinite;

    /** Calendar year when savings will be depleted. Null if infinite is true. */
    private Integer depletionYear;

    /**
     * Final balance before depletion. Null if infinite is true. Typically near zero or slightly
     * negative.
     */
    private BigDecimal finalBalance;

    /** Flag indicating if savings will deplete within the projection period. */
    @JsonProperty("willDeplete")
    private boolean willDeplete;

    /** Year-by-year projections until depletion. */
    private List<ProjectionResult> depletionProjections;

    /** Current monthly expenses. */
    private BigDecimal monthlyExpenses;

    /** Current savings balance. */
    private BigDecimal currentSavings;

    /** Expected annual return rate used for calculation. */
    private BigDecimal annualReturnRate;

    /** Human-readable message about the longevity result. */
    private String message;
}
