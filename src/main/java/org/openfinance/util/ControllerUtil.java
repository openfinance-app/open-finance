package org.openfinance.util;

import org.openfinance.entity.User;
import org.springframework.security.core.Authentication;

/**
 * Utility class for common controller operations.
 *
 * <p>Provides helper methods used across multiple REST controllers to reduce code duplication and
 * maintain consistency.
 *
 * <p>Requirement REQ-3.2: Authorization - Extract user identity from authentication
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
public final class ControllerUtil {

    /** Private constructor to prevent instantiation of utility class. */
    private ControllerUtil() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Extracts user ID from Spring Security authentication principal.
     *
     * <p>Assumes the authentication principal is a {@link User} entity, which is the case for
     * JWT-authenticated requests in this application.
     *
     * <p><strong>Usage Example:</strong>
     *
     * <pre>{@code
     * @GetMapping("/resource")
     * public ResponseEntity<Resource> getResource(Authentication authentication) {
     *     Long userId = ControllerUtil.extractUserId(authentication);
     *     // Use userId for authorization checks
     * }
     * }</pre>
     *
     * @param authentication Spring Security authentication object (auto-injected)
     * @return user ID extracted from authentication principal
     * @throws ClassCastException if principal is not a User instance
     */
    public static Long extractUserId(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return user.getId();
    }
}
