package org.openfinance.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.service.UnusualTransactionDetectionService;

/**
 * Unit tests for {@link UnusualTransactionDetectionScheduler}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Startup execution: runs when mode requires it, skipped when not
 *   <li>runDetection: iterates all users, passes correct lookback window
 *   <li>Per-user error isolation: one user failure does not stop processing
 *   <li>Empty user list: no calls to detection service
 *   <li>Default cron constant value
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UnusualTransactionDetectionScheduler Unit Tests")
class UnusualTransactionDetectionSchedulerTest {

    @Mock private UnusualTransactionDetectionService detectionService;

    @Mock private UserRepository userRepository;

    @Mock private SchedulerProperties schedulerProperties;

    @Mock private EncryptionKeyCache encryptionKeyCache;

    @InjectMocks private UnusualTransactionDetectionScheduler scheduler;

    private static final SecretKey TEST_KEY = new SecretKeySpec(new byte[32], "AES");

    private SchedulerProperties.SchedulerConfig schedulerConfig;

    @BeforeEach
    void setUp() {
        schedulerConfig = new SchedulerProperties.SchedulerConfig();
        when(schedulerProperties.getUnusualTransactionDetection()).thenReturn(schedulerConfig);
        when(encryptionKeyCache.getKey(anyLong())).thenReturn(Optional.of(TEST_KEY));
    }

    // ------------------------------------------------------------------
    // Default cron constant
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DEFAULT_CRON is set to 01:00 AM daily")
    void defaultCronIsCorrect() {
        assertThat(UnusualTransactionDetectionScheduler.DEFAULT_CRON).isEqualTo("0 0 1 * * ?");
    }

    @Test
    @DisplayName("LOOKBACK_HOURS is 25")
    void lookbackHoursIsCorrect() {
        assertThat(UnusualTransactionDetectionScheduler.LOOKBACK_HOURS).isEqualTo(25);
    }

    // ------------------------------------------------------------------
    // Startup execution (ApplicationRunner)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Startup execution")
    class StartupExecution {

        @Test
        @DisplayName("Calls runDetection on startup when mode is STARTUP_ONLY")
        void callsRunDetectionOnStartupWhenModeRequiresIt() throws Exception {
            schedulerConfig.setMode(SchedulerProperties.SchedulingMode.STARTUP_ONLY);
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            scheduler.run(null);

            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("Calls runDetection on startup when mode is STARTUP_AND_EVERY_X_HOURS")
        void callsRunDetectionOnStartupAndEveryXHours() throws Exception {
            schedulerConfig.setMode(SchedulerProperties.SchedulingMode.STARTUP_AND_EVERY_X_HOURS);
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            scheduler.run(null);

            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("Does NOT call runDetection on startup when mode is DEFAULT")
        void doesNotCallRunDetectionForDefaultMode() throws Exception {
            schedulerConfig.setMode(SchedulerProperties.SchedulingMode.DEFAULT);

            scheduler.run(null);

            verifyNoInteractions(userRepository, detectionService);
        }
    }

    // ------------------------------------------------------------------
    // runDetection — core scheduling logic
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("runDetection")
    class RunDetection {

        @Test
        @DisplayName("Calls detectAndPersist for each user")
        void callsDetectAndPersistForEachUser() {
            User u1 = User.builder().id(1L).username("alice").build();
            User u2 = User.builder().id(2L).username("bob").build();
            when(userRepository.findAll()).thenReturn(List.of(u1, u2));
            when(detectionService.detectAndPersist(anyLong(), any())).thenReturn(0);

            scheduler.runDetection();

            verify(detectionService).detectAndPersist(eq(1L), any(LocalDateTime.class));
            verify(detectionService).detectAndPersist(eq(2L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Passes a lookback timestamp approximately LOOKBACK_HOURS ago")
        void passesCorrectLookbackTimestamp() {
            User u1 = User.builder().id(1L).username("alice").build();
            when(userRepository.findAll()).thenReturn(List.of(u1));
            when(detectionService.detectAndPersist(anyLong(), any())).thenReturn(0);

            LocalDateTime before =
                    LocalDateTime.now()
                            .minusHours(UnusualTransactionDetectionScheduler.LOOKBACK_HOURS);
            scheduler.runDetection();
            LocalDateTime after =
                    LocalDateTime.now()
                            .minusHours(UnusualTransactionDetectionScheduler.LOOKBACK_HOURS);

            ArgumentCaptor<LocalDateTime> sinceCaptor =
                    ArgumentCaptor.forClass(LocalDateTime.class);
            verify(detectionService).detectAndPersist(eq(1L), sinceCaptor.capture());

            LocalDateTime capturedSince = sinceCaptor.getValue();
            // Allow a 5-second tolerance for test execution time
            assertThat(capturedSince).isBetween(before.minusSeconds(5), after.plusSeconds(5));
        }

        @Test
        @DisplayName("Does nothing when user list is empty")
        void doesNothingWhenNoUsers() {
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            scheduler.runDetection();

            verifyNoInteractions(detectionService);
        }

        @Test
        @DisplayName("Continues processing remaining users after one user throws")
        void continuesAfterOneUserThrows() {
            User u1 = User.builder().id(1L).username("alice").build();
            User u2 = User.builder().id(2L).username("bob").build();
            User u3 = User.builder().id(3L).username("carol").build();
            when(userRepository.findAll()).thenReturn(List.of(u1, u2, u3));

            when(detectionService.detectAndPersist(eq(1L), any())).thenReturn(1);
            when(detectionService.detectAndPersist(eq(2L), any()))
                    .thenThrow(new RuntimeException("Simulated DB failure"));
            when(detectionService.detectAndPersist(eq(3L), any())).thenReturn(2);

            // Must not throw — errors are caught and logged
            scheduler.runDetection();

            verify(detectionService).detectAndPersist(eq(1L), any());
            verify(detectionService).detectAndPersist(eq(2L), any());
            verify(detectionService).detectAndPersist(eq(3L), any());
        }

        @Test
        @DisplayName("Handles a single user with multiple detections correctly")
        void handlesSingleUserWithMultipleDetections() {
            User u1 = User.builder().id(1L).username("alice").build();
            when(userRepository.findAll()).thenReturn(List.of(u1));
            when(detectionService.detectAndPersist(eq(1L), any())).thenReturn(3);

            scheduler.runDetection();

            verify(detectionService, times(1)).detectAndPersist(eq(1L), any());
        }

        @Test
        @DisplayName("Skips users without cached encryption keys")
        void skipsUsersWithoutCachedEncryptionKeys() {
            User skippedUser = User.builder().id(1L).username("alice").build();
            User processedUser = User.builder().id(2L).username("bob").build();
            when(userRepository.findAll()).thenReturn(List.of(skippedUser, processedUser));
            when(encryptionKeyCache.getKey(1L)).thenReturn(Optional.empty());
            when(encryptionKeyCache.getKey(2L)).thenReturn(Optional.of(TEST_KEY));

            scheduler.runDetection();

            verify(detectionService, never()).detectAndPersist(eq(1L), any());
            verify(detectionService).detectAndPersist(eq(2L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Sets EncryptionContext from cache and clears it after processing")
        void setsEncryptionContextFromCacheAndClearsItAfterProcessing() {
            User user = User.builder().id(1L).username("alice").build();
            when(userRepository.findAll()).thenReturn(List.of(user));
            when(detectionService.detectAndPersist(eq(1L), any()))
                    .thenAnswer(
                            invocation -> {
                                assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY);
                                return 0;
                            });

            scheduler.runDetection();

            assertThat(EncryptionContext.getKey()).isNull();
            verify(detectionService).detectAndPersist(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Clears EncryptionContext after user processing fails")
        void clearsEncryptionContextAfterUserProcessingFails() {
            User user = User.builder().id(1L).username("alice").build();
            when(userRepository.findAll()).thenReturn(List.of(user));
            when(detectionService.detectAndPersist(eq(1L), any()))
                    .thenAnswer(
                            invocation -> {
                                assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY);
                                throw new RuntimeException("boom");
                            });

            scheduler.runDetection();

            assertThat(EncryptionContext.getKey()).isNull();
        }
    }
}
