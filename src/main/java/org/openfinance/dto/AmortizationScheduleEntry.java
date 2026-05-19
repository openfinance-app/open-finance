package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a single entry in an amortization schedule.
 *
 * <p>An amortization schedule shows the breakdown of each payment over the life of a loan,
 * including how much goes toward principal vs. interest.
 *
 * <p>Requirement REQ-6.1.3: Display amortization schedules for liabilities
 *
 * @see org.openfinance.service.LiabilityService#calculateAmortizationSchedule(Long, Long)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmortizationScheduleEntry {

    /**
     * Sequential payment number (1, 2, 3, ...).
     *
     * <p>Represents which payment this is in the schedule. For example, payment 1 is the first
     * monthly payment.
     */
    private Integer paymentNumber;

    /**
     * Date when this payment is due.
     *
     * <p>Typically calculated as start_date + (paymentNumber months).
     */
    private LocalDate paymentDate;

    /**
     * Total payment amount for this period.
     *
     * <p>For fixed-rate loans, this is typically constant (principal + interest). Equal to
     * principalPortion + interestPortion.
     */
    private BigDecimal paymentAmount;

    /**
     * Portion of the payment applied to the principal balance.
     *
     * <p>This reduces the outstanding balance directly. Typically increases over time as interest
     * portion decreases.
     */
    private BigDecimal principalPortion;

    /**
     * Portion of the payment applied to interest charges.
     *
     * <p>This is the cost of borrowing. Typically decreases over time as principal is paid down.
     */
    private BigDecimal interestPortion;

    /**
     * Remaining principal balance after this payment is applied.
     *
     * <p>This is the outstanding loan balance after the principal portion is deducted. Should
     * decrease to zero by the final payment.
     */
    private BigDecimal remainingBalance;

    /**
     * Cumulative principal paid up to and including this payment.
     *
     * <p>Optional field. Represents total principal paid so far.
     */
    private BigDecimal cumulativePrincipal;

    /**
     * Cumulative interest paid up to and including this payment.
     *
     * <p>Optional field. Represents total interest paid so far.
     */
    private BigDecimal cumulativeInterest;
}
