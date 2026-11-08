package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A market quote or official fixed parity used in a currency calculation. */
public record ExchangeRateLeg(
        String baseCurrency,
        String targetCurrency,
        BigDecimal rate,
        LocalDate rateDate,
        String source,
        String sourceUrl,
        LocalDate validFrom,
        LocalDate validUntil) {
    public ExchangeRateLeg(
            String baseCurrency,
            String targetCurrency,
            BigDecimal rate,
            LocalDate rateDate,
            String source) {
        this(baseCurrency, targetCurrency, rate, rateDate, source, null, null, null);
    }
}
