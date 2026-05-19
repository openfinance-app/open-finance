package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for validating uploaded import files. Checks file size, type, content, and performs basic
 * security checks.
 *
 * <p>Requirement REQ-2.5.1.1: File Format Support - File validation
 *
 * <p>Validation checks:
 *
 * <ul>
 *   <li>File size within configured limits
 *   <li>File extension matches allowed types
 *   <li>File content matches detected format
 *   <li>Basic malicious content detection
 *   <li>File not empty
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@Slf4j
@Service
public class FileValidationService {

    private final List<String> allowedExtensions;
    private final long maxFileSize;
    private final ObjectMapper objectMapper;

    /**
     * Constructor to initialize file validation service.
     *
     * @param allowedExtensions Comma-separated list of allowed file extensions
     * @param maxFileSizeString Maximum file size (e.g., "10MB")
     */
    public FileValidationService(
            @Value("${application.import.allowed-extensions:.qif,.ofx,.qfx,.csv,.json}")
                    String allowedExtensions,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") String maxFileSizeString) {
        this.allowedExtensions = Arrays.asList(allowedExtensions.split(","));
        this.maxFileSize = parseFileSize(maxFileSizeString);
        this.objectMapper = new ObjectMapper();
        log.info(
                "Initialized file validation with allowed extensions: {} and max size: {} bytes",
                this.allowedExtensions,
                this.maxFileSize);
    }

    /**
     * Parses a file size string (e.g., "10MB") to bytes.
     *
     * @param sizeString Size string with unit
     * @return Size in bytes
     */
    private long parseFileSize(String sizeString) {
        sizeString = sizeString.trim().toUpperCase();
        if (sizeString.endsWith("KB")) {
            return Long.parseLong(sizeString.replace("KB", "").trim()) * 1024;
        } else if (sizeString.endsWith("MB")) {
            return Long.parseLong(sizeString.replace("MB", "").trim()) * 1024 * 1024;
        } else if (sizeString.endsWith("GB")) {
            return Long.parseLong(sizeString.replace("GB", "").trim()) * 1024 * 1024 * 1024;
        } else {
            return Long.parseLong(sizeString);
        }
    }

    /**
     * Validates an uploaded file. Performs all validation checks and returns a result.
     *
     * @param file The uploaded file to validate
     * @return ValidationResult containing success status and error message if any
     */
    public ValidationResult validate(MultipartFile file) {
        Objects.requireNonNull(file, "File cannot be null");

        // Check if file is empty
        if (file.isEmpty()) {
            return ValidationResult.error("File is empty");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            return ValidationResult.error(
                    String.format(
                            "File size (%d bytes) exceeds maximum allowed size (%d bytes)",
                            file.getSize(), maxFileSize));
        }

        // Check filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ValidationResult.error("File name is missing");
        }

        // Check for directory traversal attack
        if (originalFilename.contains("..")) {
            return ValidationResult.error("Invalid file name: directory traversal not allowed");
        }

        // Check file extension
        String extension = getFileExtension(originalFilename);
        if (extension == null || !allowedExtensions.contains(extension.toLowerCase())) {
            return ValidationResult.error(
                    String.format(
                            "File type '%s' not allowed. Allowed types: %s",
                            extension, String.join(", ", allowedExtensions)));
        }

        // Detect file format by content
        String detectedFormat;
        try {
            detectedFormat = detectFileFormat(file);
        } catch (IOException e) {
            log.error("Failed to read file for format detection", e);
            return ValidationResult.error("Failed to read file content");
        }

        // Verify detected format matches extension
        if (!detectedFormat.equalsIgnoreCase(extension.replace(".", ""))) {
            log.warn(
                    "File extension '{}' does not match detected format '{}'",
                    extension,
                    detectedFormat);
            // This is a warning, not a hard error - allow import to proceed
        }

        if ("json".equalsIgnoreCase(detectedFormat) && !isValidJson(file)) {
            return ValidationResult.error("File contains invalid JSON");
        }

        // Basic security check for malicious content
        if (containsMaliciousContent(file, detectedFormat)) {
            return ValidationResult.error("File contains potentially malicious content");
        }

        log.debug("File '{}' passed validation (format: {})", originalFilename, detectedFormat);
        return ValidationResult.success(detectedFormat);
    }

    /**
     * Extracts the file extension from a filename.
     *
     * @param filename The filename
     * @return File extension (including dot) or null if no extension
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex);
        }
        return null;
    }

    /**
     * Detects the file format by inspecting content.
     *
     * @param file The file to inspect
     * @return Detected format: "qif", "ofx", "qfx", "csv", or "json"
     * @throws IOException if file cannot be read
     */
    public String detectFileFormat(MultipartFile file) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            String firstLine = reader.readLine();
            if (firstLine == null) {
                return "unknown";
            }

            // Trim whitespace
            firstLine = firstLine.trim();

            // Check for QIF format (starts with !Type or !Account)
            if (firstLine.startsWith("!Type") || firstLine.startsWith("!Account")) {
                return "qif";
            }

            // Check for Skrooge JSON export format
            if (firstLine.startsWith("{") || firstLine.startsWith("[")) {
                StringBuilder sampleBuilder = new StringBuilder(firstLine);
                for (int i = 0; i < 4; i++) {
                    String nextLine = reader.readLine();
                    if (nextLine == null) {
                        break;
                    }
                    sampleBuilder.append(nextLine.trim());
                }

                String sample = sampleBuilder.toString();
                if (sample.contains("\"account\"")
                        || sample.contains("\"operation\"")
                        || sample.contains("\"suboperation\"")) {
                    return "json";
                }
            }

            // Check for OFX/QFX format (XML or SGML)
            if (firstLine.startsWith("<?xml")
                    || firstLine.contains("<OFX>")
                    || firstLine.startsWith("OFXHEADER")) {
                // QFX is essentially OFX format
                return "ofx";
            }

            // Check for CSV format (contains commas, no special markers)
            if (firstLine.contains(",")) {
                return "csv";
            }

            // Unknown format
            return "unknown";
        }
    }

    /**
     * Performs basic malicious content detection. Checks for common exploit patterns.
     *
     * @param file The file to check
     * @return true if potentially malicious content is detected
     */
    private boolean containsMaliciousContent(MultipartFile file, String detectedFormat) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            String line;
            int lineCount = 0;
            int maxLinesToCheck = 100; // Only check first 100 lines for performance

            while ((line = reader.readLine()) != null && lineCount < maxLinesToCheck) {
                lineCount++;
                String lowerLine = line.toLowerCase();

                if (lineCount == 1 && (line.startsWith("MZ") || line.startsWith("PK"))) {
                    log.warn("Detected executable file signature in file");
                    return true;
                }

                if ("json".equalsIgnoreCase(detectedFormat)) {
                    continue;
                }

                // Check for script tags
                if (lowerLine.contains("<script") || lowerLine.contains("javascript:")) {
                    log.warn("Detected script tag in file at line {}", lineCount);
                    return true;
                }

                // Check for SQL injection patterns
                if (lowerLine.matches(
                        ".*\\b(drop|delete|insert|update|exec|execute)\\s+(table|database).*")) {
                    log.warn("Detected SQL injection pattern in file at line {}", lineCount);
                    return true;
                }
            }

            return false;
        } catch (IOException e) {
            log.error("Error checking file content for malicious patterns", e);
            // On error, assume file is suspicious
            return true;
        }
    }

    private boolean isValidJson(MultipartFile file) {
        try {
            objectMapper.readTree(file.getInputStream());
            return true;
        } catch (JsonProcessingException e) {
            log.warn("Detected invalid JSON content", e);
            return false;
        } catch (IOException e) {
            log.error("Error validating JSON content", e);
            return false;
        }
    }

    /** Result of file validation. */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String detectedFormat;

        private ValidationResult(boolean valid, String errorMessage, String detectedFormat) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.detectedFormat = detectedFormat;
        }

        public static ValidationResult success(String detectedFormat) {
            return new ValidationResult(true, null, detectedFormat);
        }

        public static ValidationResult error(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getDetectedFormat() {
            return detectedFormat;
        }
    }
}
