package org.openfinance.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation annotation for ISO 4217 currency codes. Validates that a string is a valid 3-letter
 * currency code (e.g., USD, EUR, GBP).
 *
 * <p>Example usage:
 *
 * <pre>
 * public class TransactionRequest {
 *     {@code @ValidCurrency}
 *     private String currency;
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CurrencyValidator.class)
@Documented
public @interface ValidCurrency {

    String message() default "Invalid currency code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
