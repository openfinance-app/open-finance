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
     * <p>New allowance only; a shortened final period is prorated by calendar days.
     */
    private BigDecimal budgeted;

    private BigDecimal carryover;
    private BigDecimal available;
    private boolean future;

    /**
     * Amount actually spent in this sub-period.
     *
     * <p>Net expenses less income in the budget's category within [periodStart, periodEnd].
     */
    private BigDecimal spent;

    /** Remaining amount (budgeted + carryover − spent). Can be negative if over budget. */
    private BigDecimal remaining;

    /** Percentage of available funds spent; null when a nonpositive allowance is depleted. */
    private BigDecimal percentageSpent;

    /**
     * Status indicator for this sub-period.
     *
     * <p>Possible values: {@code ON_TRACK}, {@code WARNING}, {@code EXCEEDED}.
     */
    private String status;
}
