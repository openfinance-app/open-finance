package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.converter.EncryptedStringConverter;

/** Encrypted operation journal. Legacy display snapshots are retained for audit only. */
@Entity
@Table(
        name = "operation_history",
        indexes = {
            @Index(name = "idx_op_history_user_id", columnList = "user_id"),
            @Index(name = "idx_op_history_created_at", columnList = "created_at DESC"),
            @Index(name = "idx_op_history_entity", columnList = "entity_type, entity_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the user who performed the operation. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The type of financial entity that was mutated. */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    /** The original primary-key ID, retained even when a supported action deletes the record. */
    @Column(name = "entity_id")
    private Long entityId;

    /** Human-readable label, encrypted at rest like the restoration payload. */
    @Column(name = "entity_label", length = 1000)
    @Convert(converter = EncryptedStringConverter.class)
    private String entityLabel;

    /** Whether this record represents a CREATE, UPDATE, or DELETE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 10)
    private OperationType operationType;

    /** Legacy display snapshot, retained for compatibility. New reversals use actionStateJson. */
    @Column(name = "entity_snapshot_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String entitySnapshotJson;

    /**
     * JSON map of {@code {field: {before, after}}} pairs describing which fields changed and what
     * their old/new values were. Used by the History view to display a human-readable diff.
     */
    @Column(name = "changed_fields_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String changedFieldsJson;

    /**
     * Timestamp when this operation was undone. {@code null} if the operation has not been undone.
     */
    @Column(name = "undone_at")
    private LocalDateTime undoneAt;

    /**
     * Timestamp when this operation was redone after having been undone. {@code null} if not
     * applicable.
     */
    @Column(name = "redone_at")
    private LocalDateTime redoneAt;

    /** When this history entry was created. Auto-set on persist. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Complete before/after row changes, encrypted as a single versioned payload. */
    @Column(name = "action_state_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String actionStateJson;

    @jakarta.persistence.Version
    @Column(name = "revision", nullable = false)
    @Builder.Default
    private Long revision = 0L;

    public boolean isUndone() {
        return undoneAt != null && redoneAt == null;
    }

    /** Availability also requires the service's live state and dependency checks. */
    public boolean canUndo() {
        return actionStateJson != null && !isUndone();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            // Intentionally UTC: the history API filters by a client-supplied ISO Instant
            // ("since"), which OperationHistoryController converts with
            // LocalDateTime.ofInstant(since, ZoneOffset.UTC). createdAt must be stored in the same
            // UTC wall-clock frame for that ">= since" comparison to be correct across timezones.
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
