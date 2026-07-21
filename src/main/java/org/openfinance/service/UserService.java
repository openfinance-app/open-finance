package org.openfinance.service;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.UpdateProfileRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.dto.UserResponse;
import org.openfinance.entity.User;
import org.openfinance.exception.DuplicateUserException;
import org.openfinance.mapper.UserMapper;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.KeyManagementService;
import org.openfinance.security.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for managing user accounts and authentication.
 *
 * <p>Handles user registration, authentication, and profile management. Integrates with security
 * services for password hashing and encryption key derivation.
 *
 * <p><strong>Security Architecture:</strong>
 *
 * <ul>
 *   <li><strong>Login password</strong>: Hashed with BCrypt via {@link PasswordService}
 *   <li><strong>Master password</strong>: Derives encryption key via {@link KeyManagementService}
 *   <li><strong>Salt generation</strong>: Unique salt per user for PBKDF2 key derivation
 * </ul>
 *
 * <p>Requirement REQ-2.1.1: User registration with dual password system
 *
 * @see UserRegistrationRequest
 * @see UserResponse
 * @see org.openfinance.entity.User
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String ENCRYPTION_DISABLED_SALT = "ENCRYPTION_DISABLED";

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final KeyManagementService keyManagementService;
    private final EncryptionProperties encryptionProperties;
    private final UserMapper userMapper;
    private final CategorySeeder categorySeeder;
    private final PayeeSeeder payeeSeeder;
    private final DefaultCurrencyProvider defaultCurrencyProvider;

    /**
     * Registers a new user account with login and master passwords.
     *
     * <p><strong>Registration Process:</strong>
     *
     * <ol>
     *   <li>Validate username uniqueness
     *   <li>Validate email uniqueness
     *   <li>Hash login password with BCrypt
     *   <li>Generate unique salt for master password key derivation when encryption is enabled
     *   <li>Create and persist User entity
     *   <li>Return non-sensitive user information
     * </ol>
     *
     * <p><strong>Security Notes:</strong>
     *
     * <ul>
     *   <li>Login password is hashed with BCrypt (never stored in plain text)
     *   <li>Master password is NOT stored - only the salt is stored
     *   <li>Salt is used later with master password to derive encryption key
     *   <li>Response DTO excludes all sensitive fields
     * </ul>
     *
     * <p><strong>Validation:</strong>
     *
     * <ul>
     *   <li>Username: 3-50 characters, unique
     *   <li>Email: Valid format, unique
     *   <li>Password: Minimum 8 characters
     *   <li>Master password: Required when application encryption is enabled
     * </ul>
     *
     * @param request registration request with username, email, password, and master password
     * @return UserResponse with non-sensitive user information
     * @throws DuplicateUserException if username or email already exists
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if password hashing or salt generation fails
     */
    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        log.info("Registering new user with username: {}", request.getUsername());

        // 1. Validate username uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed: username '{}' already exists", request.getUsername());
            throw new DuplicateUserException("Username already exists: " + request.getUsername());
        }

        // 2. Validate email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email '{}' already exists", request.getEmail());
            throw new DuplicateUserException("Email already exists: " + request.getEmail());
        }

        boolean encryptionEnabled = encryptionProperties.isEnabled();
        if (encryptionEnabled
                && (request.getMasterPassword() == null || request.getMasterPassword().isBlank())) {
            throw new IllegalArgumentException(
                    "Master password is required when encryption is enabled");
        }
        if (encryptionEnabled && request.getMasterPassword().length() < 8) {
            throw new IllegalArgumentException(
                    "Master password must be at least 8 characters when encryption is enabled");
        }

        // 3. Hash login password with BCrypt
        String passwordHash = passwordService.hashPassword(request.getPassword());

        // 4. Generate unique salt for master password key derivation
        String saltBase64;
        if (encryptionEnabled) {
            byte[] saltBytes = keyManagementService.generateSalt();
            saltBase64 = Base64.getEncoder().encodeToString(saltBytes);
        } else {
            saltBase64 = ENCRYPTION_DISABLED_SALT;
        }

        // 5. Create User entity
        User user =
                User.builder()
                        .username(request.getUsername())
                        .email(request.getEmail())
                        .passwordHash(passwordHash)
                        .masterPasswordSalt(saltBase64)
                        .baseCurrency(defaultCurrencyProvider.getDefaultCurrency())
                        .build();

        // 6. Persist to database
        User savedUser = userRepository.save(user);

        log.info(
                "Successfully registered user with ID: {} and username: {}",
                savedUser.getId(),
                savedUser.getUsername());

        // 7. Seed default categories for the new user (unless skipped)
        if (!request.isSkipSeeding()) {
            try {
                int categoriesCreated = categorySeeder.seedDefaultCategories(savedUser.getId());
                log.info(
                        "Seeded {} default categories for user: {}",
                        categoriesCreated,
                        savedUser.getUsername());
            } catch (Exception e) {
                log.error(
                        "Failed to seed default categories for user: {}",
                        savedUser.getUsername(),
                        e);
                // Don't fail registration if category seeding fails - user can create
                // categories manually
            }
        } else {
            log.info("Skipping default category seeding for user: {}", savedUser.getUsername());
        }

        // 8. Seed default payees for the new user (unless skipped)
        if (!request.isSkipSeeding()) {
            try {
                payeeSeeder.seedDefaultPayees(savedUser.getId());
                log.info("Seeded default payees for user: {}", savedUser.getUsername());
            } catch (Exception e) {
                log.error("Failed to seed default payees for user: {}", savedUser.getUsername(), e);
                // Don't fail registration if payee seeding fails - user can create payees
                // manually
            }
        } else {
            log.info("Skipping default payee seeding for user: {}", savedUser.getUsername());
        }

        // 9. Convert to response DTO (excludes sensitive fields)
        return userMapper.toResponse(savedUser);
    }

    /**
     * Updates user profile information.
     *
     * <p><strong>Update Process:</strong>
     *
     * <ol>
     *   <li>Verify user exists
     *   <li>Validate current password
     *   <li>Update email if provided and different
     *   <li>Update password if new password provided
     *   <li>Persist changes and return updated user
     * </ol>
     *
     * <p><strong>Security Notes:</strong>
     *
     * <ul>
     *   <li>Current password verification required for all changes
     *   <li>Email must be unique across system
     *   <li>New password is hashed with BCrypt before storage
     * </ul>
     *
     * @param userId ID of user to update
     * @param request update request with optional email and new password, required current password
     * @return UserResponse with updated user information
     * @throws IllegalArgumentException if user not found or current password invalid
     * @throws DuplicateUserException if new email already exists
     */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("Updating profile for user ID: {}", userId);

        // 1. Verify user exists
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> {
                                    log.warn("Profile update failed: user ID {} not found", userId);
                                    return new IllegalArgumentException(
                                            "User not found with ID: " + userId);
                                });

        // 2. Validate current password
        if (!passwordService.validatePassword(
                request.getCurrentPassword(), user.getPasswordHash())) {
            log.warn(
                    "Profile update failed for user {}: invalid current password",
                    user.getUsername());
            throw new BadCredentialsException("Invalid current password");
        }

        boolean updated = false;

        // 3. Update email if provided and different
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            // Check email uniqueness
            if (userRepository.existsByEmail(request.getEmail())) {
                log.warn(
                        "Profile update failed for user {}: email '{}' already exists",
                        user.getUsername(),
                        request.getEmail());
                throw new DuplicateUserException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
            updated = true;
            log.info("Updated email for user {}", user.getUsername());
        }

        // 4. Update password if new password provided
        if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
            String newPasswordHash = passwordService.hashPassword(request.getNewPassword());
            user.setPasswordHash(newPasswordHash);
            updated = true;
            log.info("Updated password for user {}", user.getUsername());
        }

        // 5. Persist changes if any updates were made
        if (updated) {
            user = userRepository.save(user);
            log.info("Successfully updated profile for user {}", user.getUsername());
        } else {
            log.info("No changes made to profile for user {}", user.getUsername());
        }

        return userMapper.toResponse(user);
    }

    /**
     * Updates an existing user's profile.
     *
     * <p>Allows updating email and/or password after verifying the current password. At least one
     * field (email or newPassword) must be provided to update.
     *
     * <p><strong>Update Rules:</strong>
     *
     * <ul>
     *   <li>Current password is verified with BCrypt
     *   <li>Email must be unique if provided
     *   <li>New password must be at least 8 characters if provided
     *   <li>New password is hashed with BCrypt before storage
     * </ul>
     *
     * @param userId ID of the user to update
     * @param request update profile request with optional email/password
     * @return UserResponse with updated profile data
     * @throws UserNotFoundException if user not found
     * @throws BadCredentialsException if current password invalid
     * @throws DuplicateUserException if email already exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(Long userId) {
        log.debug("Retrieving profile for user ID: {}", userId);

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Profile retrieval failed: user ID {} not found",
                                            userId);
                                    return new IllegalArgumentException(
                                            "User not found with ID: " + userId);
                                });

        return userMapper.toResponse(user);
    }

    /**
     * Updates the user's preferred base currency.
     *
     * <p>The base currency is used for multi-currency conversion throughout the application. Must
     * be a valid 3-letter ISO 4217 currency code (e.g., USD, EUR, GBP).
     *
     * <p><strong>Validation:</strong>
     *
     * <ul>
     *   <li>Currency code must be exactly 3 uppercase letters
     *   <li>Currency should exist in the currencies table (best practice)
     * </ul>
     *
     * <p>Requirement 6.2.13: Base currency setting for user preferences
     *
     * @param userId ID of the user to update
     * @param baseCurrency 3-letter ISO 4217 currency code (e.g., "USD", "EUR", "GBP")
     * @return UserResponse with updated base currency
     * @throws IllegalArgumentException if user not found or currency code invalid
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(
                        value = {
                            "dashboardSummary",
                            "accountSummaries",
                            "netWorthSummary",
                            "assetAllocation",
                            "portfolioPerformance",
                            "borrowingCapacity",
                            "networthAllocation",
                            "insights"
                        },
                        key = "#userId"),
                @CacheEvict(
                        value = {
                            "cashFlow",
                            "spendingByCategory",
                            "cashflowSankey",
                            "exchangeRates"
                        },
                        allEntries = true)
            })
    public UserResponse updateBaseCurrency(Long userId, String baseCurrency) {
        log.info("Updating base currency for user ID {} to: {}", userId, baseCurrency);

        // 1. Validate currency code format
        if (baseCurrency == null
                || baseCurrency.length() != 3
                || !baseCurrency.matches("[A-Z]{3}")) {
            log.warn(
                    "Base currency update failed: invalid format '{}' for user ID {}",
                    baseCurrency,
                    userId);
            throw new IllegalArgumentException(
                    "Base currency must be a 3-letter ISO 4217 code (e.g., USD, EUR, GBP)");
        }

        // 2. Find user
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Base currency update failed: user ID {} not found",
                                            userId);
                                    return new IllegalArgumentException(
                                            "User not found with ID: " + userId);
                                });

        // 3. Update base currency
        String oldCurrency = user.getBaseCurrency();
        user.setBaseCurrency(baseCurrency);

        // 4. Persist changes
        User updatedUser = userRepository.save(user);

        log.info(
                "Successfully updated base currency for user {} from {} to {}",
                user.getUsername(),
                oldCurrency,
                baseCurrency);

        return userMapper.toResponse(updatedUser);
    }

    /**
     * Retrieves the user's current base currency.
     *
     * <p>Returns the ISO 4217 currency code that the user prefers for multi-currency conversion.
     * Defaults to "USD" if not explicitly set.
     *
     * <p>Requirement 6.2.13: Base currency setting for user preferences
     *
     * @param userId ID of the user
     * @return 3-letter ISO 4217 currency code (e.g., "USD", "EUR", "GBP")
     * @throws IllegalArgumentException if user not found
     */
    @Transactional(readOnly = true)
    public String getBaseCurrency(Long userId) {
        log.debug("Retrieving base currency for user ID: {}", userId);

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Base currency retrieval failed: user ID {} not found",
                                            userId);
                                    return new IllegalArgumentException(
                                            "User not found with ID: " + userId);
                                });

        return user.getBaseCurrency();
    }

    /**
     * Updates the user's login password.
     *
     * <p>Verifies the current password before allowing the change. The new password is hashed with
     * BCrypt before storage.
     *
     * <p><strong>Security:</strong>
     *
     * <ul>
     *   <li>Current password must be correct
     *   <li>New password must be at least 8 characters
     *   <li>Password is hashed with BCrypt before storage
     *   <li>This does NOT affect the master password or encryption keys
     * </ul>
     *
     * <p>Requirement REQ-6.3.16: Password change functionality
     *
     * @param userId ID of the user
     * @param currentPassword current login password for verification
     * @param newPassword new login password to set
     * @throws IllegalArgumentException if user not found or current password invalid
     */
    @Transactional
    public void updatePassword(Long userId, String currentPassword, String newPassword) {
        log.info("Updating password for user ID: {}", userId);

        // 1. Find user
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Password update failed: user ID {} not found", userId);
                                    return new IllegalArgumentException(
                                            "User not found with ID: " + userId);
                                });

        // 2. Verify current password
        if (!passwordService.validatePassword(currentPassword, user.getPasswordHash())) {
            log.warn(
                    "Password update failed for user {}: invalid current password",
                    user.getUsername());
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // 3. Hash new password
        String newPasswordHash = passwordService.hashPassword(newPassword);

        // 4. Update password
        user.setPasswordHash(newPasswordHash);
        userRepository.save(user);

        log.info("Successfully updated password for user {}", user.getUsername());
    }

    /**
     * Updates the user's master password.
     *
     * <p>The master password is used to derive the encryption key for securing sensitive financial
     * data. This method verifies the current master password and generates a new salt for the new
     * password.
     *
     * <p><strong>Important:</strong> This method only updates the salt. Full re-encryption of all
     * user data (accounts, transactions, assets, liabilities) with the new key is a separate
     * complex operation that requires:
     *
     * <ul>
     *   <li>Deriving the old encryption key from current master password
     *   <li>Decrypting all encrypted data
     *   <li>Deriving the new encryption key from new master password
     *   <li>Re-encrypting all data with the new key
     * </ul>
     *
     * <p><strong>Security:</strong>
     *
     * <ul>
     *   <li>Current master password must be correct
     *   <li>New master password must be at least 8 characters
     *   <li>A new salt is generated for the new master password
     *   <li>This does NOT affect the login password
     * </ul>
     *
     * <p>Requirement REQ-6.3.16: Password change functionality
     *
     * @param userId ID of the user
     * @param currentMasterPassword current master password for verification
     * @param newMasterPassword new master password to set
     * @throws IllegalArgumentException if user not found or current master password invalid
     */
    @Transactional
    public void updateMasterPassword(
            Long userId, String currentMasterPassword, String newMasterPassword) {
        log.info("Updating master password for user ID: {}", userId);

        // 1. Find user
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Master password update failed: user ID {} not found",
                                            userId);
                                    return new IllegalArgumentException(
                                            "User not found with ID: " + userId);
                                });

        // 2. Verify current master password by deriving the key
        try {
            byte[] saltBytes = Base64.getDecoder().decode(user.getMasterPasswordSalt());
            char[] currentPasswordChars = currentMasterPassword.toCharArray();

            // Try to derive key from current password - if it fails, password is wrong
            keyManagementService.deriveKey(currentPasswordChars, saltBytes);

            // Clear the password from memory
            for (int i = 0; i < currentPasswordChars.length; i++) {
                currentPasswordChars[i] = '\0';
            }
        } catch (Exception e) {
            log.warn(
                    "Master password update failed for user {}: invalid current master password",
                    user.getUsername());
            throw new IllegalArgumentException("Current master password is incorrect");
        }

        // 3. Generate new salt for the new master password
        byte[] newSaltBytes = keyManagementService.generateSalt();
        String newSaltBase64 = Base64.getEncoder().encodeToString(newSaltBytes);

        // 4. Update the salt (note: full data re-encryption is not implemented)
        user.setMasterPasswordSalt(newSaltBase64);
        userRepository.save(user);

        log.info(
                "Successfully updated master password salt for user {}. Note: Full data re-encryption is required.",
                user.getUsername());
    }

    // -------------------------------------------------------------------------
    // Profile image management
    // -------------------------------------------------------------------------

    /** Allowed MIME types for profile images. */
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /** Maximum allowed file size for a profile image: 2 MB. */
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

    /**
     * Uploads and persists a profile image for the given user.
     *
     * <p>The image is validated (type and size) then stored as a Base64-encoded data URL directly
     * in the {@code users.profile_image} column. This approach avoids a separate file-system
     * dependency and keeps the image portable across deployments. Larger installations may wish to
     * swap this for an object-store.
     *
     * @param userId ID of the user whose image is being updated
     * @param imageFile multipart file containing the image data
     * @return {@link UserResponse} with the updated {@code profileImage} field populated
     * @throws IllegalArgumentException if the file type is not allowed or the size exceeds 2 MB
     * @throws IllegalStateException if the file cannot be read
     */
    @Transactional
    public UserResponse uploadProfileImage(Long userId, MultipartFile imageFile) {
        log.info("Uploading profile image for user ID: {}", userId);

        // 1. Validate content type
        String contentType = imageFile.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            log.warn(
                    "Profile image upload rejected for user {}: unsupported type '{}'",
                    userId,
                    contentType);
            throw new IllegalArgumentException(
                    "Unsupported image type. Allowed types: JPEG, PNG, GIF, WebP.");
        }

        // 2. Validate file size
        if (imageFile.getSize() > MAX_IMAGE_BYTES) {
            log.warn(
                    "Profile image upload rejected for user {}: file too large ({} bytes)",
                    userId,
                    imageFile.getSize());
            throw new IllegalArgumentException("Profile image must not exceed 2 MB.");
        }

        // 3. Fetch user
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));

        // 4. Convert to Base64 data URL
        byte[] imageBytes;
        try {
            imageBytes = imageFile.getBytes();
        } catch (IOException e) {
            log.error("Failed to read profile image bytes for user {}", userId, e);
            throw new IllegalStateException("Failed to read uploaded image.", e);
        }
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + contentType + ";base64," + base64;

        // 5. Persist
        user.setProfileImage(dataUrl);
        User saved = userRepository.save(user);

        log.info("Profile image uploaded successfully for user {}", user.getUsername());
        return userMapper.toResponse(saved);
    }

    /**
     * Removes the profile image for the given user, reverting to the default avatar.
     *
     * @param userId ID of the user whose image should be deleted
     * @return {@link UserResponse} with {@code profileImage} set to {@code null}
     */
    @Transactional
    public UserResponse deleteProfileImage(Long userId) {
        log.info("Deleting profile image for user ID: {}", userId);

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));

        user.setProfileImage(null);
        User saved = userRepository.save(user);

        log.info("Profile image deleted for user {}", user.getUsername());
        return userMapper.toResponse(saved);
    }
}
