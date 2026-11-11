package org.openfinance.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for handling temporary file storage operations. Manages file uploads, storage, retrieval,
 * and cleanup.
 *
 * <p>Requirement REQ-2.5.1.1: File Format Support - Temporary file storage
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Stores uploaded files temporarily with unique identifiers
 *   <li>Creates upload directory if it doesn't exist
 *   <li>Provides file retrieval by upload ID
 *   <li>Automatic cleanup of old files
 *   <li>Thread-safe file operations
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@Slf4j
@Service
public class FileStorageService {

    private final Path tempDirectory;
    private final int cleanupAfterHours;

    /**
     * Constructor to initialize file storage service.
     *
     * @param tempDirectoryPath Path to temporary storage directory
     * @param cleanupAfterHours Hours after which files are considered stale
     */
    public FileStorageService(
            @Value("${application.import.temp-directory:./temp/imports}") String tempDirectoryPath,
            @Value("${application.import.cleanup-after-hours:24}") int cleanupAfterHours) {
        this.tempDirectory = Paths.get(tempDirectoryPath);
        this.cleanupAfterHours = cleanupAfterHours;
        initializeDirectory();
    }

    /** Creates the temporary directory if it doesn't exist. */
    private void initializeDirectory() {
        try {
            if (!Files.exists(tempDirectory)) {
                Files.createDirectories(tempDirectory);
                log.info("Created temporary upload directory: {}", tempDirectory.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", tempDirectory.toAbsolutePath(), e);
            throw new IllegalStateException("Could not create upload directory", e);
        }
    }

    /**
     * Stores an uploaded file temporarily. Generates a unique ID and saves the file with a safe
     * name.
     *
     * @param file The uploaded file
     * @return Unique upload ID for later retrieval
     * @throws IOException if file storage fails
     */
    public String storeFile(MultipartFile file, Long userId) throws IOException {
        Objects.requireNonNull(file, "File cannot be null");

        String uploadId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();

        // Sanitize filename to prevent directory traversal attacks
        if (originalFilename == null
                || originalFilename.isBlank()
                || originalFilename.contains("..")
                || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        // Extract file extension
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFilename.substring(lastDotIndex);
        }

        // Create file path: uploadId + extension
        Path directory = ownerDirectory(userId);
        Files.createDirectories(directory);
        Path targetLocation = directory.resolve(uploadId + extension);

        // Copy file to target location
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, targetLocation);
        }

        log.debug("Stored file '{}' with upload ID: {}", originalFilename, uploadId);
        return uploadId;
    }

    /**
     * Retrieves a stored file by its upload ID.
     *
     * @param uploadId Unique upload identifier
     * @return Path to the stored file
     * @throws IOException if file cannot be found
     */
    private Path ownerDirectory(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("A valid upload owner is required");
        }
        return tempDirectory.resolve(userId.toString());
    }

    public Path getFile(String uploadId, Long userId) throws IOException {
        Objects.requireNonNull(uploadId, "Upload ID cannot be null");
        Path directory = ownerDirectory(userId);
        if (!uploadId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File not found for upload ID: " + uploadId);
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(
                            path -> {
                                Path fileName = path.getFileName();
                                if (fileName == null) return false;
                                String filename = fileName.toString();
                                int dot = filename.indexOf('.');
                                return (dot < 0 ? filename : filename.substring(0, dot))
                                        .equals(uploadId);
                            })
                    .findFirst()
                    .orElseThrow(
                            () -> new IOException("File not found for upload ID: " + uploadId));
        }
    }

    /**
     * Deletes a stored file by its upload ID.
     *
     * @param uploadId Unique upload identifier
     * @throws IOException if file cannot be deleted
     */
    public void deleteFile(String uploadId, Long userId) throws IOException {
        Path filePath = getFile(uploadId, userId);
        Files.deleteIfExists(filePath);
        log.debug("Deleted file for upload ID: {}", uploadId);
    }

    /**
     * Cleans up old files that exceed the configured retention period. This method should be called
     * periodically by a scheduled task.
     *
     * @return Number of files deleted
     */
    public int cleanupOldFiles() {
        log.info("Starting cleanup of old import files (older than {} hours)", cleanupAfterHours);

        LocalDateTime cutoffTime = LocalDateTime.now().minus(cleanupAfterHours, ChronoUnit.HOURS);
        int deletedCount = 0;

        try (Stream<Path> paths = Files.walk(tempDirectory)) {
            for (Path path :
                    paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                try {
                    LocalDateTime lastModified =
                            LocalDateTime.ofInstant(
                                    Files.getLastModifiedTime(path).toInstant(),
                                    java.time.ZoneId.systemDefault());

                    if (lastModified.isBefore(cutoffTime)) {
                        Files.deleteIfExists(path);
                        deletedCount++;
                        log.debug("Deleted old file: {}", path.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete file: {}", path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list files for cleanup", e);
        }

        log.info("Cleanup completed: deleted {} old files", deletedCount);
        return deletedCount;
    }

    /**
     * Gets the configured temporary directory path.
     *
     * @return Path to temporary directory
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Check if a file exists for the given upload ID.
     *
     * @param uploadId the upload ID (UUID)
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String uploadId, Long userId) {
        try {
            getFile(uploadId, userId);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the stored filename (including extension) for the given upload ID.
     *
     * <p>Files are saved on disk as {@code <uploadId><.ext>} (e.g. {@code
     * e7e2656a-ba09-4bb3-b5d8-6dcc1c0e3bb2.qif}). Returning only the bare UUID — which has no
     * dot-separated extension — would cause {@link ImportService#detectFileFormat} to treat the
     * UUID itself as the extension and throw an {@link IllegalArgumentException}.
     *
     * @param uploadId the upload ID (UUID)
     * @return the stored filename including its extension (e.g. {@code abc123.qif})
     * @throws IllegalArgumentException if no file is found for the given upload ID
     */
    public String getOriginalFileName(String uploadId, Long userId) {
        try {
            return getFile(uploadId, userId).getFileName().toString();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "No stored file found for upload ID: " + uploadId, e);
        }
    }

    /**
     * Get file content as InputStream.
     *
     * @param uploadId the upload ID (UUID)
     * @return InputStream of the file content
     * @throws IOException if file cannot be read
     */
    public java.io.InputStream getFileContent(String uploadId, Long userId) throws IOException {
        Objects.requireNonNull(uploadId, "Upload ID cannot be null");
        return Files.newInputStream(getFile(uploadId, userId));
    }
}
