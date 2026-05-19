package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Entity representing a backup of user data. Stores metadata about backups including filename,
 * size, checksum, and status.
 *
 * <p><b>Requirements:</b> REQ-2.14.2.1, REQ-2.14.2.2
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@Entity
@Table(
        name = "backups",
        indexes = {
            @Index(name = "idx_backup_user_id", columnList = "user_id"),
            @Index(name = "idx_backup_created_at", columnList = "created_at"),
            @Index(name = "idx_backup_status", columnList = "status")
        })
@Getter
@Setter
@ToString(exclude = {"user"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Backup {

    /** Unique identifier for the backup. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** The user who owns this backup. */
    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Many-to-one relationship with User entity. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /** Original filename of the backup file. Format: openfinance-backup-YYYYMMDD-HHMMSS.ofbak */
    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename must not exceed 255 characters")
    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    /** Storage path on the server (relative to backup directory). */
    @NotBlank(message = "File path is required")
    @Size(max = 500, message = "File path must not exceed 500 characters")
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** Size of the backup file in bytes. */
    @NotNull(message = "File size is required")
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * SHA-256 checksum for file integrity verification. Empty string is allowed for IN_PROGRESS
     * status, populated when backup completes.
     */
    @NotNull(message = "Checksum cannot be null")
    @Size(max = 64, message = "Checksum must be 64 characters")
    @Pattern(
            regexp = "^$|^[a-f0-9]{64}$",
            message = "Checksum must be empty or a valid SHA-256 hash")
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    /** Status of the backup: PENDING, IN_PROGRESS, COMPLETED, FAILED. */
    @NotBlank(message = "Status is required")
    @Pattern(
            regexp = "^(PENDING|IN_PROGRESS|COMPLETED|FAILED)$",
            message = "Status must be PENDING, IN_PROGRESS, COMPLETED, or FAILED")
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** Type of backup: MANUAL or AUTOMATIC. */
    @NotBlank(message = "Backup type is required")
    @Pattern(regexp = "^(MANUAL|AUTOMATIC)$", message = "Backup type must be MANUAL or AUTOMATIC")
    @Column(name = "backup_type", nullable = false, length = 20)
    @Builder.Default
    private String backupType = "MANUAL";

    /** Description or notes about the backup (optional). */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description", length = 500)
    private String description;

    /** Error message if backup failed (optional). */
    @Size(max = 1000, message = "Error message must not exceed 1000 characters")
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** Timestamp when the backup was created. */
    @NotNull(message = "Created at timestamp is required")
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Timestamp when the backup was last updated. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Lifecycle callback to set updatedAt timestamp before updating. */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Returns a human-readable file size.
     *
     * @return formatted file size (e.g., "1.5 MB")
     */
    public String getFormattedFileSize() {
        if (fileSize == null) {
            return "Unknown";
        }
        if (fileSize < 1024) {
            return fileSize + " B";
        }
        if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        }
        if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Checks if the backup is completed successfully.
     *
     * @return true if status is COMPLETED, false otherwise
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    /**
     * Checks if the backup has failed.
     *
     * @return true if status is FAILED, false otherwise
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * Checks if the backup is in progress.
     *
     * @return true if status is IN_PROGRESS, false otherwise
     */
    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }

    /**
     * Checks if the backup was created automatically.
     *
     * @return true if backupType is AUTOMATIC, false otherwise
     */
    public boolean isAutomatic() {
        return "AUTOMATIC".equals(backupType);
    }
}
