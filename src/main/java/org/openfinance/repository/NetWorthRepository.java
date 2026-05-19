package org.openfinance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.NetWorth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for NetWorth entity operations.
 *
 * <p>Provides methods to:
 *
 * <ul>
 *   <li>Store daily net worth snapshots
 *   <li>Retrieve historical net worth data for trend analysis
 *   <li>Query snapshots by user and date range
 * </ul>
 *
 * <p>Requirements: REQ-2.5.1, REQ-2.5.2
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@Repository
public interface NetWorthRepository extends JpaRepository<NetWorth, Long> {

    /**
     * Finds all net worth snapshots for a user, ordered by date descending (newest first).
     *
     * @param userId the user ID
     * @return list of net worth snapshots ordered by date desc
     */
    List<NetWorth> findByUserIdOrderBySnapshotDateDesc(Long userId);

    /**
     * Finds all net worth snapshots for a user within a date range. Results are ordered by date
     * ascending (oldest first) for time-series analysis.
     *
     * @param userId the user ID
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of snapshots in date range
     */
    @Query(
            "SELECT nw FROM NetWorth nw WHERE nw.userId = :userId "
                    + "AND nw.snapshotDate >= :startDate AND nw.snapshotDate <= :endDate "
                    + "ORDER BY nw.snapshotDate ASC")
    List<NetWorth> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Finds the net worth snapshot for a specific user and date. Used to check if a snapshot
     * already exists before creating a new one.
     *
     * @param userId the user ID
     * @param snapshotDate the snapshot date
     * @return optional containing the snapshot if found
     */
    Optional<NetWorth> findByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);

    /**
     * Finds the most recent net worth snapshot for a user. Returns empty if no snapshots exist.
     *
     * @param userId the user ID
     * @return optional containing the latest snapshot if found
     */
    @Query(
            "SELECT nw FROM NetWorth nw WHERE nw.userId = :userId "
                    + "ORDER BY nw.snapshotDate DESC LIMIT 1")
    Optional<NetWorth> findLatestByUserId(@Param("userId") Long userId);

    /**
     * Finds the net worth snapshot from one month ago for a given user. Used to calculate monthly
     * change in net worth.
     *
     * @param userId the user ID
     * @param dateOneMonthAgo the date one month ago
     * @return optional containing the snapshot if found
     */
    @Query(
            "SELECT nw FROM NetWorth nw WHERE nw.userId = :userId "
                    + "AND nw.snapshotDate <= :dateOneMonthAgo "
                    + "ORDER BY nw.snapshotDate DESC LIMIT 1")
    Optional<NetWorth> findClosestToDate(
            @Param("userId") Long userId, @Param("dateOneMonthAgo") LocalDate dateOneMonthAgo);

    /**
     * Checks if a net worth snapshot exists for a specific user and date.
     *
     * @param userId the user ID
     * @param snapshotDate the snapshot date
     * @return true if snapshot exists, false otherwise
     */
    boolean existsByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);

    /**
     * Deletes all snapshots older than a specific date for a user. Used for data retention
     * management (e.g., keep only last 2 years).
     *
     * @param userId the user ID
     * @param cutoffDate the cutoff date (delete snapshots before this date)
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM NetWorth nw WHERE nw.userId = :userId AND nw.snapshotDate < :cutoffDate")
    int deleteByUserIdAndSnapshotDateBefore(
            @Param("userId") Long userId, @Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Deletes snapshots within a date range (inclusive) for a user. Used by the force-recalculate
     * path to wipe stale snapshots before rebuilding.
     *
     * @param userId the user ID
     * @param startDate range start (inclusive)
     * @param endDate range end (inclusive)
     * @return number of deleted records
     */
    @Modifying
    @Query(
            "DELETE FROM NetWorth nw WHERE nw.userId = :userId AND nw.snapshotDate >= :startDate AND nw.snapshotDate <= :endDate")
    int deleteByUserIdAndSnapshotDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Counts the number of snapshots for a user.
     *
     * @param userId the user ID
     * @return total number of snapshots
     */
    long countByUserId(Long userId);
}
