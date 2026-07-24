package org.openfinance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for updating user settings.
 *
 * <p>All fields are optional - only non-null values will be updated.
 *
 * <p>Requirement REQ-6.3: User Settings &amp; Preferences
 *
 * <p>Requirement REQ-2.2: secondaryCurrency allows users to persist their secondary currency
 * preference
 *
 * @param theme Theme preference ("dark" or "light")
 * @param dateFormat Date format preference
 * @param numberFormat Number format preference
 * @param language Language/locale preference (ISO 639-1 code)
 * @param timezone Timezone preference (IANA identifier)
 * @param secondaryCurrency Optional ISO 4217 secondary currency code for tooltip comparison; pass
 *     an empty string or null to clear the preference
 * @param country ISO 3166-1 alpha-2 country code for tool localisation (e.g. "FR", "US"); controls
 *     country-specific defaults and France-only tool availability
 * @param decimalPlacesOverrideEnabled When true, applies preferredDecimalPlaces to every currency
 *     in the UI; when false, uses smart per-currency decimals. Null leaves unchanged.
 * @param preferredDecimalPlaces Decimal places (1-8) used when the override is enabled; null leaves
 *     unchanged
 */
public record UserSettingsUpdateRequest(
        @Pattern(regexp = "dark|light", message = "{settings.theme.invalid}") String theme,
        @Pattern(
                        regexp = "MM/DD/YYYY|DD/MM/YYYY|YYYY-MM-DD",
                        message = "{settings.date.format.invalid}")
                String dateFormat,
        @Pattern(
                        regexp = "1,234\\.56|1\\.234,56|1 234,56",
                        message = "{settings.number.format.invalid}")
                String numberFormat,
        @Pattern(regexp = "[a-z]{2}", message = "{settings.language.invalid}") String language,
        String timezone,
        @Pattern(regexp = "[A-Z]{3}|", message = "{settings.currency.invalid}")
                String secondaryCurrency,
        @Pattern(regexp = "[A-Z]{2}", message = "{settings.country.invalid}") String country,
        @Pattern(regexp = "base|native|both|", message = "{settings.amount.display.invalid}")
                String amountDisplayMode,
        Boolean decimalPlacesOverrideEnabled,
        @Min(value = 1, message = "{settings.decimal.places.invalid}")
                @Max(value = 8, message = "{settings.decimal.places.invalid}")
                Integer preferredDecimalPlaces) {}
