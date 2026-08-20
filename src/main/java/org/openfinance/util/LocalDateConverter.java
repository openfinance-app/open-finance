package org.openfinance.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Converter for LocalDate to String for SQLite compatibility. SQLite doesn't have a native DATE
 * type, so we store it as ISO-8601 string.
 *
 * <p>Falls back to epoch milliseconds/seconds if numeric values exist in the DB.
 */
@Converter(autoApply = true)
public class LocalDateConverter implements AttributeConverter<LocalDate, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public String convertToDatabaseColumn(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.format(FORMATTER);
    }

    @Override
    public LocalDate convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, FORMATTER);
        } catch (Exception e) {
            try {
                long timestamp = Long.parseLong(value);
                if (value.length() <= 10) {
                    return java.time.Instant.ofEpochSecond(timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                }
                return java.time.Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }
}
