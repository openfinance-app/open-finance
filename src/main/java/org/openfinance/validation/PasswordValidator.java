package org.openfinance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Jakarta Bean Validation implementation for the {@link ValidPassword} constraint.
 *
 * <p>Requirement TASK-15.1.9: Enforces password complexity to protect user accounts against
 * brute-force and dictionary attacks. The password must satisfy all of the following rules:
 *
 * <ol>
 *   <li>Minimum length: 8 characters
 *   <li>At least one uppercase letter (A–Z)
 *   <li>At least one lowercase letter (a–z)
 *   <li>At least one digit (0–9)
 *   <li>At least one special character from the set: {@code !@#$%^&*()_+-=[]{}|;':",.<>?/\}
 * </ol>
 *
 * <p>Null values pass validation — pair with {@code @NotBlank} when presence is required.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-03-20
 * @see ValidPassword
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    /** Minimum password length (Requirement TASK-15.1.9). */
    private static final int MIN_LENGTH = 8;

    /** Regex that requires at least one uppercase letter. */
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");

    /** Regex that requires at least one lowercase letter. */
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");

    /** Regex that requires at least one digit. */
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");

    /**
     * Regex that requires at least one special character. Character class covers common keyboard
     * special characters.
     */
    private static final Pattern SPECIAL_CHAR_PATTERN =
            Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?\\\\].*");

    /**
     * {@inheritDoc}
     *
     * <p>Validates the password against all complexity rules. Null values are accepted; empty
     * strings fail.
     *
     * @param password the candidate password
     * @param context the constraint validator context (unused)
     * @return {@code true} if the password is null or meets all complexity requirements
     */
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            // Null check is delegated to @NotNull / @NotBlank
            return true;
        }
        return password.length() >= MIN_LENGTH
                && UPPERCASE_PATTERN.matcher(password).matches()
                && LOWERCASE_PATTERN.matcher(password).matches()
                && DIGIT_PATTERN.matcher(password).matches()
                && SPECIAL_CHAR_PATTERN.matcher(password).matches();
    }
}
