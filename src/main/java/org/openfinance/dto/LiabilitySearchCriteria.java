package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Amount filters for outstanding liability balances. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiabilitySearchCriteria {
    private BigDecimal balanceMin;
    private BigDecimal balanceMax;
    private BigDecimal balanceExact;
    @Builder.Default private AmountCurrencyMode amountCurrencyMode = AmountCurrencyMode.BASE;

    public boolean hasAmountFilter() {
        return balanceMin != null || balanceMax != null || balanceExact != null;
    }
}
