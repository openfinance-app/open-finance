package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.UpdateProfileRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.dto.UserResponse;
import org.openfinance.entity.User;
import org.openfinance.exception.DuplicateUserException;
import org.openfinance.mapper.UserMapper;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.KeyManagementService;
import org.openfinance.security.PasswordService;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>Tests user registration logic including validation, password hashing, salt generation, and
 * error handling.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @Mock private PasswordService passwordService;

    @Mock private KeyManagementService keyManagementService;

    @Mock private UserMapper userMapper;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private UserService userService;

    private UserRegistrationRequest validRequest;
    private User savedUser;
    private UserResponse expectedResponse;

    @BeforeEach
    void setUp() {
        // Valid registration request
        validRequest =
                UserRegistrationRequest.builder()
                        .username("john_doe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        // Saved user entity (simulating database save)
        savedUser =
                User.builder()
                        .id(1L)
                        .username("john_doe")
                        .email("john@example.com")
                        .passwordHash("$2a$10$hashedPasswordValue")
                        .masterPasswordSalt("c29tZVNhbHRWYWx1ZQ==")
                        .createdAt(LocalDateTime.now())
                        .build();

        // Expected response
        expectedResponse =
                UserResponse.builder()
                        .id(1L)
                        .username("john_doe")
                        .email("john@example.com")
                        .createdAt(savedUser.getCreatedAt())
                        .build();
    }

    @Test
    @DisplayName("Should successfully register user with valid credentials")
    void shouldRegisterUserWithValidCredentials() {
        // Arrange
        when(userRepository.existsByUsername(validRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(passwordService.hashPassword(validRequest.getPassword()))
                .thenReturn("$2a$10$hashedPasswordValue");
        when(keyManagementService.generateSalt()).thenReturn("someSaltValue".getBytes());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        // Act
        UserResponse response = userService.registerUser(validRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("john_doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getCreatedAt()).isNotNull();

        // Verify interactions
        verify(userRepository).existsByUsername("john_doe");
        verify(userRepository).existsByEmail("john@example.com");
        verify(passwordService).hashPassword("Password123!");
        verify(keyManagementService).generateSalt();
        verify(userRepository).save(any(User.class));
        verify(userMapper).toResponse(savedUser);
    }

    @Test
    @DisplayName("Should hash password with BCrypt before saving")
    void shouldHashPasswordBeforeSaving() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$10$hashedPasswordValue");
        when(keyManagementService.generateSalt()).thenReturn("someSaltValue".getBytes());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.registerUser(validRequest);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.getPasswordHash()).isEqualTo("$2a$10$hashedPasswordValue");
        assertThat(capturedUser.getPasswordHash()).isNotEqualTo(validRequest.getPassword());
    }

    @Test
    @DisplayName("Should generate and store salt for master password")
    void shouldGenerateAndStoreSaltForMasterPassword() {
        // Arrange
        byte[] saltBytes = "randomSalt123456".getBytes();
        String expectedSaltBase64 = Base64.getEncoder().encodeToString(saltBytes);

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$10$hashedPasswordValue");
        when(keyManagementService.generateSalt()).thenReturn(saltBytes);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.registerUser(validRequest);

        // Assert
        verify(keyManagementService).generateSalt();
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.getMasterPasswordSalt()).isEqualTo(expectedSaltBase64);
    }

    @Test
    @DisplayName("Should throw DuplicateUserException when username exists")
    void shouldThrowExceptionWhenUsernameExists() {
        // Arrange
        when(userRepository.existsByUsername(validRequest.getUsername())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.registerUser(validRequest))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("Username already exists");

        // Verify no further processing occurred
        verify(userRepository).existsByUsername("john_doe");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(passwordService, never()).hashPassword(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateUserException when email exists")
    void shouldThrowExceptionWhenEmailExists() {
        // Arrange
        when(userRepository.existsByUsername(validRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.registerUser(validRequest))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("Email already exists");

        // Verify username check occurred but processing stopped
        verify(userRepository).existsByUsername("john_doe");
        verify(userRepository).existsByEmail("john@example.com");
        verify(passwordService, never()).hashPassword(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should preserve username exactly as provided")
    void shouldPreserveUsernameCase() {
        // Arrange
        UserRegistrationRequest mixedCaseRequest =
                UserRegistrationRequest.builder()
                        .username("JohnDoe")
                        .email("john@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$10$hashedPasswordValue");
        when(keyManagementService.generateSalt()).thenReturn("someSaltValue".getBytes());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.registerUser(mixedCaseRequest);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.getUsername()).isEqualTo("JohnDoe");
    }

    @Test
    @DisplayName("Should save all required user fields")
    void shouldSaveAllRequiredFields() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$10$hashedPasswordValue");
        when(keyManagementService.generateSalt()).thenReturn("someSaltValue".getBytes());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.registerUser(validRequest);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.getUsername()).isEqualTo("john_doe");
        assertThat(capturedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(capturedUser.getPasswordHash()).isNotNull();
        assertThat(capturedUser.getMasterPasswordSalt()).isNotNull();
    }

    @Test
    @DisplayName("Should check username uniqueness before email")
    void shouldCheckUsernameBeforeEmail() {
        // Arrange
        when(userRepository.existsByUsername(validRequest.getUsername())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.registerUser(validRequest))
                .isInstanceOf(DuplicateUserException.class);

        // Verify email check never occurred
        verify(userRepository).existsByUsername("john_doe");
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("Should not include sensitive data in response")
    void shouldNotIncludeSensitiveDataInResponse() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$10$hashedPasswordValue");
        when(keyManagementService.generateSalt()).thenReturn("someSaltValue".getBytes());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        // Act
        UserResponse response = userService.registerUser(validRequest);

        // Assert - UserResponse should not have password fields
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUsername()).isNotNull();
        assertThat(response.getEmail()).isNotNull();

        // Verify mapper was called to convert User to safe response
        verify(userMapper).toResponse(savedUser);
    }

    @Test
    @DisplayName("Should handle repository save errors gracefully")
    void shouldHandleRepositorySaveErrors() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$10$hashedPasswordValue");
        when(keyManagementService.generateSalt()).thenReturn("someSaltValue".getBytes());
        when(userRepository.save(any(User.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThatThrownBy(() -> userService.registerUser(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");
    }

    // Profile Management Tests

    @Test
    @DisplayName("Should successfully get user profile by ID")
    void shouldGetUserProfileById() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        // Act
        UserResponse response = userService.getUserProfile(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("john_doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");

        verify(userRepository).findById(1L);
        verify(userMapper).toResponse(savedUser);
    }

    @Test
    @DisplayName("Should throw exception when getting profile for non-existent user")
    void shouldThrowExceptionWhenGettingNonExistentProfile() {
        // Arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserProfile(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).findById(999L);
        verify(userMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("Should successfully update email when current password is correct")
    void shouldUpdateEmailWithCorrectPassword() {
        // Arrange
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .email("newemail@example.com")
                        .currentPassword("Password123!")
                        .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordService.validatePassword("Password123!", savedUser.getPasswordHash()))
                .thenReturn(true);
        when(userRepository.existsByEmail("newemail@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        // Act
        UserResponse response = userService.updateProfile(1L, request);

        // Assert
        assertThat(response).isNotNull();
        verify(userRepository).findById(1L);
        verify(passwordService).validatePassword("Password123!", savedUser.getPasswordHash());
        verify(userRepository).existsByEmail("newemail@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully update password when current password is correct")
    void shouldUpdatePasswordWithCorrectPassword() {
        // Arrange
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .currentPassword("Password123!")
                        .newPassword("newSecurePassword456")
                        .build();

        String originalPasswordHash = savedUser.getPasswordHash(); // Capture before modification

        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordService.validatePassword("Password123!", originalPasswordHash))
                .thenReturn(true);
        when(passwordService.hashPassword("newSecurePassword456"))
                .thenReturn("$2a$10$newHashedPasswordValue");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        // Act
        UserResponse response = userService.updateProfile(1L, request);

        // Assert
        assertThat(response).isNotNull();
        verify(userRepository).findById(1L);
        verify(passwordService).validatePassword("Password123!", originalPasswordHash);
        verify(passwordService).hashPassword("newSecurePassword456");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should update both email and password when both provided")
    void shouldUpdateBothEmailAndPassword() {
        // Arrange
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .email("newemail@example.com")
                        .currentPassword("Password123!")
                        .newPassword("newSecurePassword456")
                        .build();

        String originalPasswordHash = savedUser.getPasswordHash(); // Capture before modification

        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordService.validatePassword("Password123!", originalPasswordHash))
                .thenReturn(true);
        when(userRepository.existsByEmail("newemail@example.com")).thenReturn(false);
        when(passwordService.hashPassword("newSecurePassword456"))
                .thenReturn("$2a$10$newHashedPasswordValue");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        // Act
        UserResponse response = userService.updateProfile(1L, request);

        // Assert
        assertThat(response).isNotNull();
        verify(userRepository).findById(1L);
        verify(passwordService).validatePassword("Password123!", originalPasswordHash);
        verify(userRepository).existsByEmail("newemail@example.com");
        verify(passwordService).hashPassword("newSecurePassword456");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when current password is incorrect")
    void shouldThrowExceptionWhenCurrentPasswordIncorrect() {
        // Arrange
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .email("newemail@example.com")
                        .currentPassword("wrongPassword")
                        .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordService.validatePassword("wrongPassword", savedUser.getPasswordHash()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid current password");

        verify(userRepository).findById(1L);
        verify(passwordService).validatePassword("wrongPassword", savedUser.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when new email already exists")
    void shouldThrowExceptionWhenNewEmailExists() {
        // Arrange
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .email("existing@example.com")
                        .currentPassword("Password123!")
                        .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordService.validatePassword("Password123!", savedUser.getPasswordHash()))
                .thenReturn(true);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository).findById(1L);
        verify(passwordService).validatePassword("Password123!", savedUser.getPasswordHash());
        verify(userRepository).existsByEmail("existing@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not update when email is same as current")
    void shouldNotUpdateWhenEmailSameAsCurrent() {
        // Arrange
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .email("john@example.com") // Same as current
                        .currentPassword("Password123!")
                        .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordService.validatePassword("Password123!", savedUser.getPasswordHash()))
                .thenReturn(true);
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        // Act
        UserResponse response = userService.updateProfile(1L, request);

        // Assert
        assertThat(response).isNotNull();
        verify(userRepository).findById(1L);
        verify(passwordService).validatePassword("Password123!", savedUser.getPasswordHash());
        verify(userRepository, never())
                .existsByEmail(anyString()); // Should not check email uniqueness
        verify(userRepository, never()).save(any()); // Should not save since no changes
    }

    @Test
    @DisplayName("Should throw exception when updating profile for non-existent user")
    void shouldThrowExceptionWhenUpdatingNonExistentUser() {
        // Arrange
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .currentPassword("Password123!")
                        .newPassword("Password123!")
                        .build();

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateProfile(999L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).findById(999L);
        verify(passwordService, never()).validatePassword(anyString(), anyString());
        verify(userRepository, never()).save(any());
    }
}
