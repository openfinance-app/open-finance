package org.openfinance.controller;

import jakarta.validation.constraints.NotNull;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Attachment;
import org.openfinance.entity.EntityType;
import org.openfinance.service.AttachmentService;
import org.openfinance.util.ControllerUtil;
import org.openfinance.util.EncryptionUtil;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for file attachment management endpoints.
 *
 * <p>Provides endpoints for uploading, downloading, deleting, and listing file attachments
 * associated with financial entities (transactions, assets, real estate, liabilities).
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/attachments - Upload new attachment
 *   <li>GET /api/v1/attachments/{id}/download - Download attachment file
 *   <li>DELETE /api/v1/attachments/{id} - Delete attachment
 *   <li>GET /api/v1/attachments - List attachments (with optional filters)
 *   <li>GET /api/v1/attachments/{id} - Get attachment metadata
 *   <li>GET /api/v1/attachments/stats - Get storage statistics
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key required via X-Encryption-Key header for file operations
 *   <li>Users can only access their own attachments
 *   <li>Files are encrypted at rest using AES-256-GCM
 *   <li>File type validation - only allowed MIME types accepted
 *   <li>File size limit - maximum 10MB per file
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.12: File Attachment System - CRUD operations
 *   <li>REQ-2.12.1: Upload files to entities
 *   <li>REQ-2.12.2: Download and view attachments
 *   <li>REQ-2.12.3: Delete attachments
 *   <li>REQ-2.12.5: 10MB file size limit
 *   <li>REQ-2.12.6: File encryption at rest
 *   <li>REQ-3.2: Authorization - Users access only their own data
 * </ul>
 *
 * @see AttachmentService
 * @see Attachment
 * @author Open-Finance Development Team
 * @since Sprint 12
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

    private final AttachmentService attachmentService;

    /**
     * Uploads a new file attachment.
     *
     * <p>Validates the file (type, size), encrypts it, stores on filesystem, and creates a database
     * record linking it to the specified entity.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     *   <li>Content-Type: multipart/form-data
     * </ul>
     *
     * <p><strong>Form Parameters:</strong>
     *
     * <ul>
     *   <li>file (required): The file to upload
     *   <li>entityType (required): Entity type (TRANSACTION, ASSET, REAL_ESTATE, LIABILITY)
     *   <li>entityId (required): ID of the entity to attach file to
     *   <li>description (optional): User description/notes about the file
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * POST /api/v1/attachments
     * Authorization: Bearer eyJhbGc...
     * X-Encryption-Key: a2V5MTIzNDU2Nzg5MA==
     * Content-Type: multipart/form-data
     *
     * Form data:
     *   file: receipt.pdf (binary)
     *   entityType: TRANSACTION
     *   entityId: 123
     *   description: Receipt for grocery purchase
     * </pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * {
     *   "id": 1,
     *   "userId": 1,
     *   "entityType": "TRANSACTION",
     *   "entityId": 123,
     *   "fileName": "receipt.pdf",
     *   "fileType": "application/pdf",
     *   "fileSize": 245678,
     *   "filePath": "attachments/1/TRANSACTION/uuid.enc",
     *   "uploadedAt": "2026-02-03T15:30:00",
     *   "description": "Receipt for grocery purchase"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 Bad Request - Invalid file type, size too large, validation failure
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>403 Forbidden - Missing encryption key header
     *   <li>500 Internal Server Error - File storage failure
     * </ul>
     *
     * <p>Requirement REQ-2.12.1: Upload attachments to entities
     *
     * @param file The uploaded file (multipart/form-data)
     * @param entityType Type of entity to attach file to
     * @param entityId ID of the entity
     * @param description Optional description/notes
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with Attachment metadata
     * @throws IllegalArgumentException if validation fails or encryption key missing
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Attachment> uploadAttachment(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam("entityType") @NotNull EntityType entityType,
            @RequestParam("entityId") @NotNull Long entityId,
            @RequestParam(value = "description", required = false) String description,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Attachment upload requested: file={}, entityType={}, entityId={}",
                file.getOriginalFilename(),
                entityType,
                entityId);

        // Validate encryption key header
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Encryption key header (X-Encryption-Key) is required");
        }

        // Extract user ID
        Long userId = ControllerUtil.extractUserId(authentication);

        // Decode encryption key
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        // Upload attachment (validates file, encrypts, stores, creates record)
        Attachment attachment =
                attachmentService.uploadAttachment(
                        file, userId, entityType, entityId, encryptionKey, description);

        log.info(
                "Attachment uploaded successfully: id={}, fileName={}, size={} bytes",
                attachment.getId(),
                attachment.getFileName(),
                attachment.getFileSize());

        return ResponseEntity.status(HttpStatus.CREATED).body(attachment);
    }

    /**
     * Downloads an attachment file.
     *
     * <p>Retrieves the encrypted file from filesystem, decrypts it, and streams it to the client
     * with appropriate content type and filename headers.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * GET /api/v1/attachments/1/download
     * Authorization: Bearer eyJhbGc...
     * X-Encryption-Key: a2V5MTIzNDU2Nzg5MA==
     * </pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>
     * Content-Type: application/pdf
     * Content-Disposition: attachment; filename="receipt.pdf"
     * Content-Length: 245678
     *
     * [Binary file data]
     * </pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>403 Forbidden - Missing encryption key or wrong key
     *   <li>404 Not Found - Attachment not found or unauthorized access
     *   <li>500 Internal Server Error - File read/decryption failure
     * </ul>
     *
     * <p>Requirement REQ-2.12.2: Download attachments
     *
     * @param attachmentId ID of the attachment to download
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return ResponseEntity with file Resource and appropriate headers
     * @throws IllegalArgumentException if encryption key missing
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("id") Long attachmentId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Attachment download requested: attachmentId={}", attachmentId);

        // Validate encryption key header
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Encryption key header (X-Encryption-Key) is required");
        }

        // Extract user ID
        Long userId = ControllerUtil.extractUserId(authentication);

        // Decode encryption key
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        // Get attachment metadata (includes authorization check)
        Attachment attachment = attachmentService.getAttachment(attachmentId, userId);

        // Download and decrypt file
        Resource resource =
                attachmentService.downloadAttachment(attachmentId, userId, encryptionKey);

        // Encode filename for Content-Disposition header (handles special characters)
        String encodedFilename =
                URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                        .replace("+", "%20");

        log.info(
                "Streaming attachment file: id={}, fileName={}, size={} bytes",
                attachmentId,
                attachment.getFileName(),
                attachment.getFileSize());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFilename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(attachment.getFileSize()))
                .body(resource);
    }

    /**
     * Deletes an attachment.
     *
     * <p>Removes the attachment record from database and deletes the encrypted file from the
     * filesystem.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * DELETE /api/v1/attachments/1
     * Authorization: Bearer eyJhbGc...
     * </pre>
     *
     * <p><strong>Success Response (HTTP 204 No Content):</strong>
     *
     * <pre>
     * (Empty response body)
     * </pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>404 Not Found - Attachment not found or unauthorized access
     *   <li>500 Internal Server Error - File deletion failure
     * </ul>
     *
     * <p>Requirement REQ-2.12.3: Delete attachments
     *
     * @param attachmentId ID of the attachment to delete
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable("id") Long attachmentId, Authentication authentication) {

        log.info("Attachment deletion requested: attachmentId={}", attachmentId);

        // Extract user ID
        Long userId = ControllerUtil.extractUserId(authentication);

        // Delete attachment (includes authorization check)
        attachmentService.deleteAttachment(attachmentId, userId);

        log.info("Attachment deleted successfully: id={}", attachmentId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists attachments with optional filters.
     *
     * <p>Returns attachment metadata (not file contents) for all attachments matching the specified
     * filters. Can filter by entity type and entity ID.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Query Parameters (all optional):</strong>
     *
     * <ul>
     *   <li>entityType: Filter by entity type (TRANSACTION, ASSET, REAL_ESTATE, LIABILITY)
     *   <li>entityId: Filter by specific entity ID (requires entityType)
     * </ul>
     *
     * <p><strong>Example Requests:</strong>
     *
     * <pre>
     * GET /api/v1/attachments
     * Authorization: Bearer eyJhbGc...
     * (Returns all user attachments)
     *
     * GET /api/v1/attachments?entityType=TRANSACTION&entityId=123
     * Authorization: Bearer eyJhbGc...
     * (Returns attachments for transaction 123)
     * </pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     *   {
     *     "id": 1,
     *     "userId": 1,
     *     "entityType": "TRANSACTION",
     *     "entityId": 123,
     *     "fileName": "receipt.pdf",
     *     "fileType": "application/pdf",
     *     "fileSize": 245678,
     *     "filePath": "attachments/1/TRANSACTION/uuid.enc",
     *     "uploadedAt": "2026-02-03T15:30:00",
     *     "description": "Receipt for grocery purchase"
     *   },
     *   {
     *     "id": 2,
     *     "userId": 1,
     *     "entityType": "TRANSACTION",
     *     "entityId": 123,
     *     "fileName": "invoice.jpg",
     *     "fileType": "image/jpeg",
     *     "fileSize": 156789,
     *     "filePath": "attachments/1/TRANSACTION/uuid2.enc",
     *     "uploadedAt": "2026-02-03T16:00:00",
     *     "description": null
     *   }
     * ]
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>400 Bad Request - entityId provided without entityType
     *   <li>401 Unauthorized - Missing or invalid JWT token
     * </ul>
     *
     * <p>Requirement REQ-2.12.4: List attachments for entities
     *
     * @param entityType Optional entity type filter
     * @param entityId Optional entity ID filter (requires entityType)
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with List of Attachment metadata
     * @throws IllegalArgumentException if entityId provided without entityType
     */
    @GetMapping
    public ResponseEntity<List<Attachment>> listAttachments(
            @RequestParam(value = "entityType", required = false) EntityType entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            Authentication authentication) {

        log.info("Attachment list requested: entityType={}, entityId={}", entityType, entityId);

        // Validate parameters
        if (entityId != null && entityType == null) {
            throw new IllegalArgumentException("entityType is required when entityId is provided");
        }

        // Extract user ID
        Long userId = ControllerUtil.extractUserId(authentication);

        // Get attachments based on filters
        List<Attachment> attachments;

        if (entityType != null && entityId != null) {
            // Filter by specific entity
            attachments = attachmentService.listAttachments(entityType, entityId, userId);
            log.debug(
                    "Found {} attachments for entity {} ({})",
                    attachments.size(),
                    entityType,
                    entityId);
        } else {
            // Return all user attachments
            attachments = attachmentService.listUserAttachments(userId);
            log.debug("Found {} total attachments for user {}", attachments.size(), userId);
        }

        return ResponseEntity.ok(attachments);
    }

    /**
     * Gets attachment metadata by ID.
     *
     * <p>Returns metadata only (not file contents). Useful for displaying attachment info before
     * downloading.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * GET /api/v1/attachments/1
     * Authorization: Bearer eyJhbGc...
     * </pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     *   "id": 1,
     *   "userId": 1,
     *   "entityType": "TRANSACTION",
     *   "entityId": 123,
     *   "fileName": "receipt.pdf",
     *   "fileType": "application/pdf",
     *   "fileSize": 245678,
     *   "filePath": "attachments/1/TRANSACTION/uuid.enc",
     *   "uploadedAt": "2026-02-03T15:30:00",
     *   "description": "Receipt for grocery purchase"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>401 Unauthorized - Missing or invalid JWT token
     *   <li>404 Not Found - Attachment not found or unauthorized access
     * </ul>
     *
     * @param attachmentId ID of the attachment
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with Attachment metadata
     */
    @GetMapping("/{id}")
    public ResponseEntity<Attachment> getAttachment(
            @PathVariable("id") Long attachmentId, Authentication authentication) {

        log.debug("Attachment metadata requested: attachmentId={}", attachmentId);

        // Extract user ID
        Long userId = ControllerUtil.extractUserId(authentication);

        // Get attachment metadata (includes authorization check)
        Attachment attachment = attachmentService.getAttachment(attachmentId, userId);

        return ResponseEntity.ok(attachment);
    }

    /**
     * Gets storage statistics for the authenticated user.
     *
     * <p>Returns total attachment count and total storage used in bytes and human-readable format.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * GET /api/v1/attachments/stats
     * Authorization: Bearer eyJhbGc...
     * </pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     *   "totalAttachments": 15,
     *   "totalSizeBytes": 12845678,
     *   "totalSizeFormatted": "12.3 MB"
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.12.5: Track storage usage per user
     *
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with storage statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<StorageStatsResponse> getStorageStats(Authentication authentication) {

        log.debug("Storage statistics requested");

        // Extract user ID
        Long userId = ControllerUtil.extractUserId(authentication);

        // Get statistics
        long totalAttachments = attachmentService.countUserAttachments(userId);
        long totalSizeBytes = attachmentService.getUserStorageSize(userId);
        String totalSizeFormatted = attachmentService.getUserStorageSizeFormatted(userId);

        StorageStatsResponse stats =
                new StorageStatsResponse(totalAttachments, totalSizeBytes, totalSizeFormatted);

        log.debug(
                "Storage stats for user {}: {} attachments, {} bytes ({})",
                userId,
                totalAttachments,
                totalSizeBytes,
                totalSizeFormatted);

        return ResponseEntity.ok(stats);
    }

    /** Response DTO for storage statistics. */
    public record StorageStatsResponse(
            long totalAttachments, long totalSizeBytes, String totalSizeFormatted) {}
}
