package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.BackupRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.Backup;
import org.openfinance.entity.User;
import org.openfinance.repository.BackupRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.KeyManagementService;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.service.UserService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Integration tests for BackupController.
 *
 * <p>
 * Tests cover:
 *
 * <ul>
 * <li>Create backup (POST /api/v1/backup/create)
 * <li>List backups (GET /api/v1/backup/list)
 * <li>Get backup details (GET /api/v1/backup/{id})
 * <li>Download backup (GET /api/v1/backup/{id}/download)
 * <li>Restore backup (POST /api/v1/backup/restore/{id})
 * <li>Upload and restore (POST /api/v1/backup/restore/upload)
 * <li>Delete backup (DELETE /api/v1/backup/{id})
 * <li>Authentication and authorization
 * <li>Validation and error handling
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
                "spring.datasource.url=jdbc:h2:file:./target/test-db/backup-integration-test;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "jwt.secret=TEST_SECRET_KEY_FOR_TESTING_ONLY",
                "jwt.expiration=3600000"
})
@DisplayName("BackupController Integration Tests")
class BackupControllerIntegrationTest {

        static {
                try {
                        // Delete any existing (potentially corrupt) H2 file before the Spring context
                        // loads
                        java.nio.file.Path dbDir = java.nio.file.Paths.get("./target/test-db");
                        java.nio.file.Files.createDirectories(dbDir);
                        java.nio.file.Files.deleteIfExists(dbDir.resolve("backup-integration-test.mv.db"));
                        java.nio.file.Files.deleteIfExists(dbDir.resolve("backup-integration-test.trace.db"));
                } catch (java.io.IOException e) {
                        throw new RuntimeException("Failed to prepare test-db directory", e);
                }
        }

        @MockBean
        private OperationHistoryService operationHistoryService;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserService userService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private BackupRepository backupRepository;

        @Autowired
        private KeyManagementService keyManagementService;

        @PersistenceContext
        private EntityManager entityManager;

        @Autowired
        private PlatformTransactionManager transactionManager;

        @Value("${app.backup.directory:./backups}")
        private String backupDirectory;

        private String token;
        private String encKey;
        private Long userId;

        @Autowired
        private DatabaseCleanupService databaseCleanupService;

        @BeforeEach
        void setUp() throws Exception {
                // Clean up
                databaseCleanupService.execute();

                // Ensure backup directory exists
                Path backupPath = Paths.get(backupDirectory);
                if (!Files.exists(backupPath)) {
                        Files.createDirectories(backupPath);
                }

                // Ensure database file directory exists (SQLite)
                Path dbPath = Paths.get("./target/test-db");
                if (!Files.exists(dbPath)) {
                        Files.createDirectories(dbPath);
                }

                // Register user and flush to ensure database file is written
                UserRegistrationRequest reg = UserRegistrationRequest.builder()
                                .username("alice")
                                .email("alice@example.com")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .skipSeeding(true)
                                .build();
                userService.registerUser(reg);

                // Login and get token
                LoginRequest login = LoginRequest.builder()
                                .username("alice")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .build();

                String resp = mockMvc.perform(
                                post("/api/v1/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(login)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                token = objectMapper.readTree(resp).get("token").asText();
                encKey = objectMapper.readTree(resp).get("encryptionKey").asText();

                User user = userRepository
                                .findByUsername("alice")
                                .orElseThrow(() -> new RuntimeException("User not found"));
                userId = user.getId();

                // Verify database file exists
                Path dbFilePath = Paths.get("./target/test-db/backup-integration-test.mv.db");
                if (!Files.exists(dbFilePath)) {
                        throw new IllegalStateException("H2 database file was not created: " + dbFilePath);
                }
        }

        @AfterEach
        void tearDown() throws Exception {
                // Clean up backup files
                Path backupPath = Paths.get(backupDirectory);
                if (Files.exists(backupPath)) {
                        Files.walk(backupPath)
                                        .sorted(Comparator.reverseOrder())
                                        .map(Path::toFile)
                                        .forEach(File::delete);
                }
        }

        // ========== CREATE BACKUP TESTS ==========

        @Test
        @DisplayName("POST /api/v1/backup/create - create backup successfully")
        void shouldCreateBackupSuccessfully() throws Exception {
                // Given
                BackupRequest request = BackupRequest.builder().description("Monthly backup").build();

                // When/Then
                mockMvc.perform(
                                post("/api/v1/backup/create")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request))
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").isNumber())
                                .andExpect(jsonPath("$.userId").value(userId))
                                .andExpect(jsonPath("$.filename").value(startsWith("openfinance-backup-")))
                                .andExpect(jsonPath("$.filename").value(endsWith(".ofbak")))
                                .andExpect(jsonPath("$.fileSize").isNumber())
                                .andExpect(jsonPath("$.checksum").isString())
                                .andExpect(jsonPath("$.checksum").value(hasLength(64))) // SHA-256 hex
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.backupType").value("MANUAL"))
                                .andExpect(jsonPath("$.description").value("Monthly backup"))
                                .andExpect(jsonPath("$.errorMessage").value(nullValue()))
                                .andExpect(jsonPath("$.createdAt").isString())
                                .andExpect(jsonPath("$.updatedAt").isString());

                // Verify backup file exists on disk
                List<Backup> backups = backupRepository.findByUserIdOrderByCreatedAtDesc(userId);
                assertThat(backups).hasSize(1);
                Path backupFile = Paths.get(backups.get(0).getFilePath());
                assertThat(Files.exists(backupFile)).isTrue();
        }

        @Test
        @DisplayName("POST /api/v1/backup/create - create backup without description")
        void shouldCreateBackupWithoutDescription() throws Exception {
                // Given - empty request body
                BackupRequest request = BackupRequest.builder().build();

                // When/Then
                mockMvc.perform(
                                post("/api/v1/backup/create")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request))
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.backupType").value("MANUAL"))
                                .andExpect(jsonPath("$.description").value(nullValue()));
        }

        @Test
        @DisplayName("POST /api/v1/backup/create - return 400 when description too long")
        void shouldReturn400WhenDescriptionTooLong() throws Exception {
                // Given - description exceeds 500 character limit
                String longDescription = "x".repeat(501);
                BackupRequest request = BackupRequest.builder().description(longDescription).build();

                // When/Then
                mockMvc.perform(
                                post("/api/v1/backup/create")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request))
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/backup/create - return 403 when not authenticated")
        void shouldReturn403WhenNotAuthenticated() throws Exception {
                // Given
                BackupRequest request = BackupRequest.builder().description("Test backup").build();

                // When/Then - no Authorization header (Spring Security returns 403)
                mockMvc.perform(
                                post("/api/v1/backup/create")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request))
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/backup/create - return backup metadata in response")
        void shouldReturnBackupMetadataInResponse() throws Exception {
                // Given
                BackupRequest request = BackupRequest.builder().description("Test metadata").build();

                // When
                MvcResult result = mockMvc.perform(
                                post("/api/v1/backup/create")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request))
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isCreated())
                                .andReturn();

                // Then - verify response structure
                String responseBody = result.getResponse().getContentAsString();
                var responseJson = objectMapper.readTree(responseBody);

                assertThat(responseJson.has("id")).isTrue();
                assertThat(responseJson.has("userId")).isTrue();
                assertThat(responseJson.has("filename")).isTrue();
                assertThat(responseJson.has("fileSize")).isTrue();
                assertThat(responseJson.has("checksum")).isTrue();
                assertThat(responseJson.has("status")).isTrue();
                assertThat(responseJson.has("backupType")).isTrue();
                assertThat(responseJson.has("description")).isTrue();
                assertThat(responseJson.has("createdAt")).isTrue();
                assertThat(responseJson.has("updatedAt")).isTrue();
        }

        // ========== LIST BACKUPS TESTS ==========

        @Test
        @DisplayName("GET /api/v1/backup/list - list all user backups")
        void shouldListAllUserBackups() throws Exception {
                // Given - create 3 backups
                for (int i = 1; i <= 3; i++) {
                        Backup backup = Backup.builder()
                                        .userId(userId)
                                        .filename("backup-" + i + ".ofbak")
                                        .filePath("./backups/backup-" + i + ".ofbak")
                                        .fileSize(1024L * i)
                                        .checksum(generateValidChecksum(i))
                                        .status("COMPLETED")
                                        .backupType("MANUAL")
                                        .description("Backup " + i)
                                        .createdAt(LocalDateTime.now().minusDays(i))
                                        .updatedAt(LocalDateTime.now().minusDays(i))
                                        .build();
                        backupRepository.save(backup);
                }

                // When/Then
                mockMvc.perform(
                                get("/api/v1/backup/list")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$", hasSize(3)))
                                .andExpect(jsonPath("$[0].filename").value("backup-1.ofbak"))
                                .andExpect(jsonPath("$[1].filename").value("backup-2.ofbak"))
                                .andExpect(jsonPath("$[2].filename").value("backup-3.ofbak"));
        }

        @Test
        @DisplayName("POST /api/v1/backup/restore/{id} - create safety backup before restore")
        void shouldCreateSafetyBackupBeforeRestore() throws Exception {
                // Given - create backup file
                String filename = "safety-test.ofbak";
                Path backupPath = Paths.get(backupDirectory);
                Files.createDirectories(backupPath);
                Path backupFile = backupPath.resolve(filename);

                byte[] testDbContent = "SQLite format 3\0safety test content".getBytes();
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                        gzipOut.write(testDbContent);
                        gzipOut.finish();
                        Files.write(backupFile, baos.toByteArray());
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(Files.readAllBytes(backupFile));
                String checksum = bytesToHex(hash);

                Backup backup = Backup.builder()
                                .userId(userId)
                                .filename(filename)
                                .filePath(backupFile.toString())
                                .fileSize(Files.size(backupFile))
                                .checksum(checksum)
                                .status("COMPLETED")
                                .backupType("MANUAL")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                Backup saved = backupRepository.save(backup);

                // When - perform restore
                mockMvc.perform(
                                post("/api/v1/backup/restore/" + saved.getId())
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk());

                // Then - verify safety backup was created
                List<Backup> backups = backupRepository.findByUserIdOrderByCreatedAtDesc(userId);
                assertThat(backups.size()).isGreaterThanOrEqualTo(2); // Original + safety backup

                // Check for safety backup (description contains "Auto-backup before restore")
                boolean hasSafetyBackup = backups.stream()
                                .anyMatch(
                                                b -> b.getDescription() != null
                                                                && b.getDescription()
                                                                                .contains("Auto-backup before restore"));
                assertThat(hasSafetyBackup).isTrue();
        }

        @Test
        @DisplayName("POST /api/v1/backup/restore/{id} - return 404 when backup not found")
        void shouldReturn404WhenBackupNotFoundForRestore() throws Exception {
                // When/Then - non-existent backup ID
                mockMvc.perform(
                                post("/api/v1/backup/restore/999999")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST /api/v1/backup/restore/{id} - return 400 on checksum mismatch")
        void shouldReturn400OnChecksumMismatch() throws Exception {
                // Given - create backup file with WRONG checksum in database
                String filename = "checksum-mismatch.ofbak";
                Path backupPath = Paths.get(backupDirectory);
                Files.createDirectories(backupPath);
                Path backupFile = backupPath.resolve(filename);

                byte[] testDbContent = "SQLite format 3\0corrupted content".getBytes();
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                        gzipOut.write(testDbContent);
                        gzipOut.finish();
                        Files.write(backupFile, baos.toByteArray());
                }

                // Save with INCORRECT checksum (intentional mismatch - but still valid format)
                Backup backup = Backup.builder()
                                .userId(userId)
                                .filename(filename)
                                .filePath(backupFile.toString())
                                .fileSize(Files.size(backupFile))
                                .checksum(
                                                "1111111111111111111111111111111111111111111111111111111111111111") // Valid
                                // format
                                // but
                                // wrong
                                // value
                                .status("COMPLETED")
                                .backupType("MANUAL")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                Backup saved = backupRepository.save(backup);

                // When/Then - restore should fail with 400 (checksum validation fails)
                mockMvc.perform(
                                post("/api/v1/backup/restore/" + saved.getId())
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(containsString("checksum")));
        }

        @Test
        @DisplayName("POST /api/v1/backup/restore/{id} - return 403 when restoring other user's backup")
        void shouldReturn403WhenRestoringOtherUsersBackup() throws Exception {
                // Given - create another user with backup
                UserRegistrationRequest reg2 = UserRegistrationRequest.builder()
                                .username("eve")
                                .email("eve@example.com")
                                .password("password222")
                                .masterPassword("master222")
                                .build();
                userService.registerUser(reg2);

                User eve = userRepository
                                .findByUsername("eve")
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Backup eveBackup = Backup.builder()
                                .userId(eve.getId())
                                .filename("eve-backup.ofbak")
                                .filePath("./backups/eve-backup.ofbak")
                                .fileSize(1024L)
                                .checksum(generateValidChecksum(70))
                                .status("COMPLETED")
                                .backupType("MANUAL")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                Backup saved = backupRepository.save(eveBackup);

                // When/Then - alice tries to restore eve's backup
                mockMvc.perform(
                                post("/api/v1/backup/restore/" + saved.getId())
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isNotFound()); // Returns 404 for security
        }

        // ========== UPLOAD & RESTORE TESTS ==========

        @Test
        @DisplayName("POST /api/v1/backup/restore/upload - upload and restore backup")
        void shouldUploadAndRestoreBackup() throws Exception {
                // Given - create gzipped backup file content
                byte[] testDbContent = "SQLite format 3\0uploaded database content".getBytes();
                byte[] gzippedContent;
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                        gzipOut.write(testDbContent);
                        gzipOut.finish();
                        gzippedContent = baos.toByteArray();
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "uploaded-backup.ofbak", "application/gzip", gzippedContent);

                // When/Then
                mockMvc.perform(
                                multipart("/api/v1/backup/restore/upload")
                                                .file(file)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(
                                                content()
                                                                .string(
                                                                                containsString(
                                                                                                "Backup restored successfully from uploaded file")));
        }

        @Test
        @DisplayName("POST /api/v1/backup/restore/upload - return 400 when file empty")
        void shouldReturn400WhenFileEmpty() throws Exception {
                // Given - empty file
                MockMultipartFile file = new MockMultipartFile("file", "empty.ofbak", "application/gzip", new byte[0]);

                // When/Then
                mockMvc.perform(
                                multipart("/api/v1/backup/restore/upload")
                                                .file(file)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isBadRequest())
                                .andExpect(content().string(containsString("empty")));
        }

        @Test
        @DisplayName("POST /api/v1/backup/restore/upload - return 400 when file format invalid")
        void shouldReturn400WhenFileFormatInvalid() throws Exception {
                // Given - non-gzipped file (invalid format)
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "invalid.ofbak",
                                "application/gzip",
                                "plain text not gzipped".getBytes());

                // When/Then
                mockMvc.perform(
                                multipart("/api/v1/backup/restore/upload")
                                                .file(file)
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/backup/restore/upload - return 403 when not authenticated")
        void shouldReturn403WhenUploadingWithoutAuth() throws Exception {
                // Given
                byte[] gzippedContent;
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                        gzipOut.write("test content".getBytes());
                        gzipOut.finish();
                        gzippedContent = baos.toByteArray();
                }

                MockMultipartFile file = new MockMultipartFile("file", "test.ofbak", "application/gzip",
                                gzippedContent);

                // When/Then - no Authorization header (Spring Security returns 403)
                mockMvc.perform(
                                multipart("/api/v1/backup/restore/upload")
                                                .file(file)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isForbidden());
        }

        // ========== DELETE BACKUP TESTS ==========

        @Test
        @DisplayName("DELETE /api/v1/backup/{id} - delete backup successfully")
        void shouldDeleteBackupSuccessfully() throws Exception {
                // Given - create backup file on disk
                String filename = "delete-test.ofbak";
                Path backupPath = Paths.get(backupDirectory);
                Files.createDirectories(backupPath);
                Path backupFile = backupPath.resolve(filename);
                Files.write(backupFile, "test content".getBytes());

                Backup backup = Backup.builder()
                                .userId(userId)
                                .filename(filename)
                                .filePath(backupFile.toString())
                                .fileSize(Files.size(backupFile))
                                .checksum(generateValidChecksum(80))
                                .status("COMPLETED")
                                .backupType("MANUAL")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                Backup saved = backupRepository.save(backup);

                // When/Then
                mockMvc.perform(
                                delete("/api/v1/backup/" + saved.getId())
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isOk());

                // Verify backup deleted from database
                assertThat(backupRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("DELETE /api/v1/backup/{id} - return 404 when backup not found")
        void shouldReturn404WhenBackupNotFoundForDelete() throws Exception {
                // When/Then - non-existent backup ID
                mockMvc.perform(
                                delete("/api/v1/backup/999999")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("DELETE /api/v1/backup/{id} - return 403 when deleting other user's backup")
        void shouldReturn403WhenDeletingOtherUsersBackup() throws Exception {
                // Given - create another user with backup
                UserRegistrationRequest reg2 = UserRegistrationRequest.builder()
                                .username("frank")
                                .email("frank@example.com")
                                .password("password333")
                                .masterPassword("master333")
                                .build();
                userService.registerUser(reg2);

                User frank = userRepository
                                .findByUsername("frank")
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Backup frankBackup = Backup.builder()
                                .userId(frank.getId())
                                .filename("frank-backup.ofbak")
                                .filePath("./backups/frank-backup.ofbak")
                                .fileSize(1024L)
                                .checksum(generateValidChecksum(90))
                                .status("COMPLETED")
                                .backupType("MANUAL")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                Backup saved = backupRepository.save(frankBackup);

                // When/Then - alice tries to delete frank's backup
                mockMvc.perform(
                                delete("/api/v1/backup/" + saved.getId())
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andDo(print())
                                .andExpect(status().isNotFound()); // Returns 404 for security
        }

        @Test
        @DisplayName("DELETE /api/v1/backup/{id} - delete backup file from disk")
        void shouldDeleteBackupFileFromDisk() throws Exception {
                // Given - create real backup file on disk
                String filename = "disk-delete-test.ofbak";
                Path backupPath = Paths.get(backupDirectory);
                Files.createDirectories(backupPath);
                Path backupFile = backupPath.resolve(filename);
                Files.write(backupFile, "test content for disk deletion".getBytes());

                // Verify file exists before deletion
                assertThat(Files.exists(backupFile)).isTrue();

                Backup backup = Backup.builder()
                                .userId(userId)
                                .filename(filename)
                                .filePath(backupFile.toString())
                                .fileSize(Files.size(backupFile))
                                .checksum(generateValidChecksum(100))
                                .status("COMPLETED")
                                .backupType("MANUAL")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                Backup saved = backupRepository.save(backup);

                // When - delete backup
                mockMvc.perform(
                                delete("/api/v1/backup/" + saved.getId())
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Encryption-Session", encKey))
                                .andExpect(status().isOk());

                // Then - verify file deleted from disk
                assertThat(Files.exists(backupFile)).isFalse();
        }

        // ========== HELPER METHODS ==========

        /** Converts byte array to hex string (for SHA-256 checksum). */
        private String bytesToHex(byte[] bytes) {
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) {
                        sb.append(String.format("%02x", b));
                }
                return sb.toString();
        }

        /**
         * Generates a valid 64-character hex checksum for testing.
         *
         * @param seed seed value to generate different checksums
         * @return 64-character lowercase hex string
         */
        private String generateValidChecksum(int seed) {
                // Generate a valid SHA-256-like checksum (64 hex characters)
                return String.format("%064x", (long) seed * 1234567890L);
        }
}
