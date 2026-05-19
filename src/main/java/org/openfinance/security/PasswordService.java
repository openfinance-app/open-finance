// filepath: src/main/java/org/openfinance/security/PasswordService.java

package org.openfinance.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for hashing and validating user account passwords using BCrypt.
 *
 * <p>This service handles password hashing for user authentication (login passwords). It is
 * distinct from the encryption key derivation used for data encryption.
 *
 * <p><strong>Security Design:</strong>
 *
 * <ul>
 *   <li><strong>User passwords:</strong> BCrypt hashing (this service) - for authentication
 *   <li><strong>Master password:</strong> PBKDF2 key derivation - for data encryption keys
 *   <li><strong>Data encryption:</strong> AES-256-GCM - for sensitive financial data
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * // During user registration (REQ-2.1.1)
 * String hashedPassword = passwordService.hashPassword("user-password");
 * user.setPasswordHash(hashedPassword);
 *
 * // During user login (REQ-2.1.3)
 * boolean valid = passwordService.validatePassword("user-password", user.getPasswordHash());
 * if (valid) {
 *     // Proceed with login
 * }
 * }</pre>
 *
 * @see org.openfinance.config.SecurityConfig#passwordEncoder()
 * @see org.openfinance.security.KeyManagementService
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs a PasswordService with the configured BCrypt password encoder.
     *
     * @param passwordEncoder BCrypt encoder bean from SecurityConfig
     */
    public PasswordService(PasswordEncoder passwordEncoder) {
        if (passwordEncoder == null) {
            throw new IllegalArgumentException("PasswordEncoder cannot be null");
        }
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Hashes a plain-text password using BCrypt.
     *
     * <p>BCrypt automatically generates a unique salt for each password and includes it in the
     * hash. The strength is configured in {@link org.openfinance.config.SecurityConfig}.
     *
     * <p><strong>Security Properties:</strong>
     *
     * <ul>
     *   <li>Algorithm: BCrypt (Blowfish-based)
     *   <li>Salt: Random, unique per password
     *   <li>Work factor: 10 (2^10 = 1024 iterations)
     *   <li>Output: 60-character string (includes salt and hash)
     * </ul>
     *
     * <p><strong>Performance:</strong> Hashing takes approximately 100-200ms on modern hardware,
     * which provides good protection against brute-force attacks while being acceptable for login.
     *
     * @param plainPassword the plain-text password to hash (must not be null or empty)
     * @return BCrypt hash string (60 characters) including salt
     * @throws IllegalArgumentException if plainPassword is null or empty
     * @throws IllegalStateException if hashing fails
     */
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        try {
            return passwordEncoder.encode(plainPassword);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
        }
    }

    /**
     * Validates a plain-text password against a BCrypt hash.
     *
     * <p>This method is used during user login to verify credentials. It extracts the salt from the
     * stored hash and compares it with the hash of the provided password using the same salt.
     *
     * <p><strong>Security:</strong> This method is designed to be timing-attack resistant. It
     * always takes approximately the same time regardless of whether the password matches.
     *
     * @param plainPassword the plain-text password to validate (user input)
     * @param hashedPassword the stored BCrypt hash from database
     * @return true if password matches the hash, false otherwise
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public boolean validatePassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Plain password cannot be null or empty");
        }
        if (hashedPassword == null || hashedPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Hashed password cannot be null or empty");
        }

        try {
            return passwordEncoder.matches(plainPassword, hashedPassword);
        } catch (Exception e) {
            // Log non-sensitive details for operational visibility; do not log secrets
            log.warn("Password validation failed (non-sensitive): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a password needs rehashing (e.g., due to strength upgrade).
     *
     * <p>This method can be used during login to detect if a password was hashed with an
     * older/weaker configuration. If true, the application should rehash the password with current
     * settings after successful authentication.
     *
     * <p><strong>Use Case:</strong> When upgrading BCrypt work factor from 10 to 12:
     *
     * <pre>{@code
     * if (passwordService.validatePassword(password, user.getPasswordHash())) {
     *     if (passwordService.needsRehash(user.getPasswordHash())) {
     *         String newHash = passwordService.hashPassword(password);
     *         user.setPasswordHash(newHash);
     *         userRepository.save(user);
     *     }
     *     // Proceed with login
     * }
     * }</pre>
     *
     * @param hashedPassword the stored BCrypt hash to check
     * @return true if password should be rehashed with current settings, false otherwise
     */
    public boolean needsRehash(String hashedPassword) {
        if (hashedPassword == null || hashedPassword.trim().isEmpty()) {
            return true; // Invalid hash, needs rehashing
        }

        try {
            // Prefer using the PasswordEncoder upgradeEncoding if available
            try {
                return passwordEncoder.upgradeEncoding(hashedPassword);
            } catch (AbstractMethodError | NoSuchMethodError ignored) {
                // Fall through to specific checks
            }

            // Fallback for BCryptPasswordEncoder specifically
            if (passwordEncoder
                    instanceof org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder) {
                return ((org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder)
                                passwordEncoder)
                        .upgradeEncoding(hashedPassword);
            }

            // Unknown encoder type - conservative choice: assume rehash needed
            return true;
        } catch (Exception e) {
            log.warn(
                    "Failed to determine if password needs rehash (non-sensitive): {}",
                    e.getMessage());
            return true; // If check fails, assume rehash needed
        }
    }
}
