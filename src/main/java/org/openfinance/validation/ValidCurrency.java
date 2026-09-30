package org.openfinance.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation annotation for ISO 4217 codes and active application-catalog currencies, including
 * cryptocurrencies.
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
