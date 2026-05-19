package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for the detailed liability cost breakdown.
 *
 * <p>Provides a comprehensive financial snapshot of a liability, including:
 *
 * <ul>
 *   <li>How much has already been paid (principal, interest, insurance, fees)
 *   <li>How much is projected to still be paid (projected interest, insurance, fees)
 *   <li>Summary of linked transactions (loan payments recorded as transactions)
 * </ul>
 *
 * <p>Requirement REQ-LIA-3: Display liability breakdown with cost analysis
 *
 * <p>Requirement REQ-LIA-3.2: Display insurance cost
 *
 * <p>Requirement REQ-LIA-3.3: Display total cost of liability
 *
 * <p>Requirement REQ-LIA-3.4: Breakdown of principal paid, interest paid, fees paid
 *
 * @see org.openfinance.service.LiabilityService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiabilityBreakdownResponse {

    /** ID of the liability this breakdown belongs to. */
    private Long liabilityId;

    /** Decrypted name of the liability. */
    private String name;

    /** Currency code (ISO 4217) of the liability. */
    private String currency;

    // ===========================
    // Current Balance / Balances
    // ===========================

    /** Original principal amount borrowed. */
    private BigDecimal principal;

    /** Current outstanding balance owed. */
    private BigDecimal currentBalance;

    /**
     * Amount of principal already repaid (principal - currentBalance).
     *
     * <p><strong>Calculated field</strong>
     *
     * <p>Requirement REQ-LIA-3.4: Breakdown of principal paid
     */
    private BigDecimal principalPaid;

    // ===========================
    // Already Paid Breakdown
    // ===========================

    /**
     * Estimated interest already paid over the life of the loan so far.
     *
     * <p><strong>Calculated field</strong> — estimated from amortization analysis based on original
     * principal, current balance, interest rate, and payment history.
     *
     * <p>Requirement REQ-LIA-3.4: Breakdown of interest paid
     */
    private BigDecimal interestPaid;

    /**
     * Estimated insurance already paid (monthly insurance cost × months elapsed since start).
     *
     * <p><strong>Calculated field</strong>
     *
     * <p>Requirement REQ-LIA-3.4: Breakdown of insurance paid
     */
    private BigDecimal insurancePaid;

    /**
     * Additional fees associated with this liability.
     *
     * <p>Requirement REQ-LIA-3.4: Breakdown of fees paid
     */
    private BigDecimal feesPaid;

    /**
     * Total amount paid so far: principalPaid + interestPaid + insurancePaid + feesPaid.
     *
     * <p><strong>Calculated field</strong>
     */
    private BigDecimal totalPaid;

    // ===========================
    // Projected (Remaining) Costs
    // ===========================

    /**
     * Projected total interest still to be paid over the remaining loan term.
     *
     * <p><strong>Calculated field</strong> — from amortization schedule.
     *
     * <p>Null if insufficient data for calculation.
     */
    private BigDecimal projectedInterest;

    /**
     * Projected total insurance cost over the remaining loan term.
     *
     * <p><strong>Calculated field</strong> — monthlyInsuranceCost × monthsRemaining.
     *
     * <p>Null if insurancePercentage or endDate not set.
     */
    private BigDecimal projectedInsurance;

    /** Projected additional fees (currently same as additionalFees stored on liability). */
    private BigDecimal projectedFees;

    /**
     * Total projected cost of the liability from today until payoff: currentBalance +
     * projectedInterest + projectedInsurance + projectedFees.
     *
     * <p><strong>Calculated field</strong>
     *
     * <p>Requirement REQ-LIA-3.3: Display total cost of liability
     */
    private BigDecimal totalProjectedCost;

    // ===========================
    // Linked Transactions Summary
    // ===========================

    /**
     * Number of transactions linked to this liability (loan payments recorded as transactions).
     *
     * <p>Requirement REQ-LIA-4: Transaction-liability linking
     */
    private Integer linkedTransactionCount;

    /**
     * Total amount of all linked transactions (sum of payment amounts).
     *
     * <p>Requirement REQ-LIA-4: Transaction-liability linking summary
     */
    private BigDecimal linkedTransactionsTotalAmount;
}
