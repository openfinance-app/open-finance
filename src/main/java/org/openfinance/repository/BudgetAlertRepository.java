package org.openfinance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.openfinance.entity.BudgetAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing BudgetAlert entities.
 *
 * <p>Provides CRUD operations and custom queries for budget alert management, including finding
 * alerts by budget, checking enabled alerts, and managing read/unread status.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>Find alerts by budget ID
 *   <li>Find enabled alerts for monitoring
 *   <li>Count unread alerts for notification badge
 *   <li>Batch operations for alert resets
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.4: Budget alert system
 *   <li>REQ-2.9.4.1: Alert triggering and tracking
 *   <li>REQ-2.9.4.2: Enable/disable alerts per budget
 * </ul>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, UUID> {

    /**
     * Finds all alerts for a specific budget.
     *
     * <p>Returns all alerts (enabled and disabled) associated with the given budget. Useful for
     * displaying alert configuration in budget settings.
     *
     * @param budgetId the ID of the budget
     * @return list of alerts for the budget, empty list if none found
     */
    @Query("SELECT a FROM BudgetAlert a WHERE a.budget.id = :budgetId ORDER BY a.threshold ASC")
    List<BudgetAlert> findByBudgetId(@Param("budgetId") Long budgetId);

    /**
     * Finds all enabled alerts for a specific budget.
     *
     * <p>Used by alert checking logic to determine which thresholds to monitor. Only enabled alerts
     * will trigger notifications.
     *
     * @param budgetId the ID of the budget
     * @return list of enabled alerts for the budget, ordered by threshold
     */
    @Query(
            "SELECT a FROM BudgetAlert a WHERE a.budget.id = :budgetId AND a.isEnabled = true ORDER BY a.threshold ASC")
    List<BudgetAlert> findEnabledAlertsByBudgetId(@Param("budgetId") Long budgetId);

    /**
     * Finds all enabled alerts for budgets belonging to a specific user.
     *
     * <p>Used to check all user's budget alerts after a transaction is created. Allows system to
     * monitor all budgets in one query.
     *
     * @param userId the ID of the user
     * @return list of enabled alerts for all user's budgets
     */
    @Query(
            "SELECT a FROM BudgetAlert a WHERE a.budget.userId = :userId AND a.isEnabled = true ORDER BY a.budget.id, a.threshold ASC")
    List<BudgetAlert> findEnabledAlertsByUserId(@Param("userId") Long userId);

    /**
     * Finds a specific alert by budget ID and threshold.
     *
     * <p>Used to check if an alert configuration already exists before creating. Enforces
     * uniqueness constraint at application level.
     *
     * @param budgetId the ID of the budget
     * @param threshold the threshold percentage
     * @return optional containing the alert if found
     */
    @Query("SELECT a FROM BudgetAlert a WHERE a.budget.id = :budgetId AND a.threshold = :threshold")
    Optional<BudgetAlert> findByBudgetIdAndThreshold(
            @Param("budgetId") Long budgetId, @Param("threshold") java.math.BigDecimal threshold);

    /**
     * Counts unread alerts for a specific user.
     *
     * <p>Used to display notification badge count in UI. Only counts alerts that have been
     * triggered (lastTriggered is not null).
     *
     * @param userId the ID of the user
     * @return count of unread alerts
     */
    @Query(
            "SELECT COUNT(a) FROM BudgetAlert a WHERE a.budget.userId = :userId AND a.isRead = false AND a.lastTriggered IS NOT NULL")
    long countUnreadAlertsByUserId(@Param("userId") Long userId);

    /**
     * Finds all unread alerts for a specific user.
     *
     * <p>Returns alerts that have been triggered but not yet acknowledged. Ordered by most recently
     * triggered first.
     *
     * @param userId the ID of the user
     * @return list of unread alerts
     */
    @Query(
            "SELECT a FROM BudgetAlert a WHERE a.budget.userId = :userId AND a.isRead = false AND a.lastTriggered IS NOT NULL ORDER BY a.lastTriggered DESC")
    List<BudgetAlert> findUnreadAlertsByUserId(@Param("userId") Long userId);

    /**
     * Finds recently triggered alerts for a specific user within a time window.
     *
     * <p>Used to avoid duplicate alert triggering and to display recent alerts. Typically called
     * with a time window of last 24 hours.
     *
     * @param userId the ID of the user
     * @param since the starting timestamp
     * @return list of alerts triggered since the given time
     */
    @Query(
            "SELECT a FROM BudgetAlert a WHERE a.budget.userId = :userId AND a.lastTriggered >= :since ORDER BY a.lastTriggered DESC")
    List<BudgetAlert> findRecentlyTriggeredAlerts(
            @Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Marks all alerts as read for a specific user.
     *
     * <p>Used when user clicks "Mark all as read" in notifications.
     *
     * @param userId the ID of the user
     * @return number of alerts updated
     */
    @Modifying
    @Query(
            "UPDATE BudgetAlert a SET a.isRead = true WHERE a.budget.userId = :userId AND a.isRead = false")
    int markAllAlertsAsRead(@Param("userId") Long userId);

    /**
     * Resets all alerts for budgets starting a new period.
     *
     * <p>Clears lastTriggered timestamp and marks as read for fresh period tracking. Called when
     * budget period resets (e.g., start of new month).
     *
     * @param budgetIds list of budget IDs to reset
     * @return number of alerts reset
     */
    @Modifying
    @Query(
            "UPDATE BudgetAlert a SET a.lastTriggered = NULL, a.isRead = true WHERE a.budget.id IN :budgetIds")
    int resetAlertsForBudgets(@Param("budgetIds") List<Long> budgetIds);

    /**
     * Deletes all alerts for a specific budget.
     *
     * <p>Called when budget is deleted (cascade delete). Alternative to JPA cascade if manual
     * control is needed.
     *
     * @param budgetId the ID of the budget
     * @return number of alerts deleted
     */
    @Modifying
    @Query("DELETE FROM BudgetAlert a WHERE a.budget.id = :budgetId")
    int deleteByBudgetId(@Param("budgetId") Long budgetId);
}
