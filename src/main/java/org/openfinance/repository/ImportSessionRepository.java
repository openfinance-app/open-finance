package org.openfinance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link ImportSession} entity. Provides data access methods for import
 * session management.
 *
 * @see ImportSession
 * @see org.openfinance.service.ImportService
 */
@Repository
public interface ImportSessionRepository extends JpaRepository<ImportSession, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(
            "UPDATE ImportSession s SET s.metadata = :metadata, s.totalTransactions = :count, s.status = 'REVIEWING', s.updatedAt = :updatedAt WHERE s.id = :id AND s.userId = :userId AND s.status IN ('PARSED', 'REVIEWING') AND s.confirmationStarted = false")
    int saveReview(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("metadata") String metadata,
            @Param("count") int count,
            @Param("updatedAt") LocalDateTime updatedAt);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(
            "UPDATE ImportSession s SET s.accountId = :accountId, s.updatedAt = :updatedAt WHERE s.id = :id AND s.userId = :userId AND s.status IN ('PARSED', 'REVIEWING') AND s.confirmationStarted = false")
    int selectReviewAccount(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            @Param("updatedAt") LocalDateTime updatedAt);

    @org.springframework.data.jpa.repository.Modifying(
            clearAutomatically = true,
            flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query(
            value =
                    "UPDATE import_sessions SET status = 'IMPORTING' WHERE id = :id AND user_id = :userId AND status IN ('PARSED', 'REVIEWING')",
            nativeQuery = true)
    int claimConfirmation(@Param("id") Long id, @Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying(
            clearAutomatically = true,
            flushAutomatically = true)
    @Query(
            value =
                    "UPDATE import_sessions SET confirmation_started = TRUE, status = 'IMPORTING' WHERE id = :id AND user_id = :userId AND confirmation_started = FALSE AND status IN ('PARSED', 'REVIEWING', 'IMPORTING')",
            nativeQuery = true)
    int beginConfirmationExecution(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Find an import session by upload ID.
     *
     * @param uploadId the UUID of the uploaded file
     * @return Optional containing the import session if found
     */
    Optional<ImportSession> findByUploadId(String uploadId);

    /**
     * Find all import sessions for a specific user.
     *
     * @param userId the user ID
     * @return list of import sessions ordered by creation date descending
     */
    List<ImportSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find all import sessions for a specific user with a specific status.
     *
     * @param userId the user ID
     * @param status the import status
     * @return list of import sessions ordered by creation date descending
     */
    List<ImportSession> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ImportStatus status);

    /**
     * Find all import sessions with a specific status. Useful for background jobs that need to
     * process pending/stuck sessions.
     *
     * @param status the import status
     * @return list of import sessions ordered by creation date ascending
     */
    List<ImportSession> findByStatusOrderByCreatedAtAsc(ImportStatus status);

    /**
     * Find import sessions older than a specific date. Used for cleanup of old sessions.
     *
     * @param date the cutoff date
     * @return list of import sessions
     */
    List<ImportSession> findByCreatedAtBefore(LocalDateTime date);

    /**
     * Find import sessions for a specific account.
     *
     * @param accountId the account ID
     * @return list of import sessions ordered by creation date descending
     */
    List<ImportSession> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * Count total import sessions for a user.
     *
     * @param userId the user ID
     * @return total count
     */
    long countByUserId(Long userId);

    /**
     * Count completed import sessions for a user.
     *
     * @param userId the user ID
     * @param status the import status (typically COMPLETED)
     * @return count of completed sessions
     */
    long countByUserIdAndStatus(Long userId, ImportStatus status);

    /**
     * Check if an import session exists for a specific upload ID.
     *
     * @param uploadId the UUID of the uploaded file
     * @return true if exists, false otherwise
     */
    boolean existsByUploadId(String uploadId);

    /**
     * Find recent import sessions for a user (within last N days).
     *
     * @param userId the user ID
     * @param since the start date
     * @return list of import sessions
     */
    @Query(
            "SELECT i FROM ImportSession i WHERE i.userId = :userId AND i.createdAt >= :since ORDER BY i.createdAt DESC")
    List<ImportSession> findRecentByUserId(
            @Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Find incomplete import sessions for a user (not completed, cancelled, or failed). Useful for
     * showing active imports in the UI.
     *
     * @param userId the user ID
     * @return list of incomplete import sessions
     */
    @Query(
            "SELECT i FROM ImportSession i WHERE i.userId = :userId AND i.status NOT IN ('COMPLETED', 'CANCELLED', 'FAILED') ORDER BY i.createdAt DESC")
    List<ImportSession> findIncompleteByUserId(@Param("userId") Long userId);

    /**
     * Delete import sessions older than a specific date. Used for periodic cleanup.
     *
     * @param date the cutoff date
     * @return number of deleted sessions
     */
    long deleteByCreatedAtBefore(LocalDateTime date);

    /**
     * Delete all import sessions for a specific user. Used when a user account is deleted.
     *
     * @param userId the user ID
     * @return number of deleted sessions
     */
    long deleteByUserId(Long userId);
}
