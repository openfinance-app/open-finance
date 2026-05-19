package org.openfinance.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for backup information.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupResponse {

    /** Unique identifier for the backup. */
    private Long id;

    /** ID of the user who owns this backup. */
    private Long userId;

    /** Original filename of the backup file. */
    private String filename;

    /** Size of the backup file in bytes. */
    private Long fileSize;

    /** Human-readable file size (e.g., "1.5 MB"). */
    private String formattedFileSize;

    /** SHA-256 checksum for integrity verification. */
    private String checksum;

    /** Status: PENDING, IN_PROGRESS, COMPLETED, FAILED. */
    private String status;

    /** Type: MANUAL or AUTOMATIC. */
    private String backupType;

    /** Description or notes about the backup. */
    private String description;

    /** Error message if backup failed. */
    private String errorMessage;

    /** Timestamp when the backup was created. */
    private LocalDateTime createdAt;

    /** Timestamp when the backup was last updated. */
    private LocalDateTime updatedAt;
}
