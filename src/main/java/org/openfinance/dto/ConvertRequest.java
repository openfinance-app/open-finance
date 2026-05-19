package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for currency conversion.
 *
 * <p>Example JSON:
 *
 * <pre>
 * {
 *   "amount": 100.00,
 *   "fromCurrency": "USD",
 *   "toCurrency": "EUR",
 *   "date": "2024-01-15"
 * }
 * </pre>
 *
 * @param amount the amount to convert (must be positive)
 * @param fromCurrency the source currency code (ISO 4217, e.g., "USD")
 * @param toCurrency the target currency code (ISO 4217, e.g., "EUR")
 * @param date the date for historical conversion (optional - defaults to current date)
 * @author Open-Finance Development Team
 * @since 1.0
 */
public record ConvertRequest(
        @NotNull(message = "{convert.amount.required}")
                @DecimalMin(value = "0.0", inclusive = false, message = "{convert.amount.min}")
                BigDecimal amount,
        @NotBlank(message = "{convert.from.required}")
                @Pattern(regexp = "^[A-Z]{3}$", message = "{convert.from.pattern}")
                String fromCurrency,
        @NotBlank(message = "{convert.to.required}")
                @Pattern(regexp = "^[A-Z]{3}$", message = "{convert.to.pattern}")
                String toCurrency,
        LocalDate date // Optional - defaults to current date if null
        ) {}
