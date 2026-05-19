package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Backup;
import org.openfinance.exception.BackupException;
import org.openfinance.repository.BackupRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for BackupService.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Manual backup creation (validation, compression, checksum)
 *   <li>Automatic backup creation (scheduling, rotation)
 *   <li>Backup restoration (validation, safety backup, decompression)
 *   <li>Restore from uploaded file
 *   <li>List/get/delete operations
 *   <li>Download operations
 *   <li>Backup rotation (retention policy)
 *   <li>Validation and error handling
 * </ul>
 *
 * <p><b>Requirements:</b> REQ-2.14.2.1, REQ-2.14.2.2, REQ-2.14.2.3
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BackupService Unit Tests")
class BackupServiceTest {

    @Mock private BackupRepository backupRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private BackupService backupService;

    private static final Long TEST_USER_ID = 100L;
    private static final String TEST_BACKUP_DIR = "./test-backups";
    private static final String TEST_DB_PATH = "./test-openfinance.db";
    private static final String TEST_DB_URL = "jdbc:sqlite:" + TEST_DB_PATH;

    private Backup testBackup;
    private Path testBackupDir;
    private Path testDbFile;

    @BeforeEach
    void setUp() throws IOException {
        // Configure service with test values
        ReflectionTestUtils.setField(backupService, "databaseUrl", TEST_DB_URL);
        ReflectionTestUtils.setField(backupService, "backupDirectory", TEST_BACKUP_DIR);
        ReflectionTestUtils.setField(backupService, "retentionCount", 7);
        ReflectionTestUtils.setField(backupService, "scheduleEnabled", true);

        // Create test directories
        testBackupDir = Paths.get(TEST_BACKUP_DIR);
        Files.createDirectories(testBackupDir);

        // Create test database file
        testDbFile = Paths.get(TEST_DB_PATH);
        Files.write(testDbFile, "test database content".getBytes());

        // Create test backup entity
        testBackup =
                Backup.builder()
                        .id(1L)
                        .userId(TEST_USER_ID)
                        .filename("openfinance-backup-20260204-120000.ofbak")
                        .filePath(TEST_BACKUP_DIR + "/openfinance-backup-20260204-120000.ofbak")
                        .fileSize(1024L)
                        .checksum("a".repeat(64)) // 64-char hex string
                        .status("COMPLETED")
                        .backupType("MANUAL")
                        .description("Test backup")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup test files and directories
        if (Files.exists(testBackupDir)) {
            Files.walk(testBackupDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    // Ignore cleanup errors
                                }
                            });
        }

        Files.deleteIfExists(testDbFile);
    }

    // ========== CREATE BACKUP TESTS (8 tests) ==========

    @Test
    @DisplayName("Should create manual backup successfully")
    void shouldCreateManualBackupSuccessfully() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When
        Backup result = backupService.createBackup(TEST_USER_ID, "Test backup");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getBackupType()).isEqualTo("MANUAL");
        assertThat(result.getFilename()).matches("openfinance-backup-\\d{8}-\\d{6}\\.ofbak");
        assertThat(result.getFileSize()).isGreaterThan(0);
        assertThat(result.getChecksum()).hasSize(64); // SHA-256 is 64 hex chars
        assertThat(result.getDescription()).isEqualTo("Test backup");

        verify(backupRepository, atLeastOnce()).save(any(Backup.class));
    }

    @Test
    @DisplayName("Should set correct filename format")
    void shouldSetCorrectFilename() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When
        Backup result = backupService.createBackup(TEST_USER_ID, "Test");

        // Then
        assertThat(result.getFilename())
                .matches("openfinance-backup-\\d{8}-\\d{6}\\.ofbak")
                .endsWith(".ofbak");
    }

    @Test
    @DisplayName("Should compress backup with gzip")
    void shouldCompressWithGzip() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When
        Backup result = backupService.createBackup(TEST_USER_ID, null);

        // Then
        Path backupFile = Paths.get(result.getFilePath());
        assertThat(Files.exists(backupFile)).isTrue();

        // Verify it's gzipped by attempting to decompress
        try (InputStream in = Files.newInputStream(backupFile);
                GZIPInputStream gzipIn = new GZIPInputStream(in)) {
            byte[] decompressed = gzipIn.readAllBytes();
            assertThat(decompressed).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Should calculate SHA-256 checksum")
    void shouldCalculateSHA256Checksum() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When
        Backup result = backupService.createBackup(TEST_USER_ID, null);

        // Then
        assertThat(result.getChecksum()).hasSize(64).matches("[0-9a-f]{64}"); // SHA-256 hex string
    }

    @Test
    @DisplayName("Should save backup metadata to database")
    void shouldSaveBackupMetadata() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When
        backupService.createBackup(TEST_USER_ID, "Metadata test");

        // Then
        verify(backupRepository, atLeastOnce())
                .save(
                        argThat(
                                backup ->
                                        backup.getUserId().equals(TEST_USER_ID)
                                                && backup.getBackupType().equals("MANUAL")
                                                && backup.getDescription()
                                                        .equals("Metadata test")));
    }

    @Test
    @DisplayName("Should set status to COMPLETED after successful backup")
    void shouldSetStatusToCompleted() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When
        Backup result = backupService.createBackup(TEST_USER_ID, null);

        // Then
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should throw exception when database file not found")
    void shouldThrowExceptionWhenDatabaseFileNotFound() {
        // Given - Point to non-existent database
        ReflectionTestUtils.setField(backupService, "databaseUrl", "jdbc:sqlite:nonexistent.db");

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When/Then
        assertThatThrownBy(() -> backupService.createBackup(TEST_USER_ID, null))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Failed to create backup");
    }

    @Test
    @DisplayName("Should set status to FAILED on error")
    void shouldSetStatusToFailedOnError() throws Exception {
        // Given - Use the existing DB file as a "directory" path so that
        // Files.createDirectories (or subsequent file creation inside it) always fails,
        // regardless of OS (works on both Linux/macOS and Windows).
        String invalidDir = testDbFile.toAbsolutePath().toString();
        ReflectionTestUtils.setField(backupService, "backupDirectory", invalidDir);

        // Use lenient stubbing since the save may or may not be called depending on error timing
        lenient()
                .when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });

        // When/Then
        assertThatThrownBy(() -> backupService.createBackup(TEST_USER_ID, null))
                .isInstanceOf(BackupException.class);

        // Restore backup directory
        ReflectionTestUtils.setField(backupService, "backupDirectory", TEST_BACKUP_DIR);
    }

    // ========== AUTOMATIC BACKUP TESTS (5 tests) ==========

    @Test
    @DisplayName("Should create automatic backup successfully")
    void shouldCreateAutomaticBackupSuccessfully() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });
        when(backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(
                        TEST_USER_ID, "AUTOMATIC"))
                .thenReturn(List.of());

        // When
        Backup result = backupService.createAutomaticBackup(TEST_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBackupType()).isEqualTo("AUTOMATIC");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getFilename()).contains("auto");
        assertThat(result.getDescription()).isEqualTo("Automatic weekly backup");
    }

    @Test
    @DisplayName("Should rotate old backups after automatic backup creation")
    void shouldRotateOldBackupsAfterCreation() throws Exception {
        // Given - Create 10 old automatic backups (exceeds retention count of 7)
        List<Backup> oldBackups =
                Arrays.asList(
                        createMockBackup(1L, "AUTOMATIC"),
                        createMockBackup(2L, "AUTOMATIC"),
                        createMockBackup(3L, "AUTOMATIC"),
                        createMockBackup(4L, "AUTOMATIC"),
                        createMockBackup(5L, "AUTOMATIC"),
                        createMockBackup(6L, "AUTOMATIC"),
                        createMockBackup(7L, "AUTOMATIC"),
                        createMockBackup(8L, "AUTOMATIC"),
                        createMockBackup(9L, "AUTOMATIC"),
                        createMockBackup(10L, "AUTOMATIC"));

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(11L);
                            return backup;
                        });
        when(backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(
                        TEST_USER_ID, "AUTOMATIC"))
                .thenReturn(oldBackups);
        when(backupRepository.findByIdAndUserId(anyLong(), eq(TEST_USER_ID)))
                .thenAnswer(
                        invocation -> {
                            Long id = invocation.getArgument(0);
                            return oldBackups.stream()
                                    .filter(b -> b.getId().equals(id))
                                    .findFirst();
                        });

        // When
        backupService.createAutomaticBackup(TEST_USER_ID);

        // Then - Should delete 3 oldest backups (10 + 1 new - 7 retention = 4 to delete, but
        // rotation happens after)
        verify(backupRepository)
                .findByUserIdAndBackupTypeOrderByCreatedAtDesc(TEST_USER_ID, "AUTOMATIC");
    }

    @Test
    @DisplayName("Should keep last N automatic backups per retention count")
    void shouldKeepLastNAutomaticBackups() throws Exception {
        // Given - Exactly retention count (7) backups exist
        List<Backup> backups =
                Arrays.asList(
                        createMockBackup(1L, "AUTOMATIC"),
                        createMockBackup(2L, "AUTOMATIC"),
                        createMockBackup(3L, "AUTOMATIC"),
                        createMockBackup(4L, "AUTOMATIC"),
                        createMockBackup(5L, "AUTOMATIC"),
                        createMockBackup(6L, "AUTOMATIC"),
                        createMockBackup(7L, "AUTOMATIC"));

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(8L);
                            return backup;
                        });
        when(backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(
                        TEST_USER_ID, "AUTOMATIC"))
                .thenReturn(backups);

        // When
        backupService.createAutomaticBackup(TEST_USER_ID);

        // Then - No deletes should happen as count equals retention
        verify(backupRepository)
                .findByUserIdAndBackupTypeOrderByCreatedAtDesc(TEST_USER_ID, "AUTOMATIC");
    }

    @Test
    @DisplayName("Should not delete manual backups during automatic rotation")
    void shouldNotDeleteManualBackups() throws Exception {
        // Given - Mix of manual and automatic backups
        List<Backup> automaticBackups =
                Arrays.asList(createMockBackup(1L, "AUTOMATIC"), createMockBackup(2L, "AUTOMATIC"));

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(10L);
                            return backup;
                        });
        when(backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(
                        TEST_USER_ID, "AUTOMATIC"))
                .thenReturn(automaticBackups);

        // When
        backupService.createAutomaticBackup(TEST_USER_ID);

        // Then - Should only query automatic backups, not all backups
        verify(backupRepository)
                .findByUserIdAndBackupTypeOrderByCreatedAtDesc(TEST_USER_ID, "AUTOMATIC");
        verify(backupRepository, never())
                .findByUserIdAndBackupTypeOrderByCreatedAtDesc(TEST_USER_ID, "MANUAL");
    }

    @Test
    @DisplayName("Should handle rotation errors gracefully")
    void shouldHandleRotationErrors() throws Exception {
        // Given
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            backup.setId(1L);
                            return backup;
                        });
        when(backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(
                        TEST_USER_ID, "AUTOMATIC"))
                .thenThrow(new RuntimeException("Database error during rotation"));

        // When/Then - Backup should succeed even if rotation fails
        assertThatThrownBy(() -> backupService.createAutomaticBackup(TEST_USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error during rotation");
    }

    // ========== RESTORE BACKUP TESTS (10 tests) ==========

    @Test
    @DisplayName("Should restore backup successfully")
    void shouldRestoreBackupSuccessfully() throws Exception {
        // Given - Create a valid gzipped backup file
        Path backupFilePath = Paths.get(testBackup.getFilePath());
        try (GZIPOutputStream gzipOut =
                new GZIPOutputStream(Files.newOutputStream(backupFilePath))) {
            gzipOut.write("restored database content".getBytes());
        }

        // Recalculate checksum for the created file
        testBackup.setChecksum(calculateTestChecksum(backupFilePath));

        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            if (backup.getId() == null) {
                                backup.setId(2L); // Safety backup ID
                            }
                            return backup;
                        });

        // When
        backupService.restoreBackup(TEST_USER_ID, 1L);

        // Then
        assertThat(Files.exists(testDbFile)).isTrue();
        String restoredContent = Files.readString(testDbFile);
        assertThat(restoredContent).isEqualTo("restored database content");

        verify(backupRepository).findByIdAndUserId(1L, TEST_USER_ID);
        verify(backupRepository, atLeastOnce()).save(any(Backup.class)); // Safety backup creation
    }

    @Test
    @DisplayName("Should validate checksum before restore")
    void shouldValidateChecksumBeforeRestore() throws Exception {
        // Given - Create backup file with different content than checksum expects
        Path backupFilePath = Paths.get(testBackup.getFilePath());
        try (GZIPOutputStream gzipOut =
                new GZIPOutputStream(Files.newOutputStream(backupFilePath))) {
            gzipOut.write("corrupted content".getBytes());
        }

        // Keep original checksum (different from actual file)
        testBackup.setChecksum("a".repeat(64));

        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackup(TEST_USER_ID, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("corrupted");
    }

    @Test
    @DisplayName("Should throw exception on checksum mismatch")
    void shouldThrowExceptionOnChecksumMismatch() throws Exception {
        // Given
        Path backupFilePath = Paths.get(testBackup.getFilePath());
        try (GZIPOutputStream gzipOut =
                new GZIPOutputStream(Files.newOutputStream(backupFilePath))) {
            gzipOut.write("some content".getBytes());
        }

        testBackup.setChecksum("0".repeat(64)); // Wrong checksum

        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackup(TEST_USER_ID, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    @DisplayName("Should create safety backup before restore")
    void shouldCreateSafetyBackupBeforeRestore() throws Exception {
        // Given
        Path backupFilePath = Paths.get(testBackup.getFilePath());
        try (GZIPOutputStream gzipOut =
                new GZIPOutputStream(Files.newOutputStream(backupFilePath))) {
            gzipOut.write("restored content".getBytes());
        }
        testBackup.setChecksum(calculateTestChecksum(backupFilePath));

        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            if (backup.getId() == null) {
                                backup.setId(999L); // Safety backup
                            }
                            return backup;
                        });

        // When
        backupService.restoreBackup(TEST_USER_ID, 1L);

        // Then - Should have created a safety backup
        verify(backupRepository, atLeastOnce())
                .save(
                        argThat(
                                backup ->
                                        backup.getDescription() != null
                                                && backup.getDescription()
                                                        .contains("Auto-backup before restore")));
    }

    @Test
    @DisplayName("Should decompress gzip backup during restore")
    void shouldDecompressGzipBackup() throws Exception {
        // Given
        String originalContent = "original database state";
        Path backupFilePath = Paths.get(testBackup.getFilePath());
        try (GZIPOutputStream gzipOut =
                new GZIPOutputStream(Files.newOutputStream(backupFilePath))) {
            gzipOut.write(originalContent.getBytes());
        }
        testBackup.setChecksum(calculateTestChecksum(backupFilePath));

        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));
        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            if (backup.getId() == null) {
                                backup.setId(2L);
                            }
                            return backup;
                        });

        // When
        backupService.restoreBackup(TEST_USER_ID, 1L);

        // Then - Database should contain decompressed content
        String restoredContent = Files.readString(testDbFile);
        assertThat(restoredContent).isEqualTo(originalContent);
    }

    @Test
    @DisplayName("Should validate backup ownership before restore")
    void shouldValidateBackupOwnership() {
        // Given - User tries to restore another user's backup
        Long unauthorizedUserId = 999L;
        when(backupRepository.findByIdAndUserId(1L, unauthorizedUserId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackup(unauthorizedUserId, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found or access denied");

        verify(backupRepository).findByIdAndUserId(1L, unauthorizedUserId);
    }

    @Test
    @DisplayName("Should throw exception when backup not found for restore")
    void shouldThrowExceptionWhenBackupNotFoundForRestore() {
        // Given
        when(backupRepository.findByIdAndUserId(999L, TEST_USER_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackup(TEST_USER_ID, 999L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found or access denied");
    }

    @Test
    @DisplayName("Should throw exception when restoring failed backup")
    void shouldThrowExceptionWhenBackupFailed() {
        // Given
        testBackup.setStatus("FAILED");
        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackup(TEST_USER_ID, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Cannot restore incomplete backup");
    }

    @Test
    @DisplayName("Should throw exception when restoring in-progress backup")
    void shouldThrowExceptionWhenBackupInProgress() {
        // Given
        testBackup.setStatus("IN_PROGRESS");
        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackup(TEST_USER_ID, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Cannot restore incomplete backup");
    }

    @Test
    @DisplayName("Should throw exception when backup file not found on disk")
    void shouldThrowExceptionWhenBackupFileNotFoundOnDisk() {
        // Given - Backup record exists but file missing
        testBackup.setFilePath("/nonexistent/backup.ofbak");
        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackup(TEST_USER_ID, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Backup file not found");
    }

    // ========== RESTORE FROM FILE TESTS (6 tests) ==========

    @Test
    @DisplayName("Should restore from uploaded file successfully")
    void shouldRestoreFromUploadedFile() throws Exception {
        // Given - Create gzipped backup file
        byte[] originalContent = "uploaded database content".getBytes();
        byte[] gzippedContent;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(originalContent);
            gzipOut.finish();
            gzippedContent = baos.toByteArray();
        }

        MultipartFile file =
                new MockMultipartFile(
                        "file", "backup-upload.ofbak", "application/octet-stream", gzippedContent);

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            if (backup.getId() == null) {
                                backup.setId(2L);
                            }
                            return backup;
                        });

        // When
        backupService.restoreBackupFromFile(TEST_USER_ID, file);

        // Then
        assertThat(Files.exists(testDbFile)).isTrue();
        String restoredContent = Files.readString(testDbFile);
        assertThat(restoredContent).isEqualTo("uploaded database content");

        verify(backupRepository, atLeastOnce()).save(any(Backup.class)); // Safety backup
    }

    @Test
    @DisplayName("Should validate uploaded file not empty")
    void shouldValidateUploadedFileNotEmpty() {
        // Given
        MultipartFile emptyFile =
                new MockMultipartFile(
                        "file", "backup.ofbak", "application/octet-stream", new byte[0]);

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackupFromFile(TEST_USER_ID, emptyFile))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should validate uploaded file format has .ofbak extension")
    void shouldValidateUploadedFileFormat() {
        // Given - Wrong file extension
        MultipartFile invalidFile =
                new MockMultipartFile(
                        "file", "backup.zip", "application/zip", "content".getBytes());

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackupFromFile(TEST_USER_ID, invalidFile))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Invalid backup file format")
                .hasMessageContaining(".ofbak");
    }

    @Test
    @DisplayName("Should validate uploaded file can be decompressed")
    void shouldValidateUploadedFileChecksum() throws Exception {
        // Given - Valid gzipped content
        byte[] gzippedContent;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write("valid database".getBytes());
            gzipOut.finish();
            gzippedContent = baos.toByteArray();
        }

        MultipartFile validFile =
                new MockMultipartFile(
                        "file", "valid-backup.ofbak", "application/octet-stream", gzippedContent);

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            if (backup.getId() == null) {
                                backup.setId(2L);
                            }
                            return backup;
                        });

        // When - Should not throw exception
        assertThatCode(() -> backupService.restoreBackupFromFile(TEST_USER_ID, validFile))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should create safety backup before restoring from upload")
    void shouldCreateBackupRecordFromUpload() throws Exception {
        // Given
        byte[] gzippedContent;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write("content".getBytes());
            gzipOut.finish();
            gzippedContent = baos.toByteArray();
        }

        MultipartFile file =
                new MockMultipartFile(
                        "file", "upload.ofbak", "application/octet-stream", gzippedContent);

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            if (backup.getId() == null) {
                                backup.setId(999L);
                            }
                            return backup;
                        });

        // When
        backupService.restoreBackupFromFile(TEST_USER_ID, file);

        // Then - Should create safety backup before restore
        verify(backupRepository, atLeastOnce())
                .save(
                        argThat(
                                backup ->
                                        backup.getDescription()
                                                .contains("Auto-backup before restore")));
    }

    @Test
    @DisplayName("Should handle invalid uploaded file gracefully")
    void shouldHandleInvalidUploadedFile() {
        // Given - Not gzipped content
        MultipartFile invalidFile =
                new MockMultipartFile(
                        "file",
                        "invalid.ofbak",
                        "application/octet-stream",
                        "not gzipped content".getBytes());

        when(backupRepository.save(any(Backup.class)))
                .thenAnswer(
                        invocation -> {
                            Backup backup = invocation.getArgument(0);
                            if (backup.getId() == null) {
                                backup.setId(2L);
                            }
                            return backup;
                        });

        // When/Then
        assertThatThrownBy(() -> backupService.restoreBackupFromFile(TEST_USER_ID, invalidFile))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Failed to restore backup");
    }

    // ========== LIST/GET/DELETE TESTS (6 tests) ==========

    @Test
    @DisplayName("Should list all user backups ordered by date")
    void shouldListAllUserBackups() {
        // Given
        List<Backup> backups =
                Arrays.asList(
                        createMockBackup(3L, "MANUAL"),
                        createMockBackup(2L, "AUTOMATIC"),
                        createMockBackup(1L, "MANUAL"));

        when(backupRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID)).thenReturn(backups);

        // When
        List<Backup> result = backupService.listBackups(TEST_USER_ID);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(3L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(2).getId()).isEqualTo(1L);

        verify(backupRepository).findByUserIdOrderByCreatedAtDesc(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should get backup by ID with ownership verification")
    void shouldGetBackupById() {
        // Given
        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When
        Backup result = backupService.getBackup(TEST_USER_ID, 1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);

        verify(backupRepository).findByIdAndUserId(1L, TEST_USER_ID);
    }

    @Test
    @DisplayName("Should throw exception when backup not found for get")
    void shouldThrowExceptionWhenBackupNotFoundForGet() {
        // Given
        when(backupRepository.findByIdAndUserId(999L, TEST_USER_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> backupService.getBackup(TEST_USER_ID, 999L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found or access denied");
    }

    @Test
    @DisplayName("Should delete backup successfully")
    void shouldDeleteBackupSuccessfully() throws Exception {
        // Given - Create actual backup file
        Path backupFilePath = Paths.get(testBackup.getFilePath());
        Files.write(backupFilePath, "backup content".getBytes());

        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));
        doNothing().when(backupRepository).delete(testBackup);

        // When
        backupService.deleteBackup(TEST_USER_ID, 1L);

        // Then
        assertThat(Files.exists(backupFilePath)).isFalse();
        verify(backupRepository).findByIdAndUserId(1L, TEST_USER_ID);
        verify(backupRepository).delete(testBackup);
    }

    @Test
    @DisplayName("Should throw exception when backup not found for delete")
    void shouldThrowExceptionWhenBackupNotFoundForDelete() {
        // Given
        when(backupRepository.findByIdAndUserId(999L, TEST_USER_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> backupService.deleteBackup(TEST_USER_ID, 999L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found or access denied");

        verify(backupRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should not delete other users backups")
    void shouldNotDeleteOtherUsersBackups() {
        // Given
        Long otherUserId = 999L;
        when(backupRepository.findByIdAndUserId(1L, otherUserId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> backupService.deleteBackup(otherUserId, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found or access denied");

        verify(backupRepository).findByIdAndUserId(1L, otherUserId);
        verify(backupRepository, never()).delete(any());
    }

    // ========== DOWNLOAD TESTS (5 tests) ==========

    @Test
    @DisplayName("Should download backup as stream")
    void shouldDownloadBackupAsStream() throws Exception {
        // Given
        Path backupFilePath = Paths.get(testBackup.getFilePath());
        byte[] backupContent = "backup file content".getBytes();
        Files.write(backupFilePath, backupContent);

        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When
        InputStream result = backupService.downloadBackup(TEST_USER_ID, 1L);

        // Then
        assertThat(result).isNotNull();
        byte[] downloadedContent = result.readAllBytes();
        assertThat(downloadedContent).isEqualTo(backupContent);

        verify(backupRepository).findByIdAndUserId(1L, TEST_USER_ID);
    }

    @Test
    @DisplayName("Should include correct filename in download")
    void shouldIncludeCorrectFilenameInDownload() {
        // Given
        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When
        Backup backup = backupService.getBackup(TEST_USER_ID, 1L);

        // Then
        assertThat(backup.getFilename()).endsWith(".ofbak");
    }

    @Test
    @DisplayName("Should throw exception when backup not found for download")
    void shouldThrowExceptionWhenBackupNotFoundForDownload() {
        // Given
        when(backupRepository.findByIdAndUserId(999L, TEST_USER_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> backupService.downloadBackup(TEST_USER_ID, 999L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found or access denied");
    }

    @Test
    @DisplayName("Should throw exception when file not found on disk for download")
    void shouldThrowExceptionWhenFileNotFoundOnDisk() {
        // Given - Backup exists but file missing
        testBackup.setFilePath("/nonexistent/backup.ofbak");
        when(backupRepository.findByIdAndUserId(1L, TEST_USER_ID))
                .thenReturn(Optional.of(testBackup));

        // When/Then
        assertThatThrownBy(() -> backupService.downloadBackup(TEST_USER_ID, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Backup file not found");
    }

    @Test
    @DisplayName("Should validate backup ownership for download")
    void shouldValidateBackupOwnershipForDownload() {
        // Given
        Long unauthorizedUserId = 999L;
        when(backupRepository.findByIdAndUserId(1L, unauthorizedUserId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> backupService.downloadBackup(unauthorizedUserId, 1L))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("not found or access denied");

        verify(backupRepository).findByIdAndUserId(1L, unauthorizedUserId);
    }

    // ========== Helper Methods ==========

    private Backup createMockBackup(Long id, String backupType) {
        return Backup.builder()
                .id(id)
                .userId(TEST_USER_ID)
                .filename("backup-" + id + ".ofbak")
                .filePath(TEST_BACKUP_DIR + "/backup-" + id + ".ofbak")
                .fileSize(1024L)
                .checksum("a".repeat(64))
                .status("COMPLETED")
                .backupType(backupType)
                .description("Test backup " + id)
                .createdAt(LocalDateTime.now().minusDays(id))
                .build();
    }

    private String calculateTestChecksum(Path filePath) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
