package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.validation.OnCreate;
import org.openfinance.validation.ValidCurrency;

/**
 * Data Transfer Object for creating or updating a liability tranche.
 *
 * <p>A tranche represents a single planned drawdown of a liability disbursed in stages (e.g. a
 * construction loan or a mortgage released in stages).
 *
 * <p>On creation, {@code trancheNo} is auto-assigned (max existing + 1) when absent and {@code
 * currency} always defaults to the liability's currency (a provided value must match it). {@code
 * status} is only honored on update and only for PLANNED&#8596;CANCELLED transitions. On update a
 * {@code null} field means "leave unchanged" (empty string clears a String field).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiabilityTrancheRequest {

    /** Sequential number of the tranche within its liability; auto-assigned when absent. */
    @Min(value = 1, message = "{liabilityTranche.trancheNo.min}")
    private Integer trancheNo;

    /** Planned drawdown amount; required on create, optional on update (null = unchanged). */
    @NotNull(groups = OnCreate.class, message = "{liabilityTranche.plannedAmount.required}")
    @DecimalMin(value = "0", inclusive = false, message = "{liabilityTranche.plannedAmount.min}")
    @Digits(integer = 26, fraction = 18, message = "{liabilityTranche.plannedAmount.digits}")
    private BigDecimal plannedAmount;

    /** Planned drawdown date. */
    private LocalDate plannedDate;

    /** Fee associated with this tranche. */
    @DecimalMin(value = "0.00", message = "{liabilityTranche.fee.min}")
    @Digits(integer = 26, fraction = 18, message = "{liabilityTranche.fee.digits}")
    private BigDecimal fee;

    /** Whether this tranche is interest-only. */
    private Boolean interestOnly;

    /** End date of the interest-only period. */
    private LocalDate interestOnlyUntil;

    /** Optional ID of the real estate property this tranche is linked to. */
    private Long realEstateId;

    /** Optional notes about this tranche. */
    @Size(max = 1000, message = "{liabilityTranche.notes.max}")
    private String notes;

    /** Currency code in ISO 4217 format; defaults to the liability's currency when absent. */
    @ValidCurrency private String currency;

    /** Target status on update; only PLANNED&#8596;CANCELLED transitions are allowed. */
    private TrancheStatus status;
}
