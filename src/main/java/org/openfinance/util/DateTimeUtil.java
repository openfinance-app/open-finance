package org.openfinance.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Utility class for date and time operations. Provides parsing, formatting, and manipulation of
 * dates and times.
 *
 * <p>All methods use ISO-8601 format by default and handle timezone conversions.
 */
public final class DateTimeUtil {

    // Standard formatters
    public static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Default (English) display formatter. {@code "MMM"} resolves month abbreviations via a {@link
     * Locale}; explicitly pinning {@link Locale#ENGLISH} here (rather than relying on {@link
     * DateTimeFormatter#ofPattern(String)}'s implicit JVM-default-locale behaviour) keeps this
     * default deterministic across environments. Use {@link #formatDateForDisplay(LocalDate,
     * Locale)} to render in the user's actual locale.
     */
    public static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /** Default (English) display-with-time formatter. See {@link #DISPLAY_DATE_FORMATTER}. */
    public static final DateTimeFormatter DISPLAY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    /**
     * Default zone for the {@code local}-zone conversion helpers below.
     *
     * <p>Resolved once from the {@code application.default-timezone} system property (e.g. {@code
     * -Dapplication.default-timezone=UTC} or {@code Europe/Paris}); when unset it falls back to the
     * JVM's {@link ZoneId#systemDefault()}. Making this explicitly configurable removes the hard
     * dependency on the (environment-dependent) host timezone. For fully deterministic conversions,
     * prefer the {@code ZoneId}-taking overloads of {@link #toUtc(LocalDateTime, ZoneId)} etc.
     */
    private static final ZoneId DEFAULT_ZONE = resolveDefaultZone();

    private static ZoneId resolveDefaultZone() {
        String configured = System.getProperty("application.default-timezone");
        if (configured != null && !configured.isBlank()) {
            return ZoneId.of(configured.trim());
        }
        return ZoneId.systemDefault();
    }

    private DateTimeUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========== Parsing Methods ==========

    /**
     * Parse date string in ISO format (yyyy-MM-dd)
     *
     * @param dateString the date string
     * @return Optional LocalDate
     */
    public static Optional<LocalDate> parseDate(String dateString) {
        try {
            return Optional.of(LocalDate.parse(dateString, ISO_DATE_FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Parse date-time string in ISO format
     *
     * @param dateTimeString the date-time string
     * @return Optional LocalDateTime
     */
    public static Optional<LocalDateTime> parseDateTime(String dateTimeString) {
        try {
            return Optional.of(LocalDateTime.parse(dateTimeString, ISO_DATE_TIME_FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Parse date with custom format
     *
     * @param dateString the date string
     * @param pattern the pattern
     * @return Optional LocalDate
     */
    public static Optional<LocalDate> parseDate(String dateString, String pattern) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return Optional.of(LocalDate.parse(dateString, formatter));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ========== Formatting Methods ==========

    /**
     * Format date to ISO string (yyyy-MM-dd)
     *
     * @param date the date
     * @return formatted string
     */
    public static String formatDate(LocalDate date) {
        return date != null ? date.format(ISO_DATE_FORMATTER) : null;
    }

    /**
     * Format date-time to ISO string
     *
     * @param dateTime the date-time
     * @return formatted string
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_DATE_TIME_FORMATTER) : null;
    }

    /**
     * Format date for display (dd MMM yyyy)
     *
     * @param date the date
     * @return formatted string like "30 Jan 2024"
     */
    public static String formatDateForDisplay(LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE_FORMATTER) : null;
    }

    /**
     * Format date for display in a specific locale (dd MMM yyyy), e.g. "30 janv. 2024" for {@link
     * Locale#FRENCH}.
     *
     * @param date the date
     * @param locale the locale to render month abbreviations in
     * @return formatted string, or {@code null} if {@code date} is {@code null}
     */
    public static String formatDateForDisplay(LocalDate date, Locale locale) {
        if (date == null) {
            return null;
        }
        return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", locale));
    }

    /**
     * Format date-time for display
     *
     * @param dateTime the date-time
     * @return formatted string like "30 Jan 2024 10:30"
     */
    public static String formatDateTimeForDisplay(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DISPLAY_DATE_TIME_FORMATTER) : null;
    }

    /**
     * Format date-time for display in a specific locale (dd MMM yyyy HH:mm).
     *
     * @param dateTime the date-time
     * @param locale the locale to render month abbreviations in
     * @return formatted string, or {@code null} if {@code dateTime} is {@code null}
     */
    public static String formatDateTimeForDisplay(LocalDateTime dateTime, Locale locale) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", locale));
    }

    // ========== Timezone Conversion Methods ==========

    /**
     * Convert LocalDateTime to UTC, interpreting it in {@link #DEFAULT_ZONE}.
     *
     * @param dateTime the local date-time
     * @return ZonedDateTime in UTC
     */
    public static ZonedDateTime toUtc(LocalDateTime dateTime) {
        return toUtc(dateTime, DEFAULT_ZONE);
    }

    /**
     * Convert a LocalDateTime to UTC, interpreting the wall-clock time in the given source zone.
     * Portable: the result does not depend on the JVM's default timezone.
     *
     * @param dateTime the local (wall-clock) date-time
     * @param sourceZone the zone the {@code dateTime} is expressed in
     * @return ZonedDateTime in UTC
     */
    public static ZonedDateTime toUtc(LocalDateTime dateTime, ZoneId sourceZone) {
        return dateTime.atZone(sourceZone).withZoneSameInstant(ZoneOffset.UTC);
    }

    /**
     * Convert UTC to {@link #DEFAULT_ZONE}.
     *
     * @param utcDateTime the UTC date-time
     * @return ZonedDateTime in the default zone
     */
    public static ZonedDateTime toLocalZone(ZonedDateTime utcDateTime) {
        return toLocalZone(utcDateTime, DEFAULT_ZONE);
    }

    /**
     * Shift a ZonedDateTime's instant into the given target zone. Portable.
     *
     * @param dateTime the date-time (any zone)
     * @param targetZone the zone to express the same instant in
     * @return ZonedDateTime in {@code targetZone}
     */
    public static ZonedDateTime toLocalZone(ZonedDateTime dateTime, ZoneId targetZone) {
        return dateTime.withZoneSameInstant(targetZone);
    }

    /**
     * Convert epoch milliseconds to LocalDateTime using {@link #DEFAULT_ZONE}.
     *
     * @param epochMilli epoch milliseconds
     * @return LocalDateTime
     */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return fromEpochMilli(epochMilli, DEFAULT_ZONE);
    }

    /**
     * Convert epoch milliseconds to LocalDateTime using the given zone. Portable.
     *
     * @param epochMilli epoch milliseconds
     * @param zone the zone to express the instant in
     * @return LocalDateTime
     */
    public static LocalDateTime fromEpochMilli(long epochMilli, ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), zone);
    }

    /**
     * Convert LocalDateTime to epoch milliseconds, interpreting it in {@link #DEFAULT_ZONE}.
     *
     * @param dateTime the date-time
     * @return epoch milliseconds
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return toEpochMilli(dateTime, DEFAULT_ZONE);
    }

    /**
     * Convert a LocalDateTime to epoch milliseconds, interpreting the wall-clock time in the given
     * zone. Portable.
     *
     * @param dateTime the local (wall-clock) date-time
     * @param zone the zone the {@code dateTime} is expressed in
     * @return epoch milliseconds
     */
    public static long toEpochMilli(LocalDateTime dateTime, ZoneId zone) {
        return dateTime.atZone(zone).toInstant().toEpochMilli();
    }

    // ========== Date Calculation Methods ==========

    /**
     * Get start of day
     *
     * @param date the date
     * @return LocalDateTime at 00:00:00
     */
    public static LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    /**
     * Get end of day
     *
     * @param date the date
     * @return LocalDateTime at 23:59:59.999999999
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    /**
     * Get start of month
     *
     * @param date the date
     * @return LocalDate at first day of month
     */
    public static LocalDate startOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    /**
     * Get end of month
     *
     * @param date the date
     * @return LocalDate at last day of month
     */
    public static LocalDate endOfMonth(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }

    /**
     * Calculate days between two dates
     *
     * @param start start date
     * @param end end date
     * @return number of days
     */
    public static long daysBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * Calculate months between two dates
     *
     * @param start start date
     * @param end end date
     * @return number of months
     */
    public static long monthsBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.MONTHS.between(start, end);
    }

    /**
     * Add days to a date
     *
     * @param date the date
     * @param days number of days to add
     * @return new LocalDate
     */
    public static LocalDate addDays(LocalDate date, long days) {
        return date.plusDays(days);
    }

    /**
     * Add months to a date
     *
     * @param date the date
     * @param months number of months to add
     * @return new LocalDate
     */
    public static LocalDate addMonths(LocalDate date, long months) {
        return date.plusMonths(months);
    }

    // ========== Validation Methods ==========

    /**
     * Check if date is in the past
     *
     * @param date the date
     * @return true if date is before today
     */
    public static boolean isPast(LocalDate date) {
        return date.isBefore(LocalDate.now());
    }

    /**
     * Check if date is in the future
     *
     * @param date the date
     * @return true if date is after today
     */
    public static boolean isFuture(LocalDate date) {
        return date.isAfter(LocalDate.now());
    }

    /**
     * Check if date is today
     *
     * @param date the date
     * @return true if date is today
     */
    public static boolean isToday(LocalDate date) {
        return date.equals(LocalDate.now());
    }

    /**
     * Check if date is within range (inclusive)
     *
     * @param date the date to check
     * @param start start date
     * @param end end date
     * @return true if date is within range
     */
    public static boolean isBetween(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    // ========== Current Date/Time Methods ==========

    /**
     * Get current date
     *
     * @return LocalDate.now()
     */
    public static LocalDate now() {
        return LocalDate.now();
    }

    /**
     * Get current date-time
     *
     * @return LocalDateTime.now()
     */
    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now();
    }

    /**
     * Get current UTC date-time
     *
     * @return ZonedDateTime in UTC
     */
    public static ZonedDateTime nowUtc() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }
}
