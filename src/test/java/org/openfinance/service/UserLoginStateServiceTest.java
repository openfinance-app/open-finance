package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserLoginStateService Unit Tests")
class UserLoginStateServiceTest {

    @Mock private UserRepository userRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private UserLoginStateService userLoginStateService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser =
                User.builder()
                        .id(1L)
                        .username("testuser")
                        .failedLoginAttempts(2)
                        .lockedUntil(LocalDateTime.now().minusMinutes(5))
                        .build();
    }

    @Test
    @DisplayName("recordLoginSuccess: Should reset counter and set metadata")
    void recordLoginSuccess_ShouldResetCounterAndSetMetadata() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        String ip = "192.168.1.1";

        // When
        userLoginStateService.recordLoginSuccess(1L, ip);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getFailedLoginAttempts()).isZero();
        assertThat(savedUser.getLockedUntil()).isNull();
        assertThat(savedUser.getLastLoginIp()).isEqualTo(ip);
        assertThat(savedUser.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("recordLoginSuccess: Should throw IllegalArgumentException when user not found")
    void recordLoginSuccess_ShouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userLoginStateService.recordLoginSuccess(1L, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("recordLoginFailure: Should increment counter and return new count")
    void recordLoginFailure_ShouldIncrementCounter() {
        // Given
        testUser.setFailedLoginAttempts(1);
        testUser.setLockedUntil(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        int result = userLoginStateService.recordLoginFailure(1L, 5, 15);

        // Then
        assertThat(result).isEqualTo(2);
        verify(userRepository).save(testUser);
        assertThat(testUser.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(testUser.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("recordLoginFailure: Should lock account when threshold reached")
    void recordLoginFailure_ShouldLockAccountWhenThresholdReached() {
        // Given
        testUser.setFailedLoginAttempts(4);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        int result = userLoginStateService.recordLoginFailure(1L, 5, 15);

        // Then
        assertThat(result).isEqualTo(5);
        verify(userRepository).save(testUser);
        assertThat(testUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(testUser.getLockedUntil()).isNotNull();
        assertThat(testUser.getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("recordLoginFailure: Should throw IllegalArgumentException when user not found")
    void recordLoginFailure_ShouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userLoginStateService.recordLoginFailure(1L, 5, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }
}
