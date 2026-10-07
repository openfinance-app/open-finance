package org.openfinance.service.ai;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the bounded dates used by common ledger questions, before any model arithmetic. */
public record FinancialQuestion(
        String text, LocalDate start, LocalDate end, boolean periodResolved) {
    public FinancialQuestion(String text, LocalDate start, LocalDate end) {
        this(text, start, end, true);
    }

    /** Inherit the subject of a date-only follow-up, never the previous dates or assistant text. */
    public static FinancialQuestion resolve(
            String question, LocalDate today, List<String> previousUserQuestions) {
        FinancialQuestion current = parse(question, today);
        if (isPeriodOnly(question)) {
            for (int i = previousUserQuestions.size() - 1; i >= 0; i--) {
                String previous = previousUserQuestions.get(i);
                if (!isPeriodOnly(previous)) {
                    return new FinancialQuestion(
                            normalize(previous) + " " + current.text(),
                            current.start(),
                            current.end(),
                            current.periodResolved());
                }
            }
        }
        return current;
    }

    public static FinancialQuestion parse(String question, LocalDate today) {
        String text = normalize(question);
        Matcher dates = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(text);
        if (dates.find()) return explicitDates(text, today, dates);
        if (text.matches(
                ".*\\b(since|before|after|until|between|depuis|avant|apres|jusqu|entre)\\b.*"))
            return unresolved(text);
        FinancialQuestion relative = relativePeriod(text, today);
        if (relative != null) return relative;
        if (text.matches(
                ".*\\b(past|last|previous|next|quarter|quarters|week|weeks|day|days|fortnight|semester|spring|summer|autumn|winter|monday|tuesday|wednesday|thursday|friday|saturday|sunday|dernier|derniere|derniers|dernieres|precedent|precedente|prochain|prochaine|trimestre|trimestres|semaine|semaines|jour|jours|quinzaine|semestre|printemps|ete|automne|hiver|lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\b.*")) {
            return unresolved(text);
        }
        return namedPeriod(text, today);
    }

    private static FinancialQuestion explicitDates(String text, LocalDate today, Matcher dates) {
        try {
            LocalDate start = LocalDate.parse(dates.group(1));
            LocalDate end =
                    dates.find()
                            ? LocalDate.parse(dates.group(1))
                            : text.matches(".*\\b(since|depuis)\\b.*") ? today : start;
            if (dates.find() || text.matches(".*\\b(before|after|avant|apres)\\b.*"))
                return unresolved(text);
            if (end.isBefore(start) || end.isAfter(start.plusYears(1))) {
                throw new IllegalArgumentException("Choose a date range of at most one year.");
            }
            return new FinancialQuestion(text, start, end);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date range.", ex);
        }
    }

    private static FinancialQuestion relativePeriod(String text, LocalDate today) {
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today;
        if (text.matches(
                ".*\\b(last week|previous week|semaine derniere|semaine precedente)\\b.*")) {
            start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
            return new FinancialQuestion(text, start, start.plusDays(6));
        }
        if (text.matches(".*\\b(this week|cette semaine)\\b.*")) {
            return new FinancialQuestion(
                    text, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
        }
        if (text.matches(".*\\b(yesterday|hier)\\b.*")) {
            return new FinancialQuestion(text, today.minusDays(1), today.minusDays(1));
        }
        if (text.matches(".*\\b(today|aujourd hui)\\b.*")) {
            return new FinancialQuestion(text, today, today);
        }
        if (text.matches(".*(last month|previous month|mois dernier|mois precedent).*")) {
            start = start.minusMonths(1);
            end = start.withDayOfMonth(start.lengthOfMonth());
            return new FinancialQuestion(text, start, end);
        } else if (text.matches(".*(last year|previous year|annee derniere|annee precedente).*")) {
            start = today.minusYears(1).withDayOfYear(1);
            end = start.withMonth(12).withDayOfMonth(31);
            return new FinancialQuestion(text, start, end);
        } else if (text.matches(".*(this year|cette annee).*")) {
            start = today.withDayOfYear(1);
            return new FinancialQuestion(text, start, end);
        }
        return null;
    }

    private static FinancialQuestion namedPeriod(String text, LocalDate today) {
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today;
        if (java.util.Arrays.stream(Month.values())
                        .filter(month -> containsMonth(text, month))
                        .count()
                > 1) {
            return unresolved(text);
        }
        Matcher year = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(text);
        Integer explicitYear = year.find() ? Integer.valueOf(year.group(1)) : null;
        if (year.find()) return unresolved(text);
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

    private static FinancialQuestion unresolved(String text) {
        return new FinancialQuestion(text, null, null, false);
    }

    private static boolean isPeriodOnly(String question) {
        String text = normalize(question);
        String remainder = text.replaceAll("\\b\\d{4}-\\d{2}-\\d{2}\\b", " ");
        for (Month month : Month.values()) {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH)) {
                remainder =
                        remainder.replaceAll(
                                "\\b"
                                        + normalize(month.getDisplayName(TextStyle.FULL, locale))
                                        + "\\b",
                                " ");
            }
        }
        remainder =
                remainder.replaceAll(
                        "\\b(and|what|about|how|in|for|on|the|of|from|to|et|en|pour|pendant|le|la|les|du|au|a|de|des|qu|est|il|this|last|previous|month|year|week|today|yesterday|ce|cet|cette|mois|annee|semaine|dernier|derniere|precedent|precedente|aujourd|hui|hier|19\\d{2}|20\\d{2})\\b",
                        " ");
        return !text.isBlank()
                && remainder.isBlank()
                && !text.matches("(and|et|what|about|how|in|for|en|pour|qu|est|il|\\s)+");
    }

    /**
     * Conservatively reject an unresolved subject instead of silently using all
     * accounts/categories.
     */
    public boolean hasUnresolvedSubject() {
        String remainder = text;
        for (Month month : Month.values()) {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH)) {
                remainder =
                        remainder.replaceAll(
                                "\\b"
                                        + normalize(month.getDisplayName(TextStyle.FULL, locale))
                                        + "\\b",
                                " ");
            }
        }
        remainder = remainder.replaceAll("\\b\\d+(?:-\\d+)*\\b", " ");
        remainder =
                remainder.replaceAll(
                        "\\b(what|how|much|did|do|does|i|we|my|our|me|is|are|was|were|have|has|had|the|a|an|of|on|in|for|to|from|and|with|at|by|all|total|overall|across|please|show|tell|current|currently|now|this|last|previous|month|year|week|today|yesterday|balance|balances|account|accounts|bank|checking|savings|spend|spent|spending|expenses|expense|income|earn|earned|earnings|cash|flow|money|combien|ai|je|nous|mon|mes|ma|notre|nos|moi|est|sont|etait|etaient|le|la|les|un|une|de|du|des|en|pour|sur|au|aux|et|avec|tout|tous|toutes|ensemble|actuel|actuelle|actuels|actuelles|maintenant|ce|cet|cette|mois|annee|semaine|dernier|derniere|precedent|precedente|aujourd|hui|hier|solde|soldes|compte|comptes|bancaire|bancaires|courant|courants|epargne|depense|depenses|depensees|revenu|revenus|gagne|gagnes|argent|quel|quels|quelle|quelles|s|il|vous|plait)\\b",
                        " ");
        return !remainder.isBlank();
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
