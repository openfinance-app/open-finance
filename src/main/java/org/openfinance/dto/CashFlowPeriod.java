package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Income and expenses in the user's base currency, dated at the start of the period. */
@Data
@AllArgsConstructor
public class CashFlowPeriod {
    private LocalDate date;
    private BigDecimal income;
    private BigDecimal expense;
}
