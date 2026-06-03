package org.openfinance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.openfinance.entity.TransactionSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TransactionSplit} entities.
 *
 * <p>
 * Provides query methods for fetching and removing split records associated
 * with parent
 * transactions.
 *
 * <p>
 * Requirement REQ-SPL-1.3: Splits are stored in and retrieved from the {@code
 * transaction_splits} table.
 *
 * <p>
 * Requirement REQ-SPL-2.5: Support dedicated split-fetch operations.
 *
 * <p>
 * Requirement REQ-SPL-2.7: Deleting splits when parent is deleted (also handled
 * by the DB
 * cascade, but explicit delete is available for service-level replace-on-update
 * scenarios).
 */
@Repository
public interface TransactionSplitRepository extends JpaRepository<TransactionSplit, Long> {

        /**
         * Returns all splits belonging to a specific transaction, ordered by id.
         *
         * @param transactionId the parent transaction ID
         * @return ordered list of splits (may be empty)
         */
        List<TransactionSplit> findByTransactionIdOrderById(Long transactionId);

        /**
         * Deletes all splits belonging to a specific transaction.
         *
         * <p>
         * Used during update operations to replace existing splits with the new set
         * provided in the
         * request (delete-and-insert pattern).
         *
         * @param transactionId the parent transaction ID
         */
        void deleteByTransactionId(Long transactionId);

        /**
         * Returns all splits for a batch of transaction IDs.
         *
         * <p>
         * Used when building list/search responses that need to include splits for
         * multiple
         * transactions without N+1 queries.
         *
         * @param transactionIds list of parent transaction IDs
         * @return all splits for the given transactions
         */
        List<TransactionSplit> findByTransactionIdIn(List<Long> transactionIds);

        /**
         * /**
         * Counts how many unique EXPENSE transactions have splits in the given
         * categories and date
         * range.
         *
         * @param categoryIds list of category IDs
         * @param startDate   start date
         * @param endDate     end date
         * @param userId      user ID
         * @return count of unique parent transactions
         */
        @Query("SELECT COUNT(DISTINCT s.transactionId) FROM TransactionSplit s JOIN Transaction t ON s.transactionId = t.id "
                        + "WHERE s.categoryId IN :categoryIds AND t.userId = :userId "
                        + "AND t.date BETWEEN :startDate AND :endDate AND t.isDeleted = false "
                        + "AND t.type = org.openfinance.entity.TransactionType.EXPENSE")
        Long countUniqueTransactionsByCategoryIdInAndDateRange(
                        @Param("categoryIds") List<Long> categoryIds,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("userId") Long userId);

        /**
         * Finds expense splits for specific categories within a date range.
         *
         * <p>
         * Used for in-memory aggregation when amounts are encrypted.
         *
         * @param categoryIds list of category IDs
         * @param startDate   start date (inclusive)
         * @param endDate     end date (inclusive)
         * @param userId      user ID
         * @return list of matching splits
         */
        @Query("SELECT s FROM TransactionSplit s JOIN Transaction t ON s.transactionId = t.id "
                        + "WHERE s.categoryId IN :categoryIds AND t.userId = :userId "
                        + "AND t.date BETWEEN :startDate AND :endDate AND t.isDeleted = false "
                        + "AND t.type = org.openfinance.entity.TransactionType.EXPENSE")
        List<TransactionSplit> findByCategoryIdInAndDateRange(
                        @Param("categoryIds") List<Long> categoryIds,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("userId") Long userId);
}
