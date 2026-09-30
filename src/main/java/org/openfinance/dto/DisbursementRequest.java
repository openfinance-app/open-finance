package org.openfinance.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for disbursing a liability tranche.
 *
 * <p>Exactly one routing target must be set: {@code toAccountId} (the bank paid into the user's
 * account) or {@code directRealEstateId} (the bank paid the seller/property directly). {@code
 * trancheId} is optional — when absent, the next PLANNED tranche by {@code trancheNo} is drawn, or
 * a single T1 tranche is created on the fly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementRequest {

    /** ID of the account receiving the disbursed funds (bank paid my account route). */
    private Long toAccountId;

    /** ID of the real estate property for a direct disbursement (bank paid seller route). */
    private Long directRealEstateId;

    /** Optional ID of the planned tranche being drawn; auto-picked when absent. */
    private Long trancheId;

    /** Disbursed amount. */
    @NotNull(message = "{disbursement.amount.required}")
    @DecimalMin(value = "0", inclusive = false, message = "{disbursement.amount.min}")
    @Digits(integer = 26, fraction = 18, message = "{disbursement.amount.digits}")
    private BigDecimal amount;

    /** Date of the disbursement. */
    @NotNull(message = "{disbursement.date.required}")
    private LocalDate date;

    /** Optional notes about the disbursement. */
    @Size(max = 1000, message = "{disbursement.notes.max}")
    private String notes;

    /**
     * Validates that exactly one routing target is set.
     *
     * <p>Called by Jakarta Bean Validation during full-request validation.
     *
     * @return true when exactly one of toAccountId and directRealEstateId is set
     */
    @AssertTrue(message = "{disbursement.route.exclusive}")
    private boolean isValidRoute() {
        return (toAccountId != null) ^ (directRealEstateId != null);
    }
}
