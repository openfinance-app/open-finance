package org.openfinance.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.OperationHistoryResponse;
import org.openfinance.entity.EntityType;
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
 * <p>Reversals restore complete recorded actions after ownership, age and dependency validation.
 */
@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
@Slf4j
public class OperationHistoryController {

    private final OperationHistoryService historyService;

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
            @RequestParam(required = false) OperationType operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant until,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.info("Fetching operation history for user {}", user.getId());

        LocalDateTime sinceLocal =
                since != null ? LocalDateTime.ofInstant(since, ZoneOffset.UTC) : null;

        Page<OperationHistoryResponse> page =
                historyService.searchHistory(
                        user.getId(),
                        entityType,
                        operationType,
                        sinceLocal,
                        until == null ? null : LocalDateTime.ofInstant(until, ZoneOffset.UTC),
                        pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/{id}/undo")
    public ResponseEntity<OperationHistoryResponse> undo(
            @PathVariable("id") Long historyId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(historyService.reverse(historyId, user.getId(), false));
    }

    @PostMapping("/{id}/redo")
    public ResponseEntity<OperationHistoryResponse> redo(
            @PathVariable("id") Long historyId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(historyService.reverse(historyId, user.getId(), true));
    }
}
