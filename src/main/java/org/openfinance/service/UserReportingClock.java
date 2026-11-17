package org.openfinance.service;

import java.time.Clock;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.util.ReportingClock;
import org.springframework.stereotype.Service;

/** Resolves account overrides and remembers the last device zone for unattended work. */
@Service
@RequiredArgsConstructor
public class UserReportingClock {
    private final UserSettingsRepository settingsRepository;
    private final Clock reportingSystemClock;

    public Clock forUser(Long userId) {
        return forRequest(userId, null);
    }

    public Clock forRequest(Long userId, String deviceTimezone) {
        UserSettingsRepository.TimezonePreferences settings =
                userId == null
                        ? null
                        : settingsRepository.findTimezonePreferencesByUserId(userId).orElse(null);
        boolean validDevice =
                deviceTimezone != null && ZoneId.getAvailableZoneIds().contains(deviceTimezone);
        if (settings != null
                && validDevice
                && !deviceTimezone.equals(settings.getLastDeviceTimezone())) {
            // A separate, atomic write avoids SQLite read-to-write snapshot upgrades and does not
            // overwrite unrelated preferences when requests arrive concurrently.
            settingsRepository.updateLastDeviceTimezone(userId, deviceTimezone);
        }
        boolean validOverride =
                settings != null
                        && !settings.getUseDeviceTimezone()
                        && ZoneId.getAvailableZoneIds().contains(settings.getTimezone());
        String zone =
                validOverride
                        ? settings.getTimezone()
                        : validDevice
                                ? deviceTimezone
                                : settings == null ? "UTC" : settings.getLastDeviceTimezone();
        return Clock.fixed(reportingSystemClock.instant(), ReportingClock.validZone(zone));
    }
}
