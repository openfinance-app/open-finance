package org.openfinance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/**
 * Validator for {@link ValidAccountType} annotation. Checks if the account type is one of the
 * allowed values.
 */
public class AccountTypeValidator implements ConstraintValidator<ValidAccountType, String> {

    private static final Set<String> VALID_ACCOUNT_TYPES =
            Set.of("CHECKING", "SAVINGS", "CREDIT_CARD", "INVESTMENT", "CASH", "OTHER");

    @Override
    public boolean isValid(String accountType, ConstraintValidatorContext context) {
        if (accountType == null) {
            return true; // Use @NotNull for null check
        }

        return VALID_ACCOUNT_TYPES.contains(accountType.toUpperCase());
    }
}
