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
 * DTO for user profile update request.
 *
 * <p>Contains fields that can be updated in a user's profile. All fields are optional except
 * currentPassword which is required for security verification.
 *
 * <p><strong>Update Rules:</strong>
 *
 * <ul>
 *   <li><strong>email</strong>: Can be updated if not already in use by another user
 *   <li><strong>currentPassword</strong>: Required for verification before any changes
 *   <li><strong>newPassword</strong>: Optional, updates login password if provided
 * </ul>
 *
 * <p>Requirement REQ-2.1.5: User profile management
 *
 * @see org.openfinance.entity.User
 * @see org.openfinance.service.UserService#updateProfile(Long, UpdateProfileRequest)
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    /** New email address (optional). Must be a valid email format and unique across the system. */
    @Email(message = "{user.profile.email.valid}")
    private String email;

    /** Current password for verification. Required to authorize any profile changes. */
    @NotBlank(message = "{user.profile.current.password.required}")
    private String currentPassword;

    /**
     * New password for login authentication (optional). If provided, must be at least 8 characters
     * and meet complexity requirements (uppercase, lowercase, digit, special character —
     * Requirement TASK-15.1.9). Will be hashed with BCrypt before storage.
     */
    @Size(min = 8, message = "{user.profile.new.password.min}")
    @ValidPassword
    private String newPassword;

    /** Override toString to prevent logging sensitive data. Excludes password fields. */
    @Override
    public String toString() {
        return "UpdateProfileRequest{"
                + "email='"
                + email
                + '\''
                + ", hasNewPassword="
                + (newPassword != null && !newPassword.isEmpty())
                + '}';
    }
}
