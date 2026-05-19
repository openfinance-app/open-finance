package org.openfinance.repository;

import java.util.List;
import java.util.Optional;
import org.openfinance.entity.TransactionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link TransactionRule} entities.
 *
 * <p>All queries are user-scoped — every method accepts a {@code userId} parameter to ensure that a
 * user can only access their own rules.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-TR-4.4: Only enabled rules are evaluated during import
 *   <li>REQ-TR-1.5: Rules scoped to authenticated user
 *   <li>REQ-TR-NFR-3: User isolation enforced at repository level
 * </ul>
 */
@Repository
public interface TransactionRuleRepository extends JpaRepository<TransactionRule, Long> {

    /**
     * Returns all enabled rules for a user, ordered by ascending priority then creation date. This
     * is the primary query used during import rule evaluation.
     *
     * <p>Requirement: REQ-TR-4.4, REQ-TR-4.5 — only enabled rules in priority order.
     *
     * @param userId the ID of the user
     * @return ordered list of enabled rules
     */
    List<TransactionRule> findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(Long userId);

    /**
     * Returns all rules for a user (enabled and disabled), ordered by ascending priority then
     * creation date. Used for the management UI listing.
     *
     * <p>Requirement: REQ-TR-5.1 — list all rules for management.
     *
     * @param userId the ID of the user
     * @return ordered list of all rules
     */
    List<TransactionRule> findByUserIdOrderByPriorityAscCreatedAtAsc(Long userId);

    /**
     * Returns a single rule that belongs to the given user. Returns {@link
     * java.util.Optional#empty()} if the rule does not exist or belongs to a different user.
     *
     * <p>Requirement: REQ-TR-NFR-3 — strict user isolation on individual rule access.
     *
     * @param id the rule ID
     * @param userId the ID of the owning user
     * @return an {@link Optional} containing the rule, or empty
     */
    Optional<TransactionRule> findByIdAndUserId(Long id, Long userId);
}
