package org.openfinance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.openfinance.entity.AccountType;

/**
 * Validator for {@link ValidAccountType} annotation. Checks if the account type is one of the
 * allowed values.
 */
public class AccountTypeValidator implements ConstraintValidator<ValidAccountType, String> {

    private static final Set<String> VALID_ACCOUNT_TYPES =
            Arrays.stream(AccountType.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String accountType, ConstraintValidatorContext context) {
        if (accountType == null) {
            return true; // Use @NotNull for null check
        }

        return VALID_ACCOUNT_TYPES.contains(accountType.toUpperCase(Locale.ROOT));
    }
}
