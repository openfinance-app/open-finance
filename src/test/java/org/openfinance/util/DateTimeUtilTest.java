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
    void parsesLeapDatesAndRejectsInvalidDatesWithoutSilentlyNormalizingThem() {
        assertThat(DateTimeUtil.parseDate("2024-02-29")).contains(LocalDate.of(2024, 2, 29));
        assertThat(DateTimeUtil.parseDate("2023-02-29")).isEmpty();
        assertThat(DateTimeUtil.parseDateTime("2024-02-29T23:59:59"))
                .contains(LocalDateTime.of(2024, 2, 29, 23, 59, 59));
        assertThat(DateTimeUtil.parseDateTime("2024-02-30T23:59:59")).isEmpty();
        assertThat(DateTimeUtil.parseDate("29/02/2024", "dd/MM/uuuu"))
                .contains(LocalDate.of(2024, 2, 29));
        assertThat(DateTimeUtil.parseDate("invalid", "dd/MM/uuuu")).isEmpty();
        assertThat(DateTimeUtil.formatDate(LocalDate.of(2024, 2, 29))).isEqualTo("2024-02-29");
        assertThat(DateTimeUtil.formatDateTime(LocalDateTime.of(2024, 2, 29, 23, 59)))
                .isEqualTo("2024-02-29T23:59:00");
    }

    @Test
    void calendarBoundariesHandleLeapMonthsAndInclusiveDateRanges() {
        LocalDate leap = LocalDate.of(2024, 2, 29);
        assertThat(DateTimeUtil.startOfMonth(leap)).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(DateTimeUtil.endOfMonth(leap)).isEqualTo(leap);
        assertThat(DateTimeUtil.startOfDay(leap)).isEqualTo(leap.atStartOfDay());
        assertThat(DateTimeUtil.endOfDay(leap)).isEqualTo(leap.atTime(java.time.LocalTime.MAX));
        assertThat(DateTimeUtil.addDays(leap, 1)).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(DateTimeUtil.addMonths(LocalDate.of(2024, 1, 31), 1)).isEqualTo(leap);
        assertThat(DateTimeUtil.daysBetween(leap, leap.plusDays(2))).isEqualTo(2);
        assertThat(DateTimeUtil.monthsBetween(leap, leap.plusMonths(2))).isEqualTo(2);
        assertThat(DateTimeUtil.isBetween(leap, leap, leap)).isTrue();
        assertThat(DateTimeUtil.isBetween(leap.minusDays(1), leap, leap.plusDays(1))).isFalse();
        assertThat(DateTimeUtil.isBetween(leap.plusDays(2), leap, leap.plusDays(1))).isFalse();
    }

    @Test
    void defaultZoneEpochConversionsPreserveTheInstant() {
        LocalDateTime local = DateTimeUtil.fromEpochMilli(1709164800000L);
        assertThat(DateTimeUtil.toEpochMilli(local)).isEqualTo(1709164800000L);
        ZonedDateTime utc = DateTimeUtil.toUtc(local);
        assertThat(utc.toInstant().toEpochMilli()).isEqualTo(1709164800000L);
        assertThat(DateTimeUtil.toLocalZone(utc).toLocalDateTime()).isEqualTo(local);
    }

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
