package org.openfinance.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.MasterPasswordUpdateRequest;
import org.openfinance.dto.OnboardingRequest;
import org.openfinance.dto.PasswordUpdateRequest;
import org.openfinance.dto.UserResponse;
import org.openfinance.dto.UserSettingsResponse;
import org.openfinance.dto.UserSettingsUpdateRequest;
import org.openfinance.entity.User;
import org.openfinance.service.UserService;
import org.openfinance.service.UserSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for user profile and settings endpoints.
 *
 * <p>Provides endpoints for managing user profile and preferences including base currency settings,
 * display preferences, and password management.
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>GET /api/v1/users/me - Get current user profile
 *   <li>GET /api/v1/users/me/base-currency - Get user's base currency
 *   <li>PUT /api/v1/users/me/base-currency - Update user's base currency
 *   <li>GET /api/v1/users/me/settings - Get user's display settings
 *   <li>PUT /api/v1/users/me/settings - Update user's display settings
 *   <li>PUT /api/v1/users/me/password - Update user's login password
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Users can only access/modify their own profile
 * </ul>
 *
 * <p>Requirements: REQ-6.2.13 (Base currency), REQ-6.3 (User settings)
 *
 * @see UserService
 * @see UserSettingsService
 * @see UserResponse
 * @see UserSettingsResponse
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserSettingsService userSettingsService;

    /**
     * Retrieves the current authenticated user's profile.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "username": "john.doe",
     * "email": "john.doe@example.com",
     * "baseCurrency": "USD",
     * "createdAt": "2026-01-15T10:30:00",
     * "updatedAt": "2026-02-02T14:20:00"
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.1: User profile retrieval
     *
     * @param authentication Spring Security authentication object containing user details
     * @return ResponseEntity with UserResponse containing profile information
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        log.info("Retrieving profile for user: {}", user.getUsername());

        UserResponse response = userService.getUserProfile(user.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the current user's base currency preference.
     *
     * <p>Returns the ISO 4217 currency code that the user prefers for multi-currency conversion
     * throughout the application.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "baseCurrency": "USD"
     * }
     * }</pre>
     *
     * <p>Requirement REQ-6.2.13: Get user's base currency
     *
     * @param authentication Spring Security authentication object
     * @return ResponseEntity with base currency code
     */
    @GetMapping("/me/base-currency")
    public ResponseEntity<Map<String, String>> getBaseCurrency(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        log.info("Retrieving base currency for user: {}", user.getUsername());

        String baseCurrency = userService.getBaseCurrency(user.getId());
        return ResponseEntity.ok(Map.of("baseCurrency", baseCurrency));
    }

    /**
     * Updates the current user's base currency preference.
     *
     * <p>Sets the ISO 4217 currency code that will be used for multi-currency conversion throughout
     * the application. This affects dashboard summaries, reports, and all currency conversions.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>Content-Type: application/json
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "baseCurrency": "EUR"
     * }
     * }</pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "username": "john.doe",
     * "email": "john.doe@example.com",
     * "baseCurrency": "EUR",
     * "createdAt": "2026-01-15T10:30:00",
     * "updatedAt": "2026-02-02T14:25:00"
     * }
     * }</pre>
     *
     * <p><strong>Validation:</strong>
     *
     * <ul>
     *   <li>Currency code must be exactly 3 uppercase letters (e.g., USD, EUR, GBP)
     *   <li>Must follow ISO 4217 standard
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 Bad Request - Invalid currency code format
     *   <li>401 Unauthorized - Missing or invalid JWT token
     * </ul>
     *
     * <p>Requirement REQ-6.2.13: Update user's base currency
     *
     * @param authentication Spring Security authentication object
     * @param request request body containing baseCurrency field
     * @return ResponseEntity with updated UserResponse
     * @throws IllegalArgumentException if currency code format is invalid
     */
    @PutMapping("/me/base-currency")
    public ResponseEntity<UserResponse> updateBaseCurrency(
            Authentication authentication,
            @RequestBody
                    Map<String, @Size(min = 3, max = 3) @Pattern(regexp = "[A-Z]{3}") String>
                            request) {

        User user = (User) authentication.getPrincipal();
        String baseCurrency = request.get("baseCurrency");

        log.info("Updating base currency for user {} to: {}", user.getUsername(), baseCurrency);

        UserResponse response = userService.updateBaseCurrency(user.getId(), baseCurrency);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the current user's login password.
     *
     * <p>Allows users to change their login password after verifying their current password. This
     * does NOT affect the master password used for data encryption.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>Content-Type: application/json
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "currentPassword": "OldPassword123",
     * "newPassword": "NewPassword456"
     * }
     * }</pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "message": "Password updated successfully"
     * }
     * }</pre>
     *
     * <p><strong>Validation:</strong>
     *
     * <ul>
     *   <li>Current password must be correct
     *   <li>New password must be at least 8 characters
     *   <li>Current and new passwords must be different (recommended)
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 Bad Request - Validation errors (password too short, etc.)
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>403 Forbidden - Current password is incorrect
     * </ul>
     *
     * <p>Requirement REQ-6.3.16: Password change functionality
     *
     * @param authentication Spring Security authentication object
     * @param request request body with currentPassword and newPassword
     * @return ResponseEntity with success message
     * @throws IllegalArgumentException if current password is incorrect
     */
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> updatePassword(
            Authentication authentication, @Valid @RequestBody PasswordUpdateRequest request) {

        User user = (User) authentication.getPrincipal();

        log.info("Updating password for user: {}", user.getUsername());

        userService.updatePassword(user.getId(), request.currentPassword(), request.newPassword());

        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    /**
     * Updates the current user's master password.
     *
     * <p>The master password is used to derive the encryption key for securing sensitive financial
     * data. This endpoint verifies the current master password and generates a new salt for the new
     * password.
     *
     * <p><strong>Important:</strong> This endpoint only updates the salt. Full re-encryption of all
     * user data is not implemented yet. Users should be aware that changing the master password
     * without re-encryption may make their data inaccessible.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>Content-Type: application/json
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "currentMasterPassword": "OldMasterPassword123",
     * "newMasterPassword": "NewMasterPassword456"
     * }
     * }</pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "message": "Master password updated successfully"
     * }
     * }</pre>
     *
     * <p><strong>Validation:</strong>
     *
     * <ul>
     *   <li>Current master password must be correct
     *   <li>New master password must be at least 8 characters
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 Bad Request - Validation errors (password too short, etc.)
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>403 Forbidden - Current master password is incorrect
     * </ul>
     *
     * <p>Requirement REQ-6.3.16: Password change functionality
     *
     * @param authentication Spring Security authentication object
     * @param request request body with currentMasterPassword and newMasterPassword
     * @return ResponseEntity with success message
     * @throws IllegalArgumentException if current master password is incorrect
     */
    @PutMapping("/me/master-password")
    public ResponseEntity<Map<String, String>> updateMasterPassword(
            Authentication authentication,
            @Valid @RequestBody MasterPasswordUpdateRequest request) {

        User user = (User) authentication.getPrincipal();

        log.info("Updating master password for user: {}", user.getUsername());

        userService.updateMasterPassword(
                user.getId(), request.currentMasterPassword(), request.newMasterPassword());

        return ResponseEntity.ok(Map.of("message", "Master password updated successfully"));
    }

    /**
     * Retrieves the current user's display and locale settings.
     *
     * <p>Returns user preferences for theme, date format, number format, language, and timezone. If
     * settings do not exist yet, default settings are automatically created and returned.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "userId": 1,
     * "theme": "dark",
     * "dateFormat": "MM/DD/YYYY",
     * "numberFormat": "1,234.56",
     * "language": "en",
     * "timezone": "UTC",
     * "createdAt": "2026-01-15T10:30:00",
     * "updatedAt": "2026-02-02T14:30:00"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>403 Forbidden - User does not have permission
     * </ul>
     *
     * <p>Requirement REQ-6.3: User settings retrieval
     *
     * @param authentication Spring Security authentication object
     * @return ResponseEntity with UserSettingsResponse containing all settings
     */
    @GetMapping("/me/settings")
    public ResponseEntity<UserSettingsResponse> getUserSettings(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        log.info("Retrieving settings for user: {}", user.getUsername());

        UserSettingsResponse settings = userSettingsService.getUserSettings(user.getId());
        return ResponseEntity.ok(settings);
    }

    /**
     * Updates the current user's display and locale settings.
     *
     * <p>Allows partial updates - only fields provided in the request body will be updated. All
     * fields are optional. If settings do not exist yet, they are created with defaults before
     * applying updates.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>Content-Type: application/json
     * </ul>
     *
     * <p><strong>Request Body (all fields optional):</strong>
     *
     * <pre>{@code
     * {
     * "theme": "light",
     * "dateFormat": "DD/MM/YYYY",
     * "numberFormat": "1.234,56",
     * "language": "fr",
     * "timezone": "Europe/Paris"
     * }
     * }</pre>
     *
     * <p><strong>Response (200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "userId": 1,
     * "theme": "light",
     * "dateFormat": "DD/MM/YYYY",
     * "numberFormat": "1.234,56",
     * "language": "fr",
     * "timezone": "Europe/Paris",
     * "createdAt": "2026-01-15T10:30:00",
     * "updatedAt": "2026-02-02T15:00:00"
     * }
     * }</pre>
     *
     * <p><strong>Validation Rules:</strong>
     *
     * <ul>
     *   <li><strong>theme</strong>: Must be "dark" or "light"
     *   <li><strong>dateFormat</strong>: Must be one of: "MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD"
     *   <li><strong>numberFormat</strong>: Must be one of: "1,234.56", "1.234,56", "1 234,56"
     *   <li><strong>language</strong>: Must be a 2-letter ISO 639-1 code (e.g., "en", "fr", "es")
     *   <li><strong>timezone</strong>: Any valid IANA timezone identifier (e.g.,
     *       "America/New_York", "Europe/London")
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 Bad Request - Validation errors (invalid format, pattern mismatch)
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>403 Forbidden - User does not have permission
     * </ul>
     *
     * <p>Requirement REQ-6.3: User settings update
     *
     * @param authentication Spring Security authentication object
     * @param request request body with optional settings fields
     * @return ResponseEntity with updated UserSettingsResponse
     */
    @PutMapping("/me/settings")
    public ResponseEntity<UserSettingsResponse> updateUserSettings(
            Authentication authentication, @Valid @RequestBody UserSettingsUpdateRequest request) {

        User user = (User) authentication.getPrincipal();

        log.info("Updating settings for user: {}", user.getUsername());

        UserSettingsResponse settings =
                userSettingsService.updateUserSettings(user.getId(), request);
        return ResponseEntity.ok(settings);
    }

    // -------------------------------------------------------------------------
    // Profile image endpoints
    // -------------------------------------------------------------------------

    /**
     * Uploads a new profile image for the authenticated user.
     *
     * <p>The image is stored as a Base64-encoded data URL inside the database. Accepted formats:
     * JPEG, PNG, GIF, WebP. Maximum file size: 2 MB.
     *
     * <p><strong>Request:</strong>
     *
     * <pre>
     * POST /api/v1/users/me/profile-image
     * Content-Type: multipart/form-data
     * Authorization: Bearer {token}
     *
     * Form field: image (binary file)
     * </pre>
     *
     * <p><strong>Response (200 OK):</strong> Updated {@link UserResponse} with populated {@code
     * profileImage} field.
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 Bad Request – unsupported image type or file exceeds 2 MB
     *   <li>401 Unauthorized – missing or invalid JWT token
     * </ul>
     *
     * @param authentication Spring Security authentication object
     * @param image multipart form field named "image"
     * @return ResponseEntity with updated UserResponse
     */
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadProfileImage(
            Authentication authentication, @RequestParam("image") MultipartFile image) {

        User user = (User) authentication.getPrincipal();
        log.info("Uploading profile image for user: {}", user.getUsername());

        UserResponse response = userService.uploadProfileImage(user.getId(), image);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes the profile image for the authenticated user, reverting to the default avatar.
     *
     * <p><strong>Response (200 OK):</strong> Updated {@link UserResponse} with {@code profileImage}
     * set to {@code null}.
     *
     * @param authentication Spring Security authentication object
     * @return ResponseEntity with updated UserResponse
     */
    @DeleteMapping("/me/profile-image")
    public ResponseEntity<UserResponse> deleteProfileImage(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        log.info("Deleting profile image for user: {}", user.getUsername());

        UserResponse response = userService.deleteProfileImage(user.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Completes the initial onboarding preferences wizard.
     *
     * <p>Saves the user's country, base currency, secondary currency, language, date format, number
     * format, and currency display style in one atomic request, then marks the user as {@code
     * onboardingComplete = true}. Subsequent logins will no longer redirect to the onboarding
     * screen.
     *
     * @param request onboarding preferences
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with {@link UserSettingsResponse} reflecting the new preferences
     */
    @PostMapping("/me/onboarding")
    public ResponseEntity<UserSettingsResponse> completeOnboarding(
            @Valid @RequestBody OnboardingRequest request, Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.info("Completing onboarding for user: {}", user.getUsername());

        UserSettingsResponse response =
                userSettingsService.completeOnboarding(user.getId(), request);

        log.info("Onboarding completed for user: {}", user.getUsername());
        return ResponseEntity.ok(response);
    }
}
