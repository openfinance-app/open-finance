package org.openfinance.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;
import org.openfinance.util.ServerTimestampSerializer;

/**
 * Response DTO for a single operation history entry.
 *
 * <p>The {@code changedFieldsJson} is forwarded to the frontend as a raw JSON string so the UI can
 * render a human-readable diff without server-side formatting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationHistoryResponse {

    private boolean canUndo;
    private boolean canRedo;
    private Long id;
    private EntityType entityType;
    private Long entityId;
    private String entityLabel;
    private OperationType operationType;

    /**
     * Raw JSON string of the form {@code {"field":{"before":"v1","after":"v2"}, ...}}. May be
     * {@code null} for CREATE operations.
     */
    private String changedFieldsJson;

    /** When this operation was recorded (ISO-8601). */
    @JsonSerialize(using = ServerTimestampSerializer.Utc.class)
    private LocalDateTime createdAt;

    /** Non-null when the operation has been undone. */
    @JsonSerialize(using = ServerTimestampSerializer.Utc.class)
    private LocalDateTime undoneAt;

    /** Non-null when the operation has been (re)done after an undo. */
    @JsonSerialize(using = ServerTimestampSerializer.Utc.class)
    private LocalDateTime redoneAt;
}
