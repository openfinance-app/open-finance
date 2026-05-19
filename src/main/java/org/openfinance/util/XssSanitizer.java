package org.openfinance.util;

import java.util.regex.Pattern;

/**
 * Utility class providing XSS (Cross-Site Scripting) sanitization for user-supplied free-text
 * inputs.
 *
 * <p>Requirement TASK-15.1.3: Prevent XSS attacks by stripping dangerous HTML constructs from any
 * string that will later be rendered in a browser. JPA parameterized queries already prevent SQL
 * injection; this utility handles the HTML/JavaScript injection vector.
 *
 * <p>This implementation uses a deny-list of known dangerous patterns rather than a full HTML
 * parser, which is sufficient for a single-node desktop application where structured HTML is never
 * expected in free-text fields (descriptions, notes, tags, etc.). For rich-text fields, replace
 * with the OWASP Java HTML Sanitizer library.
 *
 * <p>The sanitizer performs the following transformations in order:
 *
 * <ol>
 *   <li>Removes {@code <script>...</script>} blocks (case-insensitive, dotall)
 *   <li>Removes {@code <style>...</style>} blocks
 *   <li>Removes {@code <iframe>...</iframe>} blocks
 *   <li>Removes {@code <object>...</object>} and {@code <embed>} elements
 *   <li>Strips all remaining HTML tags
 *   <li>Removes {@code javascript:} URI schemes
 *   <li>Removes {@code on*=} event handler attributes (e.g. {@code onclick=})
 *   <li>Removes HTML entity encoding of angle brackets ({@code &lt;}, {@code &gt;})
 *   <li>Trims leading/trailing whitespace from the result
 * </ol>
 *
 * <p><strong>Usage:</strong>
 *
 * <pre>{@code
 * String safe = XssSanitizer.sanitize(userInput);
 * }</pre>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-03-20
 */
public final class XssSanitizer {

    // -----------------------------------------------------------------------
    // Pre-compiled patterns — class-level for efficiency
    // -----------------------------------------------------------------------

    /** Matches {@code <script ...>...</script>} blocks (case-insensitive, dotall). */
    private static final Pattern SCRIPT_TAG =
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches {@code <style ...>...</style>} blocks. */
    private static final Pattern STYLE_TAG =
            Pattern.compile("<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches {@code <iframe ...>...</iframe>} blocks. */
    private static final Pattern IFRAME_TAG =
            Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches {@code <object ...>...</object>} blocks. */
    private static final Pattern OBJECT_TAG =
            Pattern.compile("<object[^>]*>.*?</object>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches self-closing or opening {@code <embed>} tags. */
    private static final Pattern EMBED_TAG =
            Pattern.compile("<embed[^>]*/?>", Pattern.CASE_INSENSITIVE);

    /** Matches all remaining HTML tags after dangerous block elements are removed. */
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");

    /**
     * Matches {@code javascript:} URI scheme (with optional whitespace/encoding between
     * "javascript" and ":").
     */
    private static final Pattern JAVASCRIPT_PROTOCOL =
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);

    /** Matches inline event handler attributes such as {@code onclick=}, {@code onerror=}. */
    private static final Pattern EVENT_HANDLERS =
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    /** Matches the HTML entity {@code &lt;} (encoded less-than sign). */
    private static final Pattern HTML_LT = Pattern.compile("&lt;", Pattern.CASE_INSENSITIVE);

    /** Matches the HTML entity {@code &gt;} (encoded greater-than sign). */
    private static final Pattern HTML_GT = Pattern.compile("&gt;", Pattern.CASE_INSENSITIVE);

    /** Private constructor — utility class, not instantiable. */
    private XssSanitizer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Sanitizes a user-supplied string by removing all known XSS vectors.
     *
     * <p>Returns {@code null} if the input is {@code null}, and an empty string if the input is
     * blank after sanitization.
     *
     * <p>Requirement TASK-15.1.3: Input sanitization for XSS prevention.
     *
     * @param input the raw user-supplied string; may be {@code null}
     * @return the sanitized string, or {@code null} if input was {@code null}
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input;

        // Step 1: Remove dangerous block elements
        sanitized = SCRIPT_TAG.matcher(sanitized).replaceAll("");
        sanitized = STYLE_TAG.matcher(sanitized).replaceAll("");
        sanitized = IFRAME_TAG.matcher(sanitized).replaceAll("");
        sanitized = OBJECT_TAG.matcher(sanitized).replaceAll("");
        sanitized = EMBED_TAG.matcher(sanitized).replaceAll("");

        // Step 2: Strip all remaining HTML tags
        sanitized = HTML_TAGS.matcher(sanitized).replaceAll("");

        // Step 3: Remove dangerous URI schemes and event handlers
        sanitized = JAVASCRIPT_PROTOCOL.matcher(sanitized).replaceAll("");
        sanitized = EVENT_HANDLERS.matcher(sanitized).replaceAll("");

        // Step 4: Decode and re-remove encoded angle brackets
        sanitized = HTML_LT.matcher(sanitized).replaceAll("");
        sanitized = HTML_GT.matcher(sanitized).replaceAll("");

        return sanitized.trim();
    }

    /**
     * Sanitizes a string and returns {@code null} if the result is empty.
     *
     * <p>Useful for optional fields where an empty string after sanitization should be treated the
     * same as a missing value.
     *
     * @param input the raw user-supplied string; may be {@code null}
     * @return the sanitized non-empty string, or {@code null} if input was {@code null} or empty
     *     after sanitization
     */
    public static String sanitizeNullable(String input) {
        String result = sanitize(input);
        return (result == null || result.isEmpty()) ? null : result;
    }
}
