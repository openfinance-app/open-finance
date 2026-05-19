package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.openfinance.validation.ValidPassword;

/**
 * Request DTO for updating user's login password.
 *
 * <p>Contains current password for verification and new password for update.
 *
 * <p>Requirement REQ-6.3.16: Password change functionality
 *
 * <p>Requirement TASK-15.1.9: Password complexity enforcement
 */
public record PasswordUpdateRequest(

        /** Current login password for verification. */
        @NotBlank(message = "{password.current.required}") String currentPassword,

        /**
         * New login password to set. Must be at least 8 characters long and meet complexity
         * requirements (uppercase, lowercase, digit, special character).
         */
        @NotBlank(message = "{password.new.required}")
                @Size(min = 8, message = "{password.new.min}")
                @ValidPassword
                String newPassword) {}
