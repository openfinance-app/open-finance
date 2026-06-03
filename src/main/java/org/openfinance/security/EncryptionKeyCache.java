// filepath: src/main/java/org/openfinance/security/EncryptionKeyCache.java

package org.openfinance.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * In-memory cache of per-user encryption keys backed by Caffeine.
 *
 * <p>
 * Keys are populated at login and looked up via opaque session tokens.
 * The actual AES key never leaves the server after derivation — clients
 * receive and send only the session token.
 *
 * <p>
 * A secondary userId→key cache allows {@code @Scheduled} jobs to
 * encrypt/decrypt data for recently-active users without an HTTP context.
 *
 * <p>
 * Users who have not logged in within 24 hours will not have a cached key;
 * scheduled jobs must skip those users gracefully.
 */
@Component
public class EncryptionKeyCache {

    /** userId → SecretKey — for @Scheduled jobs. */
    private final Cache<Long, SecretKey> userKeyCache;

    /** sessionToken → SecretKey — for HTTP request lookup. */
    private final Cache<String, SecretKey> sessionTokenCache;

    /** sessionToken → userId — for reverse lookup (logout, eviction). */
    private final Cache<String, Long> sessionTokenUserCache;

    private final SecureRandom secureRandom = new SecureRandom();

    private static final int SESSION_TOKEN_BYTES = 32; // 256-bit token

    public EncryptionKeyCache() {
        this.userKeyCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build();
        this.sessionTokenCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterAccess(Duration.ofHours(24))
                .build();
        this.sessionTokenUserCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterAccess(Duration.ofHours(24))
                .build();
    }

    /**
     * Creates a new session and returns an opaque session token.
     * The actual encryption key is stored server-side only.
     *
     * @param userId the authenticated user's ID
     * @param key    the user's AES-256 encryption key
     * @return a URL-safe Base64 session token (44 chars)
     */
    public String createSession(Long userId, SecretKey key) {
        if (userId == null || key == null) {
            throw new IllegalArgumentException("userId and key must not be null");
        }
        byte[] tokenBytes = new byte[SESSION_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String sessionToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        sessionTokenCache.put(sessionToken, key);
        sessionTokenUserCache.put(sessionToken, userId);
        userKeyCache.put(userId, key);

        return sessionToken;
    }

    /**
     * Retrieves the encryption key for a session token.
     *
     * @param sessionToken the opaque session token from the client
     * @return the key if the session is valid, otherwise empty
     */
    public Optional<SecretKey> getKeyBySessionToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionTokenCache.getIfPresent(sessionToken));
    }

    /**
     * Retrieves the userId associated with a session token.
     *
     * @param sessionToken the opaque session token
     * @return the userId if the session is valid, otherwise empty
     */
    public Optional<Long> getUserIdBySessionToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionTokenUserCache.getIfPresent(sessionToken));
    }

    /**
     * Invalidates a specific session token (e.g. on logout).
     *
     * @param sessionToken the session token to invalidate
     */
    public void invalidateSession(String sessionToken) {
        if (sessionToken != null) {
            sessionTokenCache.invalidate(sessionToken);
            Long userId = sessionTokenUserCache.getIfPresent(sessionToken);
            sessionTokenUserCache.invalidate(sessionToken);
            // Note: we do NOT evict the userKeyCache entry here because the user
            // may have multiple active sessions (tabs). Scheduled jobs can still use it.
        }
    }

    /**
     * Stores or refreshes the encryption key for a user (legacy/compatibility).
     * Called by EncryptionKeyFilter for backward-compatible X-Encryption-Session
     * header support.
     *
     * @param userId the authenticated user's ID
     * @param key    the user's AES-256 encryption key
     */
    public void cacheKey(Long userId, SecretKey key) {
        if (userId != null && key != null) {
            userKeyCache.put(userId, key);
        }
    }

    /**
     * Retrieves the cached encryption key for a user (for @Scheduled jobs).
     *
     * @param userId the user's ID
     * @return the key if cached and not expired, otherwise empty
     */
    public Optional<SecretKey> getKey(Long userId) {
        return Optional.ofNullable(userKeyCache.getIfPresent(userId));
    }

    /**
     * Returns the set of user IDs that currently have a cached key.
     *
     * @return set of user IDs with active cached keys
     */
    public Set<Long> getCachedUserIds() {
        userKeyCache.cleanUp();
        return userKeyCache.asMap().keySet();
    }

    /**
     * Removes all cached keys for a user (e.g. on password change).
     *
     * @param userId the user's ID
     */
    public void evict(Long userId) {
        userKeyCache.invalidate(userId);
    }
}
