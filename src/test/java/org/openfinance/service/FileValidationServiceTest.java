package org.openfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for FileValidationService. Tests file validation logic including size, type, format
 * detection, and security checks.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@DisplayName("FileValidationService Tests")
class FileValidationServiceTest {

    private FileValidationService fileValidationService;

    @BeforeEach
    void setUp() {
        fileValidationService = new FileValidationService(".qif,.ofx,.qfx,.csv,.json", "10MB");
    }

    @Test
    @DisplayName("Should validate QIF file successfully")
    void shouldValidateQifFileSuccessfully() {
        // Given
        String qifContent = "!Type:Bank\nD01/15/2024\nT-100.00\nPGrocery Store\n^\n";
        MultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.qif", "text/plain", qifContent.getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertTrue(result.isValid());
        assertEquals("qif", result.getDetectedFormat());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("Should validate OFX file successfully")
    void shouldValidateOfxFileSuccessfully() {
        // Given
        String ofxContent =
                "<?xml version=\"1.0\"?>\n<OFX>\n<SIGNONMSGSRSV1>\n</SIGNONMSGSRSV1>\n</OFX>";
        MultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.ofx", "text/xml", ofxContent.getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertTrue(result.isValid());
        assertEquals("ofx", result.getDetectedFormat());
    }

    @Test
    @DisplayName("Should validate CSV file successfully")
    void shouldValidateCsvFileSuccessfully() {
        // Given
        String csvContent = "Date,Description,Amount\n2024-01-15,Grocery,100.00\n";
        MultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.csv", "text/csv", csvContent.getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertTrue(result.isValid());
        assertEquals("csv", result.getDetectedFormat());
    }

    @Test
    @DisplayName("Should validate Skrooge JSON file successfully")
    void shouldValidateSkroogeJsonFileSuccessfully() {
        String jsonContent =
                """
                {
                  "account": [],
                  "operation": [],
                  "suboperation": []
                }
                """;
        MultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.json", "application/json", jsonContent.getBytes());

        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        assertTrue(result.isValid());
        assertEquals("json", result.getDetectedFormat());
    }

    @Test
    @DisplayName("Should allow Skrooge JSON with embedded HTML and SQL history")
    void shouldAllowSkroogeJsonWithEmbeddedHtmlAndSqlHistory() {
        String jsonContent =
                """
                {
                    "account": [],
                    "operation": [],
                    "suboperation": [],
                    "document": [
                        {
                            "t_value": "<html><body><script src='https://code.jquery.com/jquery.js'></script></body></html>"
                        },
                        {
                            "t_sqlorder": "DELETE FROM operation WHERE id=2758"
                        }
                    ]
                }
                """;
        MultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.json", "application/json", jsonContent.getBytes());

        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        assertTrue(result.isValid());
        assertEquals("json", result.getDetectedFormat());
    }

    @Test
    @DisplayName("Should detect JSON before embedded OFX markers")
    void shouldDetectJsonBeforeEmbeddedOfxMarkers() throws IOException {
        String jsonContent =
                """
                {"account":[],"operation":[],"suboperation":[],"document":[{"t_value":"<?xml version='1.0'?><OFX><script src='https://example.com/x.js'></script></OFX>"}]}
                """;
        MultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.json", "application/json", jsonContent.getBytes());

        assertEquals("json", fileValidationService.detectFileFormat(file));
        assertTrue(fileValidationService.validate(file).isValid());
    }

    @Test
    @DisplayName("Should reject invalid JSON file")
    void shouldRejectInvalidJsonFile() {
        MultipartFile file =
                new MockMultipartFile(
                        "file",
                        "transactions.json",
                        "application/json",
                        "{\"account\": [}".getBytes());

        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        assertFalse(result.isValid());
        assertEquals("File contains invalid JSON", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should reject empty file")
    void shouldRejectEmptyFile() {
        // Given
        MultipartFile file = new MockMultipartFile("file", "empty.qif", "text/plain", new byte[0]);

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertFalse(result.isValid());
        assertEquals("File is empty", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should reject file exceeding size limit")
    void shouldRejectFileExceedingSizeLimit() {
        // Given
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MultipartFile file = new MockMultipartFile("file", "large.qif", "text/plain", largeContent);

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    @DisplayName("Should reject invalid file extension")
    void shouldRejectInvalidFileExtension() {
        // Given
        MultipartFile file =
                new MockMultipartFile(
                        "file", "document.pdf", "application/pdf", "PDF content".getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("not allowed"));
    }

    @Test
    @DisplayName("Should reject file with directory traversal in name")
    void shouldRejectFileWithDirectoryTraversal() {
        // Given
        MultipartFile file =
                new MockMultipartFile(
                        "file", "../../../etc/passwd.qif", "text/plain", "!Type:Bank\n".getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertFalse(result.isValid());
        assertEquals(
                "Invalid file name: directory traversal not allowed", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should reject file with script tags")
    void shouldRejectFileWithScriptTags() {
        // Given
        String maliciousContent = "!Type:Bank\n<script>alert('xss')</script>\nD01/15/2024\n";
        MultipartFile file =
                new MockMultipartFile(
                        "file", "malicious.qif", "text/plain", maliciousContent.getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertFalse(result.isValid());
        assertEquals("File contains potentially malicious content", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should reject file with SQL injection patterns")
    void shouldRejectFileWithSqlInjection() {
        // Given
        String maliciousContent = "!Type:Bank\nDROP TABLE users;\nD01/15/2024\n";
        MultipartFile file =
                new MockMultipartFile(
                        "file", "malicious.qif", "text/plain", maliciousContent.getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertFalse(result.isValid());
        assertEquals("File contains potentially malicious content", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should reject file with missing name")
    void shouldRejectFileWithMissingName() {
        // Given
        MultipartFile file =
                new MockMultipartFile("file", null, "text/plain", "content".getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertFalse(result.isValid());
        assertEquals("File name is missing", result.getErrorMessage());
    }

    @Test
    @DisplayName("Should detect QIF format by content")
    void shouldDetectQifFormatByContent() throws IOException {
        // Given
        String qifContent = "!Type:CCard\nD01/15/2024\nT-50.00\n^\n";
        MultipartFile file =
                new MockMultipartFile("file", "test.qif", "text/plain", qifContent.getBytes());

        // When
        String detectedFormat = fileValidationService.detectFileFormat(file);

        // Then
        assertEquals("qif", detectedFormat);
    }

    @Test
    @DisplayName("Should detect OFX format by OFXHEADER")
    void shouldDetectOfxFormatByHeader() throws IOException {
        // Given
        String ofxContent = "OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\n";
        MultipartFile file =
                new MockMultipartFile("file", "test.ofx", "text/plain", ofxContent.getBytes());

        // When
        String detectedFormat = fileValidationService.detectFileFormat(file);

        // Then
        assertEquals("ofx", detectedFormat);
    }

    @Test
    @DisplayName("Should detect CSV format by commas")
    void shouldDetectCsvFormatByCommas() throws IOException {
        // Given
        String csvContent = "Date,Payee,Amount\n2024-01-15,Store,100.00\n";
        MultipartFile file =
                new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        // When
        String detectedFormat = fileValidationService.detectFileFormat(file);

        // Then
        assertEquals("csv", detectedFormat);
    }

    @Test
    @DisplayName("Should detect Skrooge JSON format by core collections")
    void shouldDetectJsonFormatByCoreCollections() throws IOException {
        String jsonContent =
                """
                {
                  "bank": [],
                  "account": [],
                  "operation": [],
                  "suboperation": []
                }
                """;
        MultipartFile file =
                new MockMultipartFile(
                        "file", "test.json", "application/json", jsonContent.getBytes());

        String detectedFormat = fileValidationService.detectFileFormat(file);

        assertEquals("json", detectedFormat);
    }

    @Test
    @DisplayName("Should detect unknown format for unrecognized content")
    void shouldDetectUnknownFormat() throws IOException {
        // Given
        String unknownContent = "This is some random text without format markers\n";
        MultipartFile file =
                new MockMultipartFile("file", "test.txt", "text/plain", unknownContent.getBytes());

        // When
        String detectedFormat = fileValidationService.detectFileFormat(file);

        // Then
        assertEquals("unknown", detectedFormat);
    }

    @Test
    @DisplayName("Should handle null file gracefully")
    void shouldHandleNullFileGracefully() {
        // When & Then
        assertThrows(NullPointerException.class, () -> fileValidationService.validate(null));
    }

    @Test
    @DisplayName("Should validate QFX file as OFX format")
    void shouldValidateQfxFileAsOfx() {
        // Given
        String qfxContent = "<?xml version=\"1.0\"?>\n<OFX>\n</OFX>";
        MultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.qfx", "text/xml", qfxContent.getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertTrue(result.isValid());
        assertEquals("ofx", result.getDetectedFormat()); // QFX is detected as OFX
    }

    @Test
    @DisplayName("Should validate file with !Account QIF header")
    void shouldValidateFileWithAccountQifHeader() {
        // Given
        String qifContent = "!Account\nNChecking\nTBank\n^\n!Type:Bank\nD01/15/2024\n^\n";
        MultipartFile file =
                new MockMultipartFile("file", "accounts.qif", "text/plain", qifContent.getBytes());

        // When
        FileValidationService.ValidationResult result = fileValidationService.validate(file);

        // Then
        assertTrue(result.isValid());
        assertEquals("qif", result.getDetectedFormat());
    }

    @Test
    @DisplayName("Should parse file size correctly for KB")
    void shouldParseFileSizeKb() {
        // Given & When
        FileValidationService service =
                new FileValidationService(".qif,.ofx,.qfx,.csv,.json", "512KB");

        byte[] content = new byte[600 * 1024]; // 600KB
        MultipartFile file = new MockMultipartFile("file", "large.qif", "text/plain", content);

        // Then
        FileValidationService.ValidationResult result = service.validate(file);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    @DisplayName("Should allow file at exact size limit")
    void shouldAllowFileAtExactSizeLimit() {
        // Given
        FileValidationService service =
                new FileValidationService(".qif,.ofx,.qfx,.csv,.json", "1KB");
        byte[] content = new byte[1024]; // Exactly 1KB
        MultipartFile file = new MockMultipartFile("file", "exact.qif", "text/plain", content);

        // When
        FileValidationService.ValidationResult result = service.validate(file);

        // Then
        assertTrue(result.isValid());
    }
}
