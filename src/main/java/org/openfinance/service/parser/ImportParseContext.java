package org.openfinance.service.parser;

import java.util.Locale;
import org.openfinance.entity.UserSettings;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * User-specific preferences that influence how import files are parsed.
 *
 * <p>Import parsing runs on a background thread (no request locale available), so the relevant
 * preferences are captured up front from {@link UserSettings} and passed to the parsers.
 *
 * @param locale the user's locale (drives validation message language)
 * @param dayFirst whether ambiguous numeric dates should be interpreted day-first ({code dd/MM})
 *     rather than month-first ({code MM/dd}); derived from the user's date-format setting or
 *     locale. Only used where the file format has no date convention of its own: QIF follows the
 *     Quicken MM/DD convention, so this preference applies to CSV only, and only when the CSV
 *     content itself cannot disambiguate (all date parts ≤ 12).
 */
public record ImportParseContext(Locale locale, boolean dayFirst) {

    /**
     * Default context built from the current thread locale (or English when unavailable).
     * Month-first is assumed only for US-style locales; everything else defaults to day-first. Used
     * when parsers are invoked outside an import session (e.g. tests).
     */
    public static ImportParseContext defaults() {
        Locale locale;
        try {
            locale = LocaleContextHolder.getLocale();
        } catch (Exception e) {
            locale = null;
        }
        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        return new ImportParseContext(locale, isDayFirstLocale(locale));
    }

    /** Build a context from the user's settings, falling back to defaults when absent. */
    public static ImportParseContext from(UserSettings settings) {
        if (settings == null) {
            return defaults();
        }
        Locale locale =
                settings.getLanguage() != null && !settings.getLanguage().isBlank()
                        ? Locale.forLanguageTag(settings.getLanguage().trim())
                        : Locale.ENGLISH;
        return new ImportParseContext(locale, isDayFirst(settings.getDateFormat(), locale));
    }

    /**
     * Resolve day-first ordering from an explicit date-format setting ("DD/MM/YYYY" → day-first,
     * "MM/DD/YYYY" → month-first), falling back to the locale convention when unset.
     */
    public static boolean isDayFirst(String dateFormat, Locale locale) {
        if (dateFormat != null && !dateFormat.isBlank()) {
            String upper = dateFormat.trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("DD")) {
                return true;
            }
            if (upper.startsWith("MM")) {
                return false;
            }
            // ISO-style ("YYYY-MM-DD") is unambiguous — either order works.
            return true;
        }
        return isDayFirstLocale(locale);
    }

    /**
     * Month-first numeric dates are essentially a US convention; most other locales are day-first.
     */
    private static boolean isDayFirstLocale(Locale locale) {
        return !("en".equals(locale.getLanguage()) && "US".equals(locale.getCountry()));
    }
}
