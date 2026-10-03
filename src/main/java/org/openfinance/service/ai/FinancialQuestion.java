package org.openfinance.service.ai;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the bounded dates used by common ledger questions, before any model arithmetic. */
public record FinancialQuestion(String text, LocalDate start, LocalDate end) {
    public static FinancialQuestion parse(String question, LocalDate today) {
        String text = normalize(question);
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today;
        Matcher dates = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(question);
        if (dates.find()) {
            try {
                start = LocalDate.parse(dates.group(1));
                end = dates.find() ? LocalDate.parse(dates.group(1)) : start;
                if (end.isBefore(start) || end.isAfter(start.plusYears(1))) {
                    throw new IllegalArgumentException("Choose a date range of at most one year.");
                }
                return new FinancialQuestion(text, start, end);
            } catch (java.time.format.DateTimeParseException ex) {
                throw new IllegalArgumentException("Invalid date range.", ex);
            }
        }
        if (text.matches(".*(last month|previous month|mois dernier|mois precedent).*")) {
            start = start.minusMonths(1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        } else if (text.matches(".*(last year|previous year|annee derniere|annee precedente).*")) {
            start = today.minusYears(1).withDayOfYear(1);
            end = start.withMonth(12).withDayOfMonth(31);
        } else if (text.matches(".*(this year|cette annee).*")) {
            start = today.withDayOfYear(1);
        }
        Matcher year = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(text);
        Integer explicitYear = year.find() ? Integer.valueOf(year.group(1)) : null;
        for (Month month : Month.values()) {
            if (containsMonth(text, month)) {
                int chosenYear = explicitYear != null ? explicitYear : today.getYear();
                if (explicitYear == null && month.getValue() > today.getMonthValue()) chosenYear--;
                start = LocalDate.of(chosenYear, month, 1);
                end = start.withDayOfMonth(start.lengthOfMonth());
                return new FinancialQuestion(
                        text, start, !start.isAfter(today) && end.isAfter(today) ? today : end);
            }
        }
        if (explicitYear != null) {
            start = LocalDate.of(explicitYear, 1, 1);
            end = explicitYear == today.getYear() ? today : start.withMonth(12).withDayOfMonth(31);
        }
        return new FinancialQuestion(text, start, end);
    }

    public boolean mentions(String expression) {
        return Pattern.compile(expression).matcher(text).find();
    }

    private static boolean containsMonth(String text, Month month) {
        return java.util.stream.Stream.of(Locale.ENGLISH, Locale.FRENCH)
                .map(locale -> normalize(month.getDisplayName(TextStyle.FULL, locale)))
                .anyMatch(name -> (" " + text + " ").contains(" " + name + " "));
    }

    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}-]+", " ")
                .strip();
    }
}
