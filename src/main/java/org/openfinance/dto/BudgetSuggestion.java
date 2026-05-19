package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.BudgetPeriod;

/**
 * DTO representing a single automatic budget suggestion derived from transaction history analysis.
 *
 * <p>Each suggestion corresponds to one EXPENSE category and contains the computed average spending
 * per target period together with metadata the frontend needs for display and user-confirmation.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.1.5: Automatic budget creation from transaction history analysis
 * </ul>
 *
 * @see BudgetSuggestionRequest
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSuggestion {

    /** The category ID this suggestion applies to. */
    private Long categoryId;

    /** Human-readable category name (denormalized for display). */
    private String categoryName;

    /**
     * Suggested budget amount — the ceiling of the average spend per target period, expressed as a
     * positive {@link BigDecimal}.
     */
    private BigDecimal suggestedAmount;

    /**
     * Exact arithmetic average of per-period spending across the lookback window (before ceiling
     * rounding).
     */
    private BigDecimal averageSpent;

    /**
     * Total number of individual EXPENSE transactions found for this category in the lookback
     * window (across all sub-periods).
     */
    private int transactionCount;

    /**
     * The budget period type that was used when computing per-period averages (mirrors {@link
     * BudgetSuggestionRequest#getPeriod()}).
     */
    private BudgetPeriod period;

    /** ISO 4217 currency code for all amounts in this suggestion. */
    private String currency;

    /** Start of the lookback analysis window (inclusive). */
    private LocalDate startDate;

    /** End of the lookback analysis window (inclusive). */
    private LocalDate endDate;

    /**
     * {@code true} when the user already has a budget for this category+period combination. The
     * frontend renders a warning badge and the bulk-create endpoint will skip this entry to prevent
     * duplicates.
     */
    private boolean hasExistingBudget;
}
