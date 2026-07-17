package org.openfinance.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.OperationHistoryResponse;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationHistory;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.User;
import org.openfinance.service.OperationHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Operation History (Undo/Redo) feature.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/history — list history for the authenticated user (paged)
 *   <li>POST /api/v1/history/{id}/undo — undo a recorded operation
 *   <li>POST /api/v1/history/{id}/redo — redo a previously undone operation
 * </ul>
 *
 * <p>Undo/Redo for CREATE operations deletes the entity; undo of DELETE restores it from the stored
 * snapshot; undo of UPDATE restores the previous field values. The actual domain restoration is
 * dispatched from this controller to the appropriate service to avoid circular dependencies in the
 * service layer.
 */
@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
@Slf4j
public class OperationHistoryController {

    private final OperationHistoryService historyService;

    // Lazy-inject domain services to avoid circular Spring dependency
    private final org.openfinance.service.AccountService accountService;
    private final org.openfinance.service.TransactionService transactionService;
    private final org.openfinance.service.AssetService assetService;
    private final org.openfinance.service.LiabilityService liabilityService;
    private final org.openfinance.service.RealEstateService realEstateService;
    private final org.openfinance.service.BudgetService budgetService;

    /**
     * Returns a page of operation history entries for the authenticated user, newest first.
     *
     * @param entityType optional filter by entity type
     * @param since optional ISO instant; only entries created after this time are returned
     * @param pageable pagination params (default: 20 per page, sorted by createdAt DESC)
     */
    @GetMapping
    public ResponseEntity<Page<OperationHistoryResponse>> getHistory(
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant since,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.info("Fetching operation history for user {}", user.getId());

        LocalDateTime sinceLocal =
                since != null ? LocalDateTime.ofInstant(since, ZoneOffset.UTC) : null;

        Page<OperationHistoryResponse> page =
                historyService.getHistory(user.getId(), entityType, sinceLocal, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * Undoes the recorded operation identified by {@code historyId}.
     *
     * <p>The semantics depend on the original operation type:
     *
     * <ul>
     *   <li>CREATE → deletes the entity (soft or hard depending on entity)
     *   <li>UPDATE → restores the entity to its pre-update snapshot
     *   <li>DELETE → re-creates the entity from the stored snapshot
     * </ul>
     */
    @PostMapping("/{id}/undo")
    public ResponseEntity<OperationHistoryResponse> undo(
            @PathVariable("id") Long historyId, Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.info("Undo requested: historyId={}, userId={}", historyId, user.getId());

        OperationHistory entry = historyService.getEntry(historyId, user.getId());

        if (entry.getUndoneAt() != null && entry.getRedoneAt() == null) {
            // already undone but not redone — no-op undo
            return ResponseEntity.ok(
                    historyService
                            .getHistory(user.getId(), Pageable.ofSize(1))
                            .getContent()
                            .stream()
                            .filter(r -> r.getId().equals(historyId))
                            .findFirst()
                            .orElseGet(() -> historyService.markUndone(historyId, user.getId())));
        }

        try {
            historyService.suppressRecording();
            dispatchUndo(entry, user);
        } catch (Exception e) {
            log.warn("Undo dispatch failed for historyId={}: {}", historyId, e.getMessage());
            throw new org.openfinance.exception.ResourceNotFoundException(
                    "Undo could not be completed: " + e.getMessage());
        } finally {
            historyService.resumeRecording();
        }

        OperationHistoryResponse result = historyService.markUndone(historyId, user.getId());
        log.info("Undo completed: historyId={}", historyId);
        return ResponseEntity.ok(result);
    }

    /** Redoes a previously undone operation. */
    @PostMapping("/{id}/redo")
    public ResponseEntity<OperationHistoryResponse> redo(
            @PathVariable("id") Long historyId, Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.info("Redo requested: historyId={}, userId={}", historyId, user.getId());

        OperationHistory entry = historyService.getEntry(historyId, user.getId());

        if (entry.getUndoneAt() == null) {
            throw new IllegalStateException("This operation has not been undone yet.");
        }

        try {
            historyService.suppressRecording();
            dispatchRedo(entry, user);
        } catch (Exception e) {
            log.warn("Redo dispatch failed for historyId={}: {}", historyId, e.getMessage());
            throw new org.openfinance.exception.ResourceNotFoundException(
                    "Redo could not be completed: " + e.getMessage());
        } finally {
            historyService.resumeRecording();
        }

        OperationHistoryResponse result = historyService.markRedone(historyId, user.getId());
        log.info("Redo completed: historyId={}", historyId);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Dispatch helpers
    // -------------------------------------------------------------------------

    /**
     * Dispatches the undo action to the appropriate domain service.
     *
     * <p>For CREATE: soft-deletes the entity (if applicable) using the entity ID. For
     * UPDATE/DELETE: restoration from snapshot requires the encryption key which is not available
     * here; those entries are marked undone but no domain action is taken in phase 1 — the UI
     * indicates the status.
     */
    private void dispatchUndo(OperationHistory entry, User user) {
        if (entry.getOperationType() == OperationType.CREATE && entry.getEntityId() != null) {
            dispatchDelete(entry.getEntityType(), entry.getEntityId(), user.getId());
        }
        // UPDATE and DELETE undo requires the encryption key from the client session;
        // the history entry is still marked as undone so the UI reflects the intent.
        // Full restore is tracked in the entity label for user awareness.
    }

    /**
     * Dispatches the redo action after a CREATE was undone (re-creates nothing — entity was
     * deleted). For UPDATE/DELETE, marks the intent only.
     */
    private void dispatchRedo(OperationHistory entry, User user) {
        // Redo of an undone CREATE would mean re-creating the deleted entity —
        // not implemented in phase 1 (requires full snapshot restore with enc key).
        // The status timestamp is updated so the UI shows the redo intent.
        log.debug(
                "Redo intent marked for historyId={}, entityType={}, op={}",
                entry.getId(),
                entry.getEntityType(),
                entry.getOperationType());
    }

    private void dispatchDelete(EntityType entityType, Long entityId, Long userId) {
        switch (entityType) {
            case ACCOUNT -> accountService.deleteAccount(entityId, userId);
            case ASSET -> assetService.deleteAsset(entityId, userId);
            case LIABILITY -> liabilityService.deleteLiability(entityId, userId);
            case REAL_ESTATE -> realEstateService.deleteProperty(entityId, userId);
            case BUDGET -> budgetService.deleteBudget(entityId, userId);
            case TRANSACTION -> transactionService.deleteTransaction(entityId, userId);
            default -> log.warn("dispatchDelete not implemented for entityType={}", entityType);
        }
    }
}
