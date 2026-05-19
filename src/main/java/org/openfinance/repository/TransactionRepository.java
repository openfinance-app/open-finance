package org.openfinance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Transaction} entity operations.
 *
 * <p>Provides CRUD operations and custom queries for managing financial transactions. All queries
 * automatically exclude soft-deleted transactions unless specifically included.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>User-scoped queries for data isolation
 *   <li>Date range filtering for reports
 *   <li>Account and category filtering
 *   <li>Automatic soft-delete handling
 *   <li>Dynamic query support via JPA Specifications
 *   <li>Pagination and sorting support
 * </ul>
 *
 * <p><strong>Requirement REQ-2.4.1.1</strong>: Transaction data access and persistence
 *
 * <p><strong>Requirement REQ-2.3.5</strong>: Advanced search and filtering
 *
 * @see Transaction
 * @since 1.0
 */
@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * Finds all active (non-deleted) transactions for a specific user.
     *
     * <p>Results ordered by transaction date descending (newest first).
     *
     * @param userId the user ID
     * @return list of user's transactions, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByUserId(@Param("userId") Long userId);

    /**
     * Finds active transactions for a specific user with pagination.
     *
     * @param userId the user ID
     * @param pageable pagination and sorting parameters
     * @return page of user's transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.isDeleted = false")
    org.springframework.data.domain.Page<Transaction> findByUserId(
            @Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    /**
     * Finds all active transactions for a specific account.
     *
     * <p>Includes transactions where the account is either the source (accountId) or destination
     * (toAccountId) for transfers.
     *
     * <p>Results ordered by transaction date descending.
     *
     * @param accountId the account ID
     * @return list of account's transactions, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE (t.accountId = :accountId OR t.toAccountId = :accountId) AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByAccountId(@Param("accountId") Long accountId);

    /**
     * Finds all active transactions where the account is the destination (toAccountId).
     *
     * <p>This is used for transfer transactions where money is transferred TO this account.
     *
     * @param accountId the destination account ID
     * @return list of incoming transfer transactions, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.toAccountId = :accountId AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByToAccountId(@Param("accountId") Long accountId);

    /**
     * Finds all active transactions for a specific user and account.
     *
     * <p>Combines user ownership check with account filtering. Results ordered by transaction date
     * descending.
     *
     * @param userId the user ID
     * @param accountId the account ID
     * @return list of transactions, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND (t.accountId = :accountId OR t.toAccountId = :accountId) AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndAccountId(
            @Param("userId") Long userId, @Param("accountId") Long accountId);

    /**
     * Finds a transaction by ID and user ID with soft-delete check.
     *
     * <p>Used for access control - ensures user can only access their own non-deleted transactions.
     *
     * <p>Requirement REQ-3.2: User-specific data isolation
     *
     * @param id the transaction ID
     * @param userId the user ID
     * @return Optional containing the transaction if found and owned by user, empty otherwise
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.id = :id AND t.userId = :userId AND t.isDeleted = false")
    Optional<Transaction> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Finds all active transactions within a date range for a user.
     *
     * <p>Useful for generating reports and analyzing spending over specific periods. Results
     * ordered by transaction date ascending.
     *
     * @param userId the user ID
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of transactions in date range, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :startDate AND :endDate AND t.isDeleted = false ORDER BY t.date ASC")
    List<Transaction> findByUserIdAndDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Finds all active transactions for a specific category.
     *
     * <p>Used for category-based reporting and budget tracking. Results ordered by transaction date
     * descending.
     *
     * @param categoryId the category ID
     * @return list of transactions in category, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.categoryId = :categoryId AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Finds all active transactions for a specific user and category.
     *
     * <p>Combines user ownership check with category filtering. Results ordered by transaction date
     * descending.
     *
     * @param userId the user ID
     * @param categoryId the category ID
     * @return list of transactions, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND t.categoryId = :categoryId AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndCategoryId(
            @Param("userId") Long userId, @Param("categoryId") Long categoryId);

    /**
     * Finds all active transactions of a specific type for a user.
     *
     * <p>Filter by INCOME, EXPENSE, or TRANSFER. Results ordered by transaction date descending.
     *
     * @param userId the user ID
     * @param type the transaction type
     * @return list of transactions of specified type, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND t.type = :type AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndType(
            @Param("userId") Long userId, @Param("type") TransactionType type);

    /**
     * Finds unreconciled transactions for an account.
     *
     * <p>Used for bank statement reconciliation process. Results ordered by transaction date
     * ascending.
     *
     * @param accountId the account ID
     * @return list of unreconciled transactions, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE (t.accountId = :accountId OR t.toAccountId = :accountId) AND t.isReconciled = false AND t.isDeleted = false ORDER BY t.date ASC")
    List<Transaction> findUnreconciledByAccountId(@Param("accountId") Long accountId);

    /**
     * Counts active transactions for a user.
     *
     * <p>Used for statistics and pagination.
     *
     * @param userId the user ID
     * @return number of active transactions
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.isDeleted = false")
    Long countByUserId(@Param("userId") Long userId);

    /**
     * Checks if a transaction exists for a user (excludes soft-deleted).
     *
     * <p>Used for access control validation.
     *
     * @param id the transaction ID
     * @param userId the user ID
     * @return true if transaction exists and belongs to user, false otherwise
     */
    @Query(
            "SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Transaction t WHERE t.id = :id AND t.userId = :userId AND t.isDeleted = false")
    boolean existsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Finds all transactions linked by a transfer ID.
     *
     * <p>A transfer creates two linked transactions (source EXPENSE and destination INCOME) that
     * share the same transferId UUID. This query retrieves both transactions for viewing the
     * complete transfer or for cascading operations like delete.
     *
     * <p>For valid transfers, this should return exactly 2 transactions. If it returns fewer, the
     * transfer may be incomplete or corrupted.
     *
     * <p>Requirement REQ-2.4.1.4: Transfer transaction linking and retrieval
     *
     * @param transferId the transfer ID (UUID) linking two transactions
     * @return list of transactions with the specified transferId (should contain 2 for valid
     *     transfers)
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.transferId = :transferId AND t.isDeleted = false ORDER BY t.id ASC")
    List<Transaction> findByTransferId(@Param("transferId") String transferId);

    /**
     * Finds all active transactions for a specific category within a date range.
     *
     * <p>Used for budget tracking - calculates spending in a category during a budget period.
     * Results ordered by transaction date ascending.
     *
     * <p>Requirement REQ-2.9.1.2: Calculate spent amount for budget tracking
     *
     * @param categoryId the category ID
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param userId the user ID (for security/isolation)
     * @return list of transactions in category within date range, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.categoryId = :categoryId AND t.userId = :userId "
                    + "AND t.date BETWEEN :startDate AND :endDate AND t.isDeleted = false ORDER BY t.date ASC")
    List<Transaction> findByCategoryIdAndDateRange(
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("userId") Long userId);

    /**
     * Finds all active transactions for a list of categories within a date range.
     *
     * <p>Used for budget tracking with category hierarchies - calculates spending across a parent
     * category and all its descendants.
     *
     * @param categoryIds the list of category IDs
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param userId the user ID
     * @return list of transactions matching the criteria
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.categoryId IN :categoryIds AND t.userId = :userId "
                    + "AND t.date BETWEEN :startDate AND :endDate AND t.isDeleted = false ORDER BY t.date ASC")
    List<Transaction> findByCategoryIdInAndDateRange(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("userId") Long userId);

    /**
     * Counts active transactions for a specific account.
     *
     * <p>Includes transactions where the account is either the source (accountId) or destination
     * (toAccountId) for transfers. Used to prevent deletion of accounts with active transactions.
     *
     * <p>Requirement REQ-2.2.4: Prevent soft-delete of accounts with active transactions
     *
     * @param accountId the account ID
     * @return number of active transactions for the account
     */
    @Query(
            "SELECT COUNT(t) FROM Transaction t WHERE (t.accountId = :accountId OR t.toAccountId = :accountId) AND t.isDeleted = false")
    Long countByAccountId(@Param("accountId") Long accountId);

    /**
     * Finds all active transactions for a specific account within a date range.
     *
     * <p>Used for calculating account balance history over time. Results ordered by transaction
     * date ascending for proper balance calculation.
     *
     * <p>Requirement REQ-2.6.1.2: Account Balance Tracking - Historical snapshots
     *
     * @param accountId the account ID
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of transactions in date range, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.accountId = :accountId AND t.date BETWEEN :startDate AND :endDate AND t.isDeleted = false ORDER BY t.date ASC")
    List<Transaction> findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Counts active transactions for a specific category.
     *
     * <p>Used for budget tracking and category management.
     *
     * @param categoryId the category ID
     * @return number of active transactions in this category
     */
    @Query(
            "SELECT COUNT(t) FROM Transaction t WHERE t.categoryId = :categoryId AND t.isDeleted = false")
    Long countByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Calculates the total balance from all active transactions for a specific account.
     *
     * <p>Sums income transactions and subtracts expense transactions. Note: Transfers are
     * represented as two transactions (one INCOME, one EXPENSE) so they are correctly accounted for
     * by this query.
     *
     * @param accountId the account ID
     * @return the total balance from transactions
     */
    @Query(
            "SELECT (COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) - "
                    + "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0)) "
                    + "FROM Transaction t WHERE t.accountId = :accountId AND t.isDeleted = false")
    BigDecimal calculateBalance(@Param("accountId") Long accountId);

    /**
     * Calculates the total amount of transactions for a specific category.
     *
     * <p>Used for displaying total amount per category in UI.
     *
     * @param categoryId the category ID
     * @return sum of transaction amounts (negative for expenses, positive for income)
     */
    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.categoryId = :categoryId AND t.isDeleted = false")
    BigDecimal sumAmountByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Finds all active transactions linked to a specific liability for a user.
     *
     * <p>Returns non-deleted transactions where the {@code liabilityId} matches the given liability
     * and the transaction belongs to the specified user.
     *
     * <p>Typical usage: retrieve loan payment transactions associated with a mortgage or loan.
     *
     * <p>Requirement REQ-LIA-4: Transaction-liability linking
     *
     * @param liabilityId the liability ID
     * @param userId the user ID (for authorization / data isolation)
     * @return list of linked transactions ordered by date descending, empty list if none found
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.liabilityId = :liabilityId AND t.userId = :userId AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByLiabilityIdAndUserId(
            @Param("liabilityId") Long liabilityId, @Param("userId") Long userId);

    /** Projection for payee transaction statistics. */
    interface PayeeStats {
        String getPayee();

        Long getCount();

        BigDecimal getTotal();
    }

    /**
     * Get transaction statistics grouped by payee for a specific user.
     *
     * @param userId the user ID
     * @return list of payee statistics
     */
    @Query(
            "SELECT t.payee as payee, COUNT(t) as count, SUM(t.amount) as total "
                    + "FROM Transaction t "
                    + "WHERE t.userId = :userId AND t.isDeleted = false AND t.payee IS NOT NULL "
                    + "GROUP BY t.payee")
    List<PayeeStats> findPayeeStatsByUserId(@Param("userId") Long userId);

    /**
     * Counts active transactions without a category for a user.
     *
     * <p>Used for uncategorized transaction notifications.
     *
     * @param userId the user ID
     * @return count of uncategorized transactions
     */
    Long countByUserIdAndCategoryIdIsNullAndIsDeletedFalse(Long userId);

    /**
     * Counts active transactions without a payee for a user.
     *
     * <p>Used for unlinked payee notifications.
     *
     * @param userId the user ID
     * @return count of transactions without payee
     */
    Long countByUserIdAndPayeeIsNullAndIsDeletedFalse(Long userId);

    /**
     * Checks whether a non-deleted transaction with the given external reference already exists for
     * an account.
     *
     * <p>Used during import duplicate detection (Tier 1 — exact reference match). A non-empty
     * result means the incoming transaction's {@code referenceNumber} (e.g. OFX FITID or QIF check
     * number) matches a previously imported transaction on the same account, so the import
     * candidate is a definite duplicate.
     *
     * <p>Requirement: REQ-2.10.4 (Duplicate transaction detection — reference-based)
     *
     * @param accountId the account ID
     * @param externalReference the external reference to look up (must not be null)
     * @return {@code true} if a matching, non-deleted transaction exists
     */
    @Query(
            "SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END "
                    + "FROM Transaction t "
                    + "WHERE t.accountId = :accountId "
                    + "AND t.externalReference = :externalReference "
                    + "AND t.isDeleted = false")
    boolean existsByAccountIdAndExternalReference(
            @Param("accountId") Long accountId,
            @Param("externalReference") String externalReference);

    // ---------------------------------------------------------------
    // Unusual transaction detection queries
    // ---------------------------------------------------------------

    /**
     * Finds all non-deleted transactions for a user created at or after the given timestamp.
     *
     * <p>Used by the daily unusual-transaction-detection scheduler to discover transactions that
     * were newly imported or manually entered since the last run.
     *
     * @param userId the user ID
     * @param since the lower-bound timestamp (inclusive)
     * @return list of recently created transactions ordered by date descending
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND t.createdAt >= :since AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndCreatedAtAfter(
            @Param("userId") Long userId, @Param("since") java.time.LocalDateTime since);

    /**
     * Counts historical non-deleted transactions for a user with the given payee that were created
     * strictly before the given timestamp.
     *
     * <p>Used to determine whether a payee is "new" (count == 0) for first-time payee anomaly
     * detection.
     *
     * @param userId the user ID
     * @param payee the payee name (case-sensitive)
     * @param before upper-bound creation timestamp (exclusive)
     * @return number of prior transactions with this payee
     */
    @Query(
            "SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.payee = :payee AND t.createdAt < :before AND t.isDeleted = false")
    Long countByUserIdAndPayeeAndCreatedAtBefore(
            @Param("userId") Long userId,
            @Param("payee") String payee,
            @Param("before") java.time.LocalDateTime before);

    /**
     * Finds all non-deleted transactions for a user with the given payee created strictly before
     * the given timestamp.
     *
     * <p>Used to compute per-payee spending statistics (mean and standard deviation) for
     * large-amount anomaly detection.
     *
     * @param userId the user ID
     * @param payee the payee name (case-sensitive)
     * @param before upper-bound creation timestamp (exclusive)
     * @return list of prior transactions for this payee ordered by date descending
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND t.payee = :payee AND t.createdAt < :before AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndPayeeAndCreatedAtBefore(
            @Param("userId") Long userId,
            @Param("payee") String payee,
            @Param("before") java.time.LocalDateTime before);

    /**
     * Finds all non-deleted EXPENSE transactions for a user in a given category created strictly
     * before the given timestamp.
     *
     * <p>Used to compute per-category spending statistics when a transaction has no payee set.
     *
     * @param userId the user ID
     * @param categoryId the category ID
     * @param before upper-bound creation timestamp (exclusive)
     * @return list of prior expense transactions in this category
     */
    @Query(
            "SELECT t FROM Transaction t WHERE t.userId = :userId AND t.categoryId = :categoryId AND t.type = org.openfinance.entity.TransactionType.EXPENSE AND t.createdAt < :before AND t.isDeleted = false ORDER BY t.date DESC")
    List<Transaction> findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("before") java.time.LocalDateTime before);
}
