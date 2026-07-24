package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DateTimeUtil}'s locale-aware display formatting.
 *
 * <p>{@code formatDateForDisplay}/{@code formatDateTimeForDisplay} use the {@code "dd MMM yyyy"}
 * pattern, which resolves month abbreviations ("Jan", "janv.", etc.) via a {@link Locale} — the
 * original 1-arg {@link java.time.format.DateTimeFormatter#ofPattern(String)} overload silently
 * uses the JVM's default locale, which is environment-dependent. These tests pin explicit,
 * locale-aware behaviour.
 */
class DateTimeUtilTest {

    @Test
    @DisplayName("formatDateForDisplay defaults to English month abbreviations")
    void formatDateForDisplayDefaultsToEnglish() {
        assertThat(DateTimeUtil.formatDateForDisplay(LocalDate.of(2024, 1, 30)))
                .isEqualTo("30 Jan 2024");
    }

    @Test
    @DisplayName(
            "formatDateForDisplay(date, locale) renders French month abbreviations for Locale.FRENCH")
    void formatDateForDisplayRespectsFrenchLocale() {
        assertThat(DateTimeUtil.formatDateForDisplay(LocalDate.of(2024, 1, 30), Locale.FRENCH))
                .isEqualTo("30 janv. 2024");
    }

    @Test
    @DisplayName("formatDateForDisplay(date, locale) returns null for a null date")
    void formatDateForDisplayNullDate() {
        assertThat(DateTimeUtil.formatDateForDisplay(null, Locale.FRENCH)).isNull();
    }

    @Test
    @DisplayName("formatDateTimeForDisplay defaults to English month abbreviations")
    void formatDateTimeForDisplayDefaultsToEnglish() {
        assertThat(DateTimeUtil.formatDateTimeForDisplay(LocalDateTime.of(2024, 1, 30, 10, 30)))
                .isEqualTo("30 Jan 2024 10:30");
    }

    @Test
    @DisplayName("formatDateTimeForDisplay(dateTime, locale) renders French month abbreviations")
    void formatDateTimeForDisplayRespectsFrenchLocale() {
        assertThat(
                        DateTimeUtil.formatDateTimeForDisplay(
                                LocalDateTime.of(2024, 1, 30, 10, 30), Locale.FRENCH))
                .isEqualTo("30 janv. 2024 10:30");
    }

    @Test
    @DisplayName("formatDateTimeForDisplay(dateTime, locale) returns null for a null date-time")
    void formatDateTimeForDisplayNullDateTime() {
        assertThat(DateTimeUtil.formatDateTimeForDisplay(null, Locale.FRENCH)).isNull();
    }
}
