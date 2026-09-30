package org.openfinance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.openfinance.repository.CurrencyRepository;
import org.springframework.beans.factory.annotation.Autowired;

/** Accepts ISO currencies and active currencies in the application catalog. */
public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    private final CurrencyRepository currencyRepository;

    /** Standalone bean validation has no catalog and retains ISO currency validation. */
    public CurrencyValidator() {
        this(null);
    }

    @Autowired
    public CurrencyValidator(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    private static final Set<String> VALID_CURRENCY_CODES =
            Currency.getAvailableCurrencies().stream()
                    .map(Currency::getCurrencyCode)
                    .collect(Collectors.toSet());

    @Override
    public boolean isValid(String currencyCode, ConstraintValidatorContext context) {
        if (currencyCode == null) {
            return true; // Use @NotNull for null check
        }

        String code = currencyCode.toUpperCase(Locale.ROOT);
        return VALID_CURRENCY_CODES.contains(code)
                || (currencyRepository != null
                        && currencyRepository
                                .findByCode(code)
                                .filter(currency -> Boolean.TRUE.equals(currency.getIsActive()))
                                .isPresent());
    }
}
