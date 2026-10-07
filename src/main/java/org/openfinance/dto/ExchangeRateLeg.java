package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An actual stored/provider quote used in a direct, inverse, or cross-currency calculation. */
public record ExchangeRateLeg(
        String baseCurrency,
        String targetCurrency,
        BigDecimal rate,
        LocalDate rateDate,
        String source) {}
