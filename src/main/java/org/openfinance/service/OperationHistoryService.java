package org.openfinance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.OperationHistoryResponse;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationHistory;
import org.openfinance.entity.OperationType;
import org.openfinance.exception.HistoryConflictException;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.OperationHistoryRepository;
import org.openfinance.service.history.HistoryActionContext;
import org.openfinance.service.history.HistoryChangeSet;
import org.openfinance.service.history.HistoryDomainRegistry;
import org.openfinance.service.history.HistoryPayloadCodec;
import org.openfinance.service.history.HistoryStateStore;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final HistoryStateStore states;
    private final HistoryPayloadCodec payloads;
    private final CacheManager cacheManager;

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
        recordingSuppressed.remove();
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
     * @param entityLabel human-readable label (encrypted when deployment encryption is enabled)
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

        if (HistoryActionContext.record(entityType, entityId, entityLabel)) return;
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

    @Transactional(readOnly = true)
    public Page<OperationHistoryResponse> getHistory(Long userId, Pageable pageable) {
        return decorate(
                historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable), userId);
    }

    @Transactional(readOnly = true)
    public Page<OperationHistoryResponse> getHistory(
            Long userId, EntityType entityType, LocalDateTime since, Pageable pageable) {
        Page<OperationHistory> page;
        if (entityType != null && since != null)
            page =
                    historyRepository
                            .findByUserIdAndEntityTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                                    userId, entityType, since, pageable);
        else if (entityType != null)
            page =
                    historyRepository.findByUserIdAndEntityTypeOrderByCreatedAtDesc(
                            userId, entityType, pageable);
        else if (since != null)
            page =
                    historyRepository.findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            userId, since, pageable);
        else page = historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return decorate(page, userId);
    }

    @Transactional(readOnly = true)
    public Page<OperationHistoryResponse> searchHistory(
            Long userId,
            EntityType entityType,
            OperationType operationType,
            LocalDateTime since,
            LocalDateTime until,
            Pageable pageable) {
        if (since != null && until != null && !until.isAfter(since))
            throw new IllegalArgumentException("End date must follow start date");
        org.springframework.data.jpa.domain.Specification<OperationHistory> filter =
                (root, query, builder) -> {
                    List<jakarta.persistence.criteria.Predicate> predicates =
                            new java.util.ArrayList<>();
                    predicates.add(builder.equal(root.get("userId"), userId));
                    if (entityType != null)
                        predicates.add(builder.equal(root.get("entityType"), entityType));
                    if (operationType != null)
                        predicates.add(builder.equal(root.get("operationType"), operationType));
                    if (since != null)
                        predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), since));
                    if (until != null)
                        predicates.add(builder.lessThan(root.get("createdAt"), until));
                    return builder.and(
                            predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        org.springframework.data.domain.Sort sort =
                pageable.getSort()
                        .and(
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "id"));
        return decorate(
                historyRepository.findAll(
                        filter,
                        org.springframework.data.domain.PageRequest.of(
                                pageable.getPageNumber(), pageable.getPageSize(), sort)),
                userId);
    }

    private Page<OperationHistoryResponse> decorate(Page<OperationHistory> page, Long userId) {
        Map<Long, HistoryChangeSet> actions = new LinkedHashMap<>();
        page.forEach(
                entry -> {
                    if (entry.getActionStateJson() != null)
                        actions.put(entry.getId(), payloads.read(entry.getActionStateJson()));
                });
        boolean needsState =
                page.stream()
                        .anyMatch(entry -> unavailable(entry, actions.get(entry.getId())) == null);
        HistoryStateStore.Snapshot current =
                needsState
                        ? states.snapshot(
                                userId,
                                actions.values().stream()
                                        .flatMap(state -> attachmentIds(state).stream())
                                        .collect(java.util.stream.Collectors.toSet()))
                        : null;
        long firstEligible =
                page.stream()
                        .filter(entry -> unavailable(entry, actions.get(entry.getId())) == null)
                        .mapToLong(OperationHistory::getId)
                        .min()
                        .orElse(Long.MAX_VALUE);
        LaterChanges later = laterChanges(userId, firstEligible, actions);
        return page.map(
                entry -> {
                    HistoryChangeSet action = actions.get(entry.getId());
                    String reason = unavailable(entry, action);
                    if (reason == null) reason = later.conflict(entry.getId(), action);
                    if (reason == null)
                        reason = states.unavailable(action, current, entry.isUndone(), userId);
                    return response(entry, action, reason);
                });
    }

    // -------------------------------------------------------------------------
    // Undo / Redo
    // -------------------------------------------------------------------------

    /** Returns an owned audit entry without changing either the journal or financial state. */
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

    private static Set<Long> attachmentIds(HistoryChangeSet state) {
        return state.changes().stream()
                .filter(change -> change.table().equals("attachments"))
                .map(HistoryChangeSet.Change::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String unavailable(OperationHistory entry, HistoryChangeSet state) {
        if (state == null || state.format() != HistoryChangeSet.FORMAT) return "legacy";
        if (entry.getCreatedAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).minusDays(30)))
            return "expired";
        return null;
    }

    private OperationHistoryResponse response(
            OperationHistory h, HistoryChangeSet state, String reason) {
        return OperationHistoryResponse.builder()
                .id(h.getId())
                .revision(h.getRevision())
                .canUndo(reason == null && !h.isUndone())
                .canRedo(reason == null && h.isUndone())
                .unavailableReason(reason)
                .affectedRecords(state == null ? null : state.changes().size())
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

    private record LaterChanges(
            Map<HistoryChangeSet.Key, Long> any, Map<HistoryChangeSet.Key, Long> substantive) {
        String conflict(long id, HistoryChangeSet state) {
            return state.changes().stream()
                            .anyMatch(
                                    change ->
                                            (change.balanceOnly() ? substantive : any)
                                                            .getOrDefault(change.key(), 0L)
                                                    > id)
                    ? "changed"
                    : null;
        }
    }

    private LaterChanges laterChanges(
            Long userId, long firstId, Map<Long, HistoryChangeSet> known) {
        Map<HistoryChangeSet.Key, Long> any = new LinkedHashMap<>();
        Map<HistoryChangeSet.Key, Long> substantive = new LinkedHashMap<>();
        if (firstId == Long.MAX_VALUE) return new LaterChanges(any, substantive);
        for (OperationHistory later :
                historyRepository
                        .findByUserIdAndIdGreaterThanAndActionStateJsonIsNotNullOrderByIdAsc(
                                userId, firstId)) {
            if (later.isUndone()) continue;
            HistoryChangeSet action =
                    known.computeIfAbsent(
                            later.getId(), ignored -> payloads.read(later.getActionStateJson()));
            for (HistoryChangeSet.Change change : action.changes()) {
                any.put(change.key(), later.getId());
                if (!change.balanceOnly()) substantive.put(change.key(), later.getId());
            }
        }
        return new LaterChanges(any, substantive);
    }

    private String laterConflict(OperationHistory entry, HistoryChangeSet state) {
        return laterChanges(entry.getUserId(), entry.getId(), new LinkedHashMap<>())
                .conflict(entry.getId(), state);
    }

    /**
     * Called inside the outer domain action's transaction, after all nested writes have flushed.
     */
    @Transactional
    public void recordAction(
            Long userId,
            EntityType type,
            OperationType operation,
            Long id,
            String label,
            HistoryChangeSet changes) {
        if (changes.changes().isEmpty()) return;
        String table = HistoryDomainRegistry.ENTITIES.get(type);
        Long requestedId = id;
        HistoryChangeSet.Change primary =
                changes.changes().stream()
                        .filter(
                                change ->
                                        change.table().equals(table)
                                                && (requestedId == null
                                                        || change.id() == requestedId))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        changes.changes().stream()
                                                .filter(change -> change.table().equals(table))
                                                .findFirst()
                                                .orElse(changes.changes().getFirst()));
        Map<String, String> primaryState =
                primary.after() == null ? primary.before() : primary.after();
        if (id == null)
            id =
                    primary.table().equals(table)
                            ? primary.id()
                            : type == EntityType.LIABILITY
                                            && primaryState.get("liability_id") != null
                                    ? Long.valueOf(primaryState.get("liability_id"))
                                    : primary.id();
        if (operation == OperationType.CREATE
                && primary.before() != null
                && type != EntityType.IMPORT) operation = OperationType.UPDATE;
        if (label == null && !primary.table().equals(table))
            label = states.label(table, id, userId);
        if (label == null)
            label =
                    primaryState.getOrDefault(
                            "name",
                            primaryState.getOrDefault(
                                    "description", primaryState.get("file_name")));
        // Async imports are claimed before their financial transaction starts. Undo returns to
        // review,
        // never to an IMPORTING state with no worker. Redo restores the captured completed result.
        if (type == EntityType.IMPORT) {
            changes.changes().stream()
                    .filter(
                            change ->
                                    change.table().equals("import_sessions")
                                            && change.before() != null)
                    .forEach(
                            change -> {
                                if ("IMPORTING".equals(change.before().get("status")))
                                    change.before().put("status", "REVIEWING");
                            });
        }
        OperationHistory entry =
                OperationHistory.builder()
                        .userId(userId)
                        .entityType(type)
                        .entityId(id)
                        .entityLabel(label)
                        .operationType(operation)
                        .actionStateJson(payloads.write(changes))
                        .changedFieldsJson(actionDiff(primary))
                        .build();
        historyRepository.save(entry);
    }

    private String actionDiff(HistoryChangeSet.Change change) {
        Set<String> visible =
                Set.of(
                        "name",
                        "description",
                        "notes",
                        "amount",
                        "balance",
                        "opening_balance",
                        "currency",
                        "type",
                        "account_type",
                        "transaction_type",
                        "asset_type",
                        "liability_type",
                        "property_type",
                        "transaction_date",
                        "is_active",
                        "active",
                        "is_enabled",
                        "is_reconciled",
                        "payee",
                        "frequency",
                        "next_occurrence",
                        "current_price",
                        "purchase_price",
                        "quantity",
                        "current_value",
                        "current_balance",
                        "principal",
                        "drawn_amount",
                        "principal_repaid",
                        "planned_amount",
                        "relationship",
                        "allocated_amount",
                        "interest_rate",
                        "start_date",
                        "end_date",
                        "status",
                        "priority");
        Map<String, Object[]> diff = new LinkedHashMap<>();
        Map<String, String> before = change.before() == null ? Map.of() : change.before();
        Map<String, String> after = change.after() == null ? Map.of() : change.after();
        for (String field : visible) {
            if (!java.util.Objects.equals(before.get(field), after.get(field)))
                diff.put(
                        field,
                        new Object[] {
                            displayValue(field, before.get(field)),
                            displayValue(field, after.get(field))
                        });
        }
        return diff.isEmpty() ? null : buildChangedFieldsJson(diff);
    }

    private Object displayValue(String field, String value) {
        if (value != null
                && Set.of("is_active", "active", "is_enabled", "is_reconciled").contains(field))
            return value.equals("1") || Boolean.parseBoolean(value);
        return value;
    }

    /** Revalidate and reverse the complete action, including its history transition, atomically. */
    @Transactional
    public OperationHistoryResponse reverse(Long historyId, Long userId, boolean redo) {
        states.lock(userId);
        OperationHistory entry = requireOwned(historyId, userId);
        HistoryChangeSet state =
                entry.getActionStateJson() == null
                        ? null
                        : payloads.read(entry.getActionStateJson());
        String reason = unavailable(entry, state);
        if (reason != null) throw new HistoryConflictException(reason);
        if (entry.isUndone() != redo) throw new HistoryConflictException("state");
        reason = laterConflict(entry, state);
        if (reason != null) throw new HistoryConflictException(reason);
        try {
            states.lockRows(state);
            states.restore(state, states.snapshot(userId, attachmentIds(state)), redo, userId);
            if (redo) entry.setRedoneAt(LocalDateTime.now(ZoneOffset.UTC));
            else {
                entry.setUndoneAt(LocalDateTime.now(ZoneOffset.UTC));
                entry.setRedoneAt(null);
            }
            entry = historyRepository.saveAndFlush(entry);
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            throw new HistoryConflictException("dependencies");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cacheManager
                                .getCacheNames()
                                .forEach(
                                        name -> {
                                            org.springframework.cache.Cache cache =
                                                    cacheManager.getCache(name);
                                            if (cache != null) cache.clear();
                                        });
                    }
                });
        return response(entry, state, null);
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
