package org.openfinance.security;

import static org.assertj.core.api.Assertions.assertThat;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EncryptionKeyCache Unit Tests")
class EncryptionKeyCacheTest {

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
        SecretKey failedKey = new SecretKeySpec(new byte[] {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
        }, "AES");
        encryptionKeyCache.cacheKey(1L, existingKey);
        String failedToken = encryptionKeyCache.createSession(1L, failedKey);

        encryptionKeyCache.invalidateFailedSession(failedToken);

        assertThat(encryptionKeyCache.getKeyBySessionToken(failedToken)).isEmpty();
        assertThat(encryptionKeyCache.getUserIdBySessionToken(failedToken)).isEmpty();
        assertThat(encryptionKeyCache.getKey(1L)).contains(existingKey);
    }
}
