package org.openfinance.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Backup;
import org.openfinance.entity.User;
import org.openfinance.exception.BackupException;
import org.openfinance.repository.BackupRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Per-user portable SQLite backups; restores never replace the live database file. */
@Service
@Slf4j
@RequiredArgsConstructor
public class BackupService {
    private final BackupRepository backupRepository;
    private final UserRepository userRepository;
    private final UserBackupArchive archives;

    @Value("${spring.datasource.url:jdbc:sqlite:openfinance.db}")
    private String databaseUrl;

    @Value("${app.backup.directory:./backups}")
    private String backupDirectory;

    @Value("${app.backup.retention.count:7}")
    private int retentionCount;

    @Value("${app.backup.schedule.enabled:true}")
    private boolean scheduleEnabled;

    @Value("${app.backup.max-expanded-bytes:1073741824}")
    private long maxExpandedBytes;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Backup createBackup(Long userId, String description) {
        return create(userId, description, "MANUAL");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Backup createAutomaticBackup(Long userId) {
        Backup result = create(userId, "Automatic scheduled backup", "AUTOMATIC");
        List<Backup> backups =
                backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(userId, "AUTOMATIC");
        for (Backup backup : backups.stream().skip(Math.max(1, retentionCount)).toList())
            deleteBackup(userId, backup.getId());
        return result;
    }

    private Backup create(Long userId, String description, String type) {
        requireSQLite();
        String filename = "openfinance-backup-" + UUID.randomUUID() + ".ofbak";
        Path target = Path.of(backupDirectory, filename);
        Backup backup =
                backupRepository.save(
                        Backup.builder()
                                .userId(userId)
                                .filename(filename)
                                .filePath(target.toString())
                                .fileSize(0L)
                                .checksum("")
                                .status("IN_PROGRESS")
                                .backupType(type)
                                .description(description)
                                .createdAt(LocalDateTime.now())
                                .build());
        Path temporary = null;
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            temporary = Files.createTempFile("openfinance-user-backup-", ".db");
            archives.write(userId, temporary);
            try (InputStream input = Files.newInputStream(temporary);
                    OutputStream output = new GZIPOutputStream(Files.newOutputStream(target))) {
                input.transferTo(output);
            }
            backup.setFileSize(Files.size(target));
            backup.setChecksum(calculateChecksum(target));
            backup.setStatus("COMPLETED");
            backup.setUpdatedAt(LocalDateTime.now());
            return backupRepository.save(backup);
        } catch (Exception ex) {
            backup.setStatus("FAILED");
            backup.setErrorMessage("Could not create backup");
            backupRepository.save(backup);
            deleteTemporary(target);
            throw BackupException.internal("Could not create user backup", ex);
        } finally {
            deleteTemporary(temporary);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void restoreBackup(Long userId, Long backupId) {
        restoreBackup(userId, backupId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void restoreBackup(Long userId, Long backupId, String masterPassword) {
        Backup backup = getBackup(userId, backupId);
        if (!"COMPLETED".equals(backup.getStatus()))
            throw BackupException.validation("Cannot restore incomplete backup");
        Path path = Path.of(backup.getFilePath());
        if (!Files.isRegularFile(path)) throw BackupException.notFound("Backup file not found");
        try {
            if (!calculateChecksum(path).equals(backup.getChecksum()))
                throw BackupException.validation("Backup file corrupted (checksum mismatch)");
            requireSQLite();
            try (InputStream input = Files.newInputStream(path)) {
                restore(userId, input, masterPassword);
            }
        } catch (IOException ex) {
            throw BackupException.validation("Invalid backup file", ex);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void restoreBackupFromFile(Long userId, MultipartFile file) {
        restoreBackupFromFile(userId, file, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void restoreBackupFromFile(Long userId, MultipartFile file, String masterPassword) {
        if (file.isEmpty()) throw BackupException.validation("Uploaded file is empty");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".ofbak")) {
            throw BackupException.validation("Invalid backup file format. Expected .ofbak file");
        }
        requireSQLite();
        try (InputStream input = file.getInputStream()) {
            restore(userId, input, masterPassword);
        } catch (IOException ex) {
            throw BackupException.validation("Invalid backup file", ex);
        }
    }

    private void restore(Long userId, InputStream compressed, String masterPassword)
            throws IOException {
        Path temporary = Files.createTempFile("openfinance-user-restore-", ".db");
        try {
            expand(compressed, temporary);
            createBackup(userId, "Safety backup before restore");
            archives.restore(userId, temporary, masterPassword);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private void expand(InputStream compressed, Path temporary) throws IOException {
        try (InputStream input = new GZIPInputStream(compressed);
                OutputStream output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxExpandedBytes)
                    throw BackupException.validation(
                            "Expanded backup exceeds the configured size limit");
                output.write(buffer, 0, count);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Backup> listBackups(Long userId) {
        return backupRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Backup getBackup(Long userId, Long backupId) {
        return backupRepository
                .findByIdAndUserId(backupId, userId)
                .orElseThrow(() -> BackupException.notFound("Backup not found or access denied"));
    }

    @Transactional(readOnly = true)
    public InputStream downloadBackup(Long userId, Long backupId) {
        Backup backup = getBackup(userId, backupId);
        if (!"COMPLETED".equals(backup.getStatus()))
            throw BackupException.validation("Cannot download incomplete backup");
        Path temporary = null;
        try {
            Path source = Path.of(backup.getFilePath());
            if (!Files.isRegularFile(source))
                throw BackupException.notFound("Backup file not found");
            temporary = Files.createTempFile("openfinance-download-check-", ".db");
            try (InputStream input = Files.newInputStream(source)) {
                expand(input, temporary);
            }
            // Legacy whole-instance files may contain other users, even when their metadata is
            // owned.
            archives.validateDownload(userId, temporary);
            return Files.newInputStream(source);
        } catch (IOException ex) {
            throw BackupException.validation("Invalid portable user backup", ex);
        } finally {
            deleteTemporary(temporary);
        }
    }

    @Transactional
    public void deleteBackup(Long userId, Long backupId) {
        Backup backup = getBackup(userId, backupId);
        try {
            Files.deleteIfExists(Path.of(backup.getFilePath()));
        } catch (IOException ex) {
            throw BackupException.internal("Could not delete backup file", ex);
        }
        backupRepository.delete(backup);
    }

    /** Creates per-user backups daily at 02:00 server time unless the schedule is overridden. */
    @Scheduled(cron = "${app.backup.schedule.cron:0 0 2 * * *}")
    public void scheduledBackup() {
        if (!scheduleEnabled || !databaseUrl.startsWith("jdbc:sqlite:")) return;
        for (User user : userRepository.findAll()) {
            try {
                createAutomaticBackup(user.getId());
            } catch (RuntimeException ex) {
                log.error("Scheduled user backup failed for user {}", user.getId(), ex);
            }
        }
    }

    private void requireSQLite() {
        if (!databaseUrl.startsWith("jdbc:sqlite:"))
            throw BackupException.validation("User backups are available for SQLite instances");
    }

    private static String calculateChecksum(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void deleteTemporary(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not remove temporary backup file {}", path, ex);
        }
    }
}
