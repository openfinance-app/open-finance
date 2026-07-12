package org.openfinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response DTO containing authentication token and user information.
 *
 * <p>Requirement REQ-2.1.3: Return JWT token after successful authentication
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT authentication token with 24-hour expiration. Client should include this in Authorization
     * header for subsequent requests.
     */
    private String token;

    /** Unique identifier of the authenticated user. */
    private Long userId;

    /** Username of the authenticated user. */
    private String username;

    /** Opaque encryption session token. Present only when application encryption is enabled. */
    private String encryptionKey;

    /** Whether application-layer encryption is enabled for this login session. */
    private boolean encryptionEnabled;

    /** ISO 4217 base currency code for the authenticated user (e.g., "USD", "EUR"). */
    private String baseCurrency;

    /**
     * Whether the user has completed the initial onboarding preferences wizard. When {@code false},
     * the frontend should redirect to /onboarding before allowing access to the main application.
     */
    private boolean onboardingComplete;
}
