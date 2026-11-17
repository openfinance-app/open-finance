package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.openfinance.repository.UserSettingsRepository;

class UserReportingClockTest {
    private final UserSettingsRepository repository = mock(UserSettingsRepository.class);
    private final UserReportingClock resolver =
            new UserReportingClock(
                    repository, Clock.fixed(Instant.parse("2026-11-15T23:51:00Z"), ZoneOffset.UTC));

    private void preferences(boolean device, String override, String lastDevice) {
        UserSettingsRepository.TimezonePreferences preferences =
                mock(UserSettingsRepository.TimezonePreferences.class);
        when(preferences.getUseDeviceTimezone()).thenReturn(device);
        when(preferences.getTimezone()).thenReturn(override);
        when(preferences.getLastDeviceTimezone()).thenReturn(lastDevice);
        when(repository.findTimezonePreferencesByUserId(1L)).thenReturn(Optional.of(preferences));
    }

    @Test
    void deviceWinsInDeviceModeAndIsRememberedForScheduledWork() {
        preferences(true, "America/New_York", "UTC");
        assertThat(LocalDate.now(resolver.forRequest(1L, "Europe/Paris"))).isEqualTo("2026-11-16");
        verify(repository).updateLastDeviceTimezone(1L, "Europe/Paris");
        preferences(true, "America/New_York", "Europe/Paris");
        assertThat(LocalDate.now(resolver.forUser(1L))).isEqualTo("2026-11-16");
    }

    @Test
    void accountOverrideWinsAndUnchangedDeviceDoesNotWrite() {
        preferences(false, "America/New_York", "Europe/Paris");
        assertThat(LocalDate.now(resolver.forRequest(1L, "Europe/Paris"))).isEqualTo("2026-11-15");
        verify(repository, never()).updateLastDeviceTimezone(any(), any());
    }

    @Test
    void invalidOrAbsentHeadersUseSavedDeviceThenUtc() {
        preferences(true, "UTC", "Europe/Paris");
        assertThat(LocalDate.now(resolver.forRequest(1L, "not/a/timezone")))
                .isEqualTo("2026-11-16");
        assertThat(LocalDate.now(resolver.forRequest(null, null))).isEqualTo("2026-11-15");
        verify(repository, never()).updateLastDeviceTimezone(any(), any());
    }

    @Test
    void invalidSavedOverridesFallBackToTheDeviceAsTheBrowserDoes() {
        preferences(false, "invalid/timezone", "Europe/Paris");
        assertThat(LocalDate.now(resolver.forRequest(1L, "America/New_York")))
                .isEqualTo("2026-11-15");
        assertThat(LocalDate.now(resolver.forUser(1L))).isEqualTo("2026-11-16");
    }
}
