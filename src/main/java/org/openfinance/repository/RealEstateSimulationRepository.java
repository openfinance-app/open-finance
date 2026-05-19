package org.openfinance.repository;

import java.util.List;
import java.util.Optional;
import org.openfinance.entity.RealEstateSimulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for RealEstateSimulation entity.
 *
 * <p>Provides CRUD operations and custom queries for simulation management.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface RealEstateSimulationRepository extends JpaRepository<RealEstateSimulation, Long> {

    /**
     * Find all simulations for a user.
     *
     * @param userId the user ID
     * @return list of simulations
     */
    List<RealEstateSimulation> findByUserId(Long userId);

    /**
     * Find simulations by user and type.
     *
     * @param userId the user ID
     * @param simulationType the simulation type
     * @return list of simulations
     */
    List<RealEstateSimulation> findByUserIdAndSimulationType(Long userId, String simulationType);

    /**
     * Find a simulation by ID and user ID (for authorization check).
     *
     * @param id the simulation ID
     * @param userId the user ID
     * @return optional simulation
     */
    Optional<RealEstateSimulation> findByIdAndUserId(Long id, Long userId);

    /**
     * Check if a simulation with given name exists for a user.
     *
     * @param userId the user ID
     * @param name the simulation name
     * @return true if exists
     */
    boolean existsByUserIdAndName(Long userId, String name);

    /**
     * Count simulations for a user.
     *
     * @param userId the user ID
     * @return count of simulations
     */
    long countByUserId(Long userId);

    /**
     * Delete all simulations for a user.
     *
     * @param userId the user ID
     */
    void deleteByUserId(Long userId);

    /**
     * Search simulations by name (case-insensitive, partial match).
     *
     * @param userId the user ID
     * @param name the name pattern
     * @return list of matching simulations
     */
    @Query(
            "SELECT s FROM RealEstateSimulation s WHERE s.userId = :userId AND LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<RealEstateSimulation> searchByName(
            @Param("userId") Long userId, @Param("name") String name);
}
