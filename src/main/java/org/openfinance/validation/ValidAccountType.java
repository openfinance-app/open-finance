package org.openfinance.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation annotation for account types. Validates that a string is a valid account type.
 *
 * <p>Example usage:
 *
 * <pre>
 * public class AccountRequest {
 *     {@code @ValidAccountType}
 *     private String type;
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AccountTypeValidator.class)
@Documented
public @interface ValidAccountType {

    String message() default "Invalid account type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
