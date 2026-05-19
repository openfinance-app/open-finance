package org.openfinance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.RealEstateValueHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link RealEstateValueHistory}.
 *
 * <p>Primarily used by the net-worth backfill algorithm to find the property value that was in
 * effect on a given historical date.
 */
@Repository
public interface RealEstateValueHistoryRepository
        extends JpaRepository<RealEstateValueHistory, Long> {

    /**
     * Returns the most recent value entry for a property whose effective date is on or before the
     * requested target date. Used during backfill to determine what the property was worth at that
     * point in time.
     */
    @Query(
            "SELECT h FROM RealEstateValueHistory h "
                    + "WHERE h.propertyId = :propertyId "
                    + "AND h.effectiveDate <= :targetDate "
                    + "ORDER BY h.effectiveDate DESC")
    List<RealEstateValueHistory> findHistoryUpToDate(
            @Param("propertyId") Long propertyId, @Param("targetDate") LocalDate targetDate);

    /** Convenience method: returns the single most-recent entry on or before targetDate. */
    default Optional<RealEstateValueHistory> findLatestOnOrBefore(
            Long propertyId, LocalDate targetDate) {
        List<RealEstateValueHistory> results = findHistoryUpToDate(propertyId, targetDate);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Loads all history entries for all properties belonging to a user (for backfill pre-loading).
     */
    List<RealEstateValueHistory> findByUserId(Long userId);
}
