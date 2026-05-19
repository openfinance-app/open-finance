package org.openfinance.service;

import lombok.RequiredArgsConstructor;
import org.openfinance.dto.OnboardingRequest;
import org.openfinance.dto.UserSettingsResponse;
import org.openfinance.dto.UserSettingsUpdateRequest;
import org.openfinance.entity.User;
import org.openfinance.entity.UserSettings;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing user display and locale settings.
 *
 * <p>Handles retrieval and updating of user preferences including:
 *
 * <ul>
 *   <li>Theme (dark/light mode)
 *   <li>Date format preferences
 *   <li>Number format preferences
 *   <li>Language/locale preferences
 *   <li>Timezone preferences
 * </ul>
 *
 * <p>Automatically creates default settings for users who don't have any yet.
 *
 * <p>Requirement REQ-6.3: User Settings & Preferences
 *
 * @see UserSettings
 * @see UserSettingsRepository
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    /**
     * Retrieves user settings for the specified user.
     *
     * <p>If the user doesn't have settings yet, creates and returns default settings.
     *
     * <p><strong>Default Settings:</strong>
     *
     * <ul>
     *   <li>Theme: dark
     *   <li>Date Format: MM/DD/YYYY
     *   <li>Number Format: 1,234.56
     *   <li>Language: en
     *   <li>Timezone: UTC
     * </ul>
     *
     * @param userId ID of the user
     * @return UserSettingsResponse containing user's settings
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    public UserSettingsResponse getUserSettings(Long userId) {
        log.debug("Retrieving settings for user ID: {}", userId);

        // Find existing settings or create defaults
        UserSettings settings =
                userSettingsRepository
                        .findByUserId(userId)
                        .orElseGet(() -> createDefaultSettings(userId));

        log.info(
                "Retrieved settings for user ID {} (theme: {}, dateFormat: {})",
                userId,
                settings.getTheme(),
                settings.getDateFormat());

        return mapToResponse(settings);
    }

    /**
     * Updates user settings.
     *
     * <p>Only non-null fields in the request will be updated. If the user doesn't have settings
     * yet, creates them with default values and then applies the requested updates.
     *
     * <p>The {@code secondaryCurrency} field (REQ-2.2) is stored on the {@link User} entity rather
     * than {@link UserSettings}. When present, it is applied to the User and the User entity is
     * saved separately.
     *
     * <p><strong>Update Rules:</strong>
     *
     * <ul>
     *   <li>All fields are optional
     *   <li>Only provided (non-null) values are updated
     *   <li>Invalid values are rejected with validation errors
     *   <li>Updated timestamp is automatically set
     *   <li>Pass an empty string for secondaryCurrency to clear the preference
     * </ul>
     *
     * @param userId ID of the user
     * @param request update request with optional fields
     * @return UserSettingsResponse with updated settings
     * @throws IllegalArgumentException if user not found or validation fails
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "netWorthSummary"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"exchangeRates"},
                        allEntries = true)
            })
    public UserSettingsResponse updateUserSettings(Long userId, UserSettingsUpdateRequest request) {
        log.info("Updating settings for user ID: {}", userId);

        // Find existing settings or create defaults
        UserSettings settings =
                userSettingsRepository
                        .findByUserId(userId)
                        .orElseGet(() -> createDefaultSettings(userId));

        // Update only non-null fields
        boolean updated = false;

        if (request.theme() != null && !request.theme().equals(settings.getTheme())) {
            log.debug("Updating theme from {} to {}", settings.getTheme(), request.theme());
            settings.setTheme(request.theme());
            updated = true;
        }

        if (request.dateFormat() != null
                && !request.dateFormat().equals(settings.getDateFormat())) {
            log.debug(
                    "Updating dateFormat from {} to {}",
                    settings.getDateFormat(),
                    request.dateFormat());
            settings.setDateFormat(request.dateFormat());
            updated = true;
        }

        if (request.numberFormat() != null
                && !request.numberFormat().equals(settings.getNumberFormat())) {
            log.debug(
                    "Updating numberFormat from {} to {}",
                    settings.getNumberFormat(),
                    request.numberFormat());
            settings.setNumberFormat(request.numberFormat());
            updated = true;
        }

        if (request.language() != null && !request.language().equals(settings.getLanguage())) {
            log.debug(
                    "Updating language from {} to {}", settings.getLanguage(), request.language());
            settings.setLanguage(request.language());
            updated = true;
        }

        if (request.timezone() != null && !request.timezone().equals(settings.getTimezone())) {
            log.debug(
                    "Updating timezone from {} to {}", settings.getTimezone(), request.timezone());
            settings.setTimezone(request.timezone());
            updated = true;
        }

        if (request.country() != null && !request.country().equals(settings.getCountry())) {
            log.debug("Updating country from {} to {}", settings.getCountry(), request.country());
            settings.setCountry(request.country());
            updated = true;
        }

        if (request.amountDisplayMode() != null
                && !request.amountDisplayMode().equals(settings.getAmountDisplayMode())) {
            log.debug(
                    "Updating amountDisplayMode from {} to {}",
                    settings.getAmountDisplayMode(),
                    request.amountDisplayMode());
            settings.setAmountDisplayMode(request.amountDisplayMode());
            updated = true;
        }

        // Requirement REQ-2.2: secondaryCurrency is stored on the User entity.
        // A non-null value triggers an update; empty string clears the preference.
        if (request.secondaryCurrency() != null) {
            User user = settings.getUser();
            String newSecondary =
                    request.secondaryCurrency().isBlank() ? null : request.secondaryCurrency();
            if (!java.util.Objects.equals(user.getSecondaryCurrency(), newSecondary)) {
                log.debug(
                        "Updating secondaryCurrency from {} to {}",
                        user.getSecondaryCurrency(),
                        newSecondary);
                user.setSecondaryCurrency(newSecondary);
                userRepository.save(user);
                updated = true;
            }
        }

        // Save if any changes were made
        if (updated) {
            UserSettings savedSettings = userSettingsRepository.save(settings);
            log.info("Successfully updated settings for user ID: {}", userId);
            return mapToResponse(savedSettings);
        } else {
            log.info("No changes to settings for user ID: {}", userId);
            return mapToResponse(settings);
        }
    }

    /**
     * Handles the initial onboarding preferences submission.
     *
     * <p>Saves all preferences gathered in the onboarding wizard (country, base currency, secondary
     * currency, language, date format, number format, and currency display style) and marks the
     * user as {@code onboardingComplete = true} so subsequent logins skip the wizard.
     *
     * @param userId the authenticated user's ID
     * @param request the onboarding preferences
     * @return updated {@link UserSettingsResponse}
     * @throws IllegalArgumentException if the user is not found
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "netWorthSummary"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"exchangeRates"},
                        allEntries = true)
            })
    public UserSettingsResponse completeOnboarding(Long userId, OnboardingRequest request) {
        log.info("Completing onboarding for user ID: {}", userId);

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with ID: " + userId));

        // Persist currency prefs on the User entity
        user.setBaseCurrency(request.baseCurrency());
        String newSecondary =
                (request.secondaryCurrency() != null && !request.secondaryCurrency().isBlank())
                        ? request.secondaryCurrency()
                        : null;
        user.setSecondaryCurrency(newSecondary);
        user.setOnboardingComplete(true);
        userRepository.save(user);

        // Persist display/locale prefs on UserSettings
        UserSettings settings =
                userSettingsRepository
                        .findByUserId(userId)
                        .orElseGet(() -> createDefaultSettings(userId));

        settings.setCountry(request.country());
        settings.setLanguage(request.language());
        settings.setDateFormat(request.dateFormat());
        settings.setNumberFormat(request.numberFormat());
        settings.setAmountDisplayMode(request.amountDisplayMode());
        UserSettings savedSettings = userSettingsRepository.save(settings);

        log.info("Onboarding complete for user ID: {}", userId);
        return mapToResponse(savedSettings);
    }

    /**
     * Creates default settings for a user.
     *
     * <p>Called automatically when retrieving or updating settings for a user who doesn't have any
     * settings yet.
     *
     * @param userId ID of the user
     * @return UserSettings entity with default values
     * @throws IllegalArgumentException if user not found
     */
    private synchronized UserSettings createDefaultSettings(Long userId) {
        log.info("Checking or creating default settings for user ID: {}", userId);

        // Double-check if it was created by another thread while waiting for lock
        return userSettingsRepository
                .findByUserId(userId)
                .orElseGet(
                        () -> {
                            log.info("Creating default settings for user ID: {}", userId);

                            // Verify user exists
                            User user =
                                    userRepository
                                            .findById(userId)
                                            .orElseThrow(
                                                    () -> {
                                                        log.warn(
                                                                "Cannot create settings: user ID {} not found",
                                                                userId);
                                                        return new IllegalArgumentException(
                                                                "User not found with ID: "
                                                                        + userId);
                                                    });

                            // Dynamically infer UI defaults based on the user's initial
                            // Accept-Language
                            java.util.Locale currentLocale =
                                    org.springframework.context.i18n.LocaleContextHolder
                                            .getLocale();
                            String langCode =
                                    currentLocale != null && currentLocale.getLanguage() != null
                                            ? currentLocale.getLanguage()
                                            : "en";

                            // French -> DD/MM/YYYY, else MM/DD/YYYY
                            String defaultDateFormat =
                                    "fr".equalsIgnoreCase(langCode) ? "DD/MM/YYYY" : "MM/DD/YYYY";
                            String defaultCountry = "fr".equalsIgnoreCase(langCode) ? "FR" : "US";

                            // Create settings with default values inferred from locale
                            UserSettings settings =
                                    UserSettings.builder()
                                            .user(user)
                                            .theme("dark")
                                            .dateFormat(defaultDateFormat)
                                            .numberFormat("1,234.56")
                                            .language(langCode)
                                            .timezone("UTC")
                                            .country(defaultCountry)
                                            .build();

                            try {
                                UserSettings savedSettings = userSettingsRepository.save(settings);
                                log.info(
                                        "Created default settings for user ID: {} (settings ID: {})",
                                        userId,
                                        savedSettings.getId());
                                return savedSettings;
                            } catch (Exception e) {
                                log.warn(
                                        "Concurrent creation detected during save for user ID: {}, attempting to fetch existing settings",
                                        userId);
                                return userSettingsRepository
                                        .findByUserId(userId)
                                        .orElseThrow(
                                                () ->
                                                        new RuntimeException(
                                                                "Failed to create or retrieve default settings",
                                                                e));
                            }
                        });
    }

    /**
     * Maps UserSettings entity to response DTO.
     *
     * <p>Requirement REQ-2.7: Expose secondaryCurrency in profile response
     *
     * @param settings UserSettings entity
     * @return UserSettingsResponse DTO
     */
    private UserSettingsResponse mapToResponse(UserSettings settings) {
        String secondaryCurrency =
                settings.getUser() != null ? settings.getUser().getSecondaryCurrency() : null;
        return new UserSettingsResponse(
                settings.getId(),
                settings.getUser().getId(),
                settings.getTheme(),
                settings.getDateFormat(),
                settings.getNumberFormat(),
                settings.getLanguage(),
                settings.getTimezone(),
                secondaryCurrency,
                settings.getCountry(),
                settings.getAmountDisplayMode(),
                settings.getCreatedAt(),
                settings.getUpdatedAt());
    }
}
