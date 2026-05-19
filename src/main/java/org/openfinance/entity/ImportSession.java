package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing an import session for transaction files (QIF, OFX, QFX).
 *
 * <p>An import session tracks the progress of importing transactions from an uploaded file. The
 * session moves through states: PENDING → PARSING → PARSED → REVIEWING → CONFIRMED/CANCELLED.
 *
 * @see org.openfinance.service.ImportService
 * @see org.openfinance.controller.ImportController
 */
@Entity
@Table(
        name = "import_sessions",
        indexes = {
            @Index(name = "idx_import_session_user", columnList = "user_id"),
            @Index(name = "idx_import_session_upload", columnList = "upload_id"),
            @Index(name = "idx_import_session_status", columnList = "status"),
            @Index(name = "idx_import_session_created", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the uploaded file (UUID from FileStorageService). */
    @NotBlank
    @Column(name = "upload_id", nullable = false, length = 36)
    private String uploadId;

    /** User who initiated the import. */
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Original filename of the uploaded file. */
    @NotBlank
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** File format detected (QIF, OFX, QFX, CSV). */
    @Column(name = "file_format", length = 10)
    private String fileFormat;

    /**
     * Target account ID for the imported transactions. Can be null if account selection happens
     * during review.
     */
    @Column(name = "account_id")
    private Long accountId;

    /**
     * Account name detected from the imported file. Used as a suggestion if accountId is not set.
     */
    @Column(name = "suggested_account_name")
    private String suggestedAccountName;

    /** Current status of the import session. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportStatus status = ImportStatus.PENDING;

    /** Total number of transactions parsed from the file. */
    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Integer totalTransactions = 0;

    /** Number of transactions successfully imported. */
    @Column(name = "imported_count", nullable = false)
    @Builder.Default
    private Integer importedCount = 0;

    /** Number of transactions with errors. */
    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Integer errorCount = 0;

    /** Number of transactions flagged as duplicates. */
    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private Integer duplicateCount = 0;

    /** Number of transactions skipped by user. */
    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private Integer skippedCount = 0;

    /** Error message if parsing or import failed. */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** Additional metadata (JSON format). Can store category mappings, user preferences, etc. */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** Timestamp when the session was created. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when the session was last updated. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Timestamp when the session was completed (confirmed or cancelled). */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Enum defining the possible states of an import session. */
    public enum ImportStatus {
        /** Import session created, waiting to start parsing. */
        PENDING,

        /** File is being parsed to extract transactions. */
        PARSING,

        /** File parsing complete, transactions extracted. Waiting for user review. */
        PARSED,

        /** User is reviewing transactions (mapping categories, handling duplicates). */
        REVIEWING,

        /** User confirmed import, transactions are being saved to database. */
        IMPORTING,

        /** Import completed successfully. */
        COMPLETED,

        /** Import cancelled by user. */
        CANCELLED,

        /** Import failed due to error. */
        FAILED
    }

    /** Check if the session is in a terminal state (completed, cancelled, or failed). */
    public boolean isTerminal() {
        return status == ImportStatus.COMPLETED
                || status == ImportStatus.CANCELLED
                || status == ImportStatus.FAILED;
    }

    /** Check if the session can be cancelled. */
    public boolean isCancellable() {
        return status == ImportStatus.PENDING
                || status == ImportStatus.PARSING
                || status == ImportStatus.PARSED
                || status == ImportStatus.REVIEWING;
    }

    /** Check if the session is ready for review. */
    public boolean isReadyForReview() {
        return status == ImportStatus.PARSED || status == ImportStatus.REVIEWING;
    }

    /** Check if the session can be confirmed (imported). */
    public boolean isConfirmable() {
        return status == ImportStatus.PARSED || status == ImportStatus.REVIEWING;
    }
}
