package org.openfinance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for {@link ValidCurrency} annotation. Checks if the currency code is a valid ISO 4217
 * code.
 */
public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    private static final Set<String> VALID_CURRENCY_CODES =
            Currency.getAvailableCurrencies().stream()
                    .map(Currency::getCurrencyCode)
                    .collect(Collectors.toSet());

    @Override
    public boolean isValid(String currencyCode, ConstraintValidatorContext context) {
        if (currencyCode == null) {
            return true; // Use @NotNull for null check
        }

        return VALID_CURRENCY_CODES.contains(currencyCode.toUpperCase());
    }
}
