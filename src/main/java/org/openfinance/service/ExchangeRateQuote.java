package org.openfinance.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.openfinance.dto.ExchangeRateLeg;
import org.openfinance.dto.ExchangeRateResponse;

/** Keeps a calculated rate and the provenance of all its inputs together. */
public record ExchangeRateQuote(BigDecimal rate, List<ExchangeRateLeg> legs) {
    public ExchangeRateQuote {
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rates must be positive");
        }
        legs = List.copyOf(legs);
    }

    public ExchangeRateResponse response(String from, String to, LocalDate valuationDate) {
        return ExchangeRateResponse.builder()
                .baseCurrency(from)
                .targetCurrency(to)
                .rate(rate)
                .inverseRate(BigDecimal.ONE.divide(rate, MathContext.DECIMAL128))
                .rateDate(
                        legs.stream()
                                .map(ExchangeRateLeg::rateDate)
                                .min(LocalDate::compareTo)
                                .orElse(valuationDate))
                .source(
                        legs.isEmpty()
                                ? "identity"
                                : legs.stream()
                                        .map(ExchangeRateLeg::source)
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .collect(Collectors.joining("; ")))
                .valuationDate(valuationDate)
                .quoteLegs(legs)
                .build();
    }
}
