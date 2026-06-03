package org.openfinance.specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.openfinance.dto.TransactionSearchCriteria;
import org.openfinance.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification builder for dynamic transaction queries.
 *
 * <p>This class creates type-safe JPA Criteria queries based on search criteria. It builds dynamic
 * WHERE clauses by combining multiple predicates with AND logic.
 *
 * <p><strong>Note on Encrypted Fields:</strong> The keyword search operates on encrypted
 * description/notes/payee fields, so it can only match exact encrypted values. For proper keyword
 * search, the service layer should decrypt fields before comparison (not ideal for large datasets).
 * This implementation provides basic database-level filtering; full-text search would require
 * decryption in application layer or use of searchable encryption.
 *
 * <p>Requirement REQ-2.3.5: Advanced transaction search with dynamic filters
 *
 * @see TransactionSearchCriteria
 * @see org.openfinance.entity.Transaction
 */
public class TransactionSpecification {

    /**
     * Builds a JPA Specification from search criteria.
     *
     * <p>Combines all non-null criteria with AND logic. If criteria has no filters, returns a
     * specification that matches all transactions for the given user.
     *
     * <p><strong>Generated Predicates:</strong>
     *
     * <ul>
     *   <li>userId = ? (always included for security)
     *   <li>isDeleted = false (always included, exclude soft-deleted)
     *   <li>keyword - LIKE search on description/notes/payee (if provided)
     *   <li>accountId = ? (if provided)
     *   <li>categoryId = ? (if provided)
     *   <li>type = ? (if provided)
     *   <li>date BETWEEN ? AND ? (if dateFrom/dateTo provided)
     *   <li>amount BETWEEN ? AND ? (if amountMin/amountMax provided)
     *   <li>tags LIKE ? (if provided)
     *   <li>isReconciled = ? (if provided)
     *   <li>categoryId IS NULL (if noCategory = true)
     *   <li>payee IS NULL or payee = '' (if noPayee = true)
     * </ul>
     *
     * @param userId the user ID (required for security)
     * @param criteria the search criteria
     * @return JPA Specification for dynamic query building
     */
    public static Specification<Transaction> buildSpecification(
            Long userId, TransactionSearchCriteria criteria, List<Long> matchedIds) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by userId (security)
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));

            // Always exclude soft-deleted transactions
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), false));

            // Limit to matchedIds if provided from FTS search
            if (matchedIds != null && !matchedIds.isEmpty()) {
                predicates.add(root.get("id").in(matchedIds));
            } else if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
                // Fallback: search in unencrypted fields if FTS was not used or failed
                String pattern = "%" + criteria.getKeyword().toLowerCase() + "%";
                predicates.add(
                        criteriaBuilder.or(
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(root.get("payee")), pattern),
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(root.get("tags")), pattern)));
            }

            // Filter by account
            if (criteria.getAccountId() != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("accountId"), criteria.getAccountId()));
            }

            // Filter by category
            if (criteria.getCategoryId() != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("categoryId"), criteria.getCategoryId()));
            }

            // Filter by transaction type
            if (criteria.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), criteria.getType()));
            }

            // Filter by date range
            if (criteria.getDateFrom() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("date"), criteria.getDateFrom()));
            }
            if (criteria.getDateTo() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(root.get("date"), criteria.getDateTo()));
            }

            // Amount range filtering is handled in-memory in TransactionService
            // because the amount field is encrypted and SQL comparisons on ciphertext
            // are meaningless. IDs are pre-filtered and passed via matchedTransactionIds.

            // Filter by payee name
            if (criteria.getPayee() != null && !criteria.getPayee().trim().isEmpty()) {
                String payee = "%" + criteria.getPayee().toLowerCase() + "%";
                predicates.add(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("payee")), payee));
            }

            // Filter by tags (contains search)
            if (criteria.getTags() != null && !criteria.getTags().trim().isEmpty()) {
                String tags = "%" + criteria.getTags().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("tags")), tags));
            }

            // Filter by reconciliation status
            if (criteria.getIsReconciled() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("isReconciled"), criteria.getIsReconciled()));
            }

            // Filter for uncategorized transactions (categoryId IS NULL)
            if (Boolean.TRUE.equals(criteria.getNoCategory())) {
                predicates.add(criteriaBuilder.isNull(root.get("categoryId")));
            }

            // Filter for transactions without a payee (payee IS NULL or empty string)
            if (Boolean.TRUE.equals(criteria.getNoPayee())) {
                predicates.add(
                        criteriaBuilder.or(
                                criteriaBuilder.isNull(root.get("payee")),
                                criteriaBuilder.equal(
                                        criteriaBuilder.trim(root.get("payee")), "")));
            }

            // Combine all predicates with AND logic
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
