package org.openfinance.util;

import java.time.LocalDate;
import java.util.Locale;

/** Inclusive history ranges shared by account balances and estimated loan interest. */
public final class HistoricalPeriod {
    private HistoricalPeriod() {}

    public static LocalDate startDate(String period, LocalDate endDate, LocalDate openingDate) {
        return switch (period.toUpperCase(Locale.ROOT)) {
            case "1D" -> endDate;
            case "7D" -> endDate.minusDays(6);
            case "1M" -> endDate.minusMonths(1);
            case "3M" -> endDate.minusMonths(3);
            case "6M" -> endDate.minusMonths(6);
            case "1Y" -> endDate.minusYears(1);
            case "YTD" -> endDate.withDayOfYear(1);
            case "ALL" -> openingDate == null ? endDate : openingDate;
            default -> numericStartDate(period, endDate);
        };
    }

    private static LocalDate numericStartDate(String period, LocalDate endDate) {
        try {
            int days = Integer.parseInt(period);
            if (days <= 0) throw new IllegalArgumentException("History period must be positive");
            return endDate.minusDays(days - 1L);
        } catch (NumberFormatException exception) {
            return endDate.minusMonths(3);
        }
    }
}
