package org.openfinance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for RealEstateProperty entity operations.
 *
 * <p>Provides database access methods for real estate property management including filtering by
 * user, property type, mortgage, and location.
 *
 * <p>Requirement REQ-2.16: Real Estate Management - CRUD operations for real estate properties
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface RealEstateRepository
        extends JpaRepository<RealEstateProperty, Long>,
                JpaSpecificationExecutor<RealEstateProperty> {

    /**
     * Finds all properties belonging to a specific user.
     *
     * <p>Requirement REQ-2.16.1: Users can view all their properties
     *
     * @param userId ID of the user
     * @return List of properties owned by the user (may be empty)
     */
    @EntityGraph(attributePaths = {"mortgage"})
    List<RealEstateProperty> findByUserId(Long userId);

    /**
     * Finds all active properties belonging to a specific user. Excludes soft-deleted properties.
     *
     * <p>Used for displaying current property portfolio without historical records.
     *
     * <p>Requirement REQ-2.16.1: Users can view their active properties
     *
     * @param userId ID of the user
     * @param isActive Whether the property is active (true) or soft-deleted (false)
     * @return List of active properties owned by the user (may be empty)
     */
    @EntityGraph(attributePaths = {"mortgage"})
    List<RealEstateProperty> findByUserIdAndIsActive(Long userId, boolean isActive);

    /**
     * Finds all properties of a specific type for a user.
     *
     * <p>Useful for filtering portfolio by property type (e.g., show only RESIDENTIAL properties).
     *
     * <p>Requirement REQ-2.16: Property type categorization and filtering
     *
     * @param userId ID of the user
     * @param propertyType Property type to filter by
     * @return List of properties matching the type (may be empty)
     */
    @EntityGraph(attributePaths = {"mortgage"})
    List<RealEstateProperty> findByUserIdAndPropertyType(Long userId, PropertyType propertyType);

    /**
     * Finds all active properties of a specific type for a user. Excludes soft-deleted properties.
     *
     * <p>Useful for filtering portfolio by property type with active status.
     *
     * <p>Requirement REQ-2.16: Property type categorization and filtering
     *
     * @param userId ID of the user
     * @param propertyType Property type to filter by
     * @param isActive Whether the property is active (true) or soft-deleted (false)
     * @return List of active properties matching the type (may be empty)
     */
    @EntityGraph(attributePaths = {"mortgage"})
    List<RealEstateProperty> findByUserIdAndPropertyTypeAndIsActive(
            Long userId, PropertyType propertyType, boolean isActive);

    /**
     * Finds all properties linked to a specific mortgage.
     *
     * <p>Used to display properties associated with a particular loan.
     *
     * <p>Requirement REQ-2.16.7: Properties can be linked to mortgages
     *
     * @param mortgageId ID of the mortgage (Liability entity)
     * @return List of properties with the specified mortgage (may be empty)
     */
    @EntityGraph(attributePaths = {"mortgage"})
    List<RealEstateProperty> findByMortgageId(Long mortgageId);

    /**
     * Finds the first property linked to a specific mortgage.
     *
     * <p>Used to fetch the connected property for liability details.
     *
     * @param mortgageId ID of the mortgage (Liability entity)
     * @return Optional containing the property with the specified mortgage
     */
    Optional<RealEstateProperty> findFirstByMortgageId(Long mortgageId);

    /**
     * Finds a property by ID, ensuring it belongs to the specified user.
     *
     * <p>This method provides authorization check at the repository level, preventing users from
     * accessing properties they don't own.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own data
     *
     * @param id Property ID
     * @param userId User ID (for ownership verification)
     * @return Optional containing the property if found and owned by user, empty otherwise
     */
    @EntityGraph(attributePaths = {"mortgage"})
    Optional<RealEstateProperty> findByIdAndUserId(Long id, Long userId);

    /**
     * Finds a property by ID with mortgage eagerly loaded, ensuring it belongs to the specified
     * user.
     *
     * <p>Alias for findByIdAndUserId with EntityGraph. Both methods load the mortgage relationship.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own data
     *
     * @param id Property ID
     * @param userId User ID (for ownership verification)
     * @return Optional containing the property with mortgage if found and owned by user, empty
     *     otherwise
     */
    default Optional<RealEstateProperty> findByIdAndUserIdWithMortgage(Long id, Long userId) {
        return findByIdAndUserId(id, userId);
    }

    /**
     * Finds all rental properties for a user (properties with rental income > 0).
     *
     * <p>Used for investment property analysis and rental income tracking.
     *
     * <p>Requirement REQ-2.16.8: Track rental income for investment properties
     *
     * @param userId ID of the user
     * @return List of rental properties (may be empty)
     */
    @Query(
            "SELECT p FROM RealEstateProperty p WHERE p.userId = :userId AND p.rentalIncome IS NOT NULL")
    List<RealEstateProperty> findRentalPropertiesByUserId(@Param("userId") Long userId);

    /**
     * Finds all properties purchased within a date range.
     *
     * <p>Used for time-based portfolio analysis.
     *
     * @param userId ID of the user
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of properties purchased in the date range (may be empty)
     */
    @EntityGraph(attributePaths = {"mortgage"})
    List<RealEstateProperty> findByUserIdAndPurchaseDateBetween(
            Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * Counts the total number of properties for a user.
     *
     * <p>Useful for displaying summary statistics or enforcing property limits.
     *
     * @param userId User ID
     * @return Count of properties owned by the user
     */
    long countByUserId(Long userId);

    /**
     * Counts active properties for a user. Excludes soft-deleted properties.
     *
     * @param userId User ID
     * @param isActive Whether to count active (true) or inactive (false) properties
     * @return Count of properties matching the active status
     */
    long countByUserIdAndIsActive(Long userId, boolean isActive);

    /**
     * Counts properties of a specific type for a user.
     *
     * <p>Used for portfolio composition analysis.
     *
     * @param userId User ID
     * @param propertyType Property type
     * @return Count of properties of the specified type
     */
    long countByUserIdAndPropertyType(Long userId, PropertyType propertyType);

    /**
     * Finds all properties for a user ordered by current value descending.
     *
     * <p>Useful for displaying portfolio sorted by highest value properties first.
     *
     * <p><strong>Note:</strong> Since currentValue is encrypted, sorting at the database level is
     * not possible. This query returns properties ordered by creation date. The service layer
     * should sort by decrypted value after retrieval.
     *
     * @param userId User ID
     * @return List of properties ordered by creation date descending
     */
    @EntityGraph(attributePaths = {"mortgage"})
    @Query("SELECT p FROM RealEstateProperty p WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    List<RealEstateProperty> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Finds all properties for a user ordered by purchase date descending.
     *
     * <p>Useful for displaying properties by acquisition timeline.
     *
     * @param userId User ID
     * @return List of properties ordered by purchase date descending
     */
    @EntityGraph(attributePaths = {"mortgage"})
    List<RealEstateProperty> findByUserIdOrderByPurchaseDateDesc(Long userId);

    /**
     * Finds properties within a geographic bounding box.
     *
     * <p>Used for map-based property visualization and location-based filtering.
     *
     * <p>Requirement REQ-2.16.10: Geographic location tracking for properties
     *
     * @param userId User ID
     * @param minLat Minimum latitude (south bound)
     * @param maxLat Maximum latitude (north bound)
     * @param minLon Minimum longitude (west bound)
     * @param maxLon Maximum longitude (east bound)
     * @return List of properties within the geographic bounds (may be empty)
     */
    @Query(
            "SELECT p FROM RealEstateProperty p WHERE p.userId = :userId "
                    + "AND p.latitude BETWEEN :minLat AND :maxLat "
                    + "AND p.longitude BETWEEN :minLon AND :maxLon")
    List<RealEstateProperty> findByUserIdAndLocationBounds(
            @Param("userId") Long userId,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon);

    /**
     * Checks if a property exists by ID and user ID.
     *
     * <p>Used for quick ownership verification without fetching the full entity.
     *
     * @param id Property ID
     * @param userId User ID
     * @return true if property exists and belongs to user, false otherwise
     */
    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Deletes a property by ID and user ID.
     *
     * <p>This is a hard delete. For soft deletes, use the service layer method that sets {@code
     * isActive} to false.
     *
     * <p>Authorization check: Only deletes if the property belongs to the user.
     *
     * @param id Property ID
     * @param userId User ID
     * @return Number of properties deleted (0 or 1)
     */
    long deleteByIdAndUserId(Long id, Long userId);
}
