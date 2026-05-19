package org.openfinance.dto;

import lombok.Builder;

/**
 * DTO for currency information.
 *
 * <p>Used in currency listing endpoints to provide supported currency details.
 *
 * <p>Example JSON:
 *
 * <pre>
 * {
 *   "code": "USD",
 *   "name": "US Dollar",
 *   "symbol": "$",
 *   "isActive": true,
 *   "nameKey": "currency.usd"
 * }
 * </pre>
 *
 * @param code the ISO 4217 currency code (e.g., "USD", "EUR")
 * @param name the full currency name (e.g., "US Dollar"), localized if nameKey is present
 * @param symbol the currency symbol (e.g., "$", "€")
 * @param isActive whether the currency is currently active for use
 * @param nameKey the i18n message key for localized name resolution (optional)
 * @author Open-Finance Development Team
 * @since 1.0
 */
@Builder
public record CurrencyResponse(
        String code, String name, String symbol, boolean isActive, String nameKey) {}
