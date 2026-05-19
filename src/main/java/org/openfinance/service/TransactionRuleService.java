package org.openfinance.service;

import java.util.List;
import java.util.Set;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleResponse;

/**
 * Service interface for managing and evaluating transaction rules.
 *
 * <p>Defines the contract for CRUD operations on user-defined rules and for the rules engine that
 * runs during the import review phase.
 *
 * <p><strong>Requirements:</strong> REQ-TR-1 through REQ-TR-4
 */
public interface TransactionRuleService {

    /**
     * Returns all rules belonging to the given user, ordered by priority then creation date.
     *
     * @param userId the authenticated user's ID
     * @return list of all rules (enabled and disabled) Requirement: REQ-TR-5.1
     */
    List<TransactionRuleResponse> getRulesForUser(Long userId);

    /**
     * Returns a single rule by ID, enforcing user ownership.
     *
     * @param id the rule ID
     * @param userId the authenticated user's ID
     * @return the rule response DTO
     * @throws org.openfinance.exception.TransactionRuleNotFoundException if not found or not owned
     *     by user Requirement: REQ-TR-5.1, REQ-TR-NFR-3
     */
    TransactionRuleResponse getRule(Long id, Long userId);

    /**
     * Creates a new rule for the given user.
     *
     * @param userId the authenticated user's ID
     * @param request the creation request
     * @return the created rule response DTO Requirement: REQ-TR-1.1, REQ-TR-5.1
     */
    TransactionRuleResponse createRule(Long userId, TransactionRuleRequest request);

    /**
     * Updates an existing rule, replacing its conditions and actions entirely.
     *
     * @param id the rule ID to update
     * @param userId the authenticated user's ID (ownership check)
     * @param request the update request
     * @return the updated rule response DTO
     * @throws org.openfinance.exception.TransactionRuleNotFoundException if not found or not owned
     *     by user Requirement: REQ-TR-1.3, REQ-TR-5.1
     */
    TransactionRuleResponse updateRule(Long id, Long userId, TransactionRuleRequest request);

    /**
     * Deletes a rule.
     *
     * @param id the rule ID to delete
     * @param userId the authenticated user's ID (ownership check)
     * @throws org.openfinance.exception.TransactionRuleNotFoundException if not found or not owned
     *     by user Requirement: REQ-TR-1.4, REQ-TR-5.1
     */
    void deleteRule(Long id, Long userId);

    /**
     * Toggles the {@code isEnabled} flag of a rule.
     *
     * @param id the rule ID
     * @param userId the authenticated user's ID (ownership check)
     * @return the updated rule response DTO
     * @throws org.openfinance.exception.TransactionRuleNotFoundException if not found or not owned
     *     by user Requirement: REQ-TR-1.2
     */
    TransactionRuleResponse toggleRule(Long id, Long userId);

    /**
     * Applies all enabled rules to the given list of imported transactions.
     *
     * <p>Rules are evaluated in ascending priority order. For each transaction, the first matching
     * rule wins (stop-on-first-match). When a rule fires its actions are applied and the
     * transaction index is added to the returned set so that the caller can skip
     * auto-categorisation for those transactions.
     *
     * <p>A matched transaction receives an informational annotation prefixed with {@code
     * RULE_MATCH:} (non-blocking). A transaction matched by a {@code SKIP_TRANSACTION} action
     * receives a {@code RULE_SKIP:} annotation (blocking — treated as an error by {@link
     * ImportedTransaction#hasErrors()}).
     *
     * @param transactions the list of parsed imported transactions to evaluate
     * @param userId the authenticated user's ID
     * @return the set of transaction indices (0-based) that were matched by a rule Requirement:
     *     REQ-TR-4.1 through REQ-TR-4.7
     */
    Set<Integer> applyRules(List<ImportedTransaction> transactions, Long userId);
}
