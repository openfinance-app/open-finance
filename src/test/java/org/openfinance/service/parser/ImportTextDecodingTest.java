package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Bank export text keeps accents, currency symbols and historical dates")
class ImportTextDecodingTest {
    private static final String TEXT = "Date;Bénéficiaire;Montant\n15/06/2024;Épicerie;−1 234,56 €";

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "UTF-16LE", "UTF-16BE"})
    void byteOrderMarksAreConsumedAndDoNotPolluteTheFirstHeader(String encoding) {
        byte[] body = TEXT.getBytes(Charset.forName(encoding));
        byte[] bom =
                switch (encoding) {
                    case "UTF-8" -> new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
                    case "UTF-16LE" -> new byte[] {(byte) 0xff, (byte) 0xfe};
                    case "UTF-16BE" -> new byte[] {(byte) 0xfe, (byte) 0xff};
                    default -> throw new AssertionError(encoding);
                };
        byte[] file = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, file, 0, bom.length);
        System.arraycopy(body, 0, file, bom.length, body.length);
        assertThat(ImportParseSupport.decode(file)).isEqualTo(TEXT);
    }

    @Test
    void utf8WithoutAMarkIsPreferredOverLegacyEncoding() {
        assertThat(ImportParseSupport.decode(TEXT.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(TEXT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "é", "Épicerie – 12,34 €", "ÿ compte", "þ compte", "ï compte"})
    void legacyExportsAndShortFilesRemainReadable(String text) {
        assertThat(ImportParseSupport.decode(text.getBytes(Charset.forName("windows-1252"))))
                .isEqualTo(text);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                " 1 234,56789 € |1234.56789",
                "£-1,234.56|-1234.56",
                "1.234,56|1234.56",
                "0,000000000000000001|0.000000000000000001",
                "+1,234,567|1234567",
                "-0,125|-0.125",
                "1 234.50|1234.50"
            })
    void regionalSeparatorsRetainSignedSubcentPrecision(String input, String expected) {
        assertThat(ImportParseSupport.parseLenientAmount(input)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "€", "unknown", "1,2,3", "1.2.3"})
    void malformedAmountsCannotBecomeZero(String text) {
        assertThat(ImportParseSupport.parseLenientAmount(text)).isNull();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {" € |EUR", "$|USD", "£|GBP", "cFa|XOF", "CHF|", "?|"})
    void onlyKnownSymbolsAreInterpreted(String symbol, String expected) {
        assertThat(ImportParseSupport.currencyCodeForSymbol(symbol)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void absentSymbolsHaveNoImpliedCurrency(String symbol) {
        assertThat(ImportParseSupport.currencyCodeForSymbol(symbol)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "-", "."})
    void theTwoDigitYearPivotIncludesNextYearButRollsBackMoreDistantYears(String separator) {
        int year = LocalDate.now().getYear();
        for (int offset : new int[] {0, 1, 2}) {
            int shortYear = (year + offset) % 100;
            int expected = (year / 100) * 100 + shortYear;
            if (expected > year + 1) expected -= 100;
            String input =
                    " 1"
                            + separator
                            + "2"
                            + separator
                            + String.format(Locale.ROOT, "%02d", shortYear)
                            + " ";
            assertThat(ImportParseSupport.expandTwoDigitYear(input))
                    .isEqualTo("1" + separator + "2" + separator + expected);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2024-01-02", "1/2-24", "1/2/2024", "not a date"})
    void yearExpansionDoesNotRewriteOtherFormats(String input) {
        assertThat(ImportParseSupport.expandTwoDigitYear(input)).isEqualTo(input);
    }

    @Test
    void accentNormalizationAndUnknownMessagesHaveStableFallbacks() {
        assertThat(ImportParseSupport.normalizeHeaderKey(" BÉNÉFICIAIRE / N° "))
                .isEqualTo("beneficiairen");
        assertThat(ImportParseSupport.normalizeHeaderKey(null)).isEmpty();
        assertThat(ImportParseSupport.stripAccents(null)).isEmpty();
        assertThat(ImportParseSupport.message("nonexistent.key", null))
                .isEqualTo("nonexistent.key");
        assertThat(ImportParseSupport.message("import.validation.date.required", null))
                .contains("date");
        assertThat(
                        ImportParseSupport.message(
                                "import.validation.split.mismatch",
                                Locale.ENGLISH,
                                "12.34",
                                "56.78"))
                .contains("12.34", "56.78");
    }
}
