package org.openfinance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link RecurringTransaction} entity operations.
 *
 * <p>Provides CRUD operations and custom queries for managing recurring financial transactions.
 * These templates are used to automatically generate actual transactions at regular intervals.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>User-scoped queries for data isolation
 *   <li>Filtering by active status and due date
 *   <li>Account and frequency filtering
 *   <li>Efficient queries for scheduled job processing
 * </ul>
 *
 * <p><strong>Requirement REQ-2.3.6</strong>: Recurring transaction data access and persistence
 *
 * @see RecurringTransaction
 * @since 1.0
 */
@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    /**
     * Finds all recurring transactions for a specific user.
     *
     * <p>Includes both active and inactive recurring transactions. Results ordered by next
     * occurrence date ascending.
     *
     * @param userId the user ID
     * @return list of user's recurring transactions, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.userId = :userId ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findByUserId(@Param("userId") Long userId);

    /**
     * Finds all active recurring transactions for a specific user.
     *
     * <p>Only returns recurring transactions where isActive = true. Results ordered by next
     * occurrence date ascending.
     *
     * @param userId the user ID
     * @return list of active recurring transactions, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.userId = :userId AND r.isActive = true ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findByUserIdAndIsActive(@Param("userId") Long userId);

    /**
     * Finds a recurring transaction by ID and user ID (for authorization).
     *
     * <p>Ensures users can only access their own recurring transactions.
     *
     * @param id the recurring transaction ID
     * @param userId the user ID
     * @return Optional containing the recurring transaction if found and owned by user
     */
    @Query("SELECT r FROM RecurringTransaction r WHERE r.id = :id AND r.userId = :userId")
    Optional<RecurringTransaction> findByIdAndUserId(
            @Param("id") Long id, @Param("userId") Long userId);

    /**
     * Finds all recurring transactions for a specific account.
     *
     * <p>Includes transactions where the account is either the source (accountId) or destination
     * (toAccountId) for transfers.
     *
     * <p>Results ordered by next occurrence date ascending.
     *
     * @param accountId the account ID
     * @return list of recurring transactions, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.accountId = :accountId OR r.toAccountId = :accountId ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findByAccountId(@Param("accountId") Long accountId);

    /**
     * Finds all recurring transactions for a specific user and account.
     *
     * <p>Combines user ownership check with account filtering. Results ordered by next occurrence
     * date ascending.
     *
     * @param userId the user ID
     * @param accountId the account ID
     * @return list of recurring transactions, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.userId = :userId AND (r.accountId = :accountId OR r.toAccountId = :accountId) ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findByUserIdAndAccountId(
            @Param("userId") Long userId, @Param("accountId") Long accountId);

    /**
     * Finds all active recurring transactions that are due for processing.
     *
     * <p>Returns recurring transactions where:
     *
     * <ul>
     *   <li>isActive = true
     *   <li>nextOccurrence <= specified date (usually today)
     *   <li>endDate is null OR endDate >= specified date
     * </ul>
     *
     * <p>This is the main query used by the scheduled job to process due transactions.
     *
     * @param asOfDate the date to check (usually LocalDate.now())
     * @return list of due recurring transactions, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.isActive = true AND r.nextOccurrence <= :asOfDate AND (r.endDate IS NULL OR r.endDate >= :asOfDate) ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findDueRecurringTransactions(@Param("asOfDate") LocalDate asOfDate);

    /**
     * Finds all recurring transactions for a specific user that are due for processing.
     *
     * <p>User-scoped version of findDueRecurringTransactions. Useful for showing users which
     * transactions will be processed soon.
     *
     * @param userId the user ID
     * @param asOfDate the date to check (usually LocalDate.now())
     * @return list of due recurring transactions for user, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.userId = :userId AND r.isActive = true AND r.nextOccurrence <= :asOfDate AND (r.endDate IS NULL OR r.endDate >= :asOfDate) ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findDueRecurringTransactionsByUserId(
            @Param("userId") Long userId, @Param("asOfDate") LocalDate asOfDate);

    /**
     * Finds all recurring transactions with a specific frequency.
     *
     * <p>Useful for analytics or bulk operations on recurring transactions of a certain frequency
     * (e.g., all MONTHLY subscriptions).
     *
     * @param userId the user ID
     * @param frequency the frequency to filter by
     * @return list of recurring transactions with specified frequency, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.userId = :userId AND r.frequency = :frequency ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findByUserIdAndFrequency(
            @Param("userId") Long userId, @Param("frequency") RecurringFrequency frequency);

    /**
     * Counts all recurring transactions for a specific user.
     *
     * @param userId the user ID
     * @return count of recurring transactions
     */
    @Query("SELECT COUNT(r) FROM RecurringTransaction r WHERE r.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    /**
     * Counts active recurring transactions for a specific user.
     *
     * @param userId the user ID
     * @return count of active recurring transactions
     */
    @Query(
            "SELECT COUNT(r) FROM RecurringTransaction r WHERE r.userId = :userId AND r.isActive = true")
    long countByUserIdAndIsActive(@Param("userId") Long userId);

    /**
     * Finds all recurring transactions that are ending soon (within specified days).
     *
     * <p>Useful for notifying users about upcoming end dates so they can extend or update their
     * recurring transactions.
     *
     * @param userId the user ID
     * @param startDate the start of the date range (usually today)
     * @param endDate the end of the date range (usually today + N days)
     * @return list of recurring transactions ending soon, empty list if none found
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.userId = :userId AND r.isActive = true AND r.endDate IS NOT NULL AND r.endDate BETWEEN :startDate AND :endDate ORDER BY r.endDate ASC")
    List<RecurringTransaction> findEndingSoon(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Checks if a recurring transaction exists with the given ID and belongs to the user.
     *
     * <p>Lightweight query for authorization checks without fetching the full entity.
     *
     * @param id the recurring transaction ID
     * @param userId the user ID
     * @return true if exists and owned by user, false otherwise
     */
    @Query(
            "SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RecurringTransaction r WHERE r.id = :id AND r.userId = :userId")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Deletes a recurring transaction by ID if it belongs to the user.
     *
     * <p>Authorization-aware delete. Only deletes if the recurring transaction belongs to the
     * specified user.
     *
     * @param id the recurring transaction ID
     * @param userId the user ID
     * @return number of entities deleted (0 or 1)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RecurringTransaction r WHERE r.id = :id AND r.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Find recurring transactions with optional filters.
     *
     * <p>Supports filtering by type, frequency, and isActive. Search by description is handled in
     * the service layer due to encryption.
     *
     * @param userId the user ID
     * @param type optional type filter
     * @param frequency optional frequency filter
     * @param isActive optional active status filter
     * @return list of recurring transactions
     */
    @Query(
            "SELECT r FROM RecurringTransaction r WHERE r.userId = :userId "
                    + "AND (:type IS NULL OR r.type = :type) "
                    + "AND (:frequency IS NULL OR r.frequency = :frequency) "
                    + "AND (:isActive IS NULL OR r.isActive = :isActive) "
                    + "ORDER BY r.nextOccurrence ASC")
    List<RecurringTransaction> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("type") org.openfinance.entity.TransactionType type,
            @Param("frequency") RecurringFrequency frequency,
            @Param("isActive") Boolean isActive);
}
