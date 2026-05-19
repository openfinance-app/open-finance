package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.KeyManagementService;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for AttachmentController.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>File upload (POST /api/v1/attachments)
 *   <li>File download (GET /api/v1/attachments/{id}/download)
 *   <li>Delete attachment (DELETE /api/v1/attachments/{id})
 *   <li>List attachments (GET /api/v1/attachments)
 *   <li>Get attachment metadata (GET /api/v1/attachments/{id})
 *   <li>Get storage statistics (GET /api/v1/attachments/stats)
 *   <li>Authentication and authorization
 *   <li>Validation and error handling
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("AttachmentController Integration Tests")
class AttachmentControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private KeyManagementService keyManagementService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;
    private String encKey;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        databaseCleanupService.execute();

        // Register user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("alice")
                        .email("alice@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        // Login and get token
        LoginRequest login =
                LoginRequest.builder()
                        .username("alice")
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

        // Derive encryption key
        User user =
                userRepository
                        .findByUsername("alice")
                        .orElseThrow(() -> new RuntimeException("User not found"));
        userId = user.getId();
        byte[] salt = Base64.getDecoder().decode(user.getMasterPasswordSalt());
        SecretKey secretKey = keyManagementService.deriveKey("Master123!".toCharArray(), salt);
        encKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    // ========== UPLOAD TESTS ==========

    @Test
    @DisplayName("POST /api/v1/attachments - upload attachment successfully")
    void shouldUploadAttachment() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "receipt.pdf",
                        "application/pdf",
                        "This is a test PDF receipt content".getBytes());

        // When/Then
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .param("description", "Receipt for grocery purchase")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.entityType").value("TRANSACTION"))
                .andExpect(jsonPath("$.entityId").value(100))
                .andExpect(jsonPath("$.fileName").value("receipt.pdf"))
                .andExpect(jsonPath("$.fileType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").isNumber())
                .andExpect(jsonPath("$.filePath").isString())
                .andExpect(jsonPath("$.uploadedAt").isString())
                .andExpect(jsonPath("$.description").value("Receipt for grocery purchase"));
    }

    @Test
    @DisplayName("POST /api/v1/attachments - upload image file")
    void shouldUploadImageFile() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "photo.jpg", "image/jpeg", "fake JPEG image content".getBytes());

        // When/Then
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file)
                                .param("entityType", "ASSET")
                                .param("entityId", "200")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("photo.jpg"))
                .andExpect(jsonPath("$.fileType").value("image/jpeg"))
                .andExpect(jsonPath("$.entityType").value("ASSET"));
    }

    @Test
    @DisplayName("POST /api/v1/attachments - missing encryption key header")
    void shouldReturn400WhenMissingEncryptionKey() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        // When/Then
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/attachments - empty file")
    void shouldReturn400WhenFileIsEmpty() throws Exception {
        // Given - empty file
        MockMultipartFile file =
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        // When/Then
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/attachments - invalid file type")
    void shouldReturn400WhenFileTypeNotAllowed() throws Exception {
        // Given - executable file (not in allowed types)
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "virus.exe",
                        "application/x-msdownload",
                        "malicious content".getBytes());

        // When/Then
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/attachments - missing JWT token")
    void shouldReturn401WhenMissingToken() throws Exception {
        // Given
        MockMultipartFile file =
                new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        // When/Then - No Authorization header
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isForbidden()); // Spring Security returns 403 when token is
        // missing
    }

    // ========== DOWNLOAD TESTS ==========

    @Test
    @DisplayName("GET /api/v1/attachments/{id}/download - download attachment successfully")
    void shouldDownloadAttachment() throws Exception {
        // Given - Upload a file first
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "receipt.pdf",
                        "application/pdf",
                        "Test PDF content for download".getBytes());

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/api/v1/attachments")
                                        .file(file)
                                        .param("entityType", "TRANSACTION")
                                        .param("entityId", "100")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey))
                        .andExpect(status().isCreated())
                        .andReturn();

        String uploadResponse = uploadResult.getResponse().getContentAsString();
        Long attachmentId = objectMapper.readTree(uploadResponse).get("id").asLong();

        // When/Then - Download the file
        mockMvc.perform(
                        get("/api/v1/attachments/" + attachmentId + "/download")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("receipt.pdf")))
                .andExpect(content().bytes("Test PDF content for download".getBytes()));
    }

    @Test
    @DisplayName("GET /api/v1/attachments/{id}/download - missing encryption key")
    void shouldReturn400WhenDownloadMissingEncryptionKey() throws Exception {
        // When/Then
        mockMvc.perform(
                        get("/api/v1/attachments/999/download")
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/attachments/{id}/download - attachment not found")
    void shouldReturn404WhenAttachmentNotFound() throws Exception {
        // When/Then
        mockMvc.perform(
                        get("/api/v1/attachments/999/download")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // ========== DELETE TESTS ==========

    @Test
    @DisplayName("DELETE /api/v1/attachments/{id} - delete attachment successfully")
    void shouldDeleteAttachment() throws Exception {
        // Given - Upload a file first
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "delete-me.pdf",
                        "application/pdf",
                        "File to be deleted".getBytes());

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/api/v1/attachments")
                                        .file(file)
                                        .param("entityType", "TRANSACTION")
                                        .param("entityId", "100")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey))
                        .andExpect(status().isCreated())
                        .andReturn();

        String uploadResponse = uploadResult.getResponse().getContentAsString();
        Long attachmentId = objectMapper.readTree(uploadResponse).get("id").asLong();

        // When - Delete the file
        mockMvc.perform(
                        delete("/api/v1/attachments/" + attachmentId)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Then - Verify file is deleted (404 on GET)
        mockMvc.perform(
                        get("/api/v1/attachments/" + attachmentId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/attachments/{id} - attachment not found")
    void shouldReturn404WhenDeletingNonExistentAttachment() throws Exception {
        // When/Then
        mockMvc.perform(
                        delete("/api/v1/attachments/999")
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/attachments/{id} - missing JWT token")
    void shouldReturn401WhenDeletingWithoutToken() throws Exception {
        // When/Then
        mockMvc.perform(delete("/api/v1/attachments/1"))
                .andDo(print())
                .andExpect(status().isForbidden()); // Spring Security returns 403 when token is
        // missing
    }

    // ========== LIST TESTS ==========

    @Test
    @DisplayName("GET /api/v1/attachments - list all user attachments")
    void shouldListAllUserAttachments() throws Exception {
        // Given - Upload 2 files
        MockMultipartFile file1 =
                new MockMultipartFile(
                        "file", "file1.pdf", "application/pdf", "content1".getBytes());
        MockMultipartFile file2 =
                new MockMultipartFile("file", "file2.jpg", "image/jpeg", "content2".getBytes());

        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file1)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file2)
                                .param("entityType", "ASSET")
                                .param("entityId", "200")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated());

        // When/Then - List all
        mockMvc.perform(get("/api/v1/attachments").header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].fileName", hasItems("file1.pdf", "file2.jpg")));
    }

    @Test
    @DisplayName("GET /api/v1/attachments?entityType=TRANSACTION&entityId=100 - filter by entity")
    void shouldListAttachmentsFilteredByEntity() throws Exception {
        // Given - Upload files to different entities
        MockMultipartFile file1 =
                new MockMultipartFile(
                        "file", "txn-file.pdf", "application/pdf", "content1".getBytes());
        MockMultipartFile file2 =
                new MockMultipartFile(
                        "file", "asset-file.jpg", "image/jpeg", "content2".getBytes());

        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file1)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file2)
                                .param("entityType", "ASSET")
                                .param("entityId", "200")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated());

        // When/Then - Filter by TRANSACTION entity
        mockMvc.perform(
                        get("/api/v1/attachments")
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("txn-file.pdf"))
                .andExpect(jsonPath("$[0].entityType").value("TRANSACTION"))
                .andExpect(jsonPath("$[0].entityId").value(100));
    }

    @Test
    @DisplayName("GET /api/v1/attachments?entityId=100 - validation error when entityType missing")
    void shouldReturn400WhenEntityIdWithoutEntityType() throws Exception {
        // When/Then
        mockMvc.perform(
                        get("/api/v1/attachments")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/attachments - empty list for new user")
    void shouldReturnEmptyListForNewUser() throws Exception {
        // When/Then - No files uploaded yet
        mockMvc.perform(get("/api/v1/attachments").header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ========== GET METADATA TESTS ==========

    @Test
    @DisplayName("GET /api/v1/attachments/{id} - get attachment metadata")
    void shouldGetAttachmentMetadata() throws Exception {
        // Given - Upload a file
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "metadata.pdf", "application/pdf", "Test content".getBytes());

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/api/v1/attachments")
                                        .file(file)
                                        .param("entityType", "LIABILITY")
                                        .param("entityId", "300")
                                        .param("description", "Loan document")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey))
                        .andExpect(status().isCreated())
                        .andReturn();

        String uploadResponse = uploadResult.getResponse().getContentAsString();
        Long attachmentId = objectMapper.readTree(uploadResponse).get("id").asLong();

        // When/Then - Get metadata
        mockMvc.perform(
                        get("/api/v1/attachments/" + attachmentId)
                                .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attachmentId))
                .andExpect(jsonPath("$.fileName").value("metadata.pdf"))
                .andExpect(jsonPath("$.fileType").value("application/pdf"))
                .andExpect(jsonPath("$.entityType").value("LIABILITY"))
                .andExpect(jsonPath("$.entityId").value(300))
                .andExpect(jsonPath("$.description").value("Loan document"))
                .andExpect(jsonPath("$.uploadedAt").isString());
    }

    @Test
    @DisplayName("GET /api/v1/attachments/{id} - attachment not found")
    void shouldReturn404WhenGettingNonExistentAttachment() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/attachments/999").header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // ========== STATS TESTS ==========

    @Test
    @DisplayName("GET /api/v1/attachments/stats - get storage statistics")
    void shouldGetStorageStatistics() throws Exception {
        // Given - Upload 2 files
        MockMultipartFile file1 =
                new MockMultipartFile(
                        "file", "stats1.pdf", "application/pdf", "content1".getBytes());
        MockMultipartFile file2 =
                new MockMultipartFile(
                        "file", "stats2.jpg", "image/jpeg", "content2 longer".getBytes());

        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file1)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "100")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(file2)
                                .param("entityType", "ASSET")
                                .param("entityId", "200")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated());

        // When/Then - Get stats
        mockMvc.perform(get("/api/v1/attachments/stats").header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttachments").value(2))
                .andExpect(jsonPath("$.totalSizeBytes").isNumber())
                .andExpect(jsonPath("$.totalSizeFormatted").isString());
    }

    @Test
    @DisplayName("GET /api/v1/attachments/stats - zero stats for new user")
    void shouldReturnZeroStatsForNewUser() throws Exception {
        // When/Then - No files uploaded
        mockMvc.perform(get("/api/v1/attachments/stats").header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttachments").value(0))
                .andExpect(jsonPath("$.totalSizeBytes").value(0))
                .andExpect(jsonPath("$.totalSizeFormatted").value("0 bytes"));
    }

    // ========== AUTHORIZATION TESTS ==========

    @Test
    @DisplayName("User cannot access other user's attachments")
    void shouldNotAccessOtherUsersAttachments() throws Exception {
        // Given - User 1 uploads a file
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "secret.pdf", "application/pdf", "private content".getBytes());

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/api/v1/attachments")
                                        .file(file)
                                        .param("entityType", "TRANSACTION")
                                        .param("entityId", "100")
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Key", encKey))
                        .andExpect(status().isCreated())
                        .andReturn();

        String uploadResponse = uploadResult.getResponse().getContentAsString();
        Long attachmentId = objectMapper.readTree(uploadResponse).get("id").asLong();

        // Create second user
        UserRegistrationRequest reg2 =
                UserRegistrationRequest.builder()
                        .username("bob")
                        .email("bob@example.com")
                        .password("password456")
                        .masterPassword("master456")
                        .build();
        userService.registerUser(reg2);

        LoginRequest login2 =
                LoginRequest.builder()
                        .username("bob")
                        .password("password456")
                        .masterPassword("master456")
                        .build();

        String resp2 =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login2)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String token2 = objectMapper.readTree(resp2).get("token").asText();

        // When/Then - User 2 tries to access User 1's attachment
        mockMvc.perform(
                        get("/api/v1/attachments/" + attachmentId)
                                .header("Authorization", "Bearer " + token2))
                .andDo(print())
                .andExpect(
                        status().isNotFound()); // Not found = unauthorized (don't expose existence)

        // User 2 tries to download User 1's attachment
        User user2 =
                userRepository
                        .findByUsername("bob")
                        .orElseThrow(() -> new RuntimeException("User not found"));
        byte[] salt2 = Base64.getDecoder().decode(user2.getMasterPasswordSalt());
        SecretKey secretKey2 = keyManagementService.deriveKey("master456".toCharArray(), salt2);
        String encKey2 = Base64.getEncoder().encodeToString(secretKey2.getEncoded());

        mockMvc.perform(
                        get("/api/v1/attachments/" + attachmentId + "/download")
                                .header("Authorization", "Bearer " + token2)
                                .header("X-Encryption-Key", encKey2))
                .andDo(print())
                .andExpect(status().isNotFound());

        // User 2 tries to delete User 1's attachment
        mockMvc.perform(
                        delete("/api/v1/attachments/" + attachmentId)
                                .header("Authorization", "Bearer " + token2))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // ========== ENTITY TYPE TESTS ==========

    @Test
    @DisplayName("POST /api/v1/attachments - upload to all entity types")
    void shouldUploadToAllEntityTypes() throws Exception {
        // Test TRANSACTION
        MockMultipartFile txnFile =
                new MockMultipartFile(
                        "file", "txn.pdf", "application/pdf", "transaction".getBytes());
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(txnFile)
                                .param("entityType", "TRANSACTION")
                                .param("entityId", "1")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityType").value("TRANSACTION"));

        // Test ASSET
        MockMultipartFile assetFile =
                new MockMultipartFile("file", "asset.jpg", "image/jpeg", "asset".getBytes());
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(assetFile)
                                .param("entityType", "ASSET")
                                .param("entityId", "2")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityType").value("ASSET"));

        // Test REAL_ESTATE
        MockMultipartFile realEstateFile =
                new MockMultipartFile(
                        "file", "property.pdf", "application/pdf", "real estate".getBytes());
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(realEstateFile)
                                .param("entityType", "REAL_ESTATE")
                                .param("entityId", "3")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityType").value("REAL_ESTATE"));

        // Test LIABILITY
        MockMultipartFile liabilityFile =
                new MockMultipartFile(
                        "file", "loan.pdf", "application/pdf", "liability".getBytes());
        mockMvc.perform(
                        multipart("/api/v1/attachments")
                                .file(liabilityFile)
                                .param("entityType", "LIABILITY")
                                .param("entityId", "4")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Key", encKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityType").value("LIABILITY"));
    }
}
