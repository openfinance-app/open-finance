package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.FileUploadResponse;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.service.UserService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for FileUploadController. Tests file upload endpoint with authentication and
 * validation.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("FileUploadController Integration Tests")
class FileUploadControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        // Register test user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("testuser")
                        .email("testuser@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        // Login to get JWT token
        LoginRequest login =
                LoginRequest.builder()
                        .username("testuser")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        token = objectMapper.readTree(resp).get("token").asText();
    }

    @Test
    @DisplayName("Should upload valid QIF file successfully")
    void shouldUploadValidQifFileSuccessfully() throws Exception {
        // Given
        String qifContent = "!Type:Bank\nD01/15/2024\nT-100.00\nPGrocery Store\n^\n";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.qif", "text/plain", qifContent.getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").exists())
                .andExpect(jsonPath("$.fileName").value("transactions.qif"))
                .andExpect(jsonPath("$.fileSize").value(qifContent.getBytes().length))
                .andExpect(jsonPath("$.fileType").value("qif"))
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.message").value("File uploaded successfully"))
                .andExpect(jsonPath("$.uploadedAt").exists());
    }

    @Test
    @DisplayName("Should upload valid OFX file successfully")
    void shouldUploadValidOfxFileSuccessfully() throws Exception {
        // Given
        String ofxContent =
                "<?xml version=\"1.0\"?>\n<OFX>\n<SIGNONMSGSRSV1>\n</SIGNONMSGSRSV1>\n</OFX>";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.ofx", "text/xml", ofxContent.getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").exists())
                .andExpect(jsonPath("$.fileName").value("transactions.ofx"))
                .andExpect(jsonPath("$.fileType").value("ofx"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    @DisplayName("Should upload valid CSV file successfully")
    void shouldUploadValidCsvFileSuccessfully() throws Exception {
        // Given
        String csvContent = "Date,Description,Amount\n2024-01-15,Grocery,100.00\n";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.csv", "text/csv", csvContent.getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").exists())
                .andExpect(jsonPath("$.fileName").value("transactions.csv"))
                .andExpect(jsonPath("$.fileType").value("csv"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    @DisplayName("Should reject file with invalid extension")
    void shouldRejectFileWithInvalidExtension() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "document.pdf", "application/pdf", "PDF content here".getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("not allowed")));
    }

    @Test
    @DisplayName("Should reject empty file")
    void shouldRejectEmptyFile() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile("file", "empty.qif", "text/plain", new byte[0]);

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.message").value("File is empty"));
    }

    @Test
    @DisplayName("Should reject oversized file")
    void shouldRejectOversizedFile() throws Exception {
        // Given - Create 11MB file (exceeds 10MB limit)
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file =
                new MockMultipartFile("file", "large.qif", "text/plain", largeContent);

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(
                        jsonPath("$.message")
                                .value(containsString("exceeds maximum allowed size")));
    }

    @Test
    @DisplayName("Should reject file with malicious content")
    void shouldRejectFileWithMaliciousContent() throws Exception {
        // Given
        String maliciousContent = "!Type:Bank\n<script>alert('xss')</script>\nD01/15/2024\n";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "malicious.qif", "text/plain", maliciousContent.getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(
                        jsonPath("$.message").value("File contains potentially malicious content"));
    }

    @Test
    @DisplayName("Should reject file with directory traversal")
    void shouldRejectFileWithDirectoryTraversal() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "../../../etc/passwd.qif", "text/plain", "!Type:Bank\n".getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Invalid file name: directory traversal not allowed"));
    }

    @Test
    @DisplayName("Should return 403 without authentication")
    void shouldReturn403WithoutAuthentication() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.qif", "text/plain", "!Type:Bank\n".getBytes());

        // When & Then
        mockMvc.perform(multipart("/api/v1/import/upload").file(file))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 with invalid token")
    void shouldReturn403WithInvalidToken() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.qif", "text/plain", "!Type:Bank\n".getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer invalid_token"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should handle missing file parameter")
    void shouldHandleMissingFileParameter() throws Exception {
        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return correct content type")
    void shouldReturnCorrectContentType() throws Exception {
        // Given
        String qifContent = "!Type:Bank\nD01/15/2024\nT-100.00\n^\n";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.qif", "text/plain", qifContent.getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Should handle QFX file as OFX")
    void shouldHandleQfxFileAsOfx() throws Exception {
        // Given
        String qfxContent = "<?xml version=\"1.0\"?>\n<OFX>\n</OFX>";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.qfx", "text/xml", qfxContent.getBytes());

        // When & Then
        mockMvc.perform(
                        multipart("/api/v1/import/upload")
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("transactions.qfx"))
                .andExpect(jsonPath("$.fileType").value("ofx")) // QFX detected as OFX
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    @DisplayName("Should upload multiple files sequentially")
    void shouldUploadMultipleFilesSequentially() throws Exception {
        // Given
        MockMultipartFile file1 =
                new MockMultipartFile("file", "test1.qif", "text/plain", "!Type:Bank\n".getBytes());
        MockMultipartFile file2 =
                new MockMultipartFile(
                        "file",
                        "test2.csv",
                        "text/csv",
                        "Date,Amount\n2024-01-15,100\n".getBytes());

        // When & Then - Upload first file
        String response1 =
                mockMvc.perform(
                                multipart("/api/v1/import/upload")
                                        .file(file1)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        FileUploadResponse uploadResponse1 =
                objectMapper.readValue(response1, FileUploadResponse.class);

        // Upload second file
        String response2 =
                mockMvc.perform(
                                multipart("/api/v1/import/upload")
                                        .file(file2)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        FileUploadResponse uploadResponse2 =
                objectMapper.readValue(response2, FileUploadResponse.class);

        // Verify different upload IDs
        assert !uploadResponse1.getUploadId().equals(uploadResponse2.getUploadId());
    }
}
