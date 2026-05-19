package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for the initial onboarding preferences wizard.
 *
 * <p>Submitted once after first login to capture the user's Country, base currency, secondary
 * currency, language, date format, number format, and currency display style. On success the
 * backend marks the user as {@code onboardingComplete = true} so subsequent logins skip the wizard.
 *
 * <p>Requirement: Post-registration onboarding flow
 *
 * @param country ISO 3166-1 alpha-2 country code (e.g. "FR", "US")
 * @param baseCurrency ISO 4217 base currency code (e.g. "EUR", "USD")
 * @param secondaryCurrency Optional ISO 4217 secondary currency code; pass empty string or null to
 *     leave unset
 * @param language ISO 639-1 language code (e.g. "en", "fr")
 * @param dateFormat One of "MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD"
 * @param numberFormat One of "1,234.56", "1.234,56", "1 234,56"
 * @param amountDisplayMode One of "base", "native", "both"
 */
public record OnboardingRequest(
        @NotBlank(message = "{onboarding.country.required}")
                @Pattern(regexp = "[A-Z]{2}", message = "{settings.country.invalid}")
                String country,
        @NotBlank(message = "{onboarding.base.currency.required}")
                @Pattern(regexp = "[A-Z]{3}", message = "{settings.currency.invalid}")
                String baseCurrency,
        @Pattern(regexp = "[A-Z]{3}|", message = "{settings.currency.invalid}")
                String secondaryCurrency,
        @NotBlank(message = "{onboarding.language.required}")
                @Pattern(regexp = "[a-z]{2}", message = "{settings.language.invalid}")
                String language,
        @NotBlank(message = "{onboarding.date.format.required}")
                @Pattern(
                        regexp = "MM/DD/YYYY|DD/MM/YYYY|YYYY-MM-DD",
                        message = "{settings.date.format.invalid}")
                String dateFormat,
        @NotBlank(message = "{onboarding.number.format.required}")
                @Pattern(
                        regexp = "1,234\\.56|1\\.234,56|1 234,56",
                        message = "{settings.number.format.invalid}")
                String numberFormat,
        @NotBlank(message = "{onboarding.amount.display.required}")
                @Pattern(regexp = "base|native|both", message = "{settings.amount.display.invalid}")
                String amountDisplayMode) {}
