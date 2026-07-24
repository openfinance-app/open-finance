package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.UserSettingsResponse;
import org.openfinance.dto.UserSettingsUpdateRequest;
import org.openfinance.entity.User;
import org.openfinance.entity.UserSettings;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;

/**
 * Unit tests for {@link UserSettingsService} covering the new decimal-places preference fields
 * (default values, partial update, and response mapping).
 */
@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private UserSettingsService service;

    private User user;
    private UserSettings settings;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).build();
        // Builder applies @Builder.Default => override disabled, 2 decimals.
        settings = UserSettings.builder().user(user).build();
    }

    private UserSettingsUpdateRequest request(Boolean enabled, Integer places) {
        return new UserSettingsUpdateRequest(
                null, null, null, null, null, null, null, null, enabled, places);
    }

    @Test
    @DisplayName("Defaults: override disabled and 2 decimal places")
    void defaults() {
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = service.getUserSettings(USER_ID);

        assertThat(response.decimalPlacesOverrideEnabled()).isFalse();
        assertThat(response.preferredDecimalPlaces()).isEqualTo(2);
    }

    @Test
    @DisplayName("Enabling the override with 4 decimals persists and is returned")
    void enableOverride() {
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserSettingsResponse response = service.updateUserSettings(USER_ID, request(true, 4));

        assertThat(response.decimalPlacesOverrideEnabled()).isTrue();
        assertThat(response.preferredDecimalPlaces()).isEqualTo(4);
        assertThat(settings.isDecimalPlacesOverrideEnabled()).isTrue();
        assertThat(settings.getPreferredDecimalPlaces()).isEqualTo(4);
    }

    @Test
    @DisplayName("Null decimal fields leave existing values unchanged")
    void partialUpdateLeavesDecimalsUnchanged() {
        settings.setDecimalPlacesOverrideEnabled(true);
        settings.setPreferredDecimalPlaces(6);
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = service.updateUserSettings(USER_ID, request(null, null));

        assertThat(response.decimalPlacesOverrideEnabled()).isTrue();
        assertThat(response.preferredDecimalPlaces()).isEqualTo(6);
    }

    @Test
    @DisplayName("Disabling the override keeps the previously chosen decimal count")
    void disableRetainsValue() {
        settings.setDecimalPlacesOverrideEnabled(true);
        settings.setPreferredDecimalPlaces(5);
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserSettingsResponse response = service.updateUserSettings(USER_ID, request(false, null));

        assertThat(response.decimalPlacesOverrideEnabled()).isFalse();
        assertThat(response.preferredDecimalPlaces()).isEqualTo(5);
    }
}
