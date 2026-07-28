package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

    // ========== Explicit-zone conversion overloads (portable, JVM-zone independent) ==========

    private static final ZoneId PLUS_TWO = ZoneOffset.ofHours(2);

    @Test
    @DisplayName("toUtc(dateTime, zone) interprets the wall-clock time in the given zone")
    void toUtcWithExplicitZone() {
        // 12:00 in UTC+2 is 10:00 UTC, regardless of the JVM's default timezone.
        ZonedDateTime utc = DateTimeUtil.toUtc(LocalDateTime.of(2024, 6, 1, 12, 0), PLUS_TWO);

        assertThat(utc.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(utc.toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 6, 1, 10, 0));
    }

    @Test
    @DisplayName("toLocalZone(zonedDateTime, zone) shifts the instant into the target zone")
    void toLocalZoneWithExplicitZone() {
        ZonedDateTime utcNoon =
                ZonedDateTime.of(LocalDateTime.of(2024, 6, 1, 12, 0), ZoneOffset.UTC);

        ZonedDateTime shifted = DateTimeUtil.toLocalZone(utcNoon, PLUS_TWO);

        assertThat(shifted.getZone()).isEqualTo(PLUS_TWO);
        assertThat(shifted.toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 6, 1, 14, 0));
    }

    @Test
    @DisplayName("fromEpochMilli(epoch, zone) uses the given zone")
    void fromEpochMilliWithExplicitZone() {
        assertThat(DateTimeUtil.fromEpochMilli(0L, ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("toEpochMilli(dateTime, zone) and fromEpochMilli round-trip under a fixed zone")
    void epochMilliRoundTripWithExplicitZone() {
        LocalDateTime original = LocalDateTime.of(2024, 6, 1, 12, 34, 56);

        long epoch = DateTimeUtil.toEpochMilli(original, PLUS_TWO);

        assertThat(DateTimeUtil.fromEpochMilli(epoch, PLUS_TWO)).isEqualTo(original);
    }
}
