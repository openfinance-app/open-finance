package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleResponse;
import org.openfinance.entity.User;
import org.openfinance.service.TransactionRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing user-defined transaction rules.
 *
 * <p>Exposes CRUD and toggle endpoints under {@code /api/v1/transaction-rules}. All operations are
 * scoped to the authenticated user extracted from the {@link Authentication} principal.
 *
 * <p><strong>Requirement:</strong> REQ-TR-5
 */
@RestController
@RequestMapping("/api/v1/transaction-rules")
@RequiredArgsConstructor
@Slf4j
public class TransactionRuleController {

    private final TransactionRuleService transactionRuleService;

    /**
     * Returns all transaction rules belonging to the authenticated user.
     *
     * <p>GET /api/v1/transaction-rules
     *
     * @param authentication the Spring Security principal
     * @return list of rule response DTOs Requirement: REQ-TR-5.1
     */
    @GetMapping
    public ResponseEntity<List<TransactionRuleResponse>> listRules(Authentication authentication) {
        Long userId = extractUserId(authentication);
        log.debug("GET /api/v1/transaction-rules — userId={}", userId);
        List<TransactionRuleResponse> rules = transactionRuleService.getRulesForUser(userId);
        return ResponseEntity.ok(rules);
    }

    /**
     * Creates a new transaction rule for the authenticated user.
     *
     * <p>POST /api/v1/transaction-rules — returns 201 Created
     *
     * @param request validated request body
     * @param authentication the Spring Security principal
     * @return the created rule with HTTP 201 Requirement: REQ-TR-5.1
     */
    @PostMapping
    public ResponseEntity<TransactionRuleResponse> createRule(
            @Valid @RequestBody TransactionRuleRequest request, Authentication authentication) {
        Long userId = extractUserId(authentication);
        log.debug("POST /api/v1/transaction-rules — userId={}", userId);
        TransactionRuleResponse created = transactionRuleService.createRule(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns a single transaction rule by ID.
     *
     * <p>GET /api/v1/transaction-rules/{id}
     *
     * @param id the rule ID
     * @param authentication the Spring Security principal
     * @return the rule response DTO Requirement: REQ-TR-5.1
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionRuleResponse> getRule(
            @PathVariable Long id, Authentication authentication) {
        Long userId = extractUserId(authentication);
        log.debug("GET /api/v1/transaction-rules/{} — userId={}", id, userId);
        TransactionRuleResponse rule = transactionRuleService.getRule(id, userId);
        return ResponseEntity.ok(rule);
    }

    /**
     * Updates an existing transaction rule.
     *
     * <p>PUT /api/v1/transaction-rules/{id}
     *
     * @param id the rule ID to update
     * @param request validated update request body
     * @param authentication the Spring Security principal
     * @return the updated rule response DTO Requirement: REQ-TR-1.3, REQ-TR-5.1
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRuleRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        log.debug("PUT /api/v1/transaction-rules/{} — userId={}", id, userId);
        TransactionRuleResponse updated = transactionRuleService.updateRule(id, userId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a transaction rule.
     *
     * <p>DELETE /api/v1/transaction-rules/{id} — returns 204 No Content
     *
     * @param id the rule ID to delete
     * @param authentication the Spring Security principal Requirement: REQ-TR-1.4, REQ-TR-5.1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id, Authentication authentication) {
        Long userId = extractUserId(authentication);
        log.debug("DELETE /api/v1/transaction-rules/{} — userId={}", id, userId);
        transactionRuleService.deleteRule(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggles the enabled/disabled state of a transaction rule.
     *
     * <p>PATCH /api/v1/transaction-rules/{id}/toggle
     *
     * @param id the rule ID to toggle
     * @param authentication the Spring Security principal
     * @return the updated rule response DTO Requirement: REQ-TR-1.2
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<TransactionRuleResponse> toggleRule(
            @PathVariable Long id, Authentication authentication) {
        Long userId = extractUserId(authentication);
        log.debug("PATCH /api/v1/transaction-rules/{}/toggle — userId={}", id, userId);
        TransactionRuleResponse toggled = transactionRuleService.toggleRule(id, userId);
        return ResponseEntity.ok(toggled);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts the user ID from the Spring Security {@link Authentication} principal. Consistent
     * with existing controllers (e.g., {@link PayeeController}).
     *
     * @param authentication the current authentication
     * @return the authenticated user's ID, or {@code null} if anonymous
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}
