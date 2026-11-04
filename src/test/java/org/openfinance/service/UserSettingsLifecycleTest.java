package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.OnboardingRequest;
import org.openfinance.dto.UserSettingsResponse;
import org.openfinance.dto.UserSettingsUpdateRequest;
import org.openfinance.entity.User;
import org.openfinance.entity.UserSettings;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Settings creation, partial updates and onboarding persistence")
class UserSettingsLifecycleTest {
    private static final long USER_ID = 41L;
    @Mock private UserSettingsRepository settingsRepository;
    @Mock private UserRepository users;
    @InjectMocks private UserSettingsService service;
    private User user;
    private UserSettings settings;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        user = User.builder().id(USER_ID).baseCurrency("USD").secondaryCurrency("GBP").build();
        settings = UserSettings.builder().id(9L).user(user).build();
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @CsvSource({"en, MM/DD/YYYY, US", "fr, DD/MM/YYYY, FR"})
    void firstReadCreatesDefaultsForTheRequestLocale(
            String language, String dateFormat, String country) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
        when(settingsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(settingsRepository.save(any(UserSettings.class)))
                .thenAnswer(call -> call.getArgument(0));

        UserSettingsResponse response = service.getUserSettings(USER_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.language()).isEqualTo(language);
        assertThat(response.dateFormat()).isEqualTo(dateFormat);
        assertThat(response.country()).isEqualTo(country);
        assertThat(response.theme()).isEqualTo("dark");
        assertThat(response.timezone()).isEqualTo("UTC");
        assertThat(response.secondaryCurrency()).isEqualTo("GBP");
        verify(settingsRepository).save(any(UserSettings.class));
        verify(users, never()).save(any());
    }

    @Test
    void missingUserCannotAcquireOrphanedSettings() {
        when(settingsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUserSettings(USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("41");
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void concurrentCreationFoundOnSecondReadDoesNotInsertAgain() {
        when(settingsRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty(), Optional.of(settings));
        assertThat(service.getUserSettings(USER_ID).id()).isEqualTo(9L);
        verify(settingsRepository, never()).save(any());
        verify(users, never()).findById(any());
    }

    @Test
    void duplicateInsertCanRecoverTheSettingsCreatedByAnotherRequest() {
        when(settingsRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(settings));
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(settingsRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate user"));

        assertThat(service.getUserSettings(USER_ID).id()).isEqualTo(9L);
    }

    @Test
    void storageFailureIsNotMistakenForSuccessfullyCreatedDefaults() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("unavailable");
        when(settingsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(settingsRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.getUserSettings(USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasCause(failure);
    }

    @Test
    void fullUpdatePersistsBothEntitiesAndRepeatingItDoesNotWriteAgain() {
        when(settingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);
        UserSettingsUpdateRequest request =
                new UserSettingsUpdateRequest(
                        "light",
                        "DD/MM/YYYY",
                        "1 234,56",
                        "fr",
                        "Europe/Paris",
                        "EUR",
                        "FR",
                        "both",
                        true,
                        4);

        UserSettingsResponse response = service.updateUserSettings(USER_ID, request);
        assertThat(response.theme()).isEqualTo("light");
        assertThat(response.dateFormat()).isEqualTo("DD/MM/YYYY");
        assertThat(response.numberFormat()).isEqualTo("1 234,56");
        assertThat(response.language()).isEqualTo("fr");
        assertThat(response.timezone()).isEqualTo("Europe/Paris");
        assertThat(response.secondaryCurrency()).isEqualTo("EUR");
        assertThat(response.country()).isEqualTo("FR");
        assertThat(response.amountDisplayMode()).isEqualTo("both");
        assertThat(response.decimalPlacesOverrideEnabled()).isTrue();
        assertThat(response.preferredDecimalPlaces()).isEqualTo(4);
        assertThat(user.getBaseCurrency()).isEqualTo("USD");
        assertThat(service.updateUserSettings(USER_ID, request)).isEqualTo(response);
        verify(settingsRepository).save(settings);
        verify(users).save(user);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"EUR", "GBP"})
    void secondaryCurrencyDistinguishesOmissionClearingAndReplacement(String currency) {
        when(settingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));
        boolean changed = currency != null && !currency.equals("GBP");
        if (changed) when(settingsRepository.save(settings)).thenReturn(settings);
        UserSettingsUpdateRequest request =
                new UserSettingsUpdateRequest(
                        null, null, null, null, null, currency, null, null, null, null);

        UserSettingsResponse response = service.updateUserSettings(USER_ID, request);
        String expected = currency == null ? "GBP" : currency.isEmpty() ? null : currency;
        assertThat(response.secondaryCurrency()).isEqualTo(expected);
        assertThat(user.getSecondaryCurrency()).isEqualTo(expected);
        assertThat(response.theme()).isEqualTo("dark");
        if (changed) {
            verify(users).save(user);
            verify(settingsRepository).save(settings);
        } else {
            verify(users, never()).save(any());
            verify(settingsRepository, never()).save(any());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"USD"})
    void onboardingStoresLocaleAndCurrenciesAndMarksTheUserComplete(String secondary) {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(settingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);

        UserSettingsResponse response = service.completeOnboarding(USER_ID, onboarding(secondary));
        assertThat(user.getBaseCurrency()).isEqualTo("EUR");
        assertThat(user.isOnboardingComplete()).isTrue();
        assertThat(user.getSecondaryCurrency())
                .isEqualTo(secondary == null || secondary.isEmpty() ? null : secondary);
        assertThat(response.language()).isEqualTo("fr");
        assertThat(response.country()).isEqualTo("FR");
        assertThat(response.dateFormat()).isEqualTo("DD/MM/YYYY");
        assertThat(response.numberFormat()).isEqualTo("1 234,56");
        assertThat(response.amountDisplayMode()).isEqualTo("native");
        assertThat(response.theme()).isEqualTo("dark");
        verify(users).save(user);
        verify(settingsRepository).save(settings);
    }

    @Test
    void unknownUserCannotCompleteOnboarding() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.completeOnboarding(USER_ID, onboarding("USD")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
        verify(settingsRepository, never()).save(any());
    }

    private OnboardingRequest onboarding(String secondary) {
        return new OnboardingRequest(
                "FR", "EUR", secondary, "fr", "DD/MM/YYYY", "1 234,56", "native");
    }
}
