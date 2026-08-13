package org.openfinance.util;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared helper for keyword search fields that can optionally be treated as a regular expression.
 *
 * <p>Used by every in-memory keyword search (accounts, assets, liabilities, real estate, recurring
 * transactions, transactions, and global search) since sensitive fields are encrypted at rest and
 * cannot be matched at the SQL level.
 */
public final class RegexSearchUtil {

    private RegexSearchUtil() {}

    /**
     * Returns true when {@code text} matches {@code query}.
     *
     * <p>In literal mode (default), this is a case-insensitive substring match, mirroring the
     * previous {@code text.toLowerCase().contains(query.toLowerCase())} behavior. In regex mode,
     * {@code query} is compiled as a case-insensitive regular expression and matched anywhere
     * within {@code text} (partial match, like {@link java.util.regex.Matcher#find()}).
     *
     * <p>An invalid regex pattern never matches (returns false) rather than throwing, so a
     * mid-typing/malformed pattern simply yields no results instead of an error.
     *
     * @param text the text to search within (null-safe)
     * @param query the literal substring or regex pattern to search for (null/empty-safe)
     * @param regex whether {@code query} should be treated as a regular expression
     * @return true if {@code text} matches {@code query} under the selected mode
     */
    public static boolean matches(String text, String query, boolean regex) {
        if (text == null || query == null || query.isEmpty()) {
            return false;
        }
        if (regex) {
            try {
                return Pattern.compile(query, Pattern.CASE_INSENSITIVE).matcher(text).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
        return text.toLowerCase().contains(query.toLowerCase());
    }
}
