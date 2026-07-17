// filepath: src/main/java/org/openfinance/security/EncryptionContext.java

package org.openfinance.security;

import javax.crypto.SecretKey;

/**
 * Thread-local holder for the per-request AES encryption key.
 *
 * <p>The key is set by {@link EncryptionKeyFilter} at the start of each HTTP request and cleared
 * after the response completes. JPA {@link org.openfinance.converter.EncryptedStringConverter} and
 * {@link org.openfinance.converter.EncryptedBigDecimalConverter} read the key from here so that
 * encryption/decryption happens transparently at the persistence layer.
 *
 * <p>For code running outside an HTTP request (e.g. {@code @Scheduled} jobs), use {@link
 * EncryptionKeyCache} to retrieve the key and set it manually via {@link #setKey(SecretKey)} before
 * accessing encrypted entities.
 */
public final class EncryptionContext {

    private static final ThreadLocal<SecretKey> KEY = new ThreadLocal<>();

    private EncryptionContext() {}

    public static void setKey(SecretKey key) {
        KEY.set(key);
    }

    public static SecretKey getKey() {
        return KEY.get();
    }

    public static void clear() {
        KEY.remove();
    }
}
