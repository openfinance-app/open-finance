package org.openfinance.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import org.openfinance.service.NetWorthService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NetWorthSnapshotScheduler — Encryption Key Cache Tests")
class NetWorthSnapshotSchedulerTest {

    @Mock private NetWorthService netWorthService;
    @Mock private UserRepository userRepository;
    @Mock private SchedulerProperties schedulerProperties;
    @Mock private EncryptionKeyCache encryptionKeyCache;

    @InjectMocks private NetWorthSnapshotScheduler scheduler;

    private static final SecretKey TEST_KEY = new SecretKeySpec(new byte[32], "AES");
    private SchedulerProperties.SchedulerConfig schedulerConfig;

    @BeforeEach
    void setUp() {
        schedulerConfig = new SchedulerProperties.SchedulerConfig();
        when(schedulerProperties.getNetWorthSnapshot()).thenReturn(schedulerConfig);
        when(encryptionKeyCache.getKey(anyLong())).thenReturn(Optional.of(TEST_KEY));
    }

    @Test
    @DisplayName("DEFAULT_CRON is set to 00:05 AM daily")
    void defaultCronIsCorrect() {
        assertThat(NetWorthSnapshotScheduler.DEFAULT_CRON).isEqualTo("0 5 0 * * ?");
    }

    @Nested
    @DisplayName("Startup execution")
    class StartupExecution {

        @Test
        @DisplayName("Runs snapshot on startup when mode is STARTUP_ONLY")
        void runsOnStartupWhenModeRequiresIt() {
            schedulerConfig.setMode(SchedulerProperties.SchedulingMode.STARTUP_ONLY);
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            scheduler.run(null);

            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("Does not run on startup when mode is DEFAULT")
        void skipsStartupWhenModeIsDefault() {
            schedulerConfig.setMode(SchedulerProperties.SchedulingMode.DEFAULT);

            scheduler.run(null);

            verify(userRepository, never()).findAll();
        }
    }

    @Nested
    @DisplayName("Encryption key cache behavior")
    class EncryptionKeyCacheBehavior {

        @Test
        @DisplayName("Skips users without cached encryption keys")
        void skipsUsersWithoutCachedEncryptionKeys() {
            User skippedUser = User.builder().id(1L).username("alice").build();
            User processedUser = User.builder().id(2L).username("bob").baseCurrency("USD").build();
            when(userRepository.findAll()).thenReturn(List.of(skippedUser, processedUser));
            when(encryptionKeyCache.getKey(1L)).thenReturn(Optional.empty());
            when(encryptionKeyCache.getKey(2L)).thenReturn(Optional.of(TEST_KEY));

            scheduler.createDailyNetWorthSnapshots();

            verify(netWorthService, never()).saveNetWorthSnapshot(eq(1L), any(), anyString());
            verify(netWorthService).saveNetWorthSnapshot(eq(2L), any(), eq("USD"));
        }

        @Test
        @DisplayName("Sets EncryptionContext from cache and clears it after processing")
        void setsEncryptionContextFromCacheAndClearsItAfterProcessing() {
            User user = User.builder().id(1L).username("alice").baseCurrency("EUR").build();
            when(userRepository.findAll()).thenReturn(List.of(user));
            doAnswer(
                            invocation -> {
                                assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY);
                                return null;
                            })
                    .when(netWorthService)
                    .saveNetWorthSnapshot(eq(1L), any(), anyString());

            scheduler.createDailyNetWorthSnapshots();

            assertThat(EncryptionContext.getKey()).isNull();
            verify(netWorthService).saveNetWorthSnapshot(eq(1L), any(), eq("EUR"));
        }

        @Test
        @DisplayName("Clears EncryptionContext after user processing fails")
        void clearsEncryptionContextAfterUserProcessingFails() {
            User user = User.builder().id(1L).username("alice").baseCurrency("USD").build();
            when(userRepository.findAll()).thenReturn(List.of(user));
            doAnswer(
                            invocation -> {
                                assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY);
                                throw new RuntimeException("boom");
                            })
                    .when(netWorthService)
                    .saveNetWorthSnapshot(eq(1L), any(), anyString());

            scheduler.createDailyNetWorthSnapshots();

            assertThat(EncryptionContext.getKey()).isNull();
        }

        @Test
        @DisplayName("Defaults to USD currency when user has no baseCurrency")
        void defaultsToUsdWhenNoBaseCurrency() {
            User user = User.builder().id(1L).username("alice").build();
            when(userRepository.findAll()).thenReturn(List.of(user));

            scheduler.createDailyNetWorthSnapshots();

            verify(netWorthService).saveNetWorthSnapshot(eq(1L), any(), eq("USD"));
        }

        @Test
        @DisplayName("Continues processing remaining users after one fails")
        void continuesAfterOneUserFails() {
            User user1 = User.builder().id(1L).username("alice").baseCurrency("USD").build();
            User user2 = User.builder().id(2L).username("bob").baseCurrency("EUR").build();
            when(userRepository.findAll()).thenReturn(List.of(user1, user2));
            doThrow(new RuntimeException("boom"))
                    .when(netWorthService)
                    .saveNetWorthSnapshot(eq(1L), any(), anyString());

            scheduler.createDailyNetWorthSnapshots();

            verify(netWorthService).saveNetWorthSnapshot(eq(2L), any(), eq("EUR"));
        }
    }
}
