package org.openfinance.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
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
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.service.RecurringTransactionService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecurringTransactionScheduler — Encryption Key Cache Tests")
class RecurringTransactionSchedulerTest {

    @Mock
    private RecurringTransactionService recurringTransactionService;
    @Mock
    private SchedulerProperties schedulerProperties;
    @Mock
    private EncryptionKeyCache encryptionKeyCache;

    @InjectMocks
    private RecurringTransactionScheduler scheduler;

    private static final SecretKey TEST_KEY_1 = new SecretKeySpec(new byte[32], "AES");
    private static final SecretKey TEST_KEY_2 = new SecretKeySpec(
            "01234567890123456789012345678901".getBytes(), "AES");

    private SchedulerProperties.SchedulerConfig schedulerConfig;

    @BeforeEach
    void setUp() {
        schedulerConfig = new SchedulerProperties.SchedulerConfig();
        when(schedulerProperties.getRecurringTransactions()).thenReturn(schedulerConfig);
    }

    @Test
    @DisplayName("DEFAULT_CRON is set to midnight daily")
    void defaultCronIsCorrect() {
        assertThat(RecurringTransactionScheduler.DEFAULT_CRON).isEqualTo("0 0 0 * * ?");
    }

    @Nested
    @DisplayName("Startup execution")
    class StartupExecution {

        @Test
        @DisplayName("Runs processing on startup when mode is STARTUP_ONLY")
        void runsOnStartupWhenModeRequiresIt() {
            schedulerConfig.setMode(SchedulerProperties.SchedulingMode.STARTUP_ONLY);
            when(encryptionKeyCache.getCachedUserIds()).thenReturn(Collections.emptySet());

            scheduler.run(null);

            verify(encryptionKeyCache).getCachedUserIds();
        }

        @Test
        @DisplayName("Skips startup when mode is DEFAULT")
        void skipsStartupWhenModeIsDefault() {
            schedulerConfig.setMode(SchedulerProperties.SchedulingMode.DEFAULT);

            scheduler.run(null);

            verify(encryptionKeyCache, never()).getCachedUserIds();
        }
    }

    @Nested
    @DisplayName("Encryption key cache behavior")
    class EncryptionKeyCacheBehavior {

        @Test
        @DisplayName("Only processes users with cached encryption keys")
        void onlyProcessesUsersWithCachedKeys() {
            when(encryptionKeyCache.getCachedUserIds()).thenReturn(Set.of(1L, 2L));
            when(encryptionKeyCache.getKey(1L)).thenReturn(Optional.of(TEST_KEY_1));
            when(encryptionKeyCache.getKey(2L)).thenReturn(Optional.empty());
            when(recurringTransactionService.processRecurringTransactionsForUser(1L))
                    .thenReturn(new RecurringTransactionService.ProcessingResult(
                            2, 0, Collections.emptyList()));

            scheduler.processRecurringTransactions();

            verify(recurringTransactionService).processRecurringTransactionsForUser(1L);
            verify(recurringTransactionService, never()).processRecurringTransactionsForUser(2L);
        }

        @Test
        @DisplayName("Sets EncryptionContext from cache and clears it after processing")
        void setsEncryptionContextFromCacheAndClearsItAfterProcessing() {
            when(encryptionKeyCache.getCachedUserIds()).thenReturn(Set.of(1L));
            when(encryptionKeyCache.getKey(1L)).thenReturn(Optional.of(TEST_KEY_1));
            when(recurringTransactionService.processRecurringTransactionsForUser(1L))
                    .thenAnswer(invocation -> {
                        assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY_1);
                        return new RecurringTransactionService.ProcessingResult(
                                1, 0, Collections.emptyList());
                    });

            scheduler.processRecurringTransactions();

            assertThat(EncryptionContext.getKey()).isNull();
            verify(recurringTransactionService).processRecurringTransactionsForUser(1L);
        }

        @Test
        @DisplayName("Clears EncryptionContext after user processing fails")
        void clearsEncryptionContextAfterUserProcessingFails() {
            when(encryptionKeyCache.getCachedUserIds()).thenReturn(Set.of(1L));
            when(encryptionKeyCache.getKey(1L)).thenReturn(Optional.of(TEST_KEY_1));
            when(recurringTransactionService.processRecurringTransactionsForUser(1L))
                    .thenAnswer(invocation -> {
                        assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY_1);
                        throw new RuntimeException("boom");
                    });

            scheduler.processRecurringTransactions();

            assertThat(EncryptionContext.getKey()).isNull();
        }

        @Test
        @DisplayName("Processes multiple users with independent encryption contexts")
        void processesMultipleUsersWithIndependentContexts() {
            when(encryptionKeyCache.getCachedUserIds()).thenReturn(Set.of(1L, 2L));
            when(encryptionKeyCache.getKey(1L)).thenReturn(Optional.of(TEST_KEY_1));
            when(encryptionKeyCache.getKey(2L)).thenReturn(Optional.of(TEST_KEY_2));

            when(recurringTransactionService.processRecurringTransactionsForUser(1L))
                    .thenAnswer(invocation -> {
                        assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY_1);
                        return new RecurringTransactionService.ProcessingResult(
                                1, 0, Collections.emptyList());
                    });
            when(recurringTransactionService.processRecurringTransactionsForUser(2L))
                    .thenAnswer(invocation -> {
                        assertThat(EncryptionContext.getKey()).isSameAs(TEST_KEY_2);
                        return new RecurringTransactionService.ProcessingResult(
                                1, 0, Collections.emptyList());
                    });

            scheduler.processRecurringTransactions();

            assertThat(EncryptionContext.getKey()).isNull();
            verify(recurringTransactionService).processRecurringTransactionsForUser(1L);
            verify(recurringTransactionService).processRecurringTransactionsForUser(2L);
        }

        @Test
        @DisplayName("No-ops when no users have cached keys")
        void noOpsWhenNoCachedUsers() {
            when(encryptionKeyCache.getCachedUserIds()).thenReturn(Collections.emptySet());

            scheduler.processRecurringTransactions();

            verifyNoInteractions(recurringTransactionService);
        }
    }
}
