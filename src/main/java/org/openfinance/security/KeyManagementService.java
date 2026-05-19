// filepath: src/main/java/org/openfinance/security/KeyManagementService.java

package org.openfinance.security;

import javax.crypto.SecretKey;

/**
 * Service interface for managing encryption keys derived from user's master password.
 *
 * <p>This service handles the critical task of deriving strong encryption keys from user-provided
 * master passwords using PBKDF2 (Password-Based Key Derivation Function 2).
 *
 * <p><strong>Security Architecture:</strong>
 *
 * <ul>
 *   <li><strong>User password:</strong> Hashed with BCrypt, stored in database, used for login
 *   <li><strong>Master password:</strong> Derives encryption key with PBKDF2, never stored, used
 *       for data encryption
 *   <li><strong>Encryption key:</strong> AES-256 key derived from master password + salt
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.18: Data encryption at rest using AES-256-GCM
 *   <li>REQ-3.2: Security - key derivation with PBKDF2
 * </ul>
 *
 * <p><strong>Key Derivation Process:</strong>
 *
 * <ol>
 *   <li>User provides master password at app unlock
 *   <li>System retrieves user's unique salt from database
 *   <li>PBKDF2-HMAC-SHA256 derives 256-bit key (100,000 iterations)
 *   <li>Derived key held in memory for session duration
 *   <li>Key cleared from memory on logout/lock
 * </ol>
 *
 * <p><strong>Security Guarantees:</strong>
 *
 * <ul>
 *   <li>Encryption key never stored on disk
 *   <li>Salt unique per user (stored in database)
 *   <li>High iteration count (100,000) resists brute-force attacks
 *   <li>HMAC-SHA256 provides cryptographic strength
 * </ul>
 *
 * @see org.openfinance.security.EncryptionService
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
public interface KeyManagementService {

    /**
     * Derives an AES-256 encryption key from a master password and salt.
     *
     * <p>Uses PBKDF2-HMAC-SHA256 with 100,000 iterations as configured in <code>
     * application.security.pbkdf2-iterations</code>.
     *
     * <p><strong>Algorithm Parameters:</strong>
     *
     * <ul>
     *   <li>Function: PBKDF2-HMAC-SHA256
     *   <li>Iterations: 100,000 (configurable via properties)
     *   <li>Key size: 256 bits (32 bytes)
     *   <li>Salt size: 16 bytes (128 bits)
     * </ul>
     *
     * <p><strong>Performance:</strong> Key derivation takes approximately 100-200ms on modern
     * hardware, providing good security without impacting user experience.
     *
     * <p><strong>Usage Example:</strong>
     *
     * <pre>{@code
     * // During user registration
     * byte[] salt = keyManagementService.generateSalt();
     * user.setMasterPasswordSalt(salt);
     *
     * // During app unlock
     * SecretKey encryptionKey = keyManagementService.deriveKey(masterPassword, user.getMasterPasswordSalt());
     * // Store key in memory for session, use for encrypt/decrypt operations
     * }</pre>
     *
     * @param masterPassword the user's master password (char array for security)
     * @param salt the unique salt for this user (16 bytes)
     * @return AES-256 SecretKey derived from password and salt
     * @throws IllegalArgumentException if masterPassword is null/empty or salt is invalid
     * @throws IllegalStateException if key derivation fails
     */
    SecretKey deriveKey(char[] masterPassword, byte[] salt);

    /**
     * Generates a cryptographically secure random salt for key derivation.
     *
     * <p>Each user must have a unique salt to ensure that identical master passwords produce
     * different encryption keys across users. This prevents rainbow table attacks and makes
     * parallel attacks more difficult.
     *
     * <p><strong>Salt Properties:</strong>
     *
     * <ul>
     *   <li>Size: 16 bytes (128 bits)
     *   <li>Generation: SecureRandom with strong algorithm
     *   <li>Storage: Stored in User entity (not sensitive, can be plaintext)
     *   <li>Uniqueness: Random generation ensures uniqueness with high probability
     * </ul>
     *
     * <p><strong>When to Call:</strong>
     *
     * <ul>
     *   <li>User registration: Generate salt and store in User.masterPasswordSalt
     *   <li>Master password change: Generate new salt
     * </ul>
     *
     * @return cryptographically secure random 16-byte salt
     * @throws IllegalStateException if random number generation fails
     */
    byte[] generateSalt();

    /**
     * Validates that a salt meets security requirements.
     *
     * <p>Ensures salt is non-null and has the correct size (16 bytes).
     *
     * @param salt the salt to validate
     * @return true if salt is valid, false otherwise
     */
    boolean validateSalt(byte[] salt);

    /**
     * Securely clears a SecretKey from memory.
     *
     * <p>Overwrites the key's byte array with zeros to prevent memory dumps from exposing the
     * encryption key. Should be called when:
     *
     * <ul>
     *   <li>User logs out
     *   <li>Application locks
     *   <li>Session expires
     * </ul>
     *
     * <p><strong>Security Note:</strong> Java's garbage collector cannot be relied upon to clear
     * sensitive data. This method explicitly zeroes memory.
     *
     * @param key the SecretKey to clear (may be null)
     */
    void clearKey(SecretKey key);
}
