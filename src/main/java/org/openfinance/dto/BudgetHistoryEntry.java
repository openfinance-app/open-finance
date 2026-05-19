package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single sub-period entry in a budget's history.
 *
 * <p>For example, a yearly budget spanning Jan–Dec 2024 with MONTHLY period breakdown would produce
 * 12 entries — one per calendar month.
 *
 * <p>Requirement REQ-2.9.1.4: Budget history per sub-period
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetHistoryEntry {

    /** Human-readable label for this sub-period (e.g., "Jan 2024", "Week 3"). */
    private String label;

    /** Start date of this sub-period (inclusive). */
    private LocalDate periodStart;

    /** End date of this sub-period (inclusive). */
    private LocalDate periodEnd;

    /**
     * Total budgeted amount for this sub-period.
     *
     * <p>Equal to the parent budget's amount. Every sub-period gets the same budget ceiling.
     */
    private BigDecimal budgeted;

    /**
     * Amount actually spent in this sub-period.
     *
     * <p>Sum of EXPENSE transactions in the budget's category within [periodStart, periodEnd].
     */
    private BigDecimal spent;

    /** Remaining amount (budgeted − spent). Can be negative if over budget. */
    private BigDecimal remaining;

    /** Percentage of budget spent in this sub-period. Can exceed 100. */
    private BigDecimal percentageSpent;

    /**
     * Status indicator for this sub-period.
     *
     * <p>Possible values: {@code ON_TRACK}, {@code WARNING}, {@code EXCEEDED}.
     */
    private String status;
}
