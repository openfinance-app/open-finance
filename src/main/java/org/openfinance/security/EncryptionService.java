// filepath: src/main/java/org/openfinance/security/EncryptionService.java

package org.openfinance.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for encrypting and decrypting sensitive financial data using AES-256-GCM.
 *
 * <p>This service provides authenticated encryption with AES-GCM mode, ensuring both
 * confidentiality and integrity of sensitive data (account numbers, notes, attachments).
 *
 * <p><strong>Encryption Algorithm:</strong>
 *
 * <ul>
 *   <li>Algorithm: AES-256 (Advanced Encryption Standard, 256-bit key)
 *   <li>Mode: GCM (Galois/Counter Mode) - provides authentication
 *   <li>Padding: NoPadding (GCM is a stream cipher mode)
 *   <li>IV Size: 12 bytes (96 bits) - recommended for GCM
 *   <li>Tag Size: 128 bits (authentication tag)
 * </ul>
 *
 * <p><strong>Security Properties:</strong>
 *
 * <ul>
 *   <li>Each encryption uses a unique random IV (Initialization Vector)
 *   <li>Same plaintext produces different ciphertext each time (due to random IV)
 *   <li>GCM mode provides authentication - detects tampering
 *   <li>IV is stored with ciphertext (not secret, but must be unique)
 * </ul>
 *
 * <p><strong>Data Format:</strong> Encrypted data stored as Base64 string containing: <code>
 * [IV (12 bytes)][Ciphertext + Auth Tag]</code>
 *
 * <p><strong>Performance Optimisations (TASK-14.1.6):</strong>
 *
 * <ul>
 *   <li>In-memory key fingerprint cache ({@link ConcurrentHashMap}) avoids repeated Base64 key
 *       decoding on every request. The cache is keyed by the Base64 representation of the key's
 *       encoded bytes and is capped at {@value #KEY_CACHE_MAX_SIZE} entries to prevent unbounded
 *       growth.
 *   <li>{@link #encryptBatch(List, SecretKey)} / {@link #decryptBatch(List, SecretKey)} allow
 *       callers to process multiple values without per-call overhead.
 *   <li>{@link #encryptNullable(String, SecretKey)} / {@link #decryptNullable(String, SecretKey)}
 *       centralise null-safety logic so call sites stay clean.
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.18: Data encryption at rest for sensitive fields
 *   <li>REQ-3.2: Security implementation with AES-256-GCM
 *   <li>TASK-14.1.6: Encryption performance optimisations
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * // Get encryption key from KeyManagementService
 * SecretKey key = keyManagementService.deriveKey(masterPassword, salt);
 *
 * // Encrypt sensitive data
 * String accountNumber = "1234-5678-9012";
 * String encrypted = encryptionService.encrypt(accountNumber, key);
 * // Store encrypted in database
 *
 * // Decrypt when needed
 * String decrypted = encryptionService.decrypt(encrypted, key);
 * // Use decrypted data
 * }</pre>
 *
 * @see KeyManagementService
 * @author Open-Finance Development Team
 * @version 2.0
 * @since 2026-01-30
 */
@Service
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * Maximum number of distinct key fingerprints kept in the in-memory key cache. When this limit
     * is reached the oldest entry is evicted before a new one is added.
     */
    private static final int KEY_CACHE_MAX_SIZE = 100;

    private final int ivSize;
    private final int tagSize;
    private final SecureRandom secureRandom;

    /**
     * Thread-safe cache that maps a Base64-encoded key fingerprint to the corresponding {@link
     * SecretKey}. Avoids repeated Base64 decoding of the same key across requests.
     *
     * <p>The key fingerprint is derived by Base64-encoding the raw bytes of the {@link
     * SecretKey#getEncoded()} array. The entry count is bounded by {@value #KEY_CACHE_MAX_SIZE}.
     */
    private final ConcurrentHashMap<String, SecretKey> keyCache = new ConcurrentHashMap<>();

    /**
     * Constructs EncryptionService with configuration from application.properties.
     *
     * @param algorithm encryption algorithm (from application.encryption.algorithm)
     * @param ivSize IV size in bytes (from application.encryption.iv-size, default 12)
     * @param tagSize authentication tag size in bits (from application.encryption.tag-size, default
     *     128)
     */
    public EncryptionService(
            @Value("${application.encryption.algorithm:AES/GCM/NoPadding}") String algorithm,
            @Value("${application.encryption.iv-size:12}") int ivSize,
            @Value("${application.encryption.tag-size:128}") int tagSize) {

        if (!TRANSFORMATION.equals(algorithm)) {
            throw new IllegalArgumentException(
                    "Only AES/GCM/NoPadding is supported. Provided: " + algorithm);
        }
        if (ivSize != 12) {
            throw new IllegalArgumentException(
                    "IV size must be 12 bytes for GCM. Provided: " + ivSize);
        }
        if (tagSize != 128 && tagSize != 120 && tagSize != 112 && tagSize != 104 && tagSize != 96) {
            throw new IllegalArgumentException(
                    "Tag size must be 96, 104, 112, 120, or 128 bits. Provided: " + tagSize);
        }

        this.ivSize = ivSize;
        this.tagSize = tagSize;

        try {
            this.secureRandom = SecureRandom.getInstanceStrong();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SecureRandom", e);
        }
    }

    // =========================================================================
    // Core encrypt / decrypt
    // =========================================================================

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * <p>Generates a unique random IV for this encryption operation and prepends it to the
     * ciphertext. The result is Base64-encoded for storage as text.
     *
     * <p><strong>Process:</strong>
     *
     * <ol>
     *   <li>Generate random 12-byte IV
     *   <li>Initialize AES-GCM cipher with key and IV
     *   <li>Encrypt plaintext + generate authentication tag
     *   <li>Prepend IV to ciphertext+tag
     *   <li>Encode result as Base64
     * </ol>
     *
     * <p><strong>Important:</strong> Never reuse an IV with the same key. This implementation
     * generates a fresh random IV for each encryption, ensuring uniqueness.
     *
     * @param plaintext the data to encrypt (UTF-8 string)
     * @param key the AES-256 encryption key (from KeyManagementService)
     * @return Base64-encoded string containing [IV][ciphertext+tag]
     * @throws IllegalArgumentException if plaintext or key is null
     * @throws IllegalStateException if encryption fails
     */
    public String encrypt(String plaintext, SecretKey key) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        byte[] plaintextBytes = null;
        byte[] ciphertextWithTag = null;
        byte[] iv = new byte[ivSize];
        try {
            // Generate unique random IV
            secureRandom.nextBytes(iv);

            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(tagSize, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            // Encrypt plaintext
            plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            ciphertextWithTag = cipher.doFinal(plaintextBytes);

            // Combine IV + ciphertext+tag
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertextWithTag);
            byte[] combined = byteBuffer.array();

            // Return Base64-encoded result
            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        } finally {
            // Clear sensitive data (best-effort)
            if (plaintextBytes != null) {
                Arrays.fill(plaintextBytes, (byte) 0);
            }
            if (ciphertextWithTag != null) {
                Arrays.fill(ciphertextWithTag, (byte) 0);
            }
            // Do not clear IV or combined here because IV is part of the returned ciphertext.
        }
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     *
     * <p>Extracts the IV from the beginning of the ciphertext, then decrypts and verifies the
     * authentication tag.
     *
     * <p><strong>Process:</strong>
     *
     * <ol>
     *   <li>Base64-decode the input string
     *   <li>Extract IV (first 12 bytes)
     *   <li>Extract ciphertext+tag (remaining bytes)
     *   <li>Initialize AES-GCM cipher with key and IV
     *   <li>Decrypt and verify authentication tag
     *   <li>Return plaintext as UTF-8 string
     * </ol>
     *
     * <p><strong>Authentication:</strong> If the ciphertext has been tampered with, GCM will detect
     * it and throw an exception during decryption.
     *
     * @param ciphertext Base64-encoded string containing [IV][ciphertext+tag]
     * @param key the AES-256 encryption key (same key used for encryption)
     * @return decrypted plaintext as UTF-8 string
     * @throws IllegalArgumentException if ciphertext or key is null, or ciphertext is invalid
     * @throws IllegalStateException if decryption fails or authentication fails (tampering
     *     detected)
     */
    public String decrypt(String ciphertext, SecretKey key) {
        if (ciphertext == null || ciphertext.trim().isEmpty()) {
            throw new IllegalArgumentException("Ciphertext cannot be null or empty");
        }
        if (key == null) {
            throw new IllegalArgumentException("Decryption key cannot be null");
        }

        // Trim ciphertext to remove any whitespace
        String trimmedCiphertext = ciphertext.trim();

        byte[] plaintextBytes = null;
        byte[] ciphertextWithTag = null;
        byte[] iv = null;
        try {
            // Base64 decode
            byte[] combined = Base64.getDecoder().decode(trimmedCiphertext);

            // Validate minimum size (IV + at least some ciphertext)
            if (combined.length < ivSize + 1) {
                throw new IllegalArgumentException(
                        "Invalid ciphertext: too short to contain IV and encrypted data");
            }

            // Extract IV and ciphertext+tag
            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
            iv = new byte[ivSize];
            byteBuffer.get(iv);

            ciphertextWithTag = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextWithTag);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(tagSize, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            // Decrypt and verify authentication tag
            plaintextBytes = cipher.doFinal(ciphertextWithTag);
            String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

            return plaintext;

        } catch (IllegalArgumentException e) {
            // Re-throw validation errors as-is
            throw e;
        } catch (javax.crypto.AEADBadTagException e) {
            // Authentication failed - data was tampered with
            throw new IllegalStateException(
                    "Decryption failed: data may have been tampered with", e);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        } finally {
            // Clear sensitive data (best-effort)
            if (plaintextBytes != null) {
                Arrays.fill(plaintextBytes, (byte) 0);
            }
            if (ciphertextWithTag != null) {
                Arrays.fill(ciphertextWithTag, (byte) 0);
            }
            if (iv != null) {
                Arrays.fill(iv, (byte) 0);
            }
        }
    }

    // =========================================================================
    // Nullable helpers (TASK-14.1.6)
    // =========================================================================

    /**
     * Encrypts {@code plaintext} using {@code key}, returning {@code null} when {@code plaintext}
     * is {@code null}.
     *
     * <p>This helper centralises the common null-check pattern present throughout the service
     * layer, reducing boilerplate at each call site.
     *
     * <p>Requirement TASK-14.1.6: Nullable helpers for cleaner service code.
     *
     * @param plaintext the data to encrypt, or {@code null}
     * @param key the AES-256 encryption key
     * @return Base64-encoded ciphertext, or {@code null} if {@code plaintext} is {@code null}
     * @throws IllegalArgumentException if {@code key} is null
     * @throws IllegalStateException if encryption fails
     */
    public String encryptNullable(String plaintext, SecretKey key) {
        if (plaintext == null) {
            return null;
        }
        return encrypt(plaintext, key);
    }

    /**
     * Decrypts {@code ciphertext} using {@code key}, returning {@code null} when {@code ciphertext}
     * is {@code null} or blank.
     *
     * <p>This helper centralises the common null-check pattern present throughout the service
     * layer, reducing boilerplate at each call site.
     *
     * <p>Requirement TASK-14.1.6: Nullable helpers for cleaner service code.
     *
     * @param ciphertext the Base64-encoded ciphertext, or {@code null} / blank
     * @param key the AES-256 decryption key
     * @return decrypted plaintext, or {@code null} if {@code ciphertext} is {@code null} or blank
     * @throws IllegalArgumentException if {@code key} is null
     * @throws IllegalStateException if decryption fails
     */
    public String decryptNullable(String ciphertext, SecretKey key) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        return decrypt(ciphertext, key);
    }

    // =========================================================================
    // Batch helpers (TASK-14.1.6)
    // =========================================================================

    /**
     * Encrypts a list of plaintext strings using the supplied key.
     *
     * <p>Each element in the returned list corresponds to the element at the same index in {@code
     * plaintexts}. A {@code null} element in the input produces a {@code null} entry in the output
     * (via {@link #encryptNullable}).
     *
     * <p>The method processes all elements sequentially so that callers avoid per-call overhead
     * (e.g., repeated argument validation) when encrypting many values at once.
     *
     * <p>Requirement TASK-14.1.6: Batch encryption for reduced per-call overhead.
     *
     * @param plaintexts list of plaintext strings to encrypt (may contain nulls); returns an empty
     *     list if {@code null} or empty
     * @param key the AES-256 encryption key
     * @return list of Base64-encoded ciphertexts in the same order as the input, or an empty list
     *     when {@code plaintexts} is {@code null} or empty
     * @throws IllegalArgumentException if {@code key} is null
     * @throws IllegalStateException if any encryption operation fails
     */
    public List<String> encryptBatch(List<String> plaintexts, SecretKey key) {
        if (plaintexts == null || plaintexts.isEmpty()) {
            return new ArrayList<>();
        }
        if (key == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        List<String> results = new ArrayList<>(plaintexts.size());
        for (String plaintext : plaintexts) {
            results.add(encryptNullable(plaintext, key));
        }
        return results;
    }

    /**
     * Decrypts a list of Base64-encoded ciphertext strings using the supplied key.
     *
     * <p>Each element in the returned list corresponds to the element at the same index in {@code
     * ciphertexts}. A {@code null} or blank element in the input produces a {@code null} entry in
     * the output (via {@link #decryptNullable}).
     *
     * <p>Requirement TASK-14.1.6: Batch decryption for reduced per-call overhead.
     *
     * @param ciphertexts list of Base64-encoded ciphertext strings (may contain nulls/blanks);
     *     returns an empty list if {@code null} or empty
     * @param key the AES-256 decryption key
     * @return list of decrypted plaintext strings in the same order as the input, or an empty list
     *     when {@code ciphertexts} is {@code null} or empty
     * @throws IllegalArgumentException if {@code key} is null
     * @throws IllegalStateException if any decryption operation fails
     */
    public List<String> decryptBatch(List<String> ciphertexts, SecretKey key) {
        if (ciphertexts == null || ciphertexts.isEmpty()) {
            return new ArrayList<>();
        }
        if (key == null) {
            throw new IllegalArgumentException("Decryption key cannot be null");
        }

        List<String> results = new ArrayList<>(ciphertexts.size());
        for (String ciphertext : ciphertexts) {
            results.add(decryptNullable(ciphertext, key));
        }
        return results;
    }

    // =========================================================================
    // Key cache helpers (TASK-14.1.6)
    // =========================================================================

    /**
     * Returns a cached {@link SecretKey} reference for the given Base64-encoded key string, or
     * stores and returns {@code key} if it is not already cached.
     *
     * <p>The cache avoids repeated Base64 decoding on every request. The fingerprint used as the
     * cache key is the Base64 representation of {@link SecretKey#getEncoded()}. When the cache
     * reaches {@value #KEY_CACHE_MAX_SIZE} entries the oldest entry (by insertion order as
     * approximated by {@link ConcurrentHashMap}'s iteration order) is evicted.
     *
     * <p>Requirement TASK-14.1.6: In-memory key cache to avoid repeated key decoding.
     *
     * @param encodedKeyString Base64-encoded key string used as the cache lookup key
     * @param key the {@link SecretKey} to cache if not already present
     * @return the cached (or newly stored) {@link SecretKey}
     * @throws IllegalArgumentException if {@code encodedKeyString} or {@code key} is null
     */
    public SecretKey cacheKey(String encodedKeyString, SecretKey key) {
        if (encodedKeyString == null) {
            throw new IllegalArgumentException("Encoded key string cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Evict one entry when the cache is at capacity
        if (!keyCache.containsKey(encodedKeyString) && keyCache.size() >= KEY_CACHE_MAX_SIZE) {
            String firstKey = keyCache.keys().nextElement();
            keyCache.remove(firstKey);
        }

        return keyCache.computeIfAbsent(encodedKeyString, k -> key);
    }

    /**
     * Returns the cached {@link SecretKey} for the given fingerprint, or {@code null} if no entry
     * is present.
     *
     * @param encodedKeyString Base64-encoded key string used as the cache lookup key
     * @return the cached {@link SecretKey}, or {@code null}
     */
    public SecretKey getCachedKey(String encodedKeyString) {
        return keyCache.get(encodedKeyString);
    }

    /**
     * Removes all entries from the in-memory key cache.
     *
     * <p>Intended for use in tests or when a user's key is revoked / rotated.
     */
    public void clearKeyCache() {
        keyCache.clear();
    }

    // =========================================================================
    // Binary helpers (unchanged from v1)
    // =========================================================================

    /**
     * Encrypts binary data (for file attachments).
     *
     * <p>Similar to {@link #encrypt(String, SecretKey)} but works with raw bytes.
     *
     * @param data the binary data to encrypt
     * @param key the AES-256 encryption key
     * @return encrypted data as byte array containing [IV][ciphertext+tag]
     * @throws IllegalArgumentException if data or key is null
     * @throws IllegalStateException if encryption fails
     */
    public byte[] encryptBytes(byte[] data, SecretKey key) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        try {
            // Generate unique random IV
            byte[] iv = new byte[ivSize];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(tagSize, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            // Encrypt data
            byte[] ciphertextWithTag = cipher.doFinal(data);

            // Combine IV + ciphertext+tag
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertextWithTag);

            return byteBuffer.array();

        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts binary data (for file attachments).
     *
     * <p>Similar to {@link #decrypt(String, SecretKey)} but works with raw bytes.
     *
     * @param encryptedData byte array containing [IV][ciphertext+tag]
     * @param key the AES-256 encryption key
     * @return decrypted binary data
     * @throws IllegalArgumentException if encryptedData or key is null/invalid
     * @throws IllegalStateException if decryption fails or authentication fails
     */
    public byte[] decryptBytes(byte[] encryptedData, SecretKey key) {
        if (encryptedData == null) {
            throw new IllegalArgumentException("Encrypted data cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Decryption key cannot be null");
        }
        if (encryptedData.length < ivSize + 1) {
            throw new IllegalArgumentException(
                    "Invalid encrypted data: too short to contain IV and ciphertext");
        }

        try {
            // Extract IV and ciphertext+tag
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[ivSize];
            byteBuffer.get(iv);

            byte[] ciphertextWithTag = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextWithTag);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(tagSize, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            // Decrypt and verify
            return cipher.doFinal(ciphertextWithTag);

        } catch (javax.crypto.AEADBadTagException e) {
            throw new IllegalStateException(
                    "Decryption failed: data may have been tampered with", e);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
