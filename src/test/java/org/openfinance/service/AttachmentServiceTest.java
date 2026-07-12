package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.entity.Attachment;
import org.openfinance.entity.EntityType;
import org.openfinance.exception.AttachmentNotFoundException;
import org.openfinance.exception.FileStorageException;
import org.openfinance.repository.AttachmentRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for AttachmentService.
 *
 * <p>
 * Tests cover:
 *
 * <ul>
 * <li>Upload operations (validation, encryption, filesystem storage)
 * <li>Download operations (decryption, authorization)
 * <li>Delete operations (authorization, filesystem cleanup)
 * <li>List/filter operations (by entity, by user)
 * <li>Metadata retrieval operations
 * <li>Storage statistics calculations
 * <li>Cascade deletion of entity attachments
 * <li>Orphaned attachment cleanup
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttachmentService Unit Tests")
class AttachmentServiceTest {

        @Mock
        private AttachmentRepository attachmentRepository;

        @Mock
        private EncryptionService encryptionService;

        @Mock
        private OperationHistoryService operationHistoryService;

        private AttachmentService attachmentService;

        private EncryptionProperties encryptionProperties;

        private MultipartFile testFile;
        private Attachment testAttachment;

        private static final Long TEST_USER_ID = 100L;
        private static final Long TEST_ENTITY_ID = 500L;
        private static final String TEST_FILE_NAME = "receipt.pdf";
        private static final String TEST_FILE_TYPE = "application/pdf";
        private static final String TEST_STORAGE_PATH = "./test-attachments";

        @BeforeEach
        void setUp() throws IOException {
                encryptionProperties = new EncryptionProperties();
                encryptionProperties.setEnabled(true);
                attachmentService = new AttachmentService(
                                attachmentRepository,
                                encryptionService,
                                encryptionProperties);

                // Set encryption context for tests
                EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));

                // Configure service with test values
                ReflectionTestUtils.setField(attachmentService, "storagePath", TEST_STORAGE_PATH);
                ReflectionTestUtils.setField(attachmentService, "maxFileSize", 10485760L); // 10MB
                ReflectionTestUtils.setField(
                                attachmentService,
                                "allowedTypesConfig",
                                "application/pdf,image/jpeg,image/png,image/gif,image/webp,"
                                                + "application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,"
                                                + "application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                ReflectionTestUtils.setField(attachmentService, "cleanupOrphanedAfterDays", 30);

                // Create test file
                testFile = new MockMultipartFile(
                                "file",
                                TEST_FILE_NAME,
                                TEST_FILE_TYPE,
                                "test file content for receipt".getBytes());

                // Create test attachment entity
                testAttachment = Attachment.builder()
                                .id(1L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.TRANSACTION)
                                .entityId(TEST_ENTITY_ID)
                                .fileName(TEST_FILE_NAME)
                                .fileType(TEST_FILE_TYPE)
                                .fileSize(1024L)
                                .filePath(
                                                TEST_STORAGE_PATH
                                                                + "/"
                                                                + TEST_USER_ID
                                                                + "/TRANSACTION/uuid-test.enc")
                                .description("Test receipt")
                                .uploadedAt(LocalDateTime.now())
                                .build();
        }

        @AfterEach
        void tearDown() {
                EncryptionContext.clear();
        }

        // ========== UPLOAD TESTS (8 tests) ==========

        @Test
        @DisplayName("Should upload attachment successfully")
        void shouldUploadAttachmentSuccessfully() throws Exception {
                // Given
                byte[] encryptedBytes = "encrypted-content".getBytes();
                when(encryptionService.encryptBytes(any(byte[].class), any(SecretKey.class)))
                                .thenReturn(encryptedBytes);
                when(attachmentRepository.save(any(Attachment.class))).thenReturn(testAttachment);

                // When
                Attachment result = attachmentService.uploadAttachment(
                                testFile,
                                TEST_USER_ID,
                                EntityType.TRANSACTION,
                                TEST_ENTITY_ID,
                                "Test receipt");

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
                assertThat(result.getFileName()).isEqualTo(TEST_FILE_NAME);
                assertThat(result.getFileType()).isEqualTo(TEST_FILE_TYPE);

                verify(encryptionService).encryptBytes(any(byte[].class), any(SecretKey.class));
                verify(attachmentRepository).save(any(Attachment.class));

                // Cleanup - delete created directory
                Path userDirectory = Paths.get(TEST_STORAGE_PATH, TEST_USER_ID.toString(), "TRANSACTION");
                if (Files.exists(userDirectory)) {
                        Files.walk(userDirectory)
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
        }

        @Test
        @DisplayName("Should store attachment bytes without encryption when encryption is disabled")
        void shouldStoreAttachmentBytesWithoutEncryptionWhenEncryptionDisabled() throws Exception {
                // Given
                encryptionProperties.setEnabled(false);
                when(attachmentRepository.save(any(Attachment.class))).thenReturn(testAttachment);

                // When
                Attachment result = attachmentService.uploadAttachment(
                                testFile,
                                TEST_USER_ID,
                                EntityType.TRANSACTION,
                                TEST_ENTITY_ID,
                                "Test receipt");

                // Then
                assertThat(result).isNotNull();
                verify(encryptionService, never()).encryptBytes(any(byte[].class), any(SecretKey.class));
                verify(attachmentRepository).save(any(Attachment.class));

                Path userDirectory = Paths.get(TEST_STORAGE_PATH, TEST_USER_ID.toString(), "TRANSACTION");
                assertThat(Files.exists(userDirectory)).isTrue();
                try (Stream<Path> files = Files.list(userDirectory)) {
                        Path storedFile = files.findFirst().orElseThrow();
                        assertThat(Files.readAllBytes(storedFile)).isEqualTo(testFile.getBytes());

                        Files.deleteIfExists(storedFile);
                }
                Files.deleteIfExists(userDirectory);
                Files.deleteIfExists(userDirectory.getParent());
        }

        @Test
        @DisplayName("Should throw exception when file is empty")
        void shouldThrowExceptionWhenFileIsEmpty() {
                // Given
                MultipartFile emptyFile = new MockMultipartFile("file", TEST_FILE_NAME, TEST_FILE_TYPE, new byte[0]);

                // When/Then
                assertThatThrownBy(
                                () -> attachmentService.uploadAttachment(
                                                emptyFile,
                                                TEST_USER_ID,
                                                EntityType.TRANSACTION,
                                                TEST_ENTITY_ID,
                                                null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Cannot upload empty file");

                verify(encryptionService, never()).encryptBytes(any(), any());
                verify(attachmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when file size exceeds limit")
        void shouldThrowExceptionWhenFileSizeExceedsLimit() {
                // Given - Create 11MB file (exceeds 10MB limit)
                byte[] largeContent = new byte[11 * 1024 * 1024];
                MultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeContent);

                // When/Then
                assertThatThrownBy(
                                () -> attachmentService.uploadAttachment(
                                                largeFile,
                                                TEST_USER_ID,
                                                EntityType.TRANSACTION,
                                                TEST_ENTITY_ID,
                                                null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("File size")
                                .hasMessageContaining("exceeds maximum allowed size");

                verify(encryptionService, never()).encryptBytes(any(), any());
                verify(attachmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when file type not allowed")
        void shouldThrowExceptionWhenFileTypeNotAllowed() {
                // Given - executable file type not in allowed list
                MultipartFile executableFile = new MockMultipartFile(
                                "file",
                                "virus.exe",
                                "application/x-msdownload",
                                "malicious content".getBytes());

                // When/Then
                assertThatThrownBy(
                                () -> attachmentService.uploadAttachment(
                                                executableFile,
                                                TEST_USER_ID,
                                                EntityType.TRANSACTION,
                                                TEST_ENTITY_ID,
                                                null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("File type")
                                .hasMessageContaining("is not allowed");

                verify(encryptionService, never()).encryptBytes(any(), any());
                verify(attachmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when file name contains path traversal")
        void shouldThrowExceptionWhenFileNameContainsPathTraversal() {
                // Test ".." path traversal
                MultipartFile traversalFile1 = new MockMultipartFile(
                                "file", "../../../etc/passwd", "text/plain", "content".getBytes());

                assertThatThrownBy(
                                () -> attachmentService.uploadAttachment(
                                                traversalFile1,
                                                TEST_USER_ID,
                                                EntityType.TRANSACTION,
                                                TEST_ENTITY_ID,
                                                null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Invalid file name")
                                .hasMessageContaining("directory traversal not allowed");

                // Test "/" path traversal
                MultipartFile traversalFile2 = new MockMultipartFile("file", "/etc/passwd", "text/plain",
                                "content".getBytes());

                assertThatThrownBy(
                                () -> attachmentService.uploadAttachment(
                                                traversalFile2,
                                                TEST_USER_ID,
                                                EntityType.TRANSACTION,
                                                TEST_ENTITY_ID,
                                                null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("directory traversal not allowed");

                // Test "\\" path traversal
                MultipartFile traversalFile3 = new MockMultipartFile(
                                "file",
                                "C:\\Windows\\System32\\config",
                                "text/plain",
                                "content".getBytes());

                assertThatThrownBy(
                                () -> attachmentService.uploadAttachment(
                                                traversalFile3,
                                                TEST_USER_ID,
                                                EntityType.TRANSACTION,
                                                TEST_ENTITY_ID,
                                                null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("directory traversal not allowed");

                verify(encryptionService, never()).encryptBytes(any(), any());
                verify(attachmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when encryption fails")
        void shouldThrowExceptionWhenEncryptionFails() {
                // Given
                when(encryptionService.encryptBytes(any(byte[].class), any(SecretKey.class)))
                                .thenThrow(new RuntimeException("Encryption failed"));

                // When/Then - RuntimeException from encryption is not caught, so it propagates
                // as-is
                assertThatThrownBy(
                                () -> attachmentService.uploadAttachment(
                                                testFile,
                                                TEST_USER_ID,
                                                EntityType.TRANSACTION,
                                                TEST_ENTITY_ID,
                                                null))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Encryption failed");

                verify(encryptionService).encryptBytes(any(byte[].class), any(SecretKey.class));
                verify(attachmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should upload image file successfully")
        void shouldUploadImageFileSuccessfully() throws Exception {
                // Given - JPEG image
                MultipartFile imageFile = new MockMultipartFile(
                                "file", "photo.jpg", "image/jpeg", "fake jpeg content".getBytes());

                Attachment imageAttachment = Attachment.builder()
                                .id(2L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.ASSET)
                                .entityId(200L)
                                .fileName("photo.jpg")
                                .fileType("image/jpeg")
                                .fileSize(imageFile.getSize())
                                .filePath(TEST_STORAGE_PATH + "/" + TEST_USER_ID + "/ASSET/uuid-image.enc")
                                .uploadedAt(LocalDateTime.now())
                                .build();

                when(encryptionService.encryptBytes(any(byte[].class), any()))
                                .thenReturn("encrypted-image".getBytes());
                when(attachmentRepository.save(any(Attachment.class))).thenReturn(imageAttachment);

                // When
                Attachment result = attachmentService.uploadAttachment(
                                imageFile, TEST_USER_ID, EntityType.ASSET, 200L, null);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getFileName()).isEqualTo("photo.jpg");
                assertThat(result.getFileType()).isEqualTo("image/jpeg");

                verify(attachmentRepository).save(any(Attachment.class));

                // Cleanup
                Path userDirectory = Paths.get(TEST_STORAGE_PATH, TEST_USER_ID.toString(), "ASSET");
                if (Files.exists(userDirectory)) {
                        Files.walk(userDirectory)
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
        }

        @Test
        @DisplayName("Should upload Word document successfully")
        void shouldUploadWordDocumentSuccessfully() throws Exception {
                // Given - Word document
                MultipartFile wordFile = new MockMultipartFile(
                                "file",
                                "contract.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "fake docx content".getBytes());

                Attachment wordAttachment = Attachment.builder()
                                .id(3L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.LIABILITY)
                                .entityId(300L)
                                .fileName("contract.docx")
                                .fileType(
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                .fileSize(wordFile.getSize())
                                .filePath(
                                                TEST_STORAGE_PATH + "/" + TEST_USER_ID + "/LIABILITY/uuid-word.enc")
                                .uploadedAt(LocalDateTime.now())
                                .build();

                when(encryptionService.encryptBytes(any(byte[].class), any()))
                                .thenReturn("encrypted-word".getBytes());
                when(attachmentRepository.save(any(Attachment.class))).thenReturn(wordAttachment);

                // When
                Attachment result = attachmentService.uploadAttachment(
                                wordFile,
                                TEST_USER_ID,
                                EntityType.LIABILITY,
                                300L,
                                "Loan contract");

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getFileName()).isEqualTo("contract.docx");

                verify(attachmentRepository).save(any(Attachment.class));

                // Cleanup
                Path userDirectory = Paths.get(TEST_STORAGE_PATH, TEST_USER_ID.toString(), "LIABILITY");
                if (Files.exists(userDirectory)) {
                        Files.walk(userDirectory)
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
        }

        // ========== DOWNLOAD TESTS (5 tests) ==========

        @Test
        @DisplayName("Should download attachment successfully with decryption")
        void shouldDownloadAttachmentSuccessfully() throws Exception {
                // Given - Create actual encrypted file for download
                Path testFilePath = Paths.get(testAttachment.getFilePath());
                Files.createDirectories(testFilePath.getParent());
                byte[] encryptedContent = "encrypted-file-content".getBytes();
                Files.write(testFilePath, encryptedContent);

                byte[] decryptedContent = "original file content".getBytes();

                when(attachmentRepository.findByIdAndUserId(1L, TEST_USER_ID))
                                .thenReturn(Optional.of(testAttachment));
                when(encryptionService.decryptBytes(any(byte[].class), any()))
                                .thenReturn(decryptedContent);

                // When
                Resource result = attachmentService.downloadAttachment(1L, TEST_USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.contentLength()).isEqualTo(decryptedContent.length);
                assertThat(result.getInputStream().readAllBytes()).isEqualTo(decryptedContent);

                verify(attachmentRepository).findByIdAndUserId(1L, TEST_USER_ID);
                verify(encryptionService).decryptBytes(any(byte[].class), any());

                // Cleanup
                Files.deleteIfExists(testFilePath);
                Files.deleteIfExists(testFilePath.getParent());
                Files.deleteIfExists(testFilePath.getParent().getParent());
        }

        @Test
        @DisplayName("Should return stored attachment bytes without decryption when encryption is disabled")
        void shouldReturnStoredAttachmentBytesWithoutDecryptionWhenEncryptionDisabled() throws Exception {
                // Given
                encryptionProperties.setEnabled(false);
                Path testFilePath = Paths.get(testAttachment.getFilePath());
                Files.createDirectories(testFilePath.getParent());
                byte[] storedContent = "plain-file-content".getBytes();
                Files.write(testFilePath, storedContent);
                when(attachmentRepository.findByIdAndUserId(1L, TEST_USER_ID))
                                .thenReturn(Optional.of(testAttachment));

                // When
                Resource result = attachmentService.downloadAttachment(1L, TEST_USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getInputStream().readAllBytes()).isEqualTo(storedContent);
                verify(encryptionService, never()).decryptBytes(any(byte[].class), any());

                Files.deleteIfExists(testFilePath);
                Files.deleteIfExists(testFilePath.getParent());
                Files.deleteIfExists(testFilePath.getParent().getParent());
        }

        @Test
        @DisplayName("Should throw exception when attachment not found for download")
        void shouldThrowExceptionWhenAttachmentNotFoundForDownload() {
                // Given
                when(attachmentRepository.findByIdAndUserId(999L, TEST_USER_ID))
                                .thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(
                                () -> attachmentService.downloadAttachment(
                                                999L, TEST_USER_ID))
                                .isInstanceOf(AttachmentNotFoundException.class)
                                .hasMessageContaining("Attachment not found")
                                .hasMessageContaining("don't have permission");

                verify(attachmentRepository).findByIdAndUserId(999L, TEST_USER_ID);
                verify(encryptionService, never()).decryptBytes(any(), any());
        }

        @Test
        @DisplayName("Should throw exception when unauthorized user downloads")
        void shouldThrowExceptionWhenUnauthorizedUserDownloads() {
                // Given - Different user ID trying to access
                Long unauthorizedUserId = 999L;
                when(attachmentRepository.findByIdAndUserId(1L, unauthorizedUserId))
                                .thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(
                                () -> attachmentService.downloadAttachment(
                                                1L, unauthorizedUserId))
                                .isInstanceOf(AttachmentNotFoundException.class)
                                .hasMessageContaining("Attachment not found")
                                .hasMessageContaining("don't have permission");

                verify(attachmentRepository).findByIdAndUserId(1L, unauthorizedUserId);
                verify(encryptionService, never()).decryptBytes(any(), any());
        }

        @Test
        @DisplayName("Should throw exception when file not found on filesystem")
        void shouldThrowExceptionWhenFileNotFoundOnFilesystem() {
                // Given - Attachment exists in DB but file missing on disk
                when(attachmentRepository.findByIdAndUserId(1L, TEST_USER_ID))
                                .thenReturn(Optional.of(testAttachment));

                // When/Then - FileStorageException thrown directly from Files.exists check
                assertThatThrownBy(
                                () -> attachmentService.downloadAttachment(
                                                1L, TEST_USER_ID))
                                .isInstanceOf(FileStorageException.class)
                                .satisfies(
                                                ex -> {
                                                        // The exception might be the original or wrapped
                                                        String message = ex.getMessage();
                                                        String causeMessage = ex.getCause() != null
                                                                        ? ex.getCause().getMessage()
                                                                        : "";
                                                        assertThat(message + " " + causeMessage)
                                                                        .containsAnyOf(
                                                                                        "Attachment file not found on disk",
                                                                                        "Failed to decrypt");
                                                });

                verify(attachmentRepository).findByIdAndUserId(1L, TEST_USER_ID);
                verify(encryptionService, never()).decryptBytes(any(), any());
        }

        @Test
        @DisplayName("Should throw exception when decryption fails")
        void shouldThrowExceptionWhenDecryptionFails() throws Exception {
                // Given - Create file but decryption fails
                Path testFilePath = Paths.get(testAttachment.getFilePath());
                Files.createDirectories(testFilePath.getParent());
                byte[] encryptedContent = "encrypted-file-content".getBytes();
                Files.write(testFilePath, encryptedContent);

                when(attachmentRepository.findByIdAndUserId(1L, TEST_USER_ID))
                                .thenReturn(Optional.of(testAttachment));
                when(encryptionService.decryptBytes(any(byte[].class), any()))
                                .thenThrow(new RuntimeException("Decryption failed - wrong key"));

                // When/Then
                assertThatThrownBy(
                                () -> attachmentService.downloadAttachment(
                                                1L, TEST_USER_ID))
                                .isInstanceOf(FileStorageException.class)
                                .hasMessageContaining("Failed to decrypt attachment file");

                verify(encryptionService).decryptBytes(any(byte[].class), any());

                // Cleanup
                Files.deleteIfExists(testFilePath);
                Files.deleteIfExists(testFilePath.getParent());
                Files.deleteIfExists(testFilePath.getParent().getParent());
        }

        // ========== DELETE TESTS (4 tests) ==========

        @Test
        @DisplayName("Should delete attachment successfully")
        void shouldDeleteAttachmentSuccessfully() throws Exception {
                // Given - Create actual file to delete
                Path testFilePath = Paths.get(testAttachment.getFilePath());
                Files.createDirectories(testFilePath.getParent());
                Files.write(testFilePath, "test content".getBytes());

                when(attachmentRepository.findByIdAndUserId(1L, TEST_USER_ID))
                                .thenReturn(Optional.of(testAttachment));
                doNothing().when(attachmentRepository).delete(testAttachment);

                // When
                attachmentService.deleteAttachment(1L, TEST_USER_ID);

                // Then
                assertThat(Files.exists(testFilePath)).isFalse();
                verify(attachmentRepository).findByIdAndUserId(1L, TEST_USER_ID);
                verify(attachmentRepository).delete(testAttachment);

                // Cleanup
                Files.deleteIfExists(testFilePath.getParent());
                Files.deleteIfExists(testFilePath.getParent().getParent());
        }

        @Test
        @DisplayName("Should throw exception when attachment not found for delete")
        void shouldThrowExceptionWhenAttachmentNotFoundForDelete() {
                // Given
                when(attachmentRepository.findByIdAndUserId(999L, TEST_USER_ID))
                                .thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> attachmentService.deleteAttachment(999L, TEST_USER_ID))
                                .isInstanceOf(AttachmentNotFoundException.class)
                                .hasMessageContaining("Attachment not found")
                                .hasMessageContaining("don't have permission to delete it");

                verify(attachmentRepository).findByIdAndUserId(999L, TEST_USER_ID);
                verify(attachmentRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw exception when unauthorized user deletes")
        void shouldThrowExceptionWhenUnauthorizedUserDeletes() {
                // Given - Different user trying to delete
                Long unauthorizedUserId = 999L;
                when(attachmentRepository.findByIdAndUserId(1L, unauthorizedUserId))
                                .thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> attachmentService.deleteAttachment(1L, unauthorizedUserId))
                                .isInstanceOf(AttachmentNotFoundException.class)
                                .hasMessageContaining("Attachment not found")
                                .hasMessageContaining("don't have permission to delete it");

                verify(attachmentRepository).findByIdAndUserId(1L, unauthorizedUserId);
                verify(attachmentRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should complete deletion even when file deletion fails")
        void shouldCompleteDeleteEvenWhenFileDeletionFails() {
                // Given - File doesn't exist (already deleted or never created)
                when(attachmentRepository.findByIdAndUserId(1L, TEST_USER_ID))
                                .thenReturn(Optional.of(testAttachment));
                doNothing().when(attachmentRepository).delete(testAttachment);

                // When - Should not throw exception even if file missing
                attachmentService.deleteAttachment(1L, TEST_USER_ID);

                // Then - Database record should still be deleted
                verify(attachmentRepository).findByIdAndUserId(1L, TEST_USER_ID);
                verify(attachmentRepository).delete(testAttachment);
        }

        // ========== LIST/FILTER TESTS (5 tests) ==========

        @Test
        @DisplayName("Should list attachments for entity with filters")
        void shouldListAttachmentsForEntity() {
                // Given
                Attachment attachment2 = Attachment.builder()
                                .id(2L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.TRANSACTION)
                                .entityId(TEST_ENTITY_ID)
                                .fileName("invoice.pdf")
                                .fileType("application/pdf")
                                .fileSize(2048L)
                                .filePath(
                                                TEST_STORAGE_PATH + "/" + TEST_USER_ID + "/TRANSACTION/uuid-2.enc")
                                .uploadedAt(LocalDateTime.now())
                                .build();

                List<Attachment> attachments = Arrays.asList(attachment2, testAttachment);

                when(attachmentRepository.findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                                TEST_USER_ID, EntityType.TRANSACTION, TEST_ENTITY_ID))
                                .thenReturn(attachments);

                // When
                List<Attachment> result = attachmentService.listAttachments(
                                EntityType.TRANSACTION, TEST_ENTITY_ID, TEST_USER_ID);

                // Then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getId()).isEqualTo(2L);
                assertThat(result.get(1).getId()).isEqualTo(1L);
                verify(attachmentRepository)
                                .findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                                                TEST_USER_ID, EntityType.TRANSACTION, TEST_ENTITY_ID);
        }

        @Test
        @DisplayName("Should list all user attachments")
        void shouldListAllUserAttachments() {
                // Given
                Attachment assetAttachment = Attachment.builder()
                                .id(2L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.ASSET)
                                .entityId(200L)
                                .fileName("photo.jpg")
                                .fileType("image/jpeg")
                                .fileSize(5120L)
                                .filePath(TEST_STORAGE_PATH + "/" + TEST_USER_ID + "/ASSET/uuid-photo.enc")
                                .uploadedAt(LocalDateTime.now().minusDays(1))
                                .build();

                List<Attachment> allAttachments = Arrays.asList(testAttachment, assetAttachment);

                when(attachmentRepository.findByUserIdOrderByUploadedAtDesc(TEST_USER_ID))
                                .thenReturn(allAttachments);

                // When
                List<Attachment> result = attachmentService.listUserAttachments(TEST_USER_ID);

                // Then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getEntityType()).isEqualTo(EntityType.TRANSACTION);
                assertThat(result.get(1).getEntityType()).isEqualTo(EntityType.ASSET);
                verify(attachmentRepository).findByUserIdOrderByUploadedAtDesc(TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return empty list when no attachments match")
        void shouldReturnEmptyListWhenNoAttachmentsMatch() {
                // Given
                when(attachmentRepository.findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                                TEST_USER_ID, EntityType.REAL_ESTATE, 999L))
                                .thenReturn(List.of());

                // When
                List<Attachment> result = attachmentService.listAttachments(EntityType.REAL_ESTATE, 999L, TEST_USER_ID);

                // Then
                assertThat(result).isEmpty();
                verify(attachmentRepository)
                                .findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                                                TEST_USER_ID, EntityType.REAL_ESTATE, 999L);
        }

        @Test
        @DisplayName("Should list attachments ordered by uploaded date descending")
        void shouldListAttachmentsOrderedByDate() {
                // Given
                Attachment recentAttachment = Attachment.builder()
                                .id(3L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.TRANSACTION)
                                .entityId(TEST_ENTITY_ID)
                                .fileName("new-receipt.pdf")
                                .fileType("application/pdf")
                                .fileSize(1500L)
                                .filePath(
                                                TEST_STORAGE_PATH
                                                                + "/"
                                                                + TEST_USER_ID
                                                                + "/TRANSACTION/uuid-new.enc")
                                .uploadedAt(LocalDateTime.now())
                                .build();

                Attachment oldAttachment = Attachment.builder()
                                .id(4L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.TRANSACTION)
                                .entityId(TEST_ENTITY_ID)
                                .fileName("old-receipt.pdf")
                                .fileType("application/pdf")
                                .fileSize(1200L)
                                .filePath(
                                                TEST_STORAGE_PATH
                                                                + "/"
                                                                + TEST_USER_ID
                                                                + "/TRANSACTION/uuid-old.enc")
                                .uploadedAt(LocalDateTime.now().minusMonths(1))
                                .build();

                // Most recent first
                List<Attachment> orderedAttachments = Arrays.asList(recentAttachment, oldAttachment);

                when(attachmentRepository.findByUserIdOrderByUploadedAtDesc(TEST_USER_ID))
                                .thenReturn(orderedAttachments);

                // When
                List<Attachment> result = attachmentService.listUserAttachments(TEST_USER_ID);

                // Then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getId()).isEqualTo(3L); // Most recent
                assertThat(result.get(1).getId()).isEqualTo(4L); // Oldest
                assertThat(result.get(0).getUploadedAt()).isAfter(result.get(1).getUploadedAt());
        }

        @Test
        @DisplayName("Should return empty list for new user")
        void shouldReturnEmptyListForNewUser() {
                // Given
                Long newUserId = 999L;
                when(attachmentRepository.findByUserIdOrderByUploadedAtDesc(newUserId))
                                .thenReturn(List.of());

                // When
                List<Attachment> result = attachmentService.listUserAttachments(newUserId);

                // Then
                assertThat(result).isEmpty();
                verify(attachmentRepository).findByUserIdOrderByUploadedAtDesc(newUserId);
        }

        // ========== GET METADATA TESTS (3 tests) ==========

        @Test
        @DisplayName("Should get attachment metadata")
        void shouldGetAttachmentMetadata() {
                // Given
                when(attachmentRepository.findByIdAndUserId(1L, TEST_USER_ID))
                                .thenReturn(Optional.of(testAttachment));

                // When
                Attachment result = attachmentService.getAttachment(1L, TEST_USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getFileName()).isEqualTo(TEST_FILE_NAME);
                assertThat(result.getFileSize()).isEqualTo(1024L);
                assertThat(result.getFileType()).isEqualTo(TEST_FILE_TYPE);
                verify(attachmentRepository).findByIdAndUserId(1L, TEST_USER_ID);
        }

        @Test
        @DisplayName("Should throw exception when attachment not found for get metadata")
        void shouldThrowExceptionWhenAttachmentNotFoundForGet() {
                // Given
                when(attachmentRepository.findByIdAndUserId(999L, TEST_USER_ID))
                                .thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> attachmentService.getAttachment(999L, TEST_USER_ID))
                                .isInstanceOf(AttachmentNotFoundException.class)
                                .hasMessageContaining("Attachment not found")
                                .hasMessageContaining("don't have permission to access it");

                verify(attachmentRepository).findByIdAndUserId(999L, TEST_USER_ID);
        }

        @Test
        @DisplayName("Should throw exception when unauthorized user gets metadata")
        void shouldThrowExceptionWhenUnauthorizedUserGetsMetadata() {
                // Given
                Long unauthorizedUserId = 999L;
                when(attachmentRepository.findByIdAndUserId(1L, unauthorizedUserId))
                                .thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> attachmentService.getAttachment(1L, unauthorizedUserId))
                                .isInstanceOf(AttachmentNotFoundException.class)
                                .hasMessageContaining("Attachment not found")
                                .hasMessageContaining("don't have permission to access it");

                verify(attachmentRepository).findByIdAndUserId(1L, unauthorizedUserId);
        }

        // ========== COUNT TESTS (2 tests) ==========

        @Test
        @DisplayName("Should count user attachments")
        void shouldCountUserAttachments() {
                // Given
                when(attachmentRepository.countByUserId(TEST_USER_ID)).thenReturn(5L);

                // When
                long count = attachmentService.countUserAttachments(TEST_USER_ID);

                // Then
                assertThat(count).isEqualTo(5L);
                verify(attachmentRepository).countByUserId(TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return zero count for new user")
        void shouldReturnZeroCountForNewUser() {
                // Given
                Long newUserId = 999L;
                when(attachmentRepository.countByUserId(newUserId)).thenReturn(0L);

                // When
                long count = attachmentService.countUserAttachments(newUserId);

                // Then
                assertThat(count).isZero();
                verify(attachmentRepository).countByUserId(newUserId);
        }

        // ========== STORAGE SIZE TESTS (3 tests) ==========

        @Test
        @DisplayName("Should calculate user storage size")
        void shouldCalculateUserStorageSize() {
                // Given - Total 15MB (1024 + 2048 + 5120 + ... = 15728640 bytes)
                when(attachmentRepository.getTotalStorageByUserId(TEST_USER_ID)).thenReturn(15728640L);

                // When
                long storageSize = attachmentService.getUserStorageSize(TEST_USER_ID);

                // Then
                assertThat(storageSize).isEqualTo(15728640L);
                verify(attachmentRepository).getTotalStorageByUserId(TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return zero storage for new user")
        void shouldReturnZeroStorageForNewUser() {
                // Given
                Long newUserId = 999L;
                when(attachmentRepository.getTotalStorageByUserId(newUserId)).thenReturn(0L);

                // When
                long storageSize = attachmentService.getUserStorageSize(newUserId);

                // Then
                assertThat(storageSize).isZero();
                verify(attachmentRepository).getTotalStorageByUserId(newUserId);
        }

        @Test
        @DisplayName("Should format storage size as human-readable")
        void shouldFormatStorageSizeAsHumanReadable() {
                // Given - 5.5 MB
                when(attachmentRepository.getTotalStorageByUserId(TEST_USER_ID))
                                .thenReturn(5767168L); // 5.5 * 1024 * 1024 bytes

                // When
                String formattedSize = attachmentService.getUserStorageSizeFormatted(TEST_USER_ID);

                // Then - Use regex to handle locale-specific decimal separator
                assertThat(formattedSize).matches("5[.,]5 MB");
                verify(attachmentRepository).getTotalStorageByUserId(TEST_USER_ID);
        }

        // ========== CASCADE DELETE TESTS (2 tests) ==========

        @Test
        @DisplayName("Should delete all attachments for entity")
        void shouldDeleteAllAttachmentsForEntity() throws Exception {
                // Given - Create files for cascade delete
                Attachment attachment2 = Attachment.builder()
                                .id(2L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.TRANSACTION)
                                .entityId(TEST_ENTITY_ID)
                                .fileName("invoice.pdf")
                                .fileType("application/pdf")
                                .fileSize(2048L)
                                .filePath(
                                                TEST_STORAGE_PATH + "/" + TEST_USER_ID + "/TRANSACTION/uuid-2.enc")
                                .uploadedAt(LocalDateTime.now())
                                .build();

                List<Attachment> attachments = Arrays.asList(testAttachment, attachment2);

                Path file1 = Paths.get(testAttachment.getFilePath());
                Path file2 = Paths.get(attachment2.getFilePath());
                Files.createDirectories(file1.getParent());
                Files.write(file1, "content1".getBytes());
                Files.write(file2, "content2".getBytes());

                when(attachmentRepository.findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                                TEST_USER_ID, EntityType.TRANSACTION, TEST_ENTITY_ID))
                                .thenReturn(attachments);
                doNothing().when(attachmentRepository).deleteAll(attachments);

                // When
                int deletedCount = attachmentService.deleteEntityAttachments(
                                EntityType.TRANSACTION, TEST_ENTITY_ID, TEST_USER_ID);

                // Then
                assertThat(deletedCount).isEqualTo(2);
                assertThat(Files.exists(file1)).isFalse();
                assertThat(Files.exists(file2)).isFalse();
                verify(attachmentRepository).deleteAll(attachments);

                // Cleanup
                Files.deleteIfExists(file1.getParent());
                Files.deleteIfExists(file1.getParent().getParent());
        }

        @Test
        @DisplayName("Should return zero when no attachments to delete")
        void shouldReturnZeroWhenNoAttachmentsToDelete() {
                // Given
                when(attachmentRepository.findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                                TEST_USER_ID, EntityType.ASSET, 999L))
                                .thenReturn(List.of());
                doNothing().when(attachmentRepository).deleteAll(List.of());

                // When
                int deletedCount = attachmentService.deleteEntityAttachments(EntityType.ASSET, 999L, TEST_USER_ID);

                // Then
                assertThat(deletedCount).isZero();
                // deleteAll is called with empty list - this is OK
                verify(attachmentRepository).deleteAll(List.of());
        }

        // ========== CLEANUP TESTS (3 tests) ==========

        @Test
        @DisplayName("Should cleanup old orphaned attachments with missing files")
        void shouldCleanupOldOrphanedAttachments() {
                // Given - Old attachment (40 days ago) with missing file
                Attachment oldOrphanedAttachment = Attachment.builder()
                                .id(5L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.TRANSACTION)
                                .entityId(TEST_ENTITY_ID)
                                .fileName("old-missing.pdf")
                                .fileType("application/pdf")
                                .fileSize(1024L)
                                .filePath("/nonexistent/path/old-missing.enc")
                                .uploadedAt(LocalDateTime.now().minusDays(40))
                                .build();

                when(attachmentRepository.findAll()).thenReturn(List.of(oldOrphanedAttachment));
                doNothing().when(attachmentRepository).delete(oldOrphanedAttachment);

                // When
                int cleanedCount = attachmentService.cleanupOrphanedAttachments();

                // Then
                assertThat(cleanedCount).isEqualTo(1);
                verify(attachmentRepository).delete(oldOrphanedAttachment);
        }

        @Test
        @DisplayName("Should not delete recent orphans less than 30 days old")
        void shouldNotDeleteRecentOrphans() {
                // Given - Recent attachment (10 days ago) with missing file
                Attachment recentOrphanedAttachment = Attachment.builder()
                                .id(6L)
                                .userId(TEST_USER_ID)
                                .entityType(EntityType.TRANSACTION)
                                .entityId(TEST_ENTITY_ID)
                                .fileName("recent-missing.pdf")
                                .fileType("application/pdf")
                                .fileSize(1024L)
                                .filePath("/nonexistent/path/recent-missing.enc")
                                .uploadedAt(LocalDateTime.now().minusDays(10))
                                .build();

                when(attachmentRepository.findAll()).thenReturn(List.of(recentOrphanedAttachment));

                // When
                int cleanedCount = attachmentService.cleanupOrphanedAttachments();

                // Then
                assertThat(cleanedCount).isZero();
                verify(attachmentRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should skip cleanup when files exist on disk")
        void shouldSkipCleanupWhenFilesExist() throws Exception {
                // Given - Attachment with existing file
                Path existingFile = Paths.get(testAttachment.getFilePath());
                Files.createDirectories(existingFile.getParent());
                Files.write(existingFile, "existing content".getBytes());

                when(attachmentRepository.findAll()).thenReturn(List.of(testAttachment));

                // When
                int cleanedCount = attachmentService.cleanupOrphanedAttachments();

                // Then
                assertThat(cleanedCount).isZero();
                verify(attachmentRepository, never()).delete(any());

                // Cleanup
                Files.deleteIfExists(existingFile);
                Files.deleteIfExists(existingFile.getParent());
                Files.deleteIfExists(existingFile.getParent().getParent());
        }
}
