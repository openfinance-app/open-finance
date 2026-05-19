package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

/**
 * DTO for exchange rate information.
 *
 * <p>Used in exchange rate query endpoints to provide rate details between currency pairs.
 *
 * <p>Example JSON:
 *
 * <pre>
 * {
 *   "baseCurrency": "USD",
 *   "targetCurrency": "EUR",
 *   "rate": 0.85,
 *   "inverseRate": 1.1764705882,
 *   "rateDate": "2024-01-15",
 *   "source": "Yahoo Finance"
 * }
 * </pre>
 *
 * @param baseCurrency the source currency code (e.g., "USD")
 * @param targetCurrency the destination currency code (e.g., "EUR")
 * @param rate the exchange rate (1 baseCurrency = rate * targetCurrency)
 * @param inverseRate the inverse exchange rate (1 targetCurrency = inverseRate * baseCurrency)
 * @param rateDate the date this rate is valid for
 * @param source the data source (e.g., "Yahoo Finance")
 * @author Open-Finance Development Team
 * @since 1.0
 */
@Builder
public record ExchangeRateResponse(
        String baseCurrency,
        String targetCurrency,
        BigDecimal rate,
        BigDecimal inverseRate,
        LocalDate rateDate,
        String source) {}
