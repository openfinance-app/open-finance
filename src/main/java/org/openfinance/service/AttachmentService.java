package org.openfinance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Attachment;
import org.openfinance.entity.EntityType;
import org.openfinance.exception.AttachmentNotFoundException;
import org.openfinance.exception.FileStorageException;
import org.openfinance.repository.AttachmentRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for managing file attachments associated with financial entities.
 *
 * <p>This service handles uploading, downloading, and deleting file attachments for transactions,
 * assets, real estate properties, and liabilities. Files are stored encrypted on the filesystem
 * using AES-256-GCM encryption.
 *
 * <p><strong>File Storage Organization:</strong>
 *
 * <pre>
 * attachments/
 *   ├── {userId}/
 *   │   ├── TRANSACTION/
 *   │   │   ├── {uuid}.enc
 *   │   │   └── {uuid}.enc
 *   │   ├── ASSET/
 *   │   │   └── {uuid}.enc
 *   │   ├── REAL_ESTATE/
 *   │   │   └── {uuid}.enc
 *   │   └── LIABILITY/
 *   │       └── {uuid}.enc
 * </pre>
 *
 * <p><strong>Security Features:</strong>
 *
 * <ul>
 *   <li>Files encrypted at rest using AES-256-GCM
 *   <li>User isolation - users can only access their own attachments
 *   <li>File type validation - only allowed MIME types accepted
 *   <li>File size limits - maximum 10MB per file
 *   <li>Automatic cleanup of orphaned attachments
 * </ul>
 *
 * <p>Requirement REQ-2.12: File Attachment System
 *
 * <p>Requirement REQ-3.2: Security - Encryption at rest
 *
 * @author Open-Finance Development Team
 * @since Sprint 12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final EncryptionService encryptionService;

    @Value("${application.attachment.storage-path:./attachments}")
    private String storagePath;

    @Value("${application.attachment.max-file-size:10485760}")
    private long maxFileSize;

    @Value("${application.attachment.allowed-types}")
    private String allowedTypesConfig;

    @Value("${application.attachment.cleanup-orphaned-after-days:30}")
    private int cleanupOrphanedAfterDays;

    private Set<String> allowedTypes;

    /**
     * Uploads a new file attachment.
     *
     * <p>Validates the file, encrypts its contents, stores it on the filesystem, and creates a
     * database record with metadata.
     *
     * <p>Requirement REQ-2.12.1: Users can upload files
     *
     * <p>Requirement REQ-2.12.5: File size limit of 10MB
     *
     * <p>Requirement REQ-2.12.6: File encryption at rest
     *
     * @param file Uploaded file from multipart request
     * @param userId ID of the user uploading the file
     * @param entityType Type of entity (TRANSACTION, ASSET, etc.)
     * @param entityId ID of the entity to attach file to
     * @param encryptionKey User's encryption key for encrypting file
     * @param description Optional description/notes about the file
     * @return Created Attachment entity with metadata
     * @throws IllegalArgumentException if file validation fails
     * @throws FileStorageException if file storage fails
     */
    @Transactional
    public Attachment uploadAttachment(
            MultipartFile file,
            Long userId,
            EntityType entityType,
            Long entityId,
            SecretKey encryptionKey,
            String description) {

        log.info(
                "Uploading attachment for user {} to entity {} ({})", userId, entityType, entityId);

        // Validate file
        validateFile(file);

        try {
            // Generate unique file path
            String fileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(fileName);
            String uniqueFileName = UUID.randomUUID().toString() + ".enc";

            // Create directory structure: attachments/{userId}/{entityType}/
            Path userDirectory = Paths.get(storagePath, userId.toString(), entityType.name());
            Files.createDirectories(userDirectory);

            Path filePath = userDirectory.resolve(uniqueFileName);

            // Encrypt file contents
            byte[] fileBytes = file.getBytes();
            byte[] encryptedBytes = encryptionService.encryptBytes(fileBytes, encryptionKey);

            // Write encrypted file to disk
            Files.write(filePath, encryptedBytes, StandardOpenOption.CREATE_NEW);

            // Create attachment metadata
            Attachment attachment =
                    Attachment.builder()
                            .userId(userId)
                            .entityType(entityType)
                            .entityId(entityId)
                            .fileName(fileName)
                            .fileType(file.getContentType())
                            .fileSize(file.getSize())
                            .filePath(filePath.toString())
                            .description(description)
                            .build();

            Attachment savedAttachment = attachmentRepository.save(attachment);

            log.info(
                    "Successfully uploaded attachment {} (ID: {}) - {} bytes, type: {}",
                    fileName,
                    savedAttachment.getId(),
                    file.getSize(),
                    file.getContentType());

            return savedAttachment;

        } catch (IOException e) {
            log.error("Failed to store attachment file", e);
            throw new FileStorageException("Failed to store attachment", e);
        }
    }

    /**
     * Downloads an attachment file.
     *
     * <p>Retrieves the encrypted file from filesystem, decrypts it, and returns as a Spring
     * Resource for streaming to the client.
     *
     * <p>Requirement REQ-2.12.2: Users can download attachments
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own files
     *
     * @param attachmentId ID of the attachment to download
     * @param userId ID of the user requesting the file (for authorization)
     * @param encryptionKey User's encryption key for decrypting file
     * @return Resource containing decrypted file bytes
     * @throws AttachmentNotFoundException if attachment not found or unauthorized
     * @throws FileStorageException if file cannot be read or decrypted
     */
    @Transactional(readOnly = true)
    public Resource downloadAttachment(Long attachmentId, Long userId, SecretKey encryptionKey) {
        log.info("Downloading attachment {} for user {}", attachmentId, userId);

        // Retrieve attachment metadata with authorization check
        Attachment attachment =
                attachmentRepository
                        .findByIdAndUserId(attachmentId, userId)
                        .orElseThrow(
                                () ->
                                        new AttachmentNotFoundException(
                                                "Attachment not found or you don't have permission to access it: "
                                                        + attachmentId));

        try {
            // Read encrypted file from disk
            Path filePath = Paths.get(attachment.getFilePath());

            if (!Files.exists(filePath)) {
                log.error("Attachment file not found on disk: {}", filePath);
                throw new FileStorageException("Attachment file not found on disk");
            }

            byte[] encryptedBytes = Files.readAllBytes(filePath);

            // Decrypt file contents
            byte[] decryptedBytes = encryptionService.decryptBytes(encryptedBytes, encryptionKey);

            log.info(
                    "Successfully downloaded attachment {} - {} bytes",
                    attachmentId,
                    decryptedBytes.length);

            // Return as Resource for streaming
            return new ByteArrayResource(decryptedBytes);

        } catch (IOException e) {
            log.error("Failed to read attachment file: {}", attachment.getFilePath(), e);
            throw new FileStorageException("Failed to read attachment file", e);
        } catch (Exception e) {
            log.error("Failed to decrypt attachment file", e);
            throw new FileStorageException("Failed to decrypt attachment file", e);
        }
    }

    /**
     * Deletes an attachment.
     *
     * <p>Removes the attachment record from database and deletes the encrypted file from the
     * filesystem.
     *
     * <p>Requirement REQ-2.12.3: Users can delete attachments
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only delete their own files
     *
     * @param attachmentId ID of the attachment to delete
     * @param userId ID of the user requesting deletion (for authorization)
     * @throws AttachmentNotFoundException if attachment not found or unauthorized
     * @throws FileStorageException if file deletion fails
     */
    @Transactional
    public void deleteAttachment(Long attachmentId, Long userId) {
        log.info("Deleting attachment {} for user {}", attachmentId, userId);

        // Retrieve attachment metadata with authorization check
        Attachment attachment =
                attachmentRepository
                        .findByIdAndUserId(attachmentId, userId)
                        .orElseThrow(
                                () ->
                                        new AttachmentNotFoundException(
                                                "Attachment not found or you don't have permission to delete it: "
                                                        + attachmentId));

        try {
            // Delete file from disk
            Path filePath = Paths.get(attachment.getFilePath());
            Files.deleteIfExists(filePath);

            // Delete database record
            attachmentRepository.delete(attachment);

            log.info(
                    "Successfully deleted attachment {} ({})",
                    attachmentId,
                    attachment.getFileName());

        } catch (IOException e) {
            log.error("Failed to delete attachment file: {}", attachment.getFilePath(), e);
            throw new FileStorageException("Failed to delete attachment file", e);
        }
    }

    /**
     * Lists all attachments for a specific entity.
     *
     * <p>Returns attachment metadata (no file contents) for all files attached to the entity.
     *
     * <p>Requirement REQ-2.12.4: Users can list attachments for entities
     *
     * @param entityType Type of entity
     * @param entityId ID of the entity
     * @param userId ID of the user (for authorization)
     * @return List of attachments, ordered by upload date descending
     */
    @Transactional(readOnly = true)
    public List<Attachment> listAttachments(EntityType entityType, Long entityId, Long userId) {
        log.debug(
                "Listing attachments for entity {} ({}) for user {}", entityType, entityId, userId);

        return attachmentRepository.findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                userId, entityType, entityId);
    }

    /**
     * Gets attachment metadata by ID.
     *
     * <p>Returns metadata only (no file contents). Useful for displaying attachment info.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own attachments
     *
     * @param attachmentId ID of the attachment
     * @param userId ID of the user (for authorization)
     * @return Attachment metadata
     * @throws AttachmentNotFoundException if attachment not found or unauthorized
     */
    @Transactional(readOnly = true)
    public Attachment getAttachment(Long attachmentId, Long userId) {
        log.debug("Getting attachment {} for user {}", attachmentId, userId);

        return attachmentRepository
                .findByIdAndUserId(attachmentId, userId)
                .orElseThrow(
                        () ->
                                new AttachmentNotFoundException(
                                        "Attachment not found or you don't have permission to access it: "
                                                + attachmentId));
    }

    /**
     * Lists all attachments for a user.
     *
     * <p>Returns all attachments owned by the user across all entities.
     *
     * @param userId ID of the user
     * @return List of all user attachments, ordered by upload date descending
     */
    @Transactional(readOnly = true)
    public List<Attachment> listUserAttachments(Long userId) {
        log.debug("Listing all attachments for user {}", userId);

        return attachmentRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    /**
     * Counts total attachments for a user.
     *
     * <p>Useful for displaying storage statistics.
     *
     * @param userId ID of the user
     * @return Total count of attachments
     */
    @Transactional(readOnly = true)
    public long countUserAttachments(Long userId) {
        return attachmentRepository.countByUserId(userId);
    }

    /**
     * Calculates total storage used by user's attachments.
     *
     * <p>Sums the file sizes of all user attachments.
     *
     * <p>Requirement REQ-2.12.5: Track storage usage per user
     *
     * @param userId ID of the user
     * @return Total storage in bytes
     */
    @Transactional(readOnly = true)
    public long getUserStorageSize(Long userId) {
        return attachmentRepository.getTotalStorageByUserId(userId);
    }

    /**
     * Gets human-readable storage size for user.
     *
     * @param userId ID of the user
     * @return Formatted storage size (e.g., "1.5 MB")
     */
    @Transactional(readOnly = true)
    public String getUserStorageSizeFormatted(Long userId) {
        long bytes = getUserStorageSize(userId);
        return formatFileSize(bytes);
    }

    /**
     * Deletes all attachments for a specific entity.
     *
     * <p>Used when deleting parent entities (transaction, asset, etc.) to clean up orphaned
     * attachments.
     *
     * <p>Requirement REQ-2.12.7: Cascade delete attachments when parent entity is deleted
     *
     * @param entityType Type of entity
     * @param entityId ID of the entity
     * @param userId ID of the user (for authorization)
     * @return Number of attachments deleted
     */
    @Transactional
    public int deleteEntityAttachments(EntityType entityType, Long entityId, Long userId) {
        log.info(
                "Deleting all attachments for entity {} ({}) for user {}",
                entityType,
                entityId,
                userId);

        List<Attachment> attachments = listAttachments(entityType, entityId, userId);

        for (Attachment attachment : attachments) {
            try {
                Path filePath = Paths.get(attachment.getFilePath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete attachment file: {}", attachment.getFilePath(), e);
            }
        }

        int count = attachments.size();
        attachmentRepository.deleteAll(attachments);

        log.info("Deleted {} attachments for entity {} ({})", count, entityType, entityId);
        return count;
    }

    /**
     * Cleans up orphaned attachment files.
     *
     * <p>Finds attachment records in database with missing files on disk, and files on disk with no
     * database records. Removes orphaned entries.
     *
     * <p>This method should be called periodically by a scheduled task.
     *
     * <p>Requirement REQ-2.12.8: Automatic cleanup of orphaned attachments
     *
     * @return Number of orphaned attachments cleaned up
     */
    @Transactional
    public int cleanupOrphanedAttachments() {
        log.info("Starting cleanup of orphaned attachments");

        int cleanedCount = 0;
        List<Attachment> allAttachments = attachmentRepository.findAll();

        // Find attachments with missing files
        for (Attachment attachment : allAttachments) {
            Path filePath = Paths.get(attachment.getFilePath());
            if (!Files.exists(filePath)) {
                log.warn("Attachment {} has missing file: {}", attachment.getId(), filePath);

                // Only delete if attachment is old enough
                LocalDateTime cutoffDate =
                        LocalDateTime.now().minus(cleanupOrphanedAfterDays, ChronoUnit.DAYS);
                if (attachment.getUploadedAt().isBefore(cutoffDate)) {
                    attachmentRepository.delete(attachment);
                    cleanedCount++;
                    log.info("Deleted orphaned attachment record: {}", attachment.getId());
                }
            }
        }

        log.info("Cleanup completed: removed {} orphaned attachment records", cleanedCount);
        return cleanedCount;
    }

    /**
     * Validates uploaded file.
     *
     * <p>Checks file size, MIME type, and file name.
     *
     * <p>Requirement REQ-2.12.5: File size limit enforcement
     *
     * <p>Requirement REQ-2.12.6: File type validation
     *
     * @param file Uploaded file
     * @throws IllegalArgumentException if validation fails
     */
    private void validateFile(MultipartFile file) {
        // Initialize allowed types if not already done
        if (allowedTypes == null) {
            allowedTypes = Set.of(allowedTypesConfig.split(","));
        }

        // Check if file is empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format(
                            "File size (%d bytes) exceeds maximum allowed size (%d bytes)",
                            file.getSize(), maxFileSize));
        }

        // Check file name
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }

        // Check for directory traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException(
                    "Invalid file name: directory traversal not allowed");
        }

        // Check MIME type
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException(
                    String.format(
                            "File type '%s' is not allowed. Allowed types: %s",
                            contentType, String.join(", ", allowedTypes)));
        }

        log.debug(
                "File validation passed: {} ({}, {} bytes)", fileName, contentType, file.getSize());
    }

    /**
     * Extracts file extension from filename.
     *
     * @param fileName The filename
     * @return File extension (including dot) or empty string if no extension
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes File size in bytes
     * @return Formatted string (e.g., "1.5 MB", "234 KB")
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
