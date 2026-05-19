package org.openfinance.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class XssSanitizerTest {

    @Test
    void shouldReturnNullWhenInputIsNull() {
        // Given: null input
        // When/Then
        assertNull(XssSanitizer.sanitize(null));
    }

    @Test
    void shouldReturnCleanTextAsIsTrimmed() {
        // Given: clean text with leading/trailing spaces
        String input = "  Clean text  ";
        // When/Then
        assertEquals("Clean text", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldStripScriptTags() {
        // Given: <script>alert('xss')</script>
        String input = "<script>alert('xss')</script>Hello World";
        // When/Then
        assertEquals("Hello World", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldStripImgWithOnerrorAttribute() {
        // Given: <img onerror="alert(1)" src="x">
        String input = "<img onerror=\"alert(1)\" src=\"x\">Hello World";
        // When/Then
        // Step 1: Remove HTML tags <img ...>
        // Step 2: Remove onerror=
        assertEquals("Hello World", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldStripJavascriptProtocol() {
        // Given: javascript:alert(1)
        String input = "javascript:alert(1)";
        // When/Then
        assertEquals("alert(1)", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldTrimPlainTextWithSpaces() {
        // Given: plain text with spaces
        String input = "  some text  ";
        // When/Then
        assertEquals("some text", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldStripStyleTags() {
        // Given: <style>.x{color:red}</style>
        String input = "<style>.x{color:red}</style>Hello";
        // When/Then
        assertEquals("Hello", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldStripIframeTags() {
        // Given: <iframe src="evil.com"></iframe>
        String input = "<iframe src=\"evil.com\"></iframe>World";
        // When/Then
        assertEquals("World", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldReturnNullForSanitizeNullableWithNull() {
        // Given: sanitizeNullable with null
        // When/Then
        assertNull(XssSanitizer.sanitizeNullable(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @NullAndEmptySource
    void shouldReturnNullForSanitizeNullableWithBlankOrWhitespace(String input) {
        // Given: sanitizeNullable with blank/whitespace-only
        // When/Then
        assertNull(XssSanitizer.sanitizeNullable(input));
    }

    @Test
    void shouldStripMultipleTagsAndVectors() {
        // Given: complex input
        String input =
                "<script>alert(1)</script><b>Hello</b> <a href=\"#\" onclick=\"evil()\">Link</a> <img src=x onerror=alert(1)>";
        // When/Then
        // Expected result: Hello Link
        assertEquals("Hello Link", XssSanitizer.sanitize(input));
    }

    @Test
    void shouldStripEncodedAngleBrackets() {
        // Given: &lt;script&gt;
        String input = "&lt;script&gt;alert(1)&lt;/script&gt;";
        // When/Then
        assertEquals("scriptalert(1)/script", XssSanitizer.sanitize(input));
    }
}
