package org.openfinance.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.LoginResponse;
import org.openfinance.entity.SecurityAuditLog.EventType;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountLockedException;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.security.KeyManagementService;
import org.openfinance.security.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for user authentication and login operations.
 *
 * <p>Handles user login with dual password system:
 *
 * <ul>
 *   <li>Login password: Verified with BCrypt
 *   <li>Master password: Derives encryption key for data protection
 * </ul>
 *
 * <p>Security hardening (TASK-15.1.7, TASK-15.1.8):
 *
 * <ul>
 *   <li>Logs all authentication attempts to the security audit log
 *   <li>Locks accounts after {@code application.security.max-failed-attempts} consecutive failures
 *       for a configurable lockout duration
 *   <li>Tracks IP address and User-Agent on each successful login
 * </ul>
 *
 * <h3>SQLite WAL / SQLITE_BUSY_SNAPSHOT mitigation</h3>
 *
 * <p>{@link #login} runs with {@link Propagation#NOT_SUPPORTED} — no outer transaction is held
 * during the method. All writes to the {@code users} table are delegated to {@link
 * UserLoginStateService}, which opens its own fresh transaction for each write. This ensures the
 * connection used for the {@code UPDATE users} statement never holds a stale WAL read-snapshot,
 * eliminating the root cause of {@code SQLITE_BUSY_SNAPSHOT} errors.
 *
 * <p>Requirement REQ-2.1.3: User authentication with JWT token generation
 *
 * <p>Requirement REQ-3.2: Security - master password-derived encryption key
 *
 * @author Open-Finance Development Team
 * @version 2.1
 * @since 2026-01-30
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Default maximum consecutive failed login attempts before account is locked. */
    private static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;

    /** Default lockout duration in minutes. */
    private static final int DEFAULT_LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final KeyManagementService keyManagementService;
    private final JwtService jwtService;
    private final MessageSource messageSource;
    private final SecurityAuditService securityAuditService;
    private final UserLoginStateService userLoginStateService;
    private final EncryptionKeyCache encryptionKeyCache;
    private final EncryptionProperties encryptionProperties;

    /**
     * Maximum failed login attempts before the account is locked. Configurable via {@code
     * application.security.max-failed-attempts}.
     */
    @Value("${application.security.max-failed-attempts:" + DEFAULT_MAX_FAILED_ATTEMPTS + "}")
    private int maxFailedAttempts;

    /**
     * Duration in minutes for which an account remains locked after exceeding the maximum failed
     * attempts. Configurable via {@code application.security.lockout-duration-minutes}.
     */
    @Value("${application.security.lockout-duration-minutes:" + DEFAULT_LOCKOUT_MINUTES + "}")
    private int lockoutDurationMinutes;

    /**
     * Authenticates a user and generates a JWT token with an encrypted encryption key.
     *
     * <p><strong>Authentication Process:</strong>
     *
     * <ol>
     *   <li>Find user by username (simple read — no outer transaction)
     *   <li>Check if account is locked (TASK-15.1.8)
     *   <li>Verify login password with BCrypt
     *   <li>On failure: delegate counter/lock update to {@link UserLoginStateService} (fresh tx)
     *       and log audit event
     *   <li>On success: derive encryption key, generate JWT, delegate user-state update to {@link
     *       UserLoginStateService} (fresh tx), log audit event
     * </ol>
     *
     * <p>Runs with {@link Propagation#NOT_SUPPORTED} so that no outer JDBC transaction/WAL snapshot
     * is held across the method call. Each write ({@code UPDATE users}, {@code INSERT
     * security_audit_log}) opens and commits its own independent transaction, preventing {@code
     * SQLITE_BUSY_SNAPSHOT} errors.
     *
     * @param request login request with username, password, and master password
     * @return LoginResponse with JWT token, user info, and encrypted encryption key
     * @throws BadCredentialsException if username not found or password incorrect
     * @throws AccountLockedException if the account is currently locked
     * @throws IllegalStateException if key derivation or encryption fails
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());
        HttpServletRequest httpRequest = resolveHttpRequest();

        // 1. Find user by username (auto-committed read — no outer tx)
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            // Requirement TASK-15.1.7: Log failed login for unknown user
            securityAuditService.logEvent(
                    null,
                    request.getUsername(),
                    EventType.LOGIN_FAILURE,
                    httpRequest,
                    "User not found");
            log.warn("Login failed: username '{}' not found", request.getUsername());
            throw new BadCredentialsException(
                    messageSource.getMessage(
                            "auth.invalid.credentials", null, LocaleContextHolder.getLocale()));
        }

        // 2. Requirement TASK-15.1.8: Check account lockout
        if (user.isAccountLocked()) {
            securityAuditService.logEvent(
                    user.getId(),
                    user.getUsername(),
                    EventType.LOGIN_FAILURE,
                    httpRequest,
                    "Account locked until " + user.getLockedUntil());
            log.warn(
                    "Login rejected: account '{}' is locked until {}",
                    user.getUsername(),
                    user.getLockedUntil());
            throw new AccountLockedException(
                    "Account is locked. Please try again after " + user.getLockedUntil());
        }

        // 3. Verify login password
        if (!passwordService.validatePassword(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user, httpRequest);
            throw new BadCredentialsException(
                    messageSource.getMessage(
                            "auth.invalid.credentials", null, LocaleContextHolder.getLocale()));
        }

        if (!encryptionProperties.isEnabled()) {
            String token = jwtService.generateToken(user);
            String clientIp = resolveClientIp(httpRequest);
            userLoginStateService.recordLoginSuccess(user.getId(), clientIp);
            securityAuditService.logEvent(
                    user.getId(), user.getUsername(), EventType.LOGIN_SUCCESS, httpRequest, null);

            log.info("Login successful for user: {} (ID: {})", user.getUsername(), user.getId());

            return LoginResponse.builder()
                    .token(token)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .encryptionKey(null)
                    .encryptionEnabled(false)
                    .baseCurrency(user.getBaseCurrency() != null ? user.getBaseCurrency() : "USD")
                    .onboardingComplete(user.isOnboardingComplete())
                    .build();
        }

        if (request.getMasterPassword() == null || request.getMasterPassword().isBlank()) {
            throw new IllegalArgumentException(
                    "Master password is required when encryption is enabled");
        }

        // 4. Derive encryption key from master password + salt
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        char[] masterPasswordChars = request.getMasterPassword().toCharArray();

        SecretKey encryptionKey = null;
        String sessionToken = null;
        try {
            try {
                encryptionKey = keyManagementService.deriveKey(masterPasswordChars, salt);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.error(
                        "Login failed: master password verification error for username '{}': {}",
                        request.getUsername(),
                        e.getMessage());
                handleFailedLogin(user, httpRequest);
                throw new BadCredentialsException("Invalid master password");
            }

            // 5. Cache the key server-side and generate an opaque session token.
            // The raw AES key never leaves the server.
            sessionToken = encryptionKeyCache.createSession(user.getId(), encryptionKey);

            // 6. Generate JWT token
            String token = jwtService.generateToken(user);

            // 7. Requirement TASK-15.1.8: Reset failed attempts on success
            // Requirement TASK-15.1.7: Track last login metadata
            // Delegated to UserLoginStateService — opens a fresh transaction to avoid
            // SQLITE_BUSY_SNAPSHOT (no prior reads on the write connection's snapshot).
            String clientIp = resolveClientIp(httpRequest);
            userLoginStateService.recordLoginSuccess(user.getId(), clientIp);

            // 8. Requirement TASK-15.1.7: Log successful login (runs outside any tx)
            securityAuditService.logEvent(
                    user.getId(), user.getUsername(), EventType.LOGIN_SUCCESS, httpRequest, null);

            log.info("Login successful for user: {} (ID: {})", user.getUsername(), user.getId());

            LoginResponse response =
                    LoginResponse.builder()
                            .token(token)
                            .userId(user.getId())
                            .username(user.getUsername())
                            .encryptionKey(sessionToken)
                            .encryptionEnabled(true)
                            .baseCurrency(
                                    user.getBaseCurrency() != null ? user.getBaseCurrency() : "USD")
                            .onboardingComplete(user.isOnboardingComplete())
                            .build();

            encryptionKeyCache.cacheKey(user.getId(), encryptionKey);

            return response;

        } catch (RuntimeException | Error e) {
            if (sessionToken != null) {
                try {
                    encryptionKeyCache.invalidateFailedSession(sessionToken);
                } catch (RuntimeException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw e;
        } finally {
            // Clear sensitive data from memory
            java.util.Arrays.fill(masterPasswordChars, '\0');
            if (encryptionKey != null) {
                keyManagementService.clearKey(encryptionKey);
            }
        }
    }

    /**
     * Invalidates an encryption session token on logout. The cached encryption key is removed from
     * the session token cache.
     *
     * @param sessionToken the opaque session token to invalidate
     */
    public void logout(String sessionToken) {
        encryptionKeyCache.invalidateSession(sessionToken);
        log.info("Encryption session invalidated on logout");
    }

    /**
     * Records a failed login attempt: increments the counter (locking the account if the threshold
     * is reached) and appends security audit events.
     *
     * <p>The user-state write is delegated to {@link UserLoginStateService} (fresh transaction).
     * Audit events are written outside any transaction via {@link SecurityAuditService}.
     *
     * <p>Requirement TASK-15.1.8: Account lockout after N failed login attempts.
     *
     * @param user the user whose counter should be incremented
     * @param httpRequest the current HTTP request for audit logging
     */
    private void handleFailedLogin(User user, HttpServletRequest httpRequest) {
        // Persist the incremented counter (and possible lock) in a fresh transaction
        int newAttempts =
                userLoginStateService.recordLoginFailure(
                        user.getId(), maxFailedAttempts, lockoutDurationMinutes);

        // Requirement TASK-15.1.7: Log each failure (auto-committed, outside any tx)
        securityAuditService.logEvent(
                user.getId(),
                user.getUsername(),
                EventType.LOGIN_FAILURE,
                httpRequest,
                "Failed attempt " + newAttempts + "/" + maxFailedAttempts);

        if (newAttempts >= maxFailedAttempts) {
            // Requirement TASK-15.1.7: Log lockout event
            securityAuditService.logEvent(
                    user.getId(),
                    user.getUsername(),
                    EventType.ACCOUNT_LOCKED,
                    httpRequest,
                    "Locked for "
                            + lockoutDurationMinutes
                            + " min after "
                            + newAttempts
                            + " failed attempts");
        }

        log.warn(
                "Login failed for user '{}': attempt {}/{}",
                user.getUsername(),
                newAttempts,
                maxFailedAttempts);
    }

    /**
     * Resolves the current {@link HttpServletRequest} from Spring's {@link RequestContextHolder}.
     * Returns {@code null} if no request is in scope (e.g., during tests).
     *
     * @return the current HTTP request, or {@code null}
     */
    private HttpServletRequest resolveHttpRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts the real client IP address from a request, handling reverse-proxy headers.
     *
     * @param request the HTTP request; may be {@code null}
     * @return the resolved IP address, or {@code "unknown"}
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
