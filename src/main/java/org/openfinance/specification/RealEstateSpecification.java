package org.openfinance.specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.openfinance.dto.RealEstateSearchCriteria;
import org.openfinance.entity.RealEstateProperty;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification builder for dynamic real estate property queries.
 *
 * <p>This class creates type-safe JPA Criteria queries based on search criteria. It builds dynamic
 * WHERE clauses by combining multiple predicates with AND logic.
 *
 * <p><strong>Note on Encrypted Fields:</strong> The keyword search operates on encrypted name and
 * address fields, so it can only match exact encrypted values. For proper keyword search, the
 * service layer should decrypt fields before comparison (not ideal for large datasets). This
 * implementation provides basic database-level filtering; full-text search would require decryption
 * in application layer or use of searchable encryption.
 *
 * @see RealEstateSearchCriteria
 * @see org.openfinance.entity.RealEstateProperty
 */
public class RealEstateSpecification {

    /**
     * Builds a JPA Specification from search criteria.
     *
     * <p>Combines all non-null criteria with AND logic. If criteria has no filters, returns a
     * specification that matches all properties for the given user.
     *
     * <p><strong>Generated Predicates:</strong>
     *
     * <ul>
     *   <li>userId = ? (always included for security)
     *   <li>keyword - LIKE search on name OR address (if provided)
     *   <li>propertyType = ? (if provided)
     *   <li>currency = ? (if provided)
     *   <li>isActive = ? (if provided)
     *   <li>mortgageId IS NOT NULL / IS NULL (if hasMortgage provided)
     *   <li>purchaseDate >= ? (if purchaseDateFrom provided)
     *   <li>purchaseDate <= ? (if purchaseDateTo provided)
     *   <li>currentValue >= ? (if valueMin provided)
     *   <li>currentValue <= ? (if valueMax provided)
     *   <li>purchasePrice >= ? (if priceMin provided)
     *   <li>purchasePrice <= ? (if priceMax provided)
     *   <li>rentalIncome >= ? (if rentalIncomeMin provided)
     * </ul>
     *
     * @param userId the user ID (required for security)
     * @param criteria the search criteria
     * @return JPA Specification for dynamic query building
     */
    public static Specification<RealEstateProperty> buildSpecification(
            Long userId, RealEstateSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by userId (security)
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));

            // Keyword search on property name OR address
            // Note: This searches encrypted fields, so exact match only
            // For production, consider decrypt-then-search in service layer
            if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
                String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
                Predicate nameMatch =
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), keyword);
                Predicate addressMatch =
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("address")), keyword);
                predicates.add(criteriaBuilder.or(nameMatch, addressMatch));
            }

            // Filter by property type
            if (criteria.getPropertyType() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("propertyType"), criteria.getPropertyType()));
            }

            // Filter by currency
            if (criteria.getCurrency() != null && !criteria.getCurrency().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("currency"), criteria.getCurrency()));
            }

            // Filter by active status
            if (criteria.getIsActive() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isActive"), criteria.getIsActive()));
            }

            // Filter by mortgage presence
            if (criteria.getHasMortgage() != null) {
                if (criteria.getHasMortgage()) {
                    // Has mortgage: mortgageId IS NOT NULL
                    predicates.add(criteriaBuilder.isNotNull(root.get("mortgageId")));
                } else {
                    // No mortgage: mortgageId IS NULL
                    predicates.add(criteriaBuilder.isNull(root.get("mortgageId")));
                }
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

            // Filter by current value range
            if (criteria.getValueMin() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("currentValue"), criteria.getValueMin()));
            }
            if (criteria.getValueMax() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("currentValue"), criteria.getValueMax()));
            }

            // Filter by purchase price range
            if (criteria.getPriceMin() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("purchasePrice"), criteria.getPriceMin()));
            }
            if (criteria.getPriceMax() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("purchasePrice"), criteria.getPriceMax()));
            }

            // Filter by rental income
            if (criteria.getRentalIncomeMin() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("rentalIncome"), criteria.getRentalIncomeMin()));
            }

            // Combine all predicates with AND logic
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
