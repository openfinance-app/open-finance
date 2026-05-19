package org.openfinance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.OperationHistoryResponse;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationHistory;
import org.openfinance.entity.OperationType;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.OperationHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for recording and retrieving operation history (Undo/Redo).
 *
 * <p>Recording methods run within the caller's transaction (REQUIRED) to ensure atomicity with
 * domain operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationHistoryService {

    private final OperationHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    // Use ThreadLocal to suppress recording during internal operations like Undo/Redo
    private static final ThreadLocal<Boolean> recordingSuppressed =
            ThreadLocal.withInitial(() -> false);

    /**
     * Suppresses history recording for the current thread. Useful during Undo/Redo operations to
     * avoid redundant entries.
     */
    public void suppressRecording() {
        recordingSuppressed.set(true);
    }

    /** Resumes history recording for the current thread. */
    public void resumeRecording() {
        recordingSuppressed.set(false);
    }

    /** Checks if history recording is suppressed for the current thread. */
    public boolean isRecordingSuppressed() {
        return Boolean.TRUE.equals(recordingSuppressed.get());
    }

    /**
     * Records a mutation in the operation history.
     *
     * @param userId the authenticated user's ID
     * @param entityType the type of entity that changed
     * @param entityId the ID of the entity (may be null after hard delete)
     * @param entityLabel human-readable label (stored plain so the UI can show it without requiring
     *     decryption)
     * @param operationType CREATE, UPDATE, or DELETE
     * @param entitySnapshotJson full JSON snapshot of the entity <em>before</em> the change; pass
     *     {@code null} for CREATE operations
     * @param changedFieldsJson JSON map of {@code {field:{before,after}}} for UPDATE display
     */
    @Transactional
    public void record(
            Long userId,
            EntityType entityType,
            Long entityId,
            String entityLabel,
            OperationType operationType,
            String entitySnapshotJson,
            String changedFieldsJson) {

        if (isRecordingSuppressed()) {
            log.debug(
                    "History recording suppressed: skipping {} on {}/{}",
                    operationType,
                    entityType,
                    entityId);
            return;
        }

        OperationHistory entry =
                OperationHistory.builder()
                        .userId(userId)
                        .entityType(entityType)
                        .entityId(entityId)
                        .entityLabel(entityLabel)
                        .operationType(operationType)
                        .entitySnapshotJson(entitySnapshotJson)
                        .changedFieldsJson(changedFieldsJson)
                        .build();

        historyRepository.save(entry);
        log.debug(
                "Operation history recorded: userId={}, entity={}/{}, op={}",
                userId,
                entityType,
                entityId,
                operationType);
    }

    /**
     * Convenience overload that serialises a response object to JSON for the snapshot.
     *
     * @param snapshotObject the Java object to serialise; may be {@code null}
     */
    @Transactional
    public void record(
            Long userId,
            EntityType entityType,
            Long entityId,
            String entityLabel,
            OperationType operationType,
            Object snapshotObject,
            Map<String, Object[]> changedFields) {

        String snapshotJson = toJson(snapshotObject);
        String changedJson =
                changedFields != null && !changedFields.isEmpty()
                        ? buildChangedFieldsJson(changedFields)
                        : null;

        record(userId, entityType, entityId, entityLabel, operationType, snapshotJson, changedJson);
    }

    // -------------------------------------------------------------------------
    // Querying
    // -------------------------------------------------------------------------

    /** Returns a page of operation history entries for the given user, ordered newest-first. */
    @Transactional(readOnly = true)
    public Page<OperationHistoryResponse> getHistory(Long userId, Pageable pageable) {
        return historyRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /**
     * Returns a page of operation history entries for the given user, optionally filtered by entity
     * type and/or a lower bound on createdAt, ordered newest-first.
     *
     * <p>Handles all four filter combinations:
     *
     * <ul>
     *   <li>neither → all entries for user
     *   <li>entityType only → entries of that type
     *   <li>since only → entries at or after since
     *   <li>entityType + since → entries of that type at or after since
     * </ul>
     */
    @Transactional(readOnly = true)
    public Page<OperationHistoryResponse> getHistory(
            Long userId, EntityType entityType, LocalDateTime since, Pageable pageable) {

        if (entityType != null && since != null) {
            return historyRepository
                    .findByUserIdAndEntityTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            userId, entityType, since, pageable)
                    .map(this::toResponse);
        } else if (entityType != null) {
            return historyRepository
                    .findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, entityType, pageable)
                    .map(this::toResponse);
        } else if (since != null) {
            return historyRepository
                    .findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            userId, since, pageable)
                    .map(this::toResponse);
        } else {
            return getHistory(userId, pageable);
        }
    }

    // -------------------------------------------------------------------------
    // Undo / Redo
    // -------------------------------------------------------------------------

    /**
     * Marks a history entry as undone. Returns the updated entry.
     *
     * <p>The actual entity restoration is handled by the caller (controller) which delegates to the
     * appropriate domain service. This method only updates the {@code undone_at} timestamp.
     *
     * @throws ResourceNotFoundException if the entry does not exist or belongs to another user
     */
    @Transactional
    public OperationHistoryResponse markUndone(Long historyId, Long userId) {
        OperationHistory entry = requireOwned(historyId, userId);
        entry.setUndoneAt(LocalDateTime.now());
        entry.setRedoneAt(null);
        return toResponse(historyRepository.save(entry));
    }

    /**
     * Marks a history entry as redone after an undo.
     *
     * @throws ResourceNotFoundException if the entry does not exist or belongs to another user
     */
    @Transactional
    public OperationHistoryResponse markRedone(Long historyId, Long userId) {
        OperationHistory entry = requireOwned(historyId, userId);
        entry.setRedoneAt(LocalDateTime.now());
        return toResponse(historyRepository.save(entry));
    }

    /**
     * Returns the raw {@link OperationHistory} entry for use by the controller (e.g., to read the
     * snapshot JSON and dispatch the actual domain operation).
     */
    @Transactional(readOnly = true)
    public OperationHistory getEntry(Long historyId, Long userId) {
        return requireOwned(historyId, userId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private OperationHistory requireOwned(Long historyId, Long userId) {
        OperationHistory entry =
                historyRepository
                        .findById(historyId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Operation history entry not found: " + historyId));
        if (!entry.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Operation history entry not found: " + historyId);
        }
        return entry;
    }

    private OperationHistoryResponse toResponse(OperationHistory h) {
        return OperationHistoryResponse.builder()
                .id(h.getId())
                .entityType(h.getEntityType())
                .entityId(h.getEntityId())
                .entityLabel(h.getEntityLabel())
                .operationType(h.getOperationType())
                .changedFieldsJson(h.getChangedFieldsJson())
                .createdAt(h.getCreatedAt())
                .undoneAt(h.getUndoneAt())
                .redoneAt(h.getRedoneAt())
                .build();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize snapshot to JSON", e);
            return null;
        }
    }

    /**
     * Builds a JSON string from a map of {@code {fieldName -> [before, after]}} entries. Output
     * format: {@code {"fieldName":{"before":"v1","after":"v2"}, ...}}
     */
    private String buildChangedFieldsJson(Map<String, Object[]> changedFields) {
        try {
            Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
            changedFields.forEach(
                    (field, values) -> {
                        Map<String, Object> diff = new java.util.LinkedHashMap<>();
                        diff.put("before", values.length > 0 ? values[0] : null);
                        diff.put("after", values.length > 1 ? values[1] : null);
                        out.put(field, diff);
                    });
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("Failed to build changedFieldsJson", e);
            return null;
        }
    }
}
