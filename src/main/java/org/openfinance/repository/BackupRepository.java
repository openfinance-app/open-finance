package org.openfinance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Backup entity operations. Provides database access methods for backup
 * management.
 *
 * <p><b>Requirements:</b> REQ-2.14.2.1, REQ-2.14.2.2
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@Repository
public interface BackupRepository extends JpaRepository<Backup, Long> {

    /**
     * Find all backups for a specific user, ordered by creation date (newest first).
     *
     * @param userId the ID of the user
     * @return list of backups, ordered by createdAt descending
     */
    List<Backup> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find a backup by ID and user ID (for authorization).
     *
     * @param id the backup ID
     * @param userId the user ID
     * @return Optional containing the backup if found and owned by user
     */
    Optional<Backup> findByIdAndUserId(Long id, Long userId);

    /**
     * Find all completed backups for a user.
     *
     * @param userId the user ID
     * @return list of completed backups
     */
    List<Backup> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    /**
     * Find backups by type (MANUAL or AUTOMATIC) for a user.
     *
     * @param userId the user ID
     * @param backupType the backup type
     * @return list of backups of the specified type
     */
    List<Backup> findByUserIdAndBackupTypeOrderByCreatedAtDesc(Long userId, String backupType);

    /**
     * Find automatic backups older than a specific date (for rotation).
     *
     * @param userId the user ID
     * @param before the date before which backups should be found
     * @param backupType the backup type (AUTOMATIC)
     * @return list of old automatic backups
     */
    @Query(
            "SELECT b FROM Backup b WHERE b.userId = :userId "
                    + "AND b.backupType = :backupType "
                    + "AND b.createdAt < :before "
                    + "ORDER BY b.createdAt ASC")
    List<Backup> findOldAutomaticBackups(
            @Param("userId") Long userId,
            @Param("before") LocalDateTime before,
            @Param("backupType") String backupType);

    /**
     * Count backups by user and type.
     *
     * @param userId the user ID
     * @param backupType the backup type
     * @return count of backups
     */
    Long countByUserIdAndBackupType(Long userId, String backupType);

    /**
     * Count all backups for a user.
     *
     * @param userId the user ID
     * @return count of backups
     */
    Long countByUserId(Long userId);

    /**
     * Delete backups older than a specific date. Used for automatic backup rotation.
     *
     * @param userId the user ID
     * @param before the date before which backups should be deleted
     * @param backupType the backup type
     * @return number of deleted backups
     */
    @Modifying
    @Query(
            "DELETE FROM Backup b WHERE b.userId = :userId "
                    + "AND b.backupType = :backupType "
                    + "AND b.createdAt < :before")
    int deleteOldBackups(
            @Param("userId") Long userId,
            @Param("before") LocalDateTime before,
            @Param("backupType") String backupType);

    /**
     * Check if a backup exists for a user by ID.
     *
     * @param id the backup ID
     * @param userId the user ID
     * @return true if backup exists and belongs to user
     */
    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Find the most recent completed backup for a user.
     *
     * @param userId the user ID
     * @param status the status (COMPLETED)
     * @return Optional containing the most recent backup
     */
    @Query(
            "SELECT b FROM Backup b WHERE b.userId = :userId "
                    + "AND b.status = :status "
                    + "ORDER BY b.createdAt DESC "
                    + "LIMIT 1")
    Optional<Backup> findMostRecentCompletedBackup(
            @Param("userId") Long userId, @Param("status") String status);
}
