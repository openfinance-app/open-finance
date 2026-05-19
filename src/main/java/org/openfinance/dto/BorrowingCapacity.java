package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the user's borrowing capacity and debt metrics.
 *
 * <p>Calculates available borrowing capacity based on debt-to-income ratio, providing insights into
 * financial health and responsible borrowing limits.
 *
 * <p>Task: Dashboard Borrowing Capacity Card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowingCapacity {

    /** Average monthly income from INCOME transactions over the analysis period */
    private BigDecimal monthlyIncome;

    /** Average monthly expenses from EXPENSE transactions over the analysis period */
    private BigDecimal monthlyExpenses;

    /** Total monthly debt payments (sum of minimum payments on all liabilities) */
    private BigDecimal monthlyDebtPayments;

    /**
     * Debt-to-income ratio as a percentage (0-100+) Formula: (monthlyDebtPayments / monthlyIncome)
     * * 100 Industry standard: ≤ 40% is healthy
     */
    private BigDecimal debtToIncomeRatio;

    /**
     * Recommended maximum additional monthly debt payment user can take on Based on maintaining 40%
     * DTI ratio
     */
    private BigDecimal recommendedMaxBorrowing;

    /**
     * Estimated available borrowing capacity (annual) Simplified calculation:
     * recommendedMaxBorrowing * 12 * loan_term_multiplier
     */
    private BigDecimal availableBorrowingCapacity;

    /**
     * Financial health status based on debt-to-income ratio EXCELLENT: DTI ≤ 20% GOOD: DTI 21-35%
     * FAIR: DTI 36-50% POOR: DTI > 50%
     */
    private String financialHealthStatus;

    /** User's base currency for all monetary values */
    private String currency;

    /** Analysis period in days (e.g., 90 for quarterly income/expense average) */
    private Integer analysisPeriod;
}
