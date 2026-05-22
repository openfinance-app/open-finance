package org.openfinance.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for archiving old transaction data.
 *
 * <p>Moves transactions older than a configurable threshold from the active {@code transactions}
 * table to the {@code transactions_archive} table. Archiving keeps the active dataset small,
 * improving query performance.
 *
 * <p>The archive table mirrors the schema of the active transactions table (all columns as of V49)
 * plus an {@code archived_at} timestamp column.
 *
 * <p>Requirement REQ-3.1: Database performance — archive old data.
 *
 * @author Open-Finance Team
 * @version 1.0
 */
@Service
@Slf4j
public class TransactionArchiveService {

    @PersistenceContext private EntityManager entityManager;

    /** Default archive threshold: transactions older than this many years are archived. */
    private static final int ARCHIVE_THRESHOLD_YEARS = 3;

    /**
     * Archives transactions older than {@value #ARCHIVE_THRESHOLD_YEARS} years for a specific user.
     *
     * <p>This operation:
     *
     * <ol>
     *   <li>Copies matching rows to {@code transactions_archive}
     *   <li>Deletes the copied rows from {@code transactions}
     * </ol>
     *
     * @param userId the ID of the user whose old transactions should be archived
     * @return the number of transactions archived
     */
    @Transactional
    public int archiveOldTransactionsForUser(Long userId) {
        LocalDate cutoffDate = LocalDate.now().minusYears(ARCHIVE_THRESHOLD_YEARS);
        log.info("Archiving transactions before {} for user {}", cutoffDate, userId);

        // Copy to archive (excludes rows already present in the archive)
        int archived =
                entityManager
                        .createNativeQuery(
                                """
                INSERT INTO transactions_archive
                    (id, user_id, account_id, to_account_id, transaction_type,
                     amount, currency, category_id, transaction_date,
                     description, notes, tags, payee,
                     is_reconciled, is_deleted, transfer_id,
                     payment_method, liability_id, external_reference,
                     payee_id, currency_id,
                     created_at, updated_at)
                SELECT id, user_id, account_id, to_account_id, transaction_type,
                       amount, currency, category_id, transaction_date,
                       description, notes, tags, payee,
                       is_reconciled, is_deleted, transfer_id,
                       payment_method, liability_id, external_reference,
                       payee_id, currency_id,
                       created_at, updated_at
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_date < :cutoffDate
                  AND id NOT IN (SELECT id FROM transactions_archive WHERE user_id = :userId)
                """)
                        .setParameter("userId", userId)
                        .setParameter("cutoffDate", cutoffDate.toString())
                        .executeUpdate();

        // Delete archived rows from active table
        int deleted =
                entityManager
                        .createNativeQuery(
                                """
                DELETE FROM transactions
                WHERE user_id = :userId
                  AND transaction_date < :cutoffDate
                  AND id IN (SELECT id FROM transactions_archive WHERE user_id = :userId)
                """)
                        .setParameter("userId", userId)
                        .setParameter("cutoffDate", cutoffDate.toString())
                        .executeUpdate();

        log.info(
                "Archived {} transactions for user {} (deleted {} from active table)",
                archived,
                userId,
                deleted);
        return archived;
    }

    /**
     * Returns the count of archived transactions for a user.
     *
     * @param userId the user ID
     * @return number of archived transactions
     */
    @Transactional(readOnly = true)
    public long getArchivedTransactionCount(Long userId) {
        Object result =
                entityManager
                        .createNativeQuery(
                                "SELECT COUNT(*) FROM transactions_archive WHERE user_id = :userId")
                        .setParameter("userId", userId)
                        .getSingleResult();
        return result != null ? ((Number) result).longValue() : 0L;
    }
}
