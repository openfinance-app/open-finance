package org.openfinance.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.FileUploadResponse;
import org.openfinance.entity.User;
import org.openfinance.service.FileStorageService;
import org.openfinance.service.FileValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for handling file upload operations in the transaction import workflow. Accepts
 * QIF, OFX, QFX, and CSV files for transaction import.
 *
 * <p>Requirement REQ-2.5.1.1: File Format Support
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /import/upload - Upload import file
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;

    /**
     * Uploads a file for transaction import. Validates the file and stores it temporarily for
     * processing.
     *
     * <p>Requirement REQ-2.5.1.1: File Format Support
     *
     * <p>Example request:
     *
     * <pre>
     * POST /api/v1/import/upload
     * Content-Type: multipart/form-data
     * Authorization: Bearer {token}
     *
     * Form data:
     *   file: transactions.qif (binary)
     * </pre>
     *
     * <p>Example successful response (HTTP 200):
     *
     * <pre>
     * {
     *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     *   "fileName": "transactions.qif",
     *   "fileSize": 12345,
     *   "fileType": "qif",
     *   "status": "VALIDATED",
     *   "message": "File uploaded successfully",
     *   "uploadedAt": "2024-01-15T10:30:00"
     * }
     * </pre>
     *
     * <p>Example error response (HTTP 400):
     *
     * <pre>
     * {
     *   "uploadId": null,
     *   "fileName": "document.txt",
     *   "fileSize": 5000,
     *   "fileType": "txt",
     *   "status": "INVALID",
     *   "message": "File type '.txt' not allowed. Allowed types: .qif, .ofx, .qfx, .csv",
     *   "uploadedAt": "2024-01-15T10:30:00"
     * }
     * </pre>
     *
     * @param file The file to upload (multipart/form-data)
     * @param authentication Spring Security authentication (auto-injected)
     * @return FileUploadResponse with upload details and validation status
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file, Authentication authentication) {

        Long userId = extractUserId(authentication);
        log.info(
                "User {} uploading file: {} (size: {} bytes)",
                userId,
                file.getOriginalFilename(),
                file.getSize());

        // Validate the file
        FileValidationService.ValidationResult validationResult =
                fileValidationService.validate(file);

        if (!validationResult.isValid()) {
            log.warn(
                    "File validation failed for user {}: {}",
                    userId,
                    validationResult.getErrorMessage());

            FileUploadResponse response =
                    FileUploadResponse.builder()
                            .uploadId(null)
                            .fileName(file.getOriginalFilename())
                            .fileSize(file.getSize())
                            .fileType(getFileExtension(file.getOriginalFilename()))
                            .status("INVALID")
                            .message(validationResult.getErrorMessage())
                            .uploadedAt(LocalDateTime.now())
                            .recordCount(null)
                            .build();

            return ResponseEntity.badRequest().body(response);
        }

        // Store the file
        String uploadId;
        try {
            uploadId = fileStorageService.storeFile(file);
        } catch (IOException e) {
            log.error("Failed to store file for user {}", userId, e);

            FileUploadResponse response =
                    FileUploadResponse.builder()
                            .uploadId(null)
                            .fileName(file.getOriginalFilename())
                            .fileSize(file.getSize())
                            .fileType(validationResult.getDetectedFormat())
                            .status("ERROR")
                            .message("Failed to store file: " + e.getMessage())
                            .uploadedAt(LocalDateTime.now())
                            .recordCount(null)
                            .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Build successful response
        FileUploadResponse response =
                FileUploadResponse.builder()
                        .uploadId(uploadId)
                        .fileName(file.getOriginalFilename())
                        .fileSize(file.getSize())
                        .fileType(validationResult.getDetectedFormat())
                        .status("VALIDATED")
                        .message("File uploaded successfully")
                        .uploadedAt(LocalDateTime.now())
                        .recordCount(null) // Will be populated after parsing
                        .build();

        log.info("File uploaded successfully for user {} with upload ID: {}", userId, uploadId);
        return ResponseEntity.ok(response);
    }

    /**
     * Extracts user ID from authentication token.
     *
     * @param authentication Spring Security authentication
     * @return User ID
     */
    private Long extractUserId(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return user.getId();
    }

    /**
     * Extracts file extension from filename.
     *
     * @param filename The filename
     * @return File extension without dot, or "unknown"
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "unknown";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return "unknown";
    }
}
