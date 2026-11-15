package org.openfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.config.ImportProperties;
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
        fileValidationService =
                new FileValidationService(
                        ".qif,.ofx,.qfx,.csv,.json", "10MB", new ImportProperties());
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
                new FileValidationService(
                        ".qif,.ofx,.qfx,.csv,.json", "512KB", new ImportProperties());

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
                new FileValidationService(
                        ".qif,.ofx,.qfx,.csv,.json", "1KB", new ImportProperties());
        byte[] content = new byte[1024]; // Exactly 1KB
        MultipartFile file = new MockMultipartFile("file", "exact.qif", "text/plain", content);

        // When
        FileValidationService.ValidationResult result = service.validate(file);

        // Then
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject Skrooge JSON file when the feature is disabled via property")
    void shouldRejectJsonFileWhenSkroogeJsonDisabled() {
        // Given
        ImportProperties importProperties = new ImportProperties();
        importProperties.setSkroogeJsonEnabled(false);
        FileValidationService service =
                new FileValidationService(".qif,.ofx,.qfx,.csv,.json", "10MB", importProperties);
        String jsonContent = "{\"account\":[],\"operation\":[]}";
        MultipartFile file =
                new MockMultipartFile(
                        "file", "export.json", "application/json", jsonContent.getBytes());

        // When
        FileValidationService.ValidationResult result = service.validate(file);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("disabled"));
    }

    @Test
    @DisplayName("Should reject an executable (MZ) disguised as a CSV")
    void shouldRejectExecutableSignature() {
        byte[] exe = "MZ this is not a spreadsheet".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("file", "evil.csv", "text/csv", exe);
        FileValidationService.ValidationResult result = fileValidationService.validate(file);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("malicious"));
    }

    @Test
    @DisplayName("Should reject a ZIP (PK) disguised as a CSV")
    void shouldRejectZipSignature() {
        byte[] zip = "PK\u0003\u0004 archive body".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("file", "evil.csv", "text/csv", zip);
        assertFalse(fileValidationService.validate(file).isValid());
    }

    @Test
    @DisplayName("Should reject filenames without a usable extension")
    void shouldRejectExtensionlessFilenames() {
        byte[] csv = "a,b\n1,2".getBytes(StandardCharsets.UTF_8);
        assertFalse(
                fileValidationService
                        .validate(new MockMultipartFile("file", "noextension", "text/csv", csv))
                        .isValid());
        assertFalse(
                fileValidationService
                        .validate(new MockMultipartFile("file", "trailingdot.", "text/csv", csv))
                        .isValid());
        // Hidden-file style name: the dot is at index 0, so there is no extension either.
        assertFalse(
                fileValidationService
                        .validate(new MockMultipartFile("file", ".csv", "text/csv", csv))
                        .isValid());
    }

    @Test
    @DisplayName("Should accept an uppercase file extension")
    void shouldAcceptUppercaseExtension() {
        byte[] csv = "a,b\n1,2".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("file", "EXPORT.CSV", "text/csv", csv);
        assertTrue(fileValidationService.validate(file).isValid());
    }

    @Test
    @DisplayName("Should fail closed when the upload stream cannot be read")
    void shouldFailClosedOnUnreadableUpload() {
        MultipartFile broken =
                new MockMultipartFile("file", "data.csv", "text/csv", "a,b".getBytes()) {
                    @Override
                    public InputStream getInputStream() throws IOException {
                        throw new IOException("disk gone");
                    }
                };
        FileValidationService.ValidationResult result = fileValidationService.validate(broken);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Failed to read"));
    }

    @Test
    @DisplayName("Should detect unknown format for an empty-content stream")
    void shouldDetectUnknownForEmptyStream() throws IOException {
        // MockMultipartFile.isEmpty() short-circuits validate(); detectFileFormat itself must cope.
        MultipartFile file =
                new MockMultipartFile("file", "data.csv", "text/csv", new byte[0]) {
                    @Override
                    public boolean isEmpty() {
                        return false;
                    }
                };
        assertEquals("unknown", fileValidationService.detectFileFormat(file));
    }

    @Test
    @DisplayName("Should parse GB and bare-byte size limits")
    void shouldParseGbAndBareByteSizes() {
        FileValidationService gbService =
                new FileValidationService(".csv", "1GB", new ImportProperties());
        byte[] csv = "a,b\n1,2".getBytes(StandardCharsets.UTF_8);
        assertTrue(
                gbService
                        .validate(new MockMultipartFile("file", "data.csv", "text/csv", csv))
                        .isValid());

        FileValidationService byteService =
                new FileValidationService(".csv", "4", new ImportProperties());
        FileValidationService.ValidationResult result =
                byteService.validate(new MockMultipartFile("file", "data.csv", "text/csv", csv));
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("exceeds maximum allowed size (4 bytes)"));
    }

    @Test
    @DisplayName("Should reject malformed max-size configuration at startup")
    void shouldRejectMalformedSizeConfig() {
        assertThrows(
                NumberFormatException.class,
                () -> new FileValidationService(".csv", "10.5MB", new ImportProperties()));
        assertThrows(
                NumberFormatException.class,
                () -> new FileValidationService(".csv", "ten", new ImportProperties()));
    }

    @Test
    @DisplayName("Malicious scan only inspects the first 100 lines (documented performance bound)")
    void maliciousScanIsBoundedToFirst100Lines() {
        StringBuilder padded = new StringBuilder("a,b\n");
        for (int i = 0; i < 110; i++) {
            padded.append("x,y\n");
        }
        padded.append("<script>alert(1)</script>\n");
        MultipartFile file =
                new MockMultipartFile(
                        "file",
                        "padded.csv",
                        "text/csv",
                        padded.toString().getBytes(StandardCharsets.UTF_8));
        // The script tag sits past the 100-line scan window, so this passes validation today.
        // Downstream parsers treat rows as data; pinning the bound makes the limit explicit.
        assertTrue(fileValidationService.validate(file).isValid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "tr-TR"})
    @DisplayName(
            "Uppercase file extensions and script markers are interpreted independently of server locale")
    void validatesMachineTokensWithoutLocaleDependence(String language) {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag(language));
        try {
            MockMultipartFile valid =
                    new MockMultipartFile(
                            "file",
                            "statement.QIF",
                            "text/plain",
                            "!Type:Bank\nD01/15/2024\nT1\n^".getBytes(StandardCharsets.UTF_8));
            assertTrue(fileValidationService.validate(valid).isValid());
            MockMultipartFile invalid =
                    new MockMultipartFile(
                            "file",
                            "statement.csv",
                            "text/csv",
                            "date,description,amount\n2024-01-15,JAVASCRIPT:alert(1),1"
                                    .getBytes(StandardCharsets.UTF_8));
            assertFalse(fileValidationService.validate(invalid).isValid());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"operation", "suboperation", "account"})
    @DisplayName("Skrooge recognition works with any identifying collection first")
    void recognizesAlternativeJsonCollectionOrder(String collection) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "export.json",
                        "application/json",
                        ("{\"" + collection + "\":[]}").getBytes(StandardCharsets.UTF_8));
        assertEquals("json", fileValidationService.detectFileFormat(file));
    }

    @Test
    @DisplayName("An OFX root on the first line is recognized without a declaration")
    void recognizesInlineOfxRoot() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "statement.ofx",
                        "text/plain",
                        " <OFX><BANKMSGSRSV1></BANKMSGSRSV1></OFX>"
                                .getBytes(StandardCharsets.UTF_8));
        assertEquals("ofx", fileValidationService.detectFileFormat(file));
    }

    @Test
    @DisplayName("A whitespace-only filename is rejected even when the file has valid content")
    void rejectsBlankFilename() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        " ",
                        "text/plain",
                        "!Type:Bank\n^".getBytes(StandardCharsets.UTF_8));
        assertEquals(
                "File name is missing", fileValidationService.validate(file).getErrorMessage());
    }
}
