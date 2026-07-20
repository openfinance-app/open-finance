package org.openfinance.service.parser;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared low-level helpers for the import parsers: charset detection, lenient amount parsing,
 * two-digit-year resolution, accent-insensitive text normalisation, currency-symbol mapping and
 * localised validation messages. Package-private on purpose — these are implementation details of
 * the parser package.
 */
@Slf4j
final class ImportParseSupport {

    private ImportParseSupport() {}

    // ------------------------------------------------------------------
    // Charset handling
    // ------------------------------------------------------------------

    /**
     * Decode file bytes honouring a BOM when present, strict UTF-8 otherwise, and falling back to
     * windows-1252 for legacy bank exports (French CSV/QIF files are frequently Latin-1/CP1252).
     */
    static String decode(byte[] bytes) {
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2) {
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
            }
            if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
            }
        }
        try {
            // Strict decode — throws on malformed input, proving the file is valid UTF-8
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            // Not valid UTF-8 — legacy single-byte export (accents encoded as CP1252).
            log.debug("File is not valid UTF-8; decoding as windows-1252");
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    // ------------------------------------------------------------------
    // Amount parsing
    // ------------------------------------------------------------------

    /**
     * Parse an amount tolerating both US ("1,234.56") and European ("1.234,56" / "1234,56")
     * formats. The right-most separator is treated as the decimal separator; a lone comma is a
     * decimal separator only when followed by one or two digits. Currency symbols ($, €, £) and
     * whitespace (including narrow no-break spaces used by French exports) are stripped.
     *
     * @return the parsed amount, or null when the value is blank or not numeric
     */
    static BigDecimal parseLenientAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return null;
        }
        try {
            String cleaned =
                    amountStr
                            .replace("$", "")
                            .replace("€", "")
                            .replace("£", "")
                            .replace(" ", "") // ASCII space
                            .replace(" ", "") // no-break space
                            .replace(" ", "") // narrow no-break space (French thousands)
                            .replace(" ", "") // thin space
                            .trim();

            int lastComma = cleaned.lastIndexOf(',');
            int lastDot = cleaned.lastIndexOf('.');
            if (lastComma >= 0 && lastDot >= 0) {
                if (lastComma > lastDot) {
                    // European: 1.234,56
                    cleaned = cleaned.replace(".", "").replace(",", ".");
                } else {
                    // US: 1,234.56
                    cleaned = cleaned.replace(",", "");
                }
            } else if (lastComma >= 0) {
                // Lone comma: decimal ("1234,56") when 1-2 digits follow, else thousands ("1,234")
                String afterComma = cleaned.substring(lastComma + 1);
                if (afterComma.length() <= 2 && afterComma.chars().allMatch(Character::isDigit)) {
                    cleaned = cleaned.replace(",", ".");
                } else {
                    cleaned = cleaned.replace(",", "");
                }
            }
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Date helpers
    // ------------------------------------------------------------------

    /** Matches a numeric date ending in a two-digit year, e.g. "01/15/95" or "6-1-01". */
    private static final Pattern SHORT_YEAR_DATE =
            Pattern.compile("^(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{2})$");

    /**
     * Expand a trailing two-digit year into four digits using a sliding pivot: the year is placed
     * in the current century and rolled back 100 years when that would land it more than one year
     * in the future. Legacy QIF files (the format predates 2000) routinely carry 19xx dates, while
     * Java's {@code yy} pattern would otherwise pin everything to 20xx.
     */
    static String expandTwoDigitYear(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        Matcher m = SHORT_YEAR_DATE.matcher(dateStr.trim());
        if (!m.matches()) {
            return dateStr;
        }
        int yy = Integer.parseInt(m.group(4));
        int currentYear = LocalDate.now().getYear();
        int year = (currentYear / 100) * 100 + yy;
        if (year > currentYear + 1) {
            year -= 100;
        }
        return m.group(1) + m.group(2) + m.group(3) + m.group(2) + year;
    }

    // ------------------------------------------------------------------
    // Text normalisation
    // ------------------------------------------------------------------

    /** Remove diacritical marks (é → e, ï → i) so header/keyword matching is accent-insensitive. */
    static String stripAccents(String text) {
        if (text == null) {
            return "";
        }
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /**
     * Normalise a CSV header cell for lookup: accent-stripped, lower-cased (locale-independent),
     * and reduced to alphanumerics so "Bénéficiaire", "beneficiaire" and "BENEFICIAIRE" all match.
     */
    static String normalizeHeaderKey(String header) {
        if (header == null) {
            return "";
        }
        return stripAccents(header).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------
    // Currency symbols
    // ------------------------------------------------------------------

    /**
     * Map a currency symbol or shorthand to an ISO 4217 code. Returns null when the symbol is not
     * recognised. Note: "$" is assumed USD and "CFA" is assumed XOF (West African CFA) — both are
     * ambiguous in theory, but they are the dominant usage in practice.
     */
    static String currencyCodeForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return switch (symbol.trim()) {
            case "€" -> "EUR";
            case "$" -> "USD";
            default -> "CFA".equalsIgnoreCase(symbol.trim()) ? "XOF" : null;
        };
    }

    // ------------------------------------------------------------------
    // Localised validation messages
    // ------------------------------------------------------------------

    private static final Map<String, String> FALLBACK_MESSAGES =
            Map.of(
                    "import.validation.date.required", "Transaction date is required",
                    "import.validation.amount.required", "Transaction amount is required",
                    "import.validation.amount.zero", "Transaction amount cannot be zero",
                    "import.validation.date.future", "Transaction date cannot be in the future",
                    "import.validation.split.mismatch",
                            "Split amounts ({0}) do not match transaction amount ({1})");

    /**
     * Resolve a validation message from {@code i18n/messages*.properties} for the given locale,
     * without depending on a Spring context (parsers are also used standalone in tests).
     */
    static String message(String key, Locale locale, Object... args) {
        Locale effective = locale != null ? locale : Locale.ENGLISH;
        String pattern = null;
        try {
            ResourceBundle bundle =
                    ResourceBundle.getBundle(
                            "i18n.messages",
                            effective,
                            ResourceBundle.Control.getNoFallbackControl(
                                    ResourceBundle.Control.FORMAT_DEFAULT));
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            // fall through to hard-coded English fallback
        }
        if (pattern == null) {
            pattern = FALLBACK_MESSAGES.getOrDefault(key, key);
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, effective).format(args);
    }
}
