package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for a loan repayment preview.
 *
 * <p>Breaks down the next repayment into principal, interest and insurance components.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentPreviewResponse {

    public enum Status {
        READY,
        BELOW_ESTIMATED_CHARGES,
        INTEREST_ONLY_DIFFERENCE,
        EXCEEDS_BALANCE
    }

    /** Total repayment amount (principal + interest + insurance). */
    private BigDecimal total;

    /** Principal component of the repayment. */
    private BigDecimal principal;

    /** Interest component of the repayment. */
    private BigDecimal interest;

    /** Insurance component of the repayment. */
    private BigDecimal insurance;

    /** Whether the previewed repayment is interest-only. */
    private Boolean interestOnly;

    /** Whether the estimate can be applied, or why actual components must be entered instead. */
    private Status status;
}
