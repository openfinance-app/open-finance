// filepath: src/main/java/org/openfinance/security/KeyManagementServiceImpl.java

package org.openfinance.security;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of KeyManagementService using PBKDF2-HMAC-SHA256.
 *
 * <p>This implementation provides secure key derivation for AES-256 encryption of sensitive
 * financial data. The service uses industry-standard PBKDF2 with configurable iteration count to
 * resist brute-force attacks.
 *
 * <p><strong>Configuration:</strong>
 *
 * <ul>
 *   <li><code>application.security.pbkdf2-iterations</code>: Iteration count (default 100,000)
 *   <li><code>application.encryption.key-size</code>: Key size in bits (default 256)
 * </ul>
 *
 * @see KeyManagementService
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Service
public class KeyManagementServiceImpl implements KeyManagementService {
    private static final Logger log = LoggerFactory.getLogger(KeyManagementServiceImpl.class);

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_SIZE_BYTES = 16; // 128 bits

    private final int iterationCount;
    private final int keySize;
    private final SecureRandom secureRandom;

    /**
     * Constructs KeyManagementServiceImpl with configuration from application.properties.
     *
     * @param iterationCount PBKDF2 iteration count from application.security.pbkdf2-iterations
     * @param keySize encryption key size in bits from application.encryption.key-size
     */
    public KeyManagementServiceImpl(
            @Value("${application.security.pbkdf2-iterations:100000}") int iterationCount,
            @Value("${application.encryption.key-size:256}") int keySize) {

        if (iterationCount < 10000) {
            throw new IllegalArgumentException(
                    "Iteration count must be at least 10,000 for security. Provided: "
                            + iterationCount);
        }
        if (keySize != 128 && keySize != 192 && keySize != 256) {
            throw new IllegalArgumentException(
                    "Key size must be 128, 192, or 256 bits. Provided: " + keySize);
        }

        this.iterationCount = iterationCount;
        this.keySize = keySize;

        try {
            // Use strongest available SecureRandom implementation
            this.secureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize SecureRandom", e);
        }
    }

    @Override
    public SecretKey deriveKey(char[] masterPassword, byte[] salt) {
        // Validate inputs
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password cannot be null or empty");
        }
        if (!validateSalt(salt)) {
            throw new IllegalArgumentException(
                    "Invalid salt: must be non-null and " + SALT_SIZE_BYTES + " bytes");
        }
        KeySpec spec = null;
        byte[] keyBytes = null;
        try {
            // Create PBKDF2 key spec with password, salt, iterations, and key size
            spec = new PBEKeySpec(masterPassword, salt, iterationCount, keySize);

            // Derive key using PBKDF2-HMAC-SHA256
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            keyBytes = factory.generateSecret(spec).getEncoded();

            // Make an explicit copy of the derived key bytes to avoid relying on
            // implementation details of SecretKeySpec (some implementations may
            // keep a reference to the provided array). We zero the original
            // keyBytes in the finally block below.
            byte[] keyCopy = Arrays.copyOf(keyBytes, keyBytes.length);

            // Create AES SecretKey from copied derived bytes
            SecretKey secretKey = new SecretKeySpec(keyCopy, "AES");

            return secretKey;

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 algorithm not available", e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to derive key from master password", e);
        } finally {
            // Clear intermediate key bytes from memory (best-effort)
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }

            // Clear password from spec if possible (best-effort)
            if (spec instanceof PBEKeySpec) {
                try {
                    ((PBEKeySpec) spec).clearPassword();
                } catch (Exception ignore) {
                    // best-effort
                }
            }
        }
    }

    @Override
    public byte[] generateSalt() {
        try {
            byte[] salt = new byte[SALT_SIZE_BYTES];
            secureRandom.nextBytes(salt);
            return salt;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate salt", e);
        }
    }

    @Override
    public boolean validateSalt(byte[] salt) {
        return salt != null && salt.length == SALT_SIZE_BYTES;
    }

    @Override
    public void clearKey(SecretKey key) {
        if (key == null) {
            return;
        }

        try {
            // Get encoded key bytes
            byte[] encoded = key.getEncoded();
            if (encoded != null) {
                // Overwrite with zeros
                Arrays.fill(encoded, (byte) 0);
            }
        } catch (Exception e) {
            // Log but don't throw - clearing is best-effort
            log.warn("Failed to clear key bytes from memory (best-effort)", e);
        }
    }
}
