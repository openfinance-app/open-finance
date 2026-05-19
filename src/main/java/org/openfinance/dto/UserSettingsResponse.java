package org.openfinance.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for user settings.
 *
 * <p>Contains user display and locale preferences returned from the API.
 *
 * <p>Requirement REQ-6.3: User Settings &amp; Preferences
 *
 * <p>Requirement REQ-2.7: Expose secondaryCurrency so the frontend can initialise the context on
 * login
 *
 * @param id Settings record ID
 * @param userId ID of the user who owns these settings
 * @param theme Theme preference ("dark" or "light")
 * @param dateFormat Date format preference (e.g., "MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD")
 * @param numberFormat Number format preference (e.g., "1,234.56", "1.234,56")
 * @param language Language/locale preference (ISO 639-1 code: "en", "fr", "es", etc.)
 * @param timezone Timezone preference (IANA identifier: "America/New_York", "Europe/Paris", etc.)
 * @param secondaryCurrency Optional secondary currency (ISO 4217) for tooltip comparison display;
 *     null when unset
 * @param country ISO 3166-1 alpha-2 country code for tool localisation (e.g. "FR", "US")
 * @param amountDisplayMode Amount display mode ("base", "native", "both")
 * @param createdAt Timestamp when settings were created
 * @param updatedAt Timestamp when settings were last updated
 */
public record UserSettingsResponse(
        Long id,
        Long userId,
        String theme,
        String dateFormat,
        String numberFormat,
        String language,
        String timezone,
        String secondaryCurrency,
        String country,
        String amountDisplayMode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
