package org.openfinance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.openfinance.converter.EncryptedStringConverter;

/**
 * Entity representing a file attachment associated with various financial entities (transactions,
 * assets, real estate properties, or liabilities) in the Open-Finance system.
 *
 * <p>Attachments allow users to store supporting documents such as receipts, invoices, contracts,
 * images, or other relevant files alongside their financial data.
 *
 * <p>Requirement REQ-2.12: File Attachment System - Users can attach files to transactions, assets,
 * real estate properties, and liabilities for record-keeping and documentation purposes.
 *
 * <p><strong>Security Note:</strong> Files are stored encrypted at rest on the filesystem using the
 * AttachmentService. The {@code filePath} field contains the encrypted file's storage path, not the
 * original file content.
 *
 * @author Open-Finance Development Team
 * @since Sprint 12
 */
@Entity
@Table(
        name = "attachments",
        indexes = {
            @Index(name = "idx_attachment_user_id", columnList = "user_id"),
            @Index(name = "idx_attachment_entity", columnList = "entity_type, entity_id"),
            @Index(name = "idx_attachment_uploaded_at", columnList = "uploaded_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Attachment {

    /** Unique identifier for the attachment. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * The user who uploaded this attachment. Requirement REQ-2.12.1: Each attachment belongs to a
     * single user for security
     */
    @NotNull(message = "User ID cannot be null")
    @Column(name = "user_id", nullable = false)
    @ToString.Include
    private Long userId;

    /** Relationship to the User entity. Lazy-loaded to optimize performance. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    /**
     * Type of entity this attachment is associated with (TRANSACTION, ASSET, REAL_ESTATE,
     * LIABILITY). Requirement REQ-2.12.2: Attachments can be linked to various entity types
     */
    @NotNull(message = "Entity type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 25)
    @ToString.Include
    private EntityType entityType;

    /**
     * ID of the entity this attachment is associated with. This is a foreign key reference to the
     * appropriate table based on {@link #entityType}. For example, if entityType is TRANSACTION,
     * this refers to a transaction ID.
     *
     * <p>Requirement REQ-2.12.2: Attachments link to specific entities
     */
    @NotNull(message = "Entity ID cannot be null")
    @Positive(message = "Entity ID must be positive")
    @Column(name = "entity_id", nullable = false)
    @ToString.Include
    private Long entityId;

    /**
     * Original file name as uploaded by the user. Preserved for display purposes and user
     * reference.
     *
     * <p>Requirement REQ-2.12.3: Original filename preserved for user convenience
     */
    @NotNull(message = "File name cannot be null")
    @Size(min = 1, max = 255, message = "File name must be between 1 and 255 characters")
    @Column(name = "file_name", nullable = false, length = 512)
    @Convert(converter = EncryptedStringConverter.class)
    @ToString.Include
    private String fileName;

    /**
     * MIME type of the file (e.g., "application/pdf", "image/jpeg"). Used for content-type header
     * when serving the file for download.
     *
     * <p>Requirement REQ-2.12.4: Store file type for proper content handling
     */
    @NotNull(message = "File type cannot be null")
    @Size(min = 1, max = 100, message = "File type must be between 1 and 100 characters")
    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    /**
     * Size of the file in bytes. Used for storage management and validation.
     *
     * <p>Requirement REQ-2.12.5: Track file size (max 10MB per file)
     */
    @NotNull(message = "File size cannot be null")
    @Positive(message = "File size must be positive")
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * Path to the encrypted file in the filesystem. This path is relative to the configured
     * attachment storage directory. Files are organized by user ID and entity type to improve
     * security and organization.
     *
     * <p>Example format: "attachments/userId/entityType/generatedFileName.enc"
     *
     * <p><strong>Security Note:</strong> Files are encrypted at rest. This path points to the
     * encrypted version of the file, not the original unencrypted file. Requirement REQ-2.12.6:
     * Files stored encrypted at rest on filesystem
     */
    @NotNull(message = "File path cannot be null")
    @Size(min = 1, max = 500, message = "File path must be between 1 and 500 characters")
    @Column(name = "file_path", nullable = false, length = 500, unique = true)
    private String filePath;

    /**
     * Timestamp when the file was uploaded. Automatically set by Hibernate on entity creation.
     *
     * <p>Requirement REQ-2.12.7: Track upload timestamp for audit trail
     */
    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /**
     * Optional description or notes about this attachment. Allows users to add context about what
     * the file contains.
     */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description", length = 1000)
    @Convert(converter = EncryptedStringConverter.class)
    private String description;

    /**
     * Helper method to get a human-readable file size string.
     *
     * @return Formatted file size (e.g., "1.50 MB", "234.00 KB", "12 bytes")
     */
    public String getFormattedFileSize() {
        return org.openfinance.util.FileSizeFormatter.format(fileSize);
    }

    /**
     * Helper method to determine if this is an image file.
     *
     * @return true if the file is an image (jpg, jpeg, png, gif, webp), false otherwise
     */
    public boolean isImage() {
        if (fileType == null) {
            return false;
        }
        return fileType.startsWith("image/");
    }

    /**
     * Helper method to determine if this is a PDF file.
     *
     * @return true if the file is a PDF, false otherwise
     */
    public boolean isPdf() {
        return "application/pdf".equals(fileType);
    }

    /**
     * Helper method to determine if this is a document file (PDF, DOC, DOCX, XLS, XLSX).
     *
     * @return true if the file is a document, false otherwise
     */
    public boolean isDocument() {
        if (fileType == null) {
            return false;
        }
        return fileType.equals("application/pdf")
                || fileType.equals("application/msword")
                || fileType.equals(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || fileType.equals("application/vnd.ms-excel")
                || fileType.equals(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * Helper method to get a file extension from the file name.
     *
     * @return File extension (e.g., "pdf", "jpg") or empty string if no extension found
     */
    public String getFileExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
