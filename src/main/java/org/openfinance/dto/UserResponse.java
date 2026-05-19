package org.openfinance.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user response data.
 *
 * <p>Contains non-sensitive user information returned to clients. This DTO explicitly excludes
 * sensitive fields like password hashes and salts.
 *
 * <p><strong>Security:</strong> This DTO never includes:
 *
 * <ul>
 *   <li>passwordHash - login password hash
 *   <li>masterPasswordSalt - encryption key derivation salt
 * </ul>
 *
 * <p>Requirement REQ-2.1.1: User registration response
 *
 * @see org.openfinance.entity.User
 * @see org.openfinance.mapper.UserMapper#toResponse(org.openfinance.entity.User)
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /** User's unique identifier. */
    private Long id;

    /** User's username for display and authentication. */
    private String username;

    /** User's email address. */
    private String email;

    /**
     * User's preferred base currency for multi-currency conversion. ISO 4217 currency code (e.g.,
     * "USD", "EUR", "GBP"). Requirement 6.2.13: Base currency setting for user preferences
     */
    private String baseCurrency;

    /** Timestamp when the user account was created. */
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user account was last updated. May be null if user has never been updated.
     */
    private LocalDateTime updatedAt;

    /**
     * User's profile image as a Base64-encoded data URL (e.g., "data:image/png;base64,..."). Null
     * when no image has been uploaded.
     */
    private String profileImage;
}
