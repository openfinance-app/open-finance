package org.openfinance.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.security.UserEncryptionLock;
import org.openfinance.service.MarketDataService;

/**
 * Unit tests for the configurable market-hours guard used by {@link MarketDataScheduler} in {@code
 * DEFAULT} mode. Verifies the window is driven by {@code application.scheduled.market-hours.*}
 * rather than hardcoded 9 AM - 4 PM ET.
 */
@DisplayName("MarketDataScheduler market-hours guard")
class MarketDataSchedulerTest {

    // 2024-01-17 is a Wednesday; 2024-01-20 is a Saturday.
    private static final LocalDate WEDNESDAY = LocalDate.of(2024, 1, 17);
    private static final LocalDate SATURDAY = LocalDate.of(2024, 1, 20);

    private MarketDataScheduler schedulerWith(String zone, int openHour, int closeHour) {
        SchedulerProperties props = new SchedulerProperties();
        props.getMarketHours().setZone(zone);
        props.getMarketHours().setOpenHour(openHour);
        props.getMarketHours().setCloseHour(closeHour);
        return new MarketDataScheduler(
                new org.openfinance.service.UserReportingClock(
                        mock(org.openfinance.repository.UserSettingsRepository.class),
                        java.time.Clock.systemUTC()),
                mock(MarketDataService.class),
                mock(UserRepository.class),
                props,
                new EncryptionKeyCache(),
                new EncryptionProperties(),
                new UserEncryptionLock());
    }

    @Test
    @DisplayName("Uses configured open/close hours instead of hardcoded 9-16")
    void usesConfiguredHours() {
        MarketDataScheduler scheduler = schedulerWith("America/New_York", 10, 15);

        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(9, 0))).isFalse();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(10, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(15, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(16, 0))).isFalse();
    }

    @Test
    @DisplayName("Excludes weekends regardless of hour")
    void excludesWeekends() {
        MarketDataScheduler scheduler = schedulerWith("America/New_York", 9, 16);

        assertThat(scheduler.isMarketHours(SATURDAY.atTime(12, 0))).isFalse();
    }

    @Test
    @DisplayName("Supports a non-US exchange window (Tokyo 9-15 JST)")
    void supportsNonUsExchangeWindow() {
        MarketDataScheduler scheduler = schedulerWith("Asia/Tokyo", 9, 15);

        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(9, 30))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(15, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(16, 0))).isFalse();
    }

    @Test
    @DisplayName("Defaults preserve the original 9 AM - 4 PM ET window")
    void defaultsPreserveOriginalWindow() {
        SchedulerProperties props = new SchedulerProperties();
        MarketDataScheduler scheduler =
                new MarketDataScheduler(
                        new org.openfinance.service.UserReportingClock(
                                mock(org.openfinance.repository.UserSettingsRepository.class),
                                java.time.Clock.systemUTC()),
                        mock(MarketDataService.class),
                        mock(UserRepository.class),
                        props,
                        new EncryptionKeyCache(),
                        new EncryptionProperties(),
                        new UserEncryptionLock());

        assertThat(props.getMarketHours().getZone()).isEqualTo("America/New_York");
        assertThat(props.getMarketHours().getOpenHour()).isEqualTo(9);
        assertThat(props.getMarketHours().getCloseHour()).isEqualTo(16);
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(9, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(16, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(8, 0))).isFalse();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(17, 0))).isFalse();
    }

    @Test
    void scheduledUpdatesIsolateUserKeysAndSkipLockedUsersEvenAfterFailure() {
        MarketDataService service = mock(MarketDataService.class);
        UserRepository users = mock(UserRepository.class);
        SchedulerProperties props = new SchedulerProperties();
        props.getMarketData().setMode(SchedulerProperties.SchedulingMode.EVERY_HOUR);
        EncryptionKeyCache keys = new EncryptionKeyCache();
        SecretKeySpec firstKey = new SecretKeySpec(new byte[32], "AES");
        SecretKeySpec secondKey = new SecretKeySpec(new byte[16], "AES");
        keys.cacheKey(1L, firstKey);
        keys.cacheKey(2L, secondKey);
        when(users.findAll())
                .thenReturn(
                        List.of(
                                User.builder().id(1L).build(),
                                User.builder().id(2L).build(),
                                User.builder().id(3L).build()));
        when(service.updateAssetPrices(1L))
                .thenAnswer(
                        invocation -> {
                            assertThat(EncryptionContext.getKey()).isSameAs(firstKey);
                            throw new IllegalStateException("Provider unavailable");
                        });
        when(service.updateAssetPrices(2L))
                .thenAnswer(
                        invocation -> {
                            assertThat(EncryptionContext.getKey()).isSameAs(secondKey);
                            return 1;
                        });

        new MarketDataScheduler(
                        new org.openfinance.service.UserReportingClock(
                                mock(org.openfinance.repository.UserSettingsRepository.class),
                                java.time.Clock.systemUTC()),
                        service,
                        users,
                        props,
                        keys,
                        new EncryptionProperties(),
                        new UserEncryptionLock())
                .updateAssetPrices();

        verify(service).updateAssetPrices(1L);
        verify(service).updateAssetPrices(2L);
        verify(service, never()).updateAssetPrices(3L);
        assertThat(EncryptionContext.getKey()).isNull();
    }

    @Test
    void plaintextDeploymentsDoNotRequireCachedKeys() {
        MarketDataService service = mock(MarketDataService.class);
        UserRepository users = mock(UserRepository.class);
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setEnabled(false);
        when(users.findAll()).thenReturn(List.of(User.builder().id(1L).build()));

        new MarketDataScheduler(
                        new org.openfinance.service.UserReportingClock(
                                mock(org.openfinance.repository.UserSettingsRepository.class),
                                java.time.Clock.systemUTC()),
                        service,
                        users,
                        new SchedulerProperties(),
                        new EncryptionKeyCache(),
                        encryption,
                        new UserEncryptionLock())
                .triggerManualUpdate();

        verify(service).updateAssetPrices(1L);
        assertThat(EncryptionContext.getKey()).isNull();
    }

    @Test
    @DisplayName("Manual update sums per-user counts and isolates a failing user")
    void manualUpdateIsolatesFailures() {
        MarketDataService service = mock(MarketDataService.class);
        UserRepository users = mock(UserRepository.class);
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setEnabled(false);
        when(users.findAll())
                .thenReturn(List.of(User.builder().id(1L).build(), User.builder().id(2L).build()));
        when(service.updateAssetPrices(1L)).thenThrow(new IllegalStateException("provider down"));
        when(service.updateAssetPrices(2L)).thenReturn(3);

        String message =
                new MarketDataScheduler(
                                new org.openfinance.service.UserReportingClock(
                                        mock(
                                                org.openfinance.repository.UserSettingsRepository
                                                        .class),
                                        java.time.Clock.systemUTC()),
                                service,
                                users,
                                new SchedulerProperties(),
                                new EncryptionKeyCache(),
                                encryption,
                                new UserEncryptionLock())
                        .triggerManualUpdate();

        assertThat(message).isEqualTo("Manual update completed. Assets updated: 3");
        verify(service).updateAssetPrices(1L);
        verify(service).updateAssetPrices(2L);
    }

    @Test
    @DisplayName("Manual update reports a fatal failure instead of propagating it")
    void manualUpdateFatalFailure() {
        UserRepository users = mock(UserRepository.class);
        when(users.findAll()).thenThrow(new IllegalStateException("db gone"));

        String message =
                new MarketDataScheduler(
                                new org.openfinance.service.UserReportingClock(
                                        mock(
                                                org.openfinance.repository.UserSettingsRepository
                                                        .class),
                                        java.time.Clock.systemUTC()),
                                mock(MarketDataService.class),
                                users,
                                new SchedulerProperties(),
                                new EncryptionKeyCache(),
                                new EncryptionProperties(),
                                new UserEncryptionLock())
                        .triggerManualUpdate();

        assertThat(message).isEqualTo("Manual update failed: db gone");
    }

    @Test
    @DisplayName("Scheduled update with no users is a no-op")
    void scheduledUpdateNoUsers() {
        MarketDataService service = mock(MarketDataService.class);
        UserRepository users = mock(UserRepository.class);
        SchedulerProperties props = new SchedulerProperties();
        props.getMarketData().setMode(SchedulerProperties.SchedulingMode.EVERY_HOUR);
        when(users.findAll()).thenReturn(List.of());

        new MarketDataScheduler(
                        new org.openfinance.service.UserReportingClock(
                                mock(org.openfinance.repository.UserSettingsRepository.class),
                                java.time.Clock.systemUTC()),
                        service,
                        users,
                        props,
                        new EncryptionKeyCache(),
                        new EncryptionProperties(),
                        new UserEncryptionLock())
                .updateAssetPrices();

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Scheduled update swallows fatal repository failures")
    void scheduledUpdateFatalFailure() {
        MarketDataService service = mock(MarketDataService.class);
        UserRepository users = mock(UserRepository.class);
        SchedulerProperties props = new SchedulerProperties();
        props.getMarketData().setMode(SchedulerProperties.SchedulingMode.EVERY_HOUR);
        when(users.findAll()).thenThrow(new IllegalStateException("db gone"));

        MarketDataScheduler scheduler =
                new MarketDataScheduler(
                        new org.openfinance.service.UserReportingClock(
                                mock(org.openfinance.repository.UserSettingsRepository.class),
                                java.time.Clock.systemUTC()),
                        service,
                        users,
                        props,
                        new EncryptionKeyCache(),
                        new EncryptionProperties(),
                        new UserEncryptionLock());
        assertThatCode(scheduler::updateAssetPrices).doesNotThrowAnyException();
        verifyNoInteractions(service);
    }
}
