package org.openfinance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Budget} entity operations.
 *
 * <p>Provides CRUD operations and custom queries for managing user budgets. Supports filtering by
 * period, category, and date ranges for budget tracking.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>User-scoped queries for data isolation
 *   <li>Period-based filtering (WEEKLY, MONTHLY, QUARTERLY, YEARLY)
 *   <li>Active budget queries based on date ranges
 *   <li>Category-based budget lookup
 * </ul>
 *
 * <p><strong>Requirement REQ-2.9.1.1</strong>: Budget creation and management
 *
 * <p><strong>Requirement REQ-2.9.1.2</strong>: Budget tracking by period and category
 *
 * @see Budget
 * @see BudgetPeriod
 * @since 1.0
 */
@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    /**
     * Finds all budgets for a specific user.
     *
     * <p>Returns all budgets regardless of period or active status. Results ordered by start date
     * descending (most recent first).
     *
     * <p>Requirement REQ-2.9.1.1: View all user budgets
     *
     * @param userId the user ID
     * @return list of user's budgets, empty list if none found
     */
    @Query(
            "SELECT b FROM Budget b JOIN FETCH b.category WHERE b.userId = :userId ORDER BY b.startDate DESC")
    List<Budget> findByUserId(@Param("userId") Long userId);

    /**
     * Finds a budget by ID and user ID.
     *
     * <p>Used for access control - ensures user can only access their own budgets.
     *
     * <p>Requirement REQ-3.2: User-specific data isolation
     *
     * @param id the budget ID
     * @param userId the user ID
     * @return Optional containing the budget if found and owned by user, empty otherwise
     */
    @Query("SELECT b FROM Budget b WHERE b.id = :id AND b.userId = :userId")
    Optional<Budget> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Finds all budgets for a user filtered by period type.
     *
     * <p>Filter budgets by WEEKLY, MONTHLY, QUARTERLY, or YEARLY period. Results ordered by start
     * date descending.
     *
     * <p>Requirement REQ-2.9.1.1: Period type filtering (monthly/annually/etc)
     *
     * @param userId the user ID
     * @param period the budget period type
     * @return list of budgets for specified period, empty list if none found
     */
    @Query(
            "SELECT b FROM Budget b WHERE b.userId = :userId AND b.period = :period ORDER BY b.startDate DESC")
    List<Budget> findByUserIdAndPeriod(
            @Param("userId") Long userId, @Param("period") BudgetPeriod period);

    /**
     * Finds all budgets for a specific category.
     *
     * <p>Returns budgets across all periods for the given category. Results ordered by start date
     * descending.
     *
     * <p>Requirement REQ-2.9.1.2: Category-based budget tracking
     *
     * @param categoryId the category ID
     * @return list of budgets for the category, empty list if none found
     */
    @Query("SELECT b FROM Budget b WHERE b.categoryId = :categoryId ORDER BY b.startDate DESC")
    List<Budget> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Finds all budgets for a user and specific category.
     *
     * <p>Used to view all historical and active budgets for a category. Results ordered by start
     * date descending.
     *
     * @param userId the user ID
     * @param categoryId the category ID
     * @return list of budgets for user and category, empty list if none found
     */
    @Query(
            "SELECT b FROM Budget b WHERE b.userId = :userId AND b.categoryId = :categoryId ORDER BY b.startDate DESC")
    List<Budget> findByUserIdAndCategoryId(
            @Param("userId") Long userId, @Param("categoryId") Long categoryId);

    /**
     * Finds active budgets for a user on a specific date.
     *
     * <p>A budget is active if the date falls within its start and end date range (inclusive).
     * Results ordered by category ID.
     *
     * <p>Requirement REQ-2.9.1.2: Budget tracking - identify active budgets
     *
     * @param userId the user ID
     * @param date the date to check (typically current date)
     * @return list of active budgets, empty list if none found
     */
    @Query(
            "SELECT b FROM Budget b WHERE b.userId = :userId "
                    + "AND b.startDate <= :date AND b.endDate >= :date "
                    + "ORDER BY b.categoryId ASC")
    List<Budget> findActiveByUserIdAndDate(
            @Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Finds active budgets for a user and period on a specific date.
     *
     * <p>Combines period filtering with active date range check. Useful for "show me all active
     * monthly budgets today".
     *
     * @param userId the user ID
     * @param period the budget period type
     * @param date the date to check
     * @return list of active budgets for period, empty list if none found
     */
    @Query(
            "SELECT b FROM Budget b WHERE b.userId = :userId "
                    + "AND b.period = :period "
                    + "AND b.startDate <= :date AND b.endDate >= :date "
                    + "ORDER BY b.categoryId ASC")
    List<Budget> findActiveByUserIdAndPeriodAndDate(
            @Param("userId") Long userId,
            @Param("period") BudgetPeriod period,
            @Param("date") LocalDate date);

    /**
     * Finds a budget by user, category, and period.
     *
     * <p>Used to check if a budget already exists before creating a new one. Typically only one
     * budget per category per period type should exist.
     *
     * <p>Requirement REQ-2.9.1.1: Prevent duplicate budgets for same category/period
     *
     * @param userId the user ID
     * @param categoryId the category ID
     * @param period the budget period type
     * @return Optional containing the budget if found, empty otherwise
     */
    @Query(
            "SELECT b FROM Budget b WHERE b.userId = :userId "
                    + "AND b.categoryId = :categoryId "
                    + "AND b.period = :period")
    Optional<Budget> findByUserIdAndCategoryIdAndPeriod(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("period") BudgetPeriod period);

    /**
     * Finds all budgets that have rollover enabled.
     *
     * <p>Used for batch processing rollover calculations when periods end. Results ordered by end
     * date ascending (oldest first).
     *
     * <p>Requirement REQ-2.9.1.1: Rollover option support
     *
     * @param userId the user ID
     * @return list of budgets with rollover enabled, empty list if none found
     */
    @Query(
            "SELECT b FROM Budget b WHERE b.userId = :userId AND b.rollover = true ORDER BY b.endDate ASC")
    List<Budget> findRolloverBudgetsByUserId(@Param("userId") Long userId);

    /**
     * Finds budgets that ended on or before a specific date.
     *
     * <p>Used for archiving expired budgets or triggering rollover processing. Results ordered by
     * end date descending.
     *
     * @param userId the user ID
     * @param date the cutoff date
     * @return list of expired budgets, empty list if none found
     */
    @Query(
            "SELECT b FROM Budget b WHERE b.userId = :userId AND b.endDate < :date ORDER BY b.endDate DESC")
    List<Budget> findExpiredBudgetsByUserId(
            @Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Counts the number of budgets for a user.
     *
     * <p>Used for statistics and dashboard display.
     *
     * @param userId the user ID
     * @return number of budgets
     */
    @Query("SELECT COUNT(b) FROM Budget b WHERE b.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    /**
     * Counts active budgets for a user on a specific date.
     *
     * <p>Used for displaying "You have X active budgets" summary.
     *
     * @param userId the user ID
     * @param date the date to check
     * @return number of active budgets
     */
    @Query(
            "SELECT COUNT(b) FROM Budget b WHERE b.userId = :userId "
                    + "AND b.startDate <= :date AND b.endDate >= :date")
    Long countActiveByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Counts budgets for a specific period type.
     *
     * <p>Used for statistics (e.g., "You have 5 monthly budgets").
     *
     * @param userId the user ID
     * @param period the budget period type
     * @return number of budgets for the period
     */
    @Query("SELECT COUNT(b) FROM Budget b WHERE b.userId = :userId AND b.period = :period")
    Long countByUserIdAndPeriod(@Param("userId") Long userId, @Param("period") BudgetPeriod period);

    /**
     * Checks if a budget exists for a user, category, and period.
     *
     * <p>Used to prevent creating duplicate budgets. Note: A user can have only one budget per
     * category per period type.
     *
     * @param userId the user ID
     * @param categoryId the category ID
     * @param period the budget period type
     * @return true if budget exists, false otherwise
     */
    @Query(
            "SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END "
                    + "FROM Budget b WHERE b.userId = :userId "
                    + "AND b.categoryId = :categoryId "
                    + "AND b.period = :period")
    boolean existsByUserIdAndCategoryIdAndPeriod(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("period") BudgetPeriod period);
}
