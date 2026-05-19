package org.openfinance.controller;

import jakarta.validation.Valid;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.BackupRequest;
import org.openfinance.dto.BackupResponse;
import org.openfinance.entity.Backup;
import org.openfinance.service.BackupService;
import org.openfinance.util.ControllerUtil;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for backup and restore operations.
 *
 * <p><b>Base URL:</b> /api/v1/backup
 *
 * <p><b>Requirements:</b> REQ-2.14.2.1, REQ-2.14.2.2, REQ-2.14.2.3
 *
 * <p><b>Endpoints:</b>
 *
 * <ul>
 *   <li>POST /api/v1/backup/create - Create manual backup
 *   <li>POST /api/v1/backup/restore/{id} - Restore from existing backup
 *   <li>POST /api/v1/backup/restore/upload - Restore from uploaded file
 *   <li>GET /api/v1/backup/list - List all backups
 *   <li>GET /api/v1/backup/{id} - Get backup details
 *   <li>GET /api/v1/backup/{id}/download - Download backup file
 *   <li>DELETE /api/v1/backup/{id} - Delete backup
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@RestController
@RequestMapping("/api/v1/backup")
@Slf4j
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * Creates a manual backup for the authenticated user.
     *
     * <p><b>Requirement:</b> REQ-2.14.2.1
     *
     * <p><b>Request Example:</b>
     *
     * <pre>
     * POST /api/v1/backup/create
     * {
     *   "description": "Manual backup before major update"
     * }
     * </pre>
     *
     * <p><b>Response Example:</b>
     *
     * <pre>
     * {
     *   "id": 1,
     *   "filename": "openfinance-backup-20260204-143052.ofbak",
     *   "fileSize": 1048576,
     *   "formattedFileSize": "1.00 MB",
     *   "checksum": "a1b2c3...",
     *   "status": "COMPLETED",
     *   "backupType": "MANUAL",
     *   "description": "Manual backup before major update",
     *   "createdAt": "2026-02-04T14:30:52"
     * }
     * </pre>
     *
     * @param request the backup request (optional description)
     * @param authentication the authenticated user
     * @return BackupResponse with backup metadata
     */
    @PostMapping("/create")
    public ResponseEntity<BackupResponse> createBackup(
            @RequestBody(required = false) @Valid BackupRequest request,
            Authentication authentication) {

        log.info("Creating manual backup");

        Long userId = ControllerUtil.extractUserId(authentication);
        String description = (request != null) ? request.getDescription() : null;

        Backup backup = backupService.createBackup(userId, description);
        BackupResponse response = toResponse(backup);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Restores a backup from an existing backup record.
     *
     * <p><b>Requirement:</b> REQ-2.14.2.3
     *
     * <p><b>Warning:</b> This operation will overwrite existing data. A safety backup is
     * automatically created before restore.
     *
     * @param backupId the ID of the backup to restore
     * @param authentication the authenticated user
     * @return success message
     */
    @PostMapping("/restore/{id}")
    public ResponseEntity<String> restoreBackup(
            @PathVariable("id") Long backupId, Authentication authentication) {

        log.info("Restoring backup ID: {}", backupId);

        Long userId = ControllerUtil.extractUserId(authentication);
        backupService.restoreBackup(userId, backupId);

        return ResponseEntity.ok(
                "Backup restored successfully. Application restart may be required.");
    }

    /**
     * Restores a backup from an uploaded file.
     *
     * <p><b>Requirement:</b> REQ-2.14.2.3
     *
     * <p><b>Request Example:</b>
     *
     * <pre>
     * POST /api/v1/backup/restore/upload
     * Content-Type: multipart/form-data
     * file: openfinance-backup-20260204-143052.ofbak
     * </pre>
     *
     * @param file the backup file (.ofbak)
     * @param authentication the authenticated user
     * @return success message
     */
    @PostMapping("/restore/upload")
    public ResponseEntity<String> restoreBackupFromFile(
            @RequestParam("file") MultipartFile file, Authentication authentication) {

        log.info("Restoring backup from uploaded file: {}", file.getOriginalFilename());

        Long userId = ControllerUtil.extractUserId(authentication);
        backupService.restoreBackupFromFile(userId, file);

        return ResponseEntity.ok(
                "Backup restored successfully from uploaded file. Application restart may be required.");
    }

    /**
     * Lists all backups for the authenticated user.
     *
     * <p><b>Response Example:</b>
     *
     * <pre>
     * [
     *   {
     *     "id": 2,
     *     "filename": "openfinance-backup-auto-20260204-020000.ofbak",
     *     "fileSize": 2097152,
     *     "formattedFileSize": "2.00 MB",
     *     "checksum": "d4e5f6...",
     *     "status": "COMPLETED",
     *     "backupType": "AUTOMATIC",
     *     "description": "Automatic weekly backup",
     *     "createdAt": "2026-02-04T02:00:00"
     *   },
     *   {
     *     "id": 1,
     *     "filename": "openfinance-backup-20260203-143052.ofbak",
     *     "fileSize": 1048576,
     *     "formattedFileSize": "1.00 MB",
     *     "checksum": "a1b2c3...",
     *     "status": "COMPLETED",
     *     "backupType": "MANUAL",
     *     "description": "Manual backup before update",
     *     "createdAt": "2026-02-03T14:30:52"
     *   }
     * ]
     * </pre>
     *
     * @param authentication the authenticated user
     * @return list of backup responses
     */
    @GetMapping("/list")
    public ResponseEntity<List<BackupResponse>> listBackups(Authentication authentication) {
        log.debug("Listing backups");

        Long userId = ControllerUtil.extractUserId(authentication);
        List<Backup> backups = backupService.listBackups(userId);

        List<BackupResponse> responses =
                backups.stream().map(this::toResponse).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Gets details of a specific backup.
     *
     * @param backupId the backup ID
     * @param authentication the authenticated user
     * @return backup response
     */
    @GetMapping("/{id}")
    public ResponseEntity<BackupResponse> getBackup(
            @PathVariable("id") Long backupId, Authentication authentication) {

        log.debug("Getting backup ID: {}", backupId);

        Long userId = ControllerUtil.extractUserId(authentication);
        Backup backup = backupService.getBackup(userId, backupId);
        BackupResponse response = toResponse(backup);

        return ResponseEntity.ok(response);
    }

    /**
     * Downloads a backup file.
     *
     * <p><b>Response:</b> Binary file download (application/octet-stream)
     *
     * @param backupId the backup ID
     * @param authentication the authenticated user
     * @return backup file as downloadable resource
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadBackup(
            @PathVariable("id") Long backupId, Authentication authentication) {

        log.info("Downloading backup ID: {}", backupId);

        Long userId = ControllerUtil.extractUserId(authentication);
        Backup backup = backupService.getBackup(userId, backupId);
        InputStream inputStream = backupService.downloadBackup(userId, backupId);

        Resource resource = new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + backup.getFilename() + "\"")
                .body(resource);
    }

    /**
     * Deletes a backup.
     *
     * @param backupId the backup ID
     * @param authentication the authenticated user
     * @return success message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBackup(
            @PathVariable("id") Long backupId, Authentication authentication) {

        log.info("Deleting backup ID: {}", backupId);

        Long userId = ControllerUtil.extractUserId(authentication);
        backupService.deleteBackup(userId, backupId);

        return ResponseEntity.ok("Backup deleted successfully");
    }

    /**
     * Converts Backup entity to BackupResponse DTO.
     *
     * @param backup the backup entity
     * @return backup response
     */
    private BackupResponse toResponse(Backup backup) {
        return BackupResponse.builder()
                .id(backup.getId())
                .userId(backup.getUserId())
                .filename(backup.getFilename())
                .fileSize(backup.getFileSize())
                .formattedFileSize(backup.getFormattedFileSize())
                .checksum(backup.getChecksum())
                .status(backup.getStatus())
                .backupType(backup.getBackupType())
                .description(backup.getDescription())
                .errorMessage(backup.getErrorMessage())
                .createdAt(backup.getCreatedAt())
                .updatedAt(backup.getUpdatedAt())
                .build();
    }
}
