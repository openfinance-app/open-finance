package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.BudgetPeriod;

/**
 * Data Transfer Object for budget progress tracking.
 *
 * <p>This DTO provides detailed progress information for a specific budget, including spending
 * statistics, remaining amounts, and status indicators.
 *
 * <p>Used by the GET /api/budgets/{id}/progress endpoint.
 *
 * <p>Requirement REQ-2.9.1.2: Budget tracking with spent/remaining calculations
 *
 * <p>Requirement REQ-2.9.1.2: Visual indicators and progress percentages
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetProgressResponse {

    /** ID of the budget being tracked. */
    private Long budgetId;

    /**
     * Name of the category this budget applies to.
     *
     * <p>Denormalized for client convenience.
     */
    private String categoryName;

    /**
     * Total budgeted amount for the period.
     *
     * <p>Requirement REQ-2.9.1.2: Display budget amount
     */
    private BigDecimal budgeted;

    /**
     * Amount spent so far in the current period.
     *
     * <p>Sum of all transactions in this category between startDate and endDate. For EXPENSE
     * categories, this is positive. For INCOME categories, this represents income received.
     *
     * <p>Requirement REQ-2.9.1.2: Calculate spent amount from transactions
     */
    private BigDecimal spent;

    /**
     * Remaining budget amount.
     *
     * <p>Calculated as: budgeted - spent
     *
     * <p>Can be negative if over budget.
     *
     * <p>Requirement REQ-2.9.1.2: Calculate remaining = Budget - Spent
     */
    private BigDecimal remaining;

    /**
     * Percentage of budget spent.
     *
     * <p>Calculated as: (spent / budgeted) × 100
     *
     * <p>Can exceed 100% if over budget.
     *
     * <p>Requirement REQ-2.9.1.2: Calculate percentage = (Spent / Budget) × 100
     */
    private BigDecimal percentageSpent;

    /**
     * Currency code in ISO 4217 format.
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    private String currency;

    /** Budget period type. */
    private BudgetPeriod period;

    /** Start date of the budget period. */
    private LocalDate startDate;

    /** End date of the budget period. */
    private LocalDate endDate;

    /**
     * Number of days remaining in the budget period.
     *
     * <p>Calculated from current date to endDate. Can be 0 or negative if period has ended.
     */
    private Integer daysRemaining;

    /**
     * Budget status indicator.
     *
     * <p>Possible values:
     *
     * <ul>
     *   <li><strong>ON_TRACK</strong>: Less than 75% spent (green indicator)
     *   <li><strong>WARNING</strong>: 75-100% spent (yellow indicator)
     *   <li><strong>EXCEEDED</strong>: Over 100% spent (red indicator)
     * </ul>
     *
     * <p>Requirement REQ-2.9.1.2: Visual indicators based on spending thresholds
     */
    private String status;
}
