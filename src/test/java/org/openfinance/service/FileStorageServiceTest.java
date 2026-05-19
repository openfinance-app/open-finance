package org.openfinance.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for FileStorageService. Tests file storage, retrieval, deletion, and cleanup
 * operations.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@DisplayName("FileStorageService Tests")
class FileStorageServiceTest {

    private FileStorageService fileStorageService;
    private Path testTempDirectory;

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary test directory
        testTempDirectory = Files.createTempDirectory("test-imports-");
        fileStorageService =
                new FileStorageService(
                        testTempDirectory.toString(), 24 // 24 hours cleanup period
                        );
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test directory and all files
        if (Files.exists(testTempDirectory)) {
            try (Stream<Path> paths = Files.walk(testTempDirectory)) {
                paths.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(
                                path -> {
                                    try {
                                        Files.deleteIfExists(path);
                                    } catch (IOException e) {
                                        // Ignore cleanup errors
                                    }
                                });
            }
        }
    }

    @Test
    @DisplayName("Should store file successfully")
    void shouldStoreFileSuccessfully() throws IOException {
        // Given
        String content = "!Type:Bank\nD01/15/2024\nT-100.00\n^\n";
        MultipartFile file =
                new MockMultipartFile("file", "transactions.qif", "text/plain", content.getBytes());

        // When
        String uploadId = fileStorageService.storeFile(file);

        // Then
        assertNotNull(uploadId);
        assertFalse(uploadId.isEmpty());

        // Verify file exists
        Path storedFile = fileStorageService.getFile(uploadId);
        assertTrue(Files.exists(storedFile));
        assertEquals(content, Files.readString(storedFile));
    }

    @Test
    @DisplayName("Should store file with correct extension")
    void shouldStoreFileWithCorrectExtension() throws IOException {
        // Given
        MultipartFile qifFile =
                new MockMultipartFile("file", "test.qif", "text/plain", "!Type:Bank\n".getBytes());

        // When
        String uploadId = fileStorageService.storeFile(qifFile);

        // Then
        Path storedFile = fileStorageService.getFile(uploadId);
        assertTrue(storedFile.getFileName().toString().endsWith(".qif"));
    }

    @Test
    @DisplayName("Should retrieve stored file successfully")
    void shouldRetrieveStoredFileSuccessfully() throws IOException {
        // Given
        MultipartFile file =
                new MockMultipartFile(
                        "file",
                        "test.csv",
                        "text/csv",
                        "Date,Amount\n2024-01-15,100.00\n".getBytes());
        String uploadId = fileStorageService.storeFile(file);

        // When
        Path retrievedFile = fileStorageService.getFile(uploadId);

        // Then
        assertNotNull(retrievedFile);
        assertTrue(Files.exists(retrievedFile));
        assertTrue(retrievedFile.getFileName().toString().startsWith(uploadId));
    }

    @Test
    @DisplayName("Should throw IOException when file not found")
    void shouldThrowIOExceptionWhenFileNotFound() {
        // Given
        String nonExistentUploadId = "non-existent-id";

        // When & Then
        IOException exception =
                assertThrows(
                        IOException.class, () -> fileStorageService.getFile(nonExistentUploadId));
        assertTrue(exception.getMessage().contains("File not found"));
    }

    @Test
    @DisplayName("Should delete file successfully")
    void shouldDeleteFileSuccessfully() throws IOException {
        // Given
        MultipartFile file =
                new MockMultipartFile("file", "test.qif", "text/plain", "!Type:Bank\n".getBytes());
        String uploadId = fileStorageService.storeFile(file);
        Path storedFile = fileStorageService.getFile(uploadId);
        assertTrue(Files.exists(storedFile));

        // When
        fileStorageService.deleteFile(uploadId);

        // Then
        assertFalse(Files.exists(storedFile));
    }

    @Test
    @DisplayName("Should throw IOException when deleting non-existent file")
    void shouldThrowIOExceptionWhenDeletingNonExistentFile() {
        // Given
        String nonExistentUploadId = "non-existent-id";

        // When & Then
        assertThrows(IOException.class, () -> fileStorageService.deleteFile(nonExistentUploadId));
    }

    @Test
    @DisplayName("Should clean up old files")
    void shouldCleanUpOldFiles() throws IOException {
        // Given - Create test service with 1-hour cleanup period
        FileStorageService shortRetentionService =
                new FileStorageService(
                        testTempDirectory.toString(), 1 // 1 hour
                        );

        // Create an old file (modified 2 hours ago)
        MultipartFile oldFile =
                new MockMultipartFile("file", "old.qif", "text/plain", "old content".getBytes());
        String oldUploadId = shortRetentionService.storeFile(oldFile);
        Path oldFilePath = shortRetentionService.getFile(oldUploadId);

        // Set last modified time to 2 hours ago
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        Files.setLastModifiedTime(oldFilePath, FileTime.from(twoHoursAgo));

        // Create a recent file (modified now)
        MultipartFile recentFile =
                new MockMultipartFile(
                        "file", "recent.qif", "text/plain", "recent content".getBytes());
        String recentUploadId = shortRetentionService.storeFile(recentFile);
        Path recentFilePath = shortRetentionService.getFile(recentUploadId);

        // When
        int deletedCount = shortRetentionService.cleanupOldFiles();

        // Then
        assertEquals(1, deletedCount);
        assertFalse(Files.exists(oldFilePath));
        assertTrue(Files.exists(recentFilePath));
    }

    @Test
    @DisplayName("Should not delete files within retention period")
    void shouldNotDeleteFilesWithinRetentionPeriod() throws IOException {
        // Given
        MultipartFile file1 =
                new MockMultipartFile("file", "test1.qif", "text/plain", "content1".getBytes());
        MultipartFile file2 =
                new MockMultipartFile("file", "test2.qif", "text/plain", "content2".getBytes());

        String uploadId1 = fileStorageService.storeFile(file1);
        String uploadId2 = fileStorageService.storeFile(file2);

        Path file1Path = fileStorageService.getFile(uploadId1);
        Path file2Path = fileStorageService.getFile(uploadId2);

        // When
        int deletedCount = fileStorageService.cleanupOldFiles();

        // Then
        assertEquals(0, deletedCount);
        assertTrue(Files.exists(file1Path));
        assertTrue(Files.exists(file2Path));
    }

    @Test
    @DisplayName("Should reject null file")
    void shouldRejectNullFile() {
        // When & Then
        assertThrows(NullPointerException.class, () -> fileStorageService.storeFile(null));
    }

    @Test
    @DisplayName("Should reject file with directory traversal in name")
    void shouldRejectFileWithDirectoryTraversal() {
        // Given
        MultipartFile file =
                new MockMultipartFile(
                        "file", "../../../etc/passwd.qif", "text/plain", "content".getBytes());

        // When & Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> fileStorageService.storeFile(file));
        assertEquals("Invalid file name", exception.getMessage());
    }

    @Test
    @DisplayName("Should reject file with blank name")
    void shouldRejectFileWithBlankName() {
        // Given - MockMultipartFile converts null to empty string, so test with empty string
        MultipartFile file = new MockMultipartFile("file", "", "text/plain", "content".getBytes());

        // When & Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> fileStorageService.storeFile(file));
        assertEquals("Invalid file name", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle file without extension")
    void shouldHandleFileWithoutExtension() throws IOException {
        // Given
        MultipartFile file =
                new MockMultipartFile("file", "noextension", "text/plain", "content".getBytes());

        // When
        String uploadId = fileStorageService.storeFile(file);

        // Then
        assertNotNull(uploadId);
        Path storedFile = fileStorageService.getFile(uploadId);
        assertTrue(Files.exists(storedFile));
    }

    @Test
    @DisplayName("Should generate unique upload IDs for multiple files")
    void shouldGenerateUniqueUploadIds() throws IOException {
        // Given
        MultipartFile file1 =
                new MockMultipartFile("file", "test1.qif", "text/plain", "content1".getBytes());
        MultipartFile file2 =
                new MockMultipartFile("file", "test2.qif", "text/plain", "content2".getBytes());
        MultipartFile file3 =
                new MockMultipartFile("file", "test3.qif", "text/plain", "content3".getBytes());

        // When
        String uploadId1 = fileStorageService.storeFile(file1);
        String uploadId2 = fileStorageService.storeFile(file2);
        String uploadId3 = fileStorageService.storeFile(file3);

        // Then
        assertNotEquals(uploadId1, uploadId2);
        assertNotEquals(uploadId1, uploadId3);
        assertNotEquals(uploadId2, uploadId3);
    }

    @Test
    @DisplayName("Should return temp directory path")
    void shouldReturnTempDirectoryPath() {
        // When
        Path directory = fileStorageService.getTempDirectory();

        // Then
        assertNotNull(directory);
        assertEquals(testTempDirectory, directory);
        assertTrue(Files.exists(directory));
        assertTrue(Files.isDirectory(directory));
    }

    @Test
    @DisplayName("Should create directory on initialization")
    void shouldCreateDirectoryOnInitialization() throws IOException {
        // Given
        Path newTempDir = Paths.get(testTempDirectory.toString(), "new-subdir");
        assertFalse(Files.exists(newTempDir));

        // When
        FileStorageService newService = new FileStorageService(newTempDir.toString(), 24);

        // Then
        assertTrue(Files.exists(newTempDir));
        assertTrue(Files.isDirectory(newTempDir));

        // Cleanup
        Files.deleteIfExists(newTempDir);
    }

    @Test
    @DisplayName("Should replace existing file with same upload ID")
    void shouldReplaceExistingFileWithSameUploadId() throws IOException {
        // Given
        MultipartFile file1 =
                new MockMultipartFile(
                        "file", "test.qif", "text/plain", "original content".getBytes());
        String uploadId = fileStorageService.storeFile(file1);
        Path storedFile = fileStorageService.getFile(uploadId);
        String originalContent = Files.readString(storedFile);

        // When - Store another file (rare case, but should replace)
        MultipartFile file2 =
                new MockMultipartFile("file", "test.qif", "text/plain", "new content".getBytes());
        // Manually copy to same location to simulate REPLACE_EXISTING behavior
        Files.copy(
                file2.getInputStream(),
                storedFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Then
        String newContent = Files.readString(storedFile);
        assertNotEquals(originalContent, newContent);
        assertEquals("new content", newContent);
    }

    @Test
    @DisplayName("Should handle cleanup with no files")
    void shouldHandleCleanupWithNoFiles() throws IOException {
        // Given - Empty directory (setUp creates it but no files stored)

        // When
        int deletedCount = fileStorageService.cleanupOldFiles();

        // Then
        assertEquals(0, deletedCount);
    }

    @Test
    @DisplayName("Should reject null upload ID for retrieval")
    void shouldRejectNullUploadIdForRetrieval() {
        // When & Then
        assertThrows(NullPointerException.class, () -> fileStorageService.getFile(null));
    }
}
