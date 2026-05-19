// filepath: src/main/java/org/openfinance/util/EncryptionUtil.java

package org.openfinance.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class providing helper methods for encryption operations.
 *
 * <p>This class contains static utility methods for common encryption-related tasks:
 *
 * <ul>
 *   <li>Base64 encoding/decoding
 *   <li>Salt generation
 *   <li>Secure random number generation
 *   <li>Secure string handling
 * </ul>
 *
 * <p><strong>Security Best Practices:</strong>
 *
 * <ul>
 *   <li>Always use {@link #generateSecureRandom()} for cryptographic operations
 *   <li>Clear sensitive data from memory using {@link #clearArray(byte[])}
 *   <li>Use {@link #charArrayToBytes(char[])} for password handling
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
public final class EncryptionUtil {

    // Private constructor to prevent instantiation
    private EncryptionUtil() {
        // Prevent instantiation
    }

    /**
     * Decodes a Base64-encoded encryption key into a SecretKey.
     *
     * <p>This method:
     *
     * <ol>
     *   <li>Validates that the key is not null or empty
     *   <li>Decodes the Base64-encoded key
     *   <li>Validates key length (must be 16, 24, or 32 bytes for AES-128/192/256)
     *   <li>Creates a copy of the key material to avoid retaining references
     *   <li>Securely clears the decoded key from memory
     * </ol>
     *
     * <p><strong>Security Notes:</strong>
     *
     * <ul>
     *   <li>Key material is zeroed out in memory after use (finally block)
     *   <li>A defensive copy is created before returning the SecretKey
     *   <li>Validates key length to prevent weak encryption
     * </ul>
     *
     * <p><strong>Usage Example:</strong>
     *
     * <pre>{@code
     * @PostMapping
     * public ResponseEntity<Response> create(
     *         @RequestHeader("X-Encryption-Key") String encodedKey) {
     *     SecretKey key = EncryptionUtil.decodeEncryptionKey(encodedKey);
     *     // Use key for encryption/decryption
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.18: Data encryption at rest for sensitive fields
     *
     * @param encodedKey Base64-encoded AES key string
     * @return SecretKey for AES encryption/decryption
     * @throws IllegalArgumentException if key is null, empty, has invalid format, or invalid length
     */
    public static SecretKey decodeEncryptionKey(String encodedKey) {
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        // Trim the key to remove any whitespace
        String trimmedKey = encodedKey.trim();

        byte[] decodedKey = null;
        try {
            decodedKey = Base64.getDecoder().decode(trimmedKey);

            // AES key must be 16, 24, or 32 bytes for AES-128/192/256
            if (!(decodedKey.length == 16 || decodedKey.length == 24 || decodedKey.length == 32)) {
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid AES key length: %d bytes (expected 16, 24, or 32)",
                                decodedKey.length));
            }

            // Copy key material to avoid retaining references that can be cleared
            byte[] keyCopy = Arrays.copyOf(decodedKey, decodedKey.length);
            return new SecretKeySpec(keyCopy, "AES");

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid encryption key format: " + e.getMessage(), e);
        } finally {
            // Securely clear key material from memory
            clearArray(decodedKey);
        }
    }

    /**
     * Encodes binary data to Base64 string.
     *
     * <p>Uses standard Base64 encoding (RFC 4648) for storing binary data as text. This is used for
     * storing encrypted data in text-based database columns.
     *
     * <p><strong>Usage:</strong>
     *
     * <pre>{@code
     * byte[] encryptedData = ...;
     * String base64 = EncryptionUtil.encodeBase64(encryptedData);
     * // Store base64 string in database
     * }</pre>
     *
     * @param data the binary data to encode
     * @return Base64-encoded string
     * @throws IllegalArgumentException if data is null
     */
    public static String encodeBase64(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes Base64 string to binary data.
     *
     * <p>Inverse operation of {@link #encodeBase64(byte[])}.
     *
     * @param base64 the Base64-encoded string
     * @return decoded binary data
     * @throws IllegalArgumentException if base64 is null or not valid Base64
     */
    public static byte[] decodeBase64(String base64) {
        if (base64 == null) {
            throw new IllegalArgumentException("base64 cannot be null");
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 string", e);
        }
    }

    /**
     * Generates a cryptographically secure random salt.
     *
     * <p>Wraps {@link SecureRandom} for convenience. Each salt is unique with extremely high
     * probability (2^128 possible values for 16-byte salt).
     *
     * <p><strong>When to Use:</strong>
     *
     * <ul>
     *   <li>User registration: Generate salt for PBKDF2 key derivation
     *   <li>Master password change: Generate new salt
     * </ul>
     *
     * @param sizeBytes the size of salt in bytes (typically 16)
     * @return cryptographically secure random byte array
     * @throws IllegalArgumentException if sizeBytes is not positive
     * @throws IllegalStateException if random generation fails
     */
    public static byte[] generateSalt(int sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Salt size must be positive, got: " + sizeBytes);
        }

        try {
            byte[] salt = new byte[sizeBytes];
            SecureRandom secureRandom = SecureRandom.getInstanceStrong();
            secureRandom.nextBytes(salt);
            return salt;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate salt", e);
        }
    }

    /**
     * Gets a strong SecureRandom instance for cryptographic operations.
     *
     * <p>Uses the strongest available SecureRandom algorithm on the platform. On Linux, this is
     * typically NativePRNGBlocking which reads from /dev/random.
     *
     * <p><strong>Important:</strong> Do NOT use {@link java.util.Random} for cryptographic
     * operations. Always use SecureRandom.
     *
     * @return strong SecureRandom instance
     * @throws IllegalStateException if strong SecureRandom is not available
     */
    public static SecureRandom generateSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (Exception e) {
            throw new IllegalStateException("Strong SecureRandom not available", e);
        }
    }

    /**
     * Generates cryptographically secure random bytes.
     *
     * <p>Convenience method for generating random data (IVs, nonces, etc.).
     *
     * @param sizeBytes number of random bytes to generate
     * @return random byte array of specified size
     * @throws IllegalArgumentException if sizeBytes is not positive
     */
    public static byte[] generateRandomBytes(int sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Size must be positive, got: " + sizeBytes);
        }

        byte[] randomBytes = new byte[sizeBytes];
        generateSecureRandom().nextBytes(randomBytes);
        return randomBytes;
    }

    /**
     * Securely clears a byte array by overwriting with zeros.
     *
     * <p>This prevents sensitive data (keys, passwords, plaintext) from lingering in memory where
     * it could be exposed via memory dumps or swap files.
     *
     * <p><strong>When to Use:</strong>
     *
     * <ul>
     *   <li>After using encryption key bytes
     *   <li>After processing password bytes
     *   <li>After decrypting sensitive data
     * </ul>
     *
     * <p><strong>Note:</strong> Java's garbage collector cannot be relied upon to clear sensitive
     * data. Always explicitly zero arrays.
     *
     * @param array the byte array to clear (may be null)
     */
    public static void clearArray(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
        }
    }

    /**
     * Securely clears a char array by overwriting with null characters.
     *
     * <p>Used for clearing passwords stored as char arrays.
     *
     * <p><strong>Why char[] for passwords?</strong> Strings are immutable and cannot be cleared, so
     * they persist in memory until garbage collected. char[] can be explicitly cleared after use.
     *
     * @param array the char array to clear (may be null)
     */
    public static void clearArray(char[] array) {
        if (array != null) {
            Arrays.fill(array, '\0');
        }
    }

    /**
     * Converts char array to byte array using UTF-8 encoding.
     *
     * <p>This is used for converting password char arrays to bytes for key derivation. The
     * resulting byte array should be cleared after use with {@link #clearArray(byte[])}.
     *
     * <p><strong>Usage Example:</strong>
     *
     * <pre>{@code
     * char[] password = getUserPassword(); // from password field
     * byte[] passwordBytes = EncryptionUtil.charArrayToBytes(password);
     * try {
     *     // Use passwordBytes for key derivation
     * } finally {
     *     EncryptionUtil.clearArray(passwordBytes);
     *     EncryptionUtil.clearArray(password);
     * }
     * }</pre>
     *
     * @param chars the char array to convert
     * @return UTF-8 encoded byte array
     * @throws IllegalArgumentException if chars is null
     */
    public static byte[] charArrayToBytes(char[] chars) {
        if (chars == null) {
            throw new IllegalArgumentException("chars cannot be null");
        }

        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes =
                Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());

        // Clear the buffers
        Arrays.fill(byteBuffer.array(), (byte) 0);

        return bytes;
    }

    /**
     * Converts byte array to char array using UTF-8 decoding.
     *
     * <p>Inverse of {@link #charArrayToBytes(char[])}.
     *
     * @param bytes the byte array to convert
     * @return char array
     * @throws IllegalArgumentException if bytes is null
     */
    public static char[] bytesToCharArray(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars =
                Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());

        // Clear the buffer
        Arrays.fill(charBuffer.array(), '\0');

        return chars;
    }

    /**
     * Constant-time comparison of two byte arrays.
     *
     * <p>Prevents timing attacks by always comparing all bytes regardless of when a mismatch is
     * found. Regular {@code Arrays.equals()} returns early on first mismatch, which leaks
     * information via timing.
     *
     * <p><strong>When to Use:</strong> Comparing authentication tags, MACs, or any
     * security-sensitive values where timing attacks are a concern.
     *
     * @param a first byte array
     * @param b second byte array
     * @return true if arrays are equal, false otherwise (or if either is null)
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b; // Both null = equal, one null = not equal
        }

        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }

        return result == 0;
    }

    /**
     * Validates that a byte array is not null and has the expected size.
     *
     * @param data the byte array to validate
     * @param expectedSize the expected size in bytes
     * @param paramName parameter name for error messages
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateByteArray(byte[] data, int expectedSize, String paramName) {
        if (data == null) {
            throw new IllegalArgumentException(paramName + " cannot be null");
        }
        if (data.length != expectedSize) {
            throw new IllegalArgumentException(
                    paramName + " must be " + expectedSize + " bytes, got: " + data.length);
        }
    }

    /**
     * Converts byte array to hexadecimal string.
     *
     * <p>Useful for debugging and logging (but never log sensitive data!).
     *
     * @param bytes the byte array to convert
     * @return hexadecimal string (lowercase)
     * @throws IllegalArgumentException if bytes is null
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }

        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Converts hexadecimal string to byte array.
     *
     * <p>Inverse of {@link #bytesToHex(byte[])}.
     *
     * @param hex the hexadecimal string (must have even length)
     * @return byte array
     * @throws IllegalArgumentException if hex is null or invalid
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex cannot be null");
        }
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];

        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] =
                        (byte)
                                ((Character.digit(hex.charAt(i), 16) << 4)
                                        + Character.digit(hex.charAt(i + 1), 16));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid hexadecimal string", e);
        }

        return data;
    }
}
