package org.openfinance.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.BudgetPeriod;

/**
 * Data Transfer Object for budget summary information.
 *
 * <p>This DTO provides an aggregate view of all budgets for a specific period, including total
 * budgeted amounts, total spent, and individual budget details.
 *
 * <p>Used by the GET /api/budgets/summary?period=MONTHLY endpoint.
 *
 * <p>Requirement REQ-2.9.1.3: Budget reports with budgeted vs actual
 *
 * <p>Requirement REQ-2.9.1.3: Aggregate statistics across all budgets
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSummaryResponse {

    /**
     * Budget period being summarized.
     *
     * <p>All budgets in the summary share this period type.
     */
    private BudgetPeriod period;

    /**
     * Total number of budgets for this period.
     *
     * <p>Includes all budgets regardless of status (active, expired, etc.).
     */
    private Integer totalBudgets;

    /**
     * Number of currently active budgets.
     *
     * <p>Budgets where current date falls between startDate and endDate.
     */
    private Integer activeBudgets;

    /**
     * Total budgeted amount across all budgets.
     *
     * <p>Sum of all budget amounts for this period. Requirement REQ-2.9.1.3: Display total budgeted
     * amount
     */
    private BigDecimal totalBudgeted;

    /**
     * Total amount spent across all budgets.
     *
     * <p>Sum of all spending in categories with budgets for this period. Requirement REQ-2.9.1.3:
     * Display total actual spending
     */
    private BigDecimal totalSpent;

    /**
     * Total remaining amount across all budgets.
     *
     * <p>Calculated as: totalBudgeted - totalSpent
     *
     * <p>Can be negative if over budget overall.
     *
     * <p>Requirement REQ-2.9.1.3: Calculate variance (budgeted - actual)
     */
    private BigDecimal totalRemaining;

    /**
     * Average percentage spent across all budgets.
     *
     * <p>Provides an overall budget health indicator. Calculated as average of individual budget
     * percentages.
     */
    private BigDecimal averageSpentPercentage;

    /**
     * List of individual budget progress details.
     *
     * <p>Contains full progress information for each budget in this period. Allows clients to
     * display detailed breakdowns.
     *
     * <p>Requirement REQ-2.9.1.3: Report individual budget performance
     */
    private List<BudgetProgressResponse> budgets;

    /**
     * Currency code for all monetary values.
     *
     * <p>Note: If user has budgets in multiple currencies, this summary assumes single currency.
     * Multi-currency summary requires currency conversion logic (future enhancement).
     *
     * <p>Requirement REQ-2.8: Multi-currency awareness
     */
    private String currency;
}
