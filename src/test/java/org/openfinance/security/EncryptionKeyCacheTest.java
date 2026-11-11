package org.openfinance.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EncryptionKeyCache Unit Tests")
class EncryptionKeyCacheTest {

    @Test
    @DisplayName("Session activity slides the configured 31-day encryption expiry")
    void sessionActivityExtendsExpiry() {
        AtomicLong elapsed = new AtomicLong();
        EncryptionKeyCache cache =
                new EncryptionKeyCache(Duration.ofDays(31).toMillis(), elapsed::get);
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        String active = cache.createSession(1L, key);
        String idle = cache.createSession(1L, key);
        cache.cacheKey(1L, key);

        elapsed.set(Duration.ofDays(30).toNanos());
        assertThat(cache.getKeyBySessionToken(active)).contains(key);
        assertThat(cache.getUserIdBySessionToken(active)).contains(1L);
        assertThat(cache.getKey(1L)).contains(key);

        elapsed.set(Duration.ofDays(32).toNanos());
        // Scheduler reads do not extend the key's lifetime.
        assertThat(cache.getKey(1L)).isEmpty();
        assertThat(cache.getKeyBySessionToken(idle)).isEmpty();
        assertThat(cache.getUserIdBySessionToken(idle)).isEmpty();
        assertThat(cache.getKeyBySessionToken(active)).contains(key);
        assertThat(cache.getUserIdBySessionToken(active)).contains(1L);

        elapsed.set(Duration.ofDays(64).toNanos());
        assertThat(cache.getKeyBySessionToken(active)).isEmpty();
        assertThat(cache.getUserIdBySessionToken(active)).isEmpty();
    }

    @Test
    @DisplayName("Encryption expiry follows a customized JWT lifetime")
    void honorsConfiguredExpiry() {
        AtomicLong elapsed = new AtomicLong();
        EncryptionKeyCache cache =
                new EncryptionKeyCache(Duration.ofHours(1).toMillis(), elapsed::get);
        String session = cache.createSession(1L, new SecretKeySpec(new byte[32], "AES"));
        elapsed.set(Duration.ofHours(2).toNanos());
        assertThat(cache.getKeyBySessionToken(session)).isEmpty();
        assertThat(cache.getUserIdBySessionToken(session)).isEmpty();
    }

    @Test
    @DisplayName("Should create session token mappings without caching scheduler key")
    void shouldCreateSessionTokenMappingsWithoutCachingSchedulerKey() {
        EncryptionKeyCache encryptionKeyCache = new EncryptionKeyCache();
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        String sessionToken = encryptionKeyCache.createSession(1L, key);

        assertThat(encryptionKeyCache.getKeyBySessionToken(sessionToken)).contains(key);
        assertThat(encryptionKeyCache.getUserIdBySessionToken(sessionToken)).contains(1L);
        assertThat(encryptionKeyCache.getKey(1L)).isEmpty();
    }

    @Test
    @DisplayName("Should cache scheduler key only when explicitly cached")
    void shouldCacheSchedulerKeyOnlyWhenExplicitlyCached() {
        EncryptionKeyCache encryptionKeyCache = new EncryptionKeyCache();
        SecretKey key = new SecretKeySpec(new byte[32], "AES");

        encryptionKeyCache.cacheKey(1L, key);

        assertThat(encryptionKeyCache.getKey(1L)).contains(key);
    }

    @Test
    @DisplayName("Should invalidate failed session token mappings")
    void shouldInvalidateFailedSessionTokenMappings() {
        EncryptionKeyCache encryptionKeyCache = new EncryptionKeyCache();
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        String sessionToken = encryptionKeyCache.createSession(1L, key);

        encryptionKeyCache.invalidateFailedSession(sessionToken);

        assertThat(encryptionKeyCache.getKeyBySessionToken(sessionToken)).isEmpty();
        assertThat(encryptionKeyCache.getUserIdBySessionToken(sessionToken)).isEmpty();
        assertThat(encryptionKeyCache.getKey(1L)).isEmpty();
    }

    @Test
    @DisplayName("Should preserve existing scheduler key when failed new session is invalidated")
    void shouldPreserveExistingSchedulerKeyWhenFailedNewSessionIsInvalidated() {
        EncryptionKeyCache encryptionKeyCache = new EncryptionKeyCache();
        SecretKey existingKey = new SecretKeySpec(new byte[32], "AES");
        SecretKey failedKey =
                new SecretKeySpec(
                        new byte[] {
                            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
                        },
                        "AES");
        encryptionKeyCache.cacheKey(1L, existingKey);
        String failedToken = encryptionKeyCache.createSession(1L, failedKey);

        encryptionKeyCache.invalidateFailedSession(failedToken);

        assertThat(encryptionKeyCache.getKeyBySessionToken(failedToken)).isEmpty();
        assertThat(encryptionKeyCache.getUserIdBySessionToken(failedToken)).isEmpty();
        assertThat(encryptionKeyCache.getKey(1L)).contains(existingKey);
    }
}
