package org.openfinance.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom constraint annotation that enforces password complexity requirements.
 *
 * <p>Requirement TASK-15.1.9: Passwords must meet the following criteria:
 *
 * <ul>
 *   <li>Minimum 8 characters
 *   <li>At least one uppercase letter (A–Z)
 *   <li>At least one lowercase letter (a–z)
 *   <li>At least one digit (0–9)
 *   <li>At least one special character ({@code !@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?})
 * </ul>
 *
 * <p>Null values are considered valid (use {@code @NotNull} or {@code @NotBlank} separately to
 * enforce presence). Empty strings fail validation.
 *
 * <p>Example usage:
 *
 * <pre>
 * public class UserRegistrationRequest {
 *     {@code @NotBlank}
 *     {@code @ValidPassword}
 *     private String password;
 * }
 * </pre>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-03-20
 * @see PasswordValidator
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
@Documented
public @interface ValidPassword {

    /**
     * Validation message key.
     *
     * <p>Defaults to the {@code user.password.weak} key which is resolved from {@code
     * ValidationMessages.properties}.
     */
    String message() default "{user.password.weak}";

    /** Constraint group. */
    Class<?>[] groups() default {};

    /** Constraint payload. */
    Class<? extends Payload>[] payload() default {};
}
