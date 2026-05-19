package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

/**
 * Response DTO for currency conversion.
 *
 * <p>Example JSON:
 *
 * <pre>
 * {
 *   "originalAmount": 100.00,
 *   "fromCurrency": "USD",
 *   "convertedAmount": 85.00,
 *   "toCurrency": "EUR",
 *   "exchangeRate": 0.85,
 *   "date": "2024-01-15"
 * }
 * </pre>
 *
 * @param originalAmount the input amount
 * @param fromCurrency the source currency code
 * @param convertedAmount the output amount after conversion
 * @param toCurrency the target currency code
 * @param exchangeRate the exchange rate used (1 fromCurrency = exchangeRate * toCurrency)
 * @param date the date of the exchange rate
 * @author Open-Finance Development Team
 * @since 1.0
 */
@Builder
public record ConvertResponse(
        BigDecimal originalAmount,
        String fromCurrency,
        BigDecimal convertedAmount,
        String toCurrency,
        BigDecimal exchangeRate,
        LocalDate date) {}
