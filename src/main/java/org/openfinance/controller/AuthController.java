package org.openfinance.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.LoginResponse;
import org.openfinance.dto.UpdateProfileRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.dto.UserResponse;
import org.openfinance.entity.User;
import org.openfinance.service.AuthService;
import org.openfinance.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * <p>
 * Provides endpoints for user registration, login, and authentication-related
 * operations. All
 * endpoints are prefixed with {@code /api/v1/auth}.
 *
 * <p>
 * <strong>Endpoints:</strong>
 *
 * <ul>
 * <li>POST /api/v1/auth/register - Register new user account
 * <li>POST /api/v1/auth/login - Authenticate user and return JWT token
 * <li>GET /api/v1/auth/profile - Get current user profile (requires
 * authentication)
 * <li>PUT /api/v1/auth/profile - Update current user profile (requires
 * authentication)
 * </ul>
 *
 * <p>
 * Requirement REQ-2.1.1: User registration endpoint
 *
 * <p>
 * Requirement REQ-2.1.3: User authentication with JWT
 *
 * <p>
 * Requirement REQ-2.1.5: User profile management
 *
 * @see UserService
 * @see UserRegistrationRequest
 * @see UserResponse
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final AuthService authService;

    /**
     * Registers a new user account.
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "username": "john_doe",
     * "email": "john@example.com",
     * "password": "securePassword123",
     * "masterPassword": "masterPassword456"
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "username": "john_doe",
     * "email": "john@example.com",
     * "createdAt": "2026-01-30T10:30:00",
     * "updatedAt": null
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     * <li>HTTP 400 Bad Request - Validation errors (missing fields, invalid format)
     * <li>HTTP 409 Conflict - Username or email already exists
     * <li>HTTP 500 Internal Server Error - Unexpected server error
     * </ul>
     *
     * <p><strong>Security:</strong>
     *
     * <ul>
     * <li>Login password is hashed with BCrypt before storage
     * <li>Master password is used to generate encryption salt (not stored)
     * <li>Response excludes all sensitive fields
     * </ul>
     *
     * @param request registration request with user credentials
     * @return HTTP 201 Created with UserResponse containing non-sensitive user data
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody UserRegistrationRequest request) {
        log.info("Received registration request for username: {}", request.getUsername());

        UserResponse response = userService.registerUser(request);

        log.info("User registration successful for username: {}", response.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user and returns JWT token.
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "username": "john_doe",
     * "password": "securePassword123",
     * "masterPassword": "masterPassword456"
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "token": "eyJhbGciOiJIUzI1NiIs...",
     * "userId": 1,
     * "username": "john_doe",
     * "encryptionKey": "base64EncodedEncryptedKey..."
     * }
     * }</pre>
     *
     * <p><strong>Authentication Flow:</strong>
     *
     * <ol>
     * <li>Verify username and login password with BCrypt
     * <li>Derive encryption key from master password
     * <li>Generate JWT token with 24-hour expiration
     * <li>Return token and encrypted encryption key
     * </ol>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     * <li>HTTP 400 Bad Request - Validation errors (missing fields)
     * <li>HTTP 401 Unauthorized - Invalid credentials
     * <li>HTTP 500 Internal Server Error - Unexpected server error
     * </ul>
     *
     * <p><strong>Security:</strong>
     *
     * <ul>
     * <li>JWT token expires after 24 hours
     * <li>Encryption key is encrypted before transport
     * <li>Client should store token in localStorage and key in sessionStorage
     * <li>Include token in Authorization header: "Bearer {token}"
     * </ul>
     *
     * @param request login request with username, password, and master password
     * @return HTTP 200 OK with LoginResponse containing JWT token and encrypted
     * encryption key
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for username: {}", request.getUsername());

        LoginResponse response = authService.login(request);

        log.info("User login successful for username: {}", response.getUsername());

        return ResponseEntity.ok(response);
    }

    /**
     * Logs out the current user by invalidating their encryption session token.
     * The session token is read from the {@code X-Encryption-Session} header.
     *
     * @param request the HTTP request containing the session token header
     * @return HTTP 204 No Content on success
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String sessionToken = request.getHeader("X-Encryption-Session");
        if (sessionToken != null && !sessionToken.isBlank()) {
            authService.logout(sessionToken);
        }
        log.info("User logged out");
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the current authenticated user's profile.
     *
     * <p><strong>Authentication Required:</strong> Must include valid JWT token in
     * Authorization
     * header: "Bearer {token}"
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "username": "john_doe",
     * "email": "john@example.com",
     * "createdAt": "2026-01-30T10:30:00",
     * "updatedAt": "2026-01-31T14:20:00"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     * <li>HTTP 401 Unauthorized - Missing or invalid JWT token
     * <li>HTTP 404 Not Found - User not found
     * <li>HTTP 500 Internal Server Error - Unexpected server error
     * </ul>
     *
     * @param authentication Spring Security authentication object (injected)
     * @return HTTP 200 OK with UserResponse containing user profile data
     */
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        log.info("Received profile retrieval request");

        User user = (User) authentication.getPrincipal();
        UserResponse response = userService.getUserProfile(user.getId());

        log.info("Profile retrieval successful for user: {}", user.getUsername());

        return ResponseEntity.ok(response);
    }

    /**
     * Updates the current authenticated user's profile.
     *
     * <p><strong>Authentication Required:</strong> Must include valid JWT token in
     * Authorization
     * header: "Bearer {token}"
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "email": "newemail@example.com", // Optional: update email
     * "currentPassword": "currentPassword123", // Required: for verification
     * "newPassword": "newPassword456" // Optional: update password
     * }
     * }</pre>
     *
     * <p><strong>Update Rules:</strong>
     *
     * <ul>
     * <li>Current password verification required for all changes
     * <li>Email must be unique across system
     * <li>New password must be at least 8 characters
     * <li>At least one of email or newPassword must be provided
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "username": "john_doe",
     * "email": "newemail@example.com",
     * "createdAt": "2026-01-30T10:30:00",
     * "updatedAt": "2026-01-31T14:25:00"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     * <li>HTTP 400 Bad Request - Validation errors (invalid email format, password
     * too short)
     * <li>HTTP 401 Unauthorized - Missing/invalid JWT token or incorrect current
     * password
     * <li>HTTP 404 Not Found - User not found
     * <li>HTTP 409 Conflict - Email already exists
     * <li>HTTP 500 Internal Server Error - Unexpected server error
     * </ul>
     *
     * <p><strong>Security:</strong>
     *
     * <ul>
     * <li>Current password is verified with BCrypt
     * <li>New password is hashed with BCrypt before storage
     * <li>Response excludes all sensitive fields
     * </ul>
     *
     * @param request update profile request with optional email/password and
     * required current
     * password
     * @param authentication Spring Security authentication object (injected)
     * @return HTTP 200 OK with UserResponse containing updated profile data
     */
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        log.info("Received profile update request");

        User user = (User) authentication.getPrincipal();
        UserResponse response = userService.updateProfile(user.getId(), request);

        log.info("Profile update successful for user: {}", user.getUsername());

        return ResponseEntity.ok(response);
    }
}
