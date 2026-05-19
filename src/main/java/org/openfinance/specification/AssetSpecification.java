package org.openfinance.specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.openfinance.dto.AssetSearchCriteria;
import org.openfinance.entity.Asset;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification builder for dynamic asset queries.
 *
 * <p>This class creates type-safe JPA Criteria queries based on search criteria. It builds dynamic
 * WHERE clauses by combining multiple predicates with AND logic.
 *
 * <p><strong>Note on Encrypted Fields:</strong> The keyword search operates on encrypted name
 * field, so it can only match exact encrypted values. For proper keyword search, the service layer
 * should decrypt fields before comparison (not ideal for large datasets). This implementation
 * provides basic database-level filtering; full-text search would require decryption in application
 * layer or use of searchable encryption.
 *
 * @see AssetSearchCriteria
 * @see org.openfinance.entity.Asset
 */
public class AssetSpecification {

    /**
     * Builds a JPA Specification from search criteria.
     *
     * <p>Combines all non-null criteria with AND logic. If criteria has no filters, returns a
     * specification that matches all assets for the given user.
     *
     * <p><strong>Generated Predicates:</strong>
     *
     * <ul>
     *   <li>userId = ? (always included for security)
     *   <li>keyword - LIKE search on name (if provided)
     *   <li>type = ? (if provided)
     *   <li>accountId = ? (if provided)
     *   <li>currency = ? (if provided)
     *   <li>symbol - LIKE search (if provided)
     *   <li>purchaseDate >= ? (if purchaseDateFrom provided)
     *   <li>purchaseDate <= ? (if purchaseDateTo provided)
     *   <li>(quantity * currentPrice) >= ? (if valueMin provided)
     *   <li>(quantity * currentPrice) <= ? (if valueMax provided)
     * </ul>
     *
     * @param userId the user ID (required for security)
     * @param criteria the search criteria
     * @return JPA Specification for dynamic query building
     */
    public static Specification<Asset> buildSpecification(
            Long userId, AssetSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by userId (security)
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));

            // Keyword search on asset name
            // Note: This searches encrypted field, so exact match only
            // For production, consider decrypt-then-search in service layer
            if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
                String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
                predicates.add(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), keyword));
            }

            // Filter by asset type
            if (criteria.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), criteria.getType()));
            }

            // Filter by account ID
            if (criteria.getAccountId() != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("accountId"), criteria.getAccountId()));
            }

            // Filter by currency
            if (criteria.getCurrency() != null && !criteria.getCurrency().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("currency"), criteria.getCurrency()));
            }

            // Filter by symbol (contains search)
            if (criteria.getSymbol() != null && !criteria.getSymbol().trim().isEmpty()) {
                String symbol = "%" + criteria.getSymbol().toLowerCase() + "%";
                predicates.add(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("symbol")), symbol));
            }

            // Filter by purchase date range
            if (criteria.getPurchaseDateFrom() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("purchaseDate"), criteria.getPurchaseDateFrom()));
            }
            if (criteria.getPurchaseDateTo() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("purchaseDate"), criteria.getPurchaseDateTo()));
            }

            // Filter by total value (quantity * currentPrice)
            if (criteria.getValueMin() != null) {
                // (quantity * currentPrice) >= valueMin
                jakarta.persistence.criteria.Expression<java.math.BigDecimal> totalValue =
                        criteriaBuilder.prod(
                                root.<java.math.BigDecimal>get("quantity"),
                                root.<java.math.BigDecimal>get("currentPrice"));
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(totalValue, criteria.getValueMin()));
            }
            if (criteria.getValueMax() != null) {
                // (quantity * currentPrice) <= valueMax
                jakarta.persistence.criteria.Expression<java.math.BigDecimal> totalValue =
                        criteriaBuilder.prod(
                                root.<java.math.BigDecimal>get("quantity"),
                                root.<java.math.BigDecimal>get("currentPrice"));
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(totalValue, criteria.getValueMax()));
            }

            // Combine all predicates with AND logic
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
