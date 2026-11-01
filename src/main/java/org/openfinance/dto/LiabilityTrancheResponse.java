package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.TrancheStatus;

/**
 * Data Transfer Object for liability tranche responses.
 *
 * <p>The {@code remaining} field is computed (outstanding drawn principal), not persisted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiabilityTrancheResponse {

    /** Unique identifier of the tranche. */
    private Long id;

    /** ID of the liability this tranche belongs to. */
    private Long liabilityId;

    /** Sequential number of the tranche within its liability. */
    private Integer trancheNo;

    /** Planned drawdown amount. */
    private BigDecimal plannedAmount;

    /** Amount already drawn. */
    private BigDecimal drawnAmount;

    /**
     * Outstanding drawn principal of this tranche (drawn amount minus principal allocated by
     * REPAYMENT transactions, capped at the drawn amount). Computed from the allocation ledger by
     * {@code LiabilityTrancheService#remainingOf}; not persisted.
     */
    private BigDecimal remaining;

    /** Planned drawdown date. */
    private LocalDate plannedDate;

    /** Actual drawdown date. */
    private LocalDate drawnDate;

    private boolean directDisbursement;
    private LocalDate reversedDate;

    /** Fee associated with this tranche. */
    private BigDecimal fee;

    /** Whether this tranche is interest-only. */
    private Boolean interestOnly;

    /** End date of the interest-only period. */
    private LocalDate interestOnlyUntil;

    /** Lifecycle status of the tranche. */
    private TrancheStatus status;

    /** ID of the linked real estate property. */
    private Long realEstateId;

    private Long assetId;

    /** Optional notes about this tranche. */
    private String notes;

    /** Currency code in ISO 4217 format. */
    private String currency;
}
