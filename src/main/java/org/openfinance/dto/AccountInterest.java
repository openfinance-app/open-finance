package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO representing interest data for a single account. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInterest {

    /** The ID of the account. */
    private Long accountId;

    /** The name of the account (decrypted for display). */
    private String accountName;

    /** The actual net interest earned over the selected period. */
    private BigDecimal interestEarned;

    /** The projected 1-year net interest based on current balance and rate. */
    private BigDecimal projectedInterest;
}
