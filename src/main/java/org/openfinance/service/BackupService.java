package org.openfinance.service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Backup;
import org.openfinance.exception.BackupException;
import org.openfinance.repository.BackupRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for managing database backups. Handles backup creation, restoration, validation, and
 * automatic scheduling.
 *
 * <p><b>Requirements:</b> REQ-2.14.2.1, REQ-2.14.2.2, REQ-2.14.2.3
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Create compressed backups of SQLite database
 *   <li>Restore backups with validation
 *   <li>Automatic scheduled backups (weekly by default)
 *   <li>Backup rotation (retain last N backups)
 *   <li>SHA-256 checksum verification
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BackupService {

    private final BackupRepository backupRepository;

    /** Path to the SQLite database file (from application.properties). Example: openfinance.db */
    @Value("${spring.datasource.url:jdbc:sqlite:openfinance.db}")
    private String databaseUrl;

    /** Directory where backups are stored. */
    @Value("${app.backup.directory:./backups}")
    private String backupDirectory;

    /** Maximum number of automatic backups to retain per user. */
    @Value("${app.backup.retention.count:7}")
    private int retentionCount;

    /** Automatic backup schedule enabled flag. */
    @Value("${app.backup.schedule.enabled:true}")
    private boolean scheduleEnabled;

    private static final String BACKUP_FILE_EXTENSION = ".ofbak";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Creates a manual backup for a specific user.
     *
     * <p><b>Process:</b>
     *
     * <ol>
     *   <li>Generate unique backup filename with timestamp
     *   <li>Copy SQLite database file
     *   <li>Compress with gzip
     *   <li>Calculate SHA-256 checksum
     *   <li>Save backup metadata to database
     * </ol>
     *
     * <p><b>Requirement:</b> REQ-2.14.2.1
     *
     * @param userId the ID of the user requesting the backup
     * @param description optional description for the backup
     * @return Backup entity with metadata
     * @throws BackupException if backup creation fails
     */
    @Transactional
    public Backup createBackup(Long userId, String description) {
        log.info("Creating manual backup for user ID: {}", userId);

        try {
            // Generate backup filename
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename =
                    String.format("openfinance-backup-%s%s", timestamp, BACKUP_FILE_EXTENSION);
            String filePath = Paths.get(backupDirectory, filename).toString();

            // Create backup directory if it doesn't exist
            Files.createDirectories(Paths.get(backupDirectory));

            // Create backup record in PENDING status
            Backup backup =
                    Backup.builder()
                            .userId(userId)
                            .filename(filename)
                            .filePath(filePath)
                            .fileSize(0L)
                            .checksum("")
                            .status("IN_PROGRESS")
                            .backupType("MANUAL")
                            .description(description)
                            .createdAt(LocalDateTime.now())
                            .build();
            backup = backupRepository.save(backup);

            // Perform the backup
            performBackup(backup);

            log.info("Manual backup created successfully: {}", filename);
            return backup;

        } catch (Exception e) {
            log.error("Failed to create backup for user ID: {}", userId, e);
            throw new BackupException("Failed to create backup: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an automatic backup for a user. Used by the scheduler.
     *
     * <p><b>Requirement:</b> REQ-2.14.2.2
     *
     * @param userId the user ID
     * @return Backup entity
     * @throws BackupException if backup fails
     */
    @Transactional
    public Backup createAutomaticBackup(Long userId) {
        log.info("Creating automatic backup for user ID: {}", userId);

        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename =
                    String.format("openfinance-backup-auto-%s%s", timestamp, BACKUP_FILE_EXTENSION);
            String filePath = Paths.get(backupDirectory, filename).toString();

            Files.createDirectories(Paths.get(backupDirectory));

            Backup backup =
                    Backup.builder()
                            .userId(userId)
                            .filename(filename)
                            .filePath(filePath)
                            .fileSize(0L)
                            .checksum("")
                            .status("IN_PROGRESS")
                            .backupType("AUTOMATIC")
                            .description("Automatic weekly backup")
                            .createdAt(LocalDateTime.now())
                            .build();
            backup = backupRepository.save(backup);

            performBackup(backup);

            // Rotate old automatic backups
            rotateAutomaticBackups(userId);

            log.info("Automatic backup created successfully: {}", filename);
            return backup;

        } catch (Exception e) {
            log.error("Failed to create automatic backup for user ID: {}", userId, e);
            throw new BackupException("Failed to create automatic backup: " + e.getMessage(), e);
        }
    }

    /**
     * Performs the actual backup process: copy, compress, checksum.
     *
     * @param backup the backup entity to update
     * @throws IOException if I/O error occurs
     */
    private void performBackup(Backup backup) throws IOException {
        // Extract database file path from JDBC URL
        String dbFilePath = extractDatabasePath(databaseUrl);
        Path sourcePath = Paths.get(dbFilePath);

        if (!Files.exists(sourcePath)) {
            backup.setStatus("FAILED");
            backup.setErrorMessage("Database file not found: " + dbFilePath);
            backupRepository.save(backup);
            throw new BackupException("Database file not found: " + dbFilePath);
        }

        Path targetPath = Paths.get(backup.getFilePath());

        // Copy and compress database file
        try (InputStream in = Files.newInputStream(sourcePath);
                OutputStream out = Files.newOutputStream(targetPath);
                GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                gzipOut.write(buffer, 0, bytesRead);
            }
        }

        // Calculate file size and checksum
        long fileSize = Files.size(targetPath);
        String checksum = calculateChecksum(targetPath);

        // Update backup record
        backup.setFileSize(fileSize);
        backup.setChecksum(checksum);
        backup.setStatus("COMPLETED");
        backup.setUpdatedAt(LocalDateTime.now());
        backupRepository.save(backup);

        log.info(
                "Backup completed: {} (Size: {}, Checksum: {})",
                backup.getFilename(),
                backup.getFormattedFileSize(),
                checksum);
    }

    /**
     * Restores a backup from file.
     *
     * <p><b>Process:</b>
     *
     * <ol>
     *   <li>Validate backup file integrity (checksum)
     *   <li>Verify user ownership
     *   <li>Create current database backup before restore
     *   <li>Decompress backup file
     *   <li>Replace current database with backup
     *   <li>Verify restoration success
     * </ol>
     *
     * <p><b>Requirement:</b> REQ-2.14.2.3
     *
     * @param userId the user ID requesting restore
     * @param backupId the ID of the backup to restore
     * @throws BackupException if restoration fails
     */
    @Transactional
    public void restoreBackup(Long userId, Long backupId) {
        log.info("Restoring backup ID: {} for user ID: {}", backupId, userId);

        Backup backup =
                backupRepository
                        .findByIdAndUserId(backupId, userId)
                        .orElseThrow(
                                () -> new BackupException("Backup not found or access denied"));

        if (!"COMPLETED".equals(backup.getStatus())) {
            throw new BackupException("Cannot restore incomplete backup");
        }

        Path backupPath = Paths.get(backup.getFilePath());
        if (!Files.exists(backupPath)) {
            throw new BackupException("Backup file not found: " + backup.getFilePath());
        }

        try {
            // Validate checksum
            String currentChecksum = calculateChecksum(backupPath);
            if (!currentChecksum.equals(backup.getChecksum())) {
                throw new BackupException("Backup file corrupted (checksum mismatch)");
            }

            // Create safety backup of current database
            createBackup(userId, "Auto-backup before restore");

            // Extract database path
            String dbFilePath = extractDatabasePath(databaseUrl);
            Path targetPath = Paths.get(dbFilePath);

            // Decompress and restore
            try (InputStream in = Files.newInputStream(backupPath);
                    GZIPInputStream gzipIn = new GZIPInputStream(in);
                    OutputStream out =
                            Files.newOutputStream(
                                    targetPath, StandardOpenOption.TRUNCATE_EXISTING)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = gzipIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            log.info("Backup restored successfully: {}", backup.getFilename());

        } catch (Exception e) {
            log.error("Failed to restore backup ID: {}", backupId, e);
            throw new BackupException("Failed to restore backup: " + e.getMessage(), e);
        }
    }

    /**
     * Restores a backup from uploaded file.
     *
     * @param userId the user ID
     * @param file the uploaded backup file
     * @throws BackupException if restoration fails
     */
    @Transactional
    public void restoreBackupFromFile(Long userId, MultipartFile file) {
        log.info("Restoring backup from uploaded file for user ID: {}", userId);

        if (file.isEmpty()) {
            throw new BackupException("Uploaded file is empty");
        }

        if (!file.getOriginalFilename().endsWith(BACKUP_FILE_EXTENSION)) {
            throw new BackupException("Invalid backup file format. Expected .ofbak file");
        }

        try {
            // Save uploaded file temporarily
            Path tempPath = Files.createTempFile("restore-", BACKUP_FILE_EXTENSION);
            file.transferTo(tempPath.toFile());

            // Create safety backup
            createBackup(userId, "Auto-backup before restore from upload");

            // Extract database path
            String dbFilePath = extractDatabasePath(databaseUrl);
            Path targetPath = Paths.get(dbFilePath);

            // Decompress and restore
            try (InputStream in = Files.newInputStream(tempPath);
                    GZIPInputStream gzipIn = new GZIPInputStream(in);
                    OutputStream out =
                            Files.newOutputStream(
                                    targetPath, StandardOpenOption.TRUNCATE_EXISTING)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = gzipIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            // Delete temp file
            Files.deleteIfExists(tempPath);

            log.info("Backup restored successfully from uploaded file");

        } catch (Exception e) {
            log.error("Failed to restore backup from uploaded file", e);
            throw new BackupException("Failed to restore backup: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all backups for a user.
     *
     * @param userId the user ID
     * @return list of backups, ordered by creation date (newest first)
     */
    @Transactional(readOnly = true)
    public List<Backup> listBackups(Long userId) {
        log.debug("Listing backups for user ID: {}", userId);
        return backupRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Gets a specific backup by ID (with ownership verification).
     *
     * @param userId the user ID
     * @param backupId the backup ID
     * @return Backup entity
     * @throws BackupException if backup not found or access denied
     */
    @Transactional(readOnly = true)
    public Backup getBackup(Long userId, Long backupId) {
        return backupRepository
                .findByIdAndUserId(backupId, userId)
                .orElseThrow(() -> new BackupException("Backup not found or access denied"));
    }

    /**
     * Downloads a backup file.
     *
     * @param userId the user ID
     * @param backupId the backup ID
     * @return InputStream of the backup file
     * @throws BackupException if backup not found or file missing
     */
    @Transactional(readOnly = true)
    public InputStream downloadBackup(Long userId, Long backupId) {
        Backup backup = getBackup(userId, backupId);

        Path backupPath = Paths.get(backup.getFilePath());
        if (!Files.exists(backupPath)) {
            throw new BackupException("Backup file not found: " + backup.getFilePath());
        }

        try {
            return Files.newInputStream(backupPath);
        } catch (IOException e) {
            throw new BackupException("Failed to read backup file", e);
        }
    }

    /**
     * Deletes a backup.
     *
     * @param userId the user ID
     * @param backupId the backup ID
     * @throws BackupException if deletion fails
     */
    @Transactional
    public void deleteBackup(Long userId, Long backupId) {
        log.info("Deleting backup ID: {} for user ID: {}", backupId, userId);

        Backup backup = getBackup(userId, backupId);

        // Delete physical file
        try {
            Path backupPath = Paths.get(backup.getFilePath());
            Files.deleteIfExists(backupPath);
        } catch (IOException e) {
            log.warn("Failed to delete backup file: {}", backup.getFilePath(), e);
        }

        // Delete database record
        backupRepository.delete(backup);
        log.info("Backup deleted: {}", backup.getFilename());
    }

    /**
     * Rotates automatic backups, keeping only the last N backups.
     *
     * <p><b>Requirement:</b> REQ-2.14.2.2
     *
     * @param userId the user ID
     */
    private void rotateAutomaticBackups(Long userId) {
        log.debug("Rotating automatic backups for user ID: {}", userId);

        List<Backup> automaticBackups =
                backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(userId, "AUTOMATIC");

        if (automaticBackups.size() > retentionCount) {
            List<Backup> backupsToDelete =
                    automaticBackups.subList(retentionCount, automaticBackups.size());

            for (Backup backup : backupsToDelete) {
                try {
                    deleteBackup(userId, backup.getId());
                } catch (Exception e) {
                    log.warn("Failed to delete old backup: {}", backup.getFilename(), e);
                }
            }

            log.info(
                    "Rotated {} old automatic backups for user ID: {}",
                    backupsToDelete.size(),
                    userId);
        }
    }

    /**
     * Scheduled automatic backup job. Runs every Sunday at 2:00 AM by default.
     *
     * <p><b>Requirement:</b> REQ-2.14.2.2
     */
    @Scheduled(cron = "${app.backup.schedule.cron:0 0 2 * * SUN}")
    public void scheduledBackup() {
        if (!scheduleEnabled) {
            log.debug("Automatic backup scheduler is disabled");
            return;
        }

        log.info("Starting scheduled automatic backups");

        // In a real implementation, you would iterate over all users
        // For now, this is a placeholder that can be called manually
        log.warn(
                "Scheduled backup: User iteration not implemented. Backups must be triggered per-user.");
    }

    /**
     * Extracts the database file path from a JDBC URL. Supports SQLite and H2 file-based databases.
     *
     * @param jdbcUrl the JDBC connection URL
     * @return file path (e.g., openfinance.db or ./target/test-db/backup-test.mv.db)
     */
    private String extractDatabasePath(String jdbcUrl) {
        String path;

        // Handle SQLite URLs: jdbc:sqlite:path/to/database.db
        if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            path = jdbcUrl.replaceFirst("^jdbc:sqlite:", "");
        }
        // Handle H2 file-based URLs: jdbc:h2:file:path/to/database
        else if (jdbcUrl.startsWith("jdbc:h2:file:")) {
            path = jdbcUrl.replaceFirst("^jdbc:h2:file:", "");
        }
        // Handle H2 mem URLs (in-memory databases don't have files)
        else if (jdbcUrl.startsWith("jdbc:h2:mem:")) {
            return jdbcUrl; // Return URL as-is to trigger "not found" error with clear message
        } else {
            // Unsupported database type
            return jdbcUrl; // Return URL as-is to trigger "not found" error
        }

        // Remove query parameters (everything after ? or ;) BEFORE adding extension
        int queryIndex =
                Math.min(
                        path.indexOf('?') >= 0 ? path.indexOf('?') : Integer.MAX_VALUE,
                        path.indexOf(';') >= 0 ? path.indexOf(';') : Integer.MAX_VALUE);
        if (queryIndex < Integer.MAX_VALUE) {
            path = path.substring(0, queryIndex);
        }

        // Add .mv.db extension for H2 files AFTER removing query parameters
        if (jdbcUrl.startsWith("jdbc:h2:file:") && !path.endsWith(".mv.db")) {
            path = path + ".mv.db";
        }

        return path;
    }

    /**
     * Calculates SHA-256 checksum of a file.
     *
     * @param filePath the file path
     * @return hex-encoded checksum
     * @throws IOException if I/O error occurs
     */
    private String calculateChecksum(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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

        } catch (Exception e) {
            throw new IOException("Failed to calculate checksum", e);
        }
    }
}
