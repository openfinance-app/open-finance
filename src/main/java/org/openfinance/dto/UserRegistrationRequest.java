package org.openfinance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.validation.ValidPassword;

/**
 * DTO for user registration request.
 *
 * <p>Contains credentials and personal information needed to create a new user account. All fields
 * are validated using Jakarta Bean Validation annotations.
 *
 * <p><strong>Password Security:</strong>
 *
 * <ul>
 *   <li><strong>password</strong>: User's login password (hashed with BCrypt before storage)
 *   <li><strong>masterPassword</strong>: Derives encryption key with PBKDF2 (never stored)
 * </ul>
 *
 * <p>Requirement REQ-2.1.1: User registration with dual password system
 *
 * @see org.openfinance.entity.User
 * @see org.openfinance.service.UserService#registerUser(UserRegistrationRequest)
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationRequest {

    /** Unique username for login authentication. Must be 3-50 characters long. */
    @NotBlank(message = "{user.username.required}")
    @Size(min = 3, max = 50, message = "{user.username.between}")
    private String username;

    /** User's email address. Must be a valid email format and unique across the system. */
    @NotBlank(message = "{user.email.required}")
    @Email(message = "{user.profile.email.valid}")
    private String email;

    /**
     * Plain-text password for login authentication. Minimum 8 characters required for security.
     * Must contain at least one uppercase letter, one lowercase letter, one digit, and one special
     * character (Requirement TASK-15.1.9). Will be hashed with BCrypt before storage.
     */
    @NotBlank(message = "{user.password.required}")
    @Size(min = 8, message = "{password.new.min}")
    @ValidPassword
    private String password;

    /**
     * Optional flag to skip seeding default categories and payees. Primarily used in integration
     * tests to maintain full control over test data. Defaults to false.
     */
    @Builder.Default private boolean skipSeeding = false;

    /**
     * Master password for deriving encryption keys. Minimum 8 characters required for security.
     * Used with PBKDF2 to derive AES-256 key, never stored in database.
     */
    @NotBlank(message = "{user.master.password.required}")
    @Size(min = 8, message = "{user.master.password.min}")
    private String masterPassword;

    /**
     * Override toString to prevent logging sensitive data. Excludes password and masterPassword
     * fields.
     */
    @Override
    public String toString() {
        return "UserRegistrationRequest{"
                + "username='"
                + username
                + '\''
                + ", email='"
                + email
                + '\''
                + '}';
    }
}
