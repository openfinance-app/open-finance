package org.openfinance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Insight;
import org.openfinance.entity.InsightPriority;
import org.openfinance.entity.InsightType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Insight} entities.
 *
 * <p>Provides data access methods for AI-generated financial insights including querying by user,
 * type, priority, and dismissed status.
 *
 * <p><strong>Query Methods:</strong>
 *
 * <ul>
 *   <li>{@link #findByUser_IdOrderByPriorityAscCreatedAtDesc(Long)} - All insights for user sorted
 *       by priority
 *   <li>{@link #findByUser_IdAndDismissedFalse(Long)} - Active (non-dismissed) insights only
 *   <li>{@link #findByUser_IdAndType(Long, InsightType)} - Filter by insight type
 *   <li>{@link #findByUser_IdAndPriority(Long, InsightPriority)} - Filter by priority level
 * </ul>
 *
 * @since Sprint 11 - AI Assistant Integration (Task 11.4)
 */
@Repository
public interface InsightRepository extends JpaRepository<Insight, Long> {

    /**
     * Find all insights for a user, ordered by priority (HIGH first) and creation date (newest
     * first).
     *
     * <p>Includes both active and dismissed insights. Use {@link
     * #findByUser_IdAndDismissedFalse(Long)} to get only active insights.
     *
     * <p><strong>Sort order:</strong>
     *
     * <ol>
     *   <li>Priority: HIGH &rarr; MEDIUM &rarr; LOW (using enum ordinal)
     *   <li>Creation date: newest first
     * </ol>
     *
     * @param userId User ID
     * @return List of insights sorted by priority and date
     */
    @Query(
            "SELECT i FROM Insight i WHERE i.user.id = :userId ORDER BY i.priority ASC, i.createdAt DESC")
    List<Insight> findByUser_IdOrderByPriorityAscCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Find active (non-dismissed) insights for a user, ordered by priority and date.
     *
     * <p>This is the most common query for displaying insights to users.
     *
     * @param userId User ID
     * @return List of active insights sorted by priority (HIGH first) and date (newest first)
     */
    @Query(
            "SELECT i FROM Insight i WHERE i.user.id = :userId AND i.dismissed = false ORDER BY i.priority ASC, i.createdAt DESC")
    List<Insight> findByUser_IdAndDismissedFalse(@Param("userId") Long userId);

    /**
     * Find top N active insights for dashboard display.
     *
     * <p>Returns the most important insights (sorted by priority and recency) for quick view.
     *
     * @param userId User ID
     * @param limit Maximum number of insights to return
     * @return List of top N active insights
     */
    @Query(
            value =
                    "SELECT * FROM insights WHERE user_id = :userId AND dismissed = 0 "
                            + "ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END, "
                            + "created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Insight> findTopNByUser_IdAndDismissedFalse(
            @Param("userId") Long userId, @Param("limit") int limit);

    /**
     * Find insights by user and specific type.
     *
     * <p>Useful for filtering insights by category (e.g., show only budget warnings).
     *
     * @param userId User ID
     * @param type Insight type
     * @return List of insights matching the type
     */
    List<Insight> findByUser_IdAndType(Long userId, InsightType type);

    /**
     * Find insights by user and priority level.
     *
     * <p>Useful for showing only high-priority alerts.
     *
     * @param userId User ID
     * @param priority Priority level
     * @return List of insights matching the priority
     */
    List<Insight> findByUser_IdAndPriority(Long userId, InsightPriority priority);

    /**
     * Find an insight by ID and verify ownership.
     *
     * <p>Returns empty if insight doesn't exist or doesn't belong to the user.
     *
     * @param id Insight ID
     * @param userId User ID
     * @return Optional containing the insight if found and owned by user
     */
    Optional<Insight> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Count total insights for a user.
     *
     * @param userId User ID
     * @return Total number of insights
     */
    long countByUser_Id(Long userId);

    /**
     * Count active (non-dismissed) insights for a user.
     *
     * @param userId User ID
     * @return Number of active insights
     */
    long countByUser_IdAndDismissedFalse(Long userId);

    /**
     * Find insights created after a specific date.
     *
     * <p>Useful for "new insights since last login" functionality.
     *
     * @param userId User ID
     * @param since Only insights created after this timestamp
     * @return List of recent insights
     */
    List<Insight> findByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /**
     * Delete all insights for a user.
     *
     * <p>Used when regenerating insights or cleaning up old data.
     *
     * @param userId User ID
     * @return Number of deleted insights
     */
    long deleteByUser_Id(Long userId);

    /**
     * Delete insights older than a specific date.
     *
     * <p>Used for automatic cleanup of old dismissed insights.
     *
     * @param before Delete insights created before this timestamp
     * @return Number of deleted insights
     */
    long deleteByCreatedAtBefore(LocalDateTime before);
}
