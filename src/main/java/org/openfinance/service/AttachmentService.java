package org.openfinance.service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.entity.Attachment;
import org.openfinance.entity.EntityType;
import org.openfinance.exception.AttachmentNotFoundException;
import org.openfinance.exception.FileStorageException;
import org.openfinance.repository.AttachmentRepository;
import org.openfinance.security.EncryptionContext;
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
 * assets, real estate properties, and liabilities. File contents are encrypted with AES-256-GCM and
 * stored directly in the {@code attachments.file_data} database column.
 *
 * <p><strong>Security Features:</strong>
 *
 * <ul>
 *   <li>Files encrypted at rest using AES-256-GCM
 *   <li>User isolation - users can only access their own attachments
 *   <li>File type validation - only allowed MIME types accepted
 *   <li>File size limits - maximum 10MB per file
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
    private final EncryptionProperties encryptionProperties;

    @Value("${application.attachment.max-file-size:10485760}")
    private long maxFileSize;

    @Value("${application.attachment.allowed-types}")
    private String allowedTypesConfig;

    private Set<String> allowedTypes;

    /**
     * Uploads a new file attachment.
     *
     * <p>Validates the file, encrypts its contents, and stores it in the database along with a
     * metadata record.
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
            String description) {

        log.info(
                "Uploading attachment for user {} to entity {} ({})", userId, entityType, entityId);

        // Validate file
        validateFile(file);

        try {
            // Store plaintext only when application-layer encryption is explicitly disabled.
            byte[] fileBytes = file.getBytes();
            byte[] storedBytes =
                    encryptionProperties.isEnabled()
                            ? encryptionService.encryptBytes(fileBytes, EncryptionContext.getKey())
                            : fileBytes;

            // Create attachment metadata + encrypted file contents
            Attachment attachment =
                    Attachment.builder()
                            .userId(userId)
                            .entityType(entityType)
                            .entityId(entityId)
                            .fileName(file.getOriginalFilename())
                            .fileType(file.getContentType())
                            .fileSize(file.getSize())
                            .fileData(storedBytes)
                            .description(description)
                            .build();

            Attachment savedAttachment = attachmentRepository.save(attachment);

            log.info(
                    "Successfully uploaded attachment {} (ID: {}) - {} bytes, type: {}",
                    file.getOriginalFilename(),
                    savedAttachment.getId(),
                    file.getSize(),
                    file.getContentType());

            return savedAttachment;

        } catch (IOException e) {
            log.error("Failed to read uploaded attachment file", e);
            throw new FileStorageException("Failed to store attachment", e);
        }
    }

    /**
     * Downloads an attachment file.
     *
     * <p>Retrieves the encrypted file contents from the database, decrypts them, and returns as a
     * Spring Resource for streaming to the client.
     *
     * <p>Requirement REQ-2.12.2: Users can download attachments
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own files
     *
     * @param attachmentId ID of the attachment to download
     * @param userId ID of the user requesting the file (for authorization)
     * @return Resource containing decrypted file bytes
     * @throws AttachmentNotFoundException if attachment not found or unauthorized
     * @throws FileStorageException if file cannot be decrypted
     */
    @Transactional(readOnly = true)
    public Resource downloadAttachment(Long attachmentId, Long userId) {
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

        byte[] storedBytes = attachment.getFileData();

        try {
            // Return plaintext bytes directly when encryption is explicitly disabled.
            byte[] decryptedBytes =
                    encryptionProperties.isEnabled()
                            ? encryptionService.decryptBytes(
                                    storedBytes, EncryptionContext.getKey())
                            : storedBytes;

            log.info(
                    "Successfully downloaded attachment {} - {} bytes",
                    attachmentId,
                    decryptedBytes.length);

            // Return as Resource for streaming
            return new ByteArrayResource(decryptedBytes);

        } catch (Exception e) {
            log.error("Failed to decrypt attachment file", e);
            throw new FileStorageException("Failed to decrypt attachment file", e);
        }
    }

    /**
     * Deletes an attachment.
     *
     * <p>Removes the attachment record from the database.
     *
     * <p>Requirement REQ-2.12.3: Users can delete attachments
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only delete their own files
     *
     * @param attachmentId ID of the attachment to delete
     * @param userId ID of the user requesting deletion (for authorization)
     * @throws AttachmentNotFoundException if attachment not found or unauthorized
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

        // Delete database record
        attachmentRepository.delete(attachment);

        log.info("Successfully deleted attachment {} ({})", attachmentId, attachment.getFileName());
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

        int count = attachments.size();
        attachmentRepository.deleteAll(attachments);

        log.info("Deleted {} attachments for entity {} ({})", count, entityType, entityId);
        return count;
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
