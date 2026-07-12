package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login request DTO for user authentication.
 *
 * <p>Requirement REQ-2.1.3: User authentication with credentials
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /** Username for authentication. Required field that cannot be blank. */
    @NotBlank(message = "{login.username.required}")
    private String username;

    /** User password for authentication. Required field that cannot be blank. */
    @NotBlank(message = "{login.password.required}")
    private String password;

    /** Master password used to derive encryption key when application encryption is enabled. */
    private String masterPassword;

    /**
     * Custom toString that excludes sensitive password fields for security.
     *
     * @return String representation without passwords
     */
    @Override
    public String toString() {
        return "LoginRequest{"
                + "username='"
                + username
                + '\''
                + ", password='[PROTECTED]'"
                + ", masterPassword='[PROTECTED]'"
                + '}';
    }
}
