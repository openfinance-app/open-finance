package org.openfinance.service;

import org.openfinance.entity.User;

/**
 * Server-side invalidation of login sessions (including renewed JWTs) and all sessions of a user.
 */
public interface SessionRevocationService {
    boolean isRevoked(String token);

    void revoke(String token);

    /** Increment the version on the user that the caller persists in its password transaction. */
    void revokeAllSessions(User user);

    void purgeExpiredTokens();
}
