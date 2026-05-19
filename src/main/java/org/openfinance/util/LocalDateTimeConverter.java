package org.openfinance.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converter for LocalDateTime to String for SQLite compatibility. SQLite doesn't have a native
 * DATETIME type, so we store it as ISO-8601 string.
 */
@Converter(autoApply = true)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String convertToDatabaseColumn(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.format(FORMATTER);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, FORMATTER);
        } catch (Exception e) {
            // fallback for numeric timestamps if they exist in the DB
            try {
                long timestamp = Long.parseLong(s);
                return LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(timestamp),
                        java.time.ZoneId.systemDefault());
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }
}
