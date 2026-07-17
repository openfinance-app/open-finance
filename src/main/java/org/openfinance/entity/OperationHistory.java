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

/**
 * Persistent record of a single Create / Update / Delete mutation on a financial entity.
 *
 * <p>Stored in the {@code operation_history} table. Used to drive the Undo/Redo feature: the {@code
 * entitySnapshotJson} field holds a full JSON snapshot of the entity <em>before</em> the change so
 * that any operation can be undone without loss of data.
 *
 * <p>Write operations are performed outside any active transaction (propagation NOT_SUPPORTED) to
 * avoid SQLite WAL BUSY_SNAPSHOT conflicts — identical to the pattern used in {@code
 * SecurityAuditService}.
 */
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

    /** The primary-key ID of the entity that was mutated (null if the entity was deleted). */
    @Column(name = "entity_id")
    private Long entityId;

    /**
     * Human-readable label for the entity (e.g., account name, transaction description). Stored
     * plain-text at record time so the History view can display it without requiring decryption.
     */
    @Column(name = "entity_label", length = 1000)
    @Convert(converter = EncryptedStringConverter.class)
    private String entityLabel;

    /** Whether this record represents a CREATE, UPDATE, or DELETE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 10)
    private OperationType operationType;

    /**
     * Full JSON snapshot of the entity <strong>before</strong> the change. Used to restore the
     * entity when this operation is undone. {@code null} for CREATE operations (no prior state).
     */
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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
