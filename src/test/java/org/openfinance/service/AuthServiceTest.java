package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.LoginResponse;
import org.openfinance.entity.SecurityAuditLog.EventType;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountLockedException;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.KeyManagementService;
import org.openfinance.security.PasswordService;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for AuthService.
 *
 * <p>Tests user authentication, JWT token generation, and encryption key handling. Updated to cover
 * security audit logging and account lockout logic.
 *
 * @author Open-Finance Development Team
 * @since 2026-03-20
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordService passwordService;
    @Mock private KeyManagementService keyManagementService;
    @Mock private JwtService jwtService;
    @Mock private MessageSource messageSource;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private UserLoginStateService userLoginStateService;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;
    private byte[] testSalt;
    private SecretKey testEncryptionKey;

    @BeforeEach
    void setUp() {
        testSalt = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        testEncryptionKey = new SecretKeySpec(new byte[32], 0, 32, "AES");

        testUser =
                User.builder()
                        .id(1L)
                        .username("john_doe")
                        .email("john@example.com")
                        .passwordHash("$2a$10$hashedPassword")
                        .masterPasswordSalt(Base64.getEncoder().encodeToString(testSalt))
                        .failedLoginAttempts(0)
                        .build();

        loginRequest =
                LoginRequest.builder()
                        .username("john_doe")
                        .password("Password123!")
                        .masterPassword("masterPass456")
                        .build();

        ReflectionTestUtils.setField(authService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockoutDurationMinutes", 15);

        lenient()
                .when(messageSource.getMessage(anyString(), any(), any()))
                .thenReturn("Invalid username or password");
    }

    @Test
    @DisplayName("Should successfully authenticate user with valid credentials")
    void shouldSuccessfullyAuthenticateUserWithValidCredentials() {
        // Given
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(testUser));
        when(passwordService.validatePassword("Password123!", testUser.getPasswordHash()))
                .thenReturn(true);
        when(keyManagementService.deriveKey(any(char[].class), eq(testSalt)))
                .thenReturn(testEncryptionKey);
        when(jwtService.generateToken(testUser)).thenReturn("jwt.token.here");

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt.token.here");
        assertThat(response.getUserId()).isEqualTo(1L);
        String expectedKey = Base64.getEncoder().encodeToString(testEncryptionKey.getEncoded());
        assertThat(response.getEncryptionKey()).isEqualTo(expectedKey);

        verify(userLoginStateService).recordLoginSuccess(eq(1L), anyString());
        verify(securityAuditService)
                .logEvent(eq(1L), eq("john_doe"), eq(EventType.LOGIN_SUCCESS), any(), isNull());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when username not found and log event")
    void shouldThrowExceptionWhenUsernameNotFound() {
        // Given
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(securityAuditService)
                .logEvent(
                        isNull(),
                        eq("john_doe"),
                        eq(EventType.LOGIN_FAILURE),
                        any(),
                        eq("User not found"));
        verifyNoInteractions(userLoginStateService);
    }

    @Test
    @DisplayName("Should throw AccountLockedException when account is locked")
    void shouldThrowExceptionWhenAccountLocked() {
        // Given
        testUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AccountLockedException.class);

        verify(securityAuditService)
                .logEvent(
                        eq(1L),
                        eq("john_doe"),
                        eq(EventType.LOGIN_FAILURE),
                        any(),
                        contains("Account locked"));
        verifyNoInteractions(passwordService);
        verifyNoInteractions(userLoginStateService);
    }

    @Test
    @DisplayName("Should handle failed login attempt and increment counter")
    void shouldHandleFailedLoginAttempt() {
        // Given
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(testUser));
        when(passwordService.validatePassword("Password123!", testUser.getPasswordHash()))
                .thenReturn(false);
        when(userLoginStateService.recordLoginFailure(1L, 5, 15)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userLoginStateService).recordLoginFailure(1L, 5, 15);
        verify(securityAuditService)
                .logEvent(
                        eq(1L),
                        eq("john_doe"),
                        eq(EventType.LOGIN_FAILURE),
                        any(),
                        contains("Failed attempt 1/5"));
    }

    @Test
    @DisplayName("Should log ACCOUNT_LOCKED event when lockout threshold reached")
    void shouldLogLockoutEvent() {
        // Given
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(testUser));
        when(passwordService.validatePassword("Password123!", testUser.getPasswordHash()))
                .thenReturn(false);
        when(userLoginStateService.recordLoginFailure(1L, 5, 15)).thenReturn(5);

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(securityAuditService)
                .logEvent(
                        eq(1L),
                        eq("john_doe"),
                        eq(EventType.ACCOUNT_LOCKED),
                        any(),
                        contains("Locked for 15 min"));
    }

    @Test
    @DisplayName("Should handle master password failure as a failed login attempt")
    void shouldHandleMasterPasswordFailure() {
        // Given
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(testUser));
        when(passwordService.validatePassword("Password123!", testUser.getPasswordHash()))
                .thenReturn(true);
        when(keyManagementService.deriveKey(any(), any()))
                .thenThrow(new IllegalStateException("Key derivation failed"));
        when(userLoginStateService.recordLoginFailure(1L, 5, 15)).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid master password");

        verify(userLoginStateService).recordLoginFailure(1L, 5, 15);
    }
}
