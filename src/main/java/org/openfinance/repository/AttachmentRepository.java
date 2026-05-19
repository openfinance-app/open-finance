package org.openfinance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Attachment;
import org.openfinance.entity.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Attachment entity operations.
 *
 * <p>Provides database access methods for file attachment management including filtering by user,
 * entity type, entity ID, and authorization checks.
 *
 * <p>Requirement REQ-2.12: File Attachment System - Users can attach files to transactions, assets,
 * real estate properties, and liabilities for record-keeping and documentation.
 *
 * @author Open-Finance Development Team
 * @since Sprint 12
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * Finds all attachments belonging to a specific user.
     *
     * <p>Useful for displaying all user attachments across all entities or calculating total
     * storage usage.
     *
     * <p>Requirement REQ-2.12.1: Users can view all their uploaded attachments
     *
     * @param userId ID of the user
     * @return List of attachments owned by the user, ordered by upload date descending (may be
     *     empty)
     */
    List<Attachment> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * Finds all attachments for a specific entity.
     *
     * <p>Used to display all files attached to a transaction, asset, real estate property, or
     * liability. For example, showing all receipt images attached to a specific transaction.
     *
     * <p>Requirement REQ-2.12.2: Attachments can be linked to various entity types
     *
     * @param entityType Type of entity (TRANSACTION, ASSET, REAL_ESTATE, LIABILITY)
     * @param entityId ID of the entity
     * @return List of attachments for the entity, ordered by upload date descending (may be empty)
     */
    List<Attachment> findByEntityTypeAndEntityIdOrderByUploadedAtDesc(
            EntityType entityType, Long entityId);

    /**
     * Finds all attachments of a specific entity type for a user.
     *
     * <p>Useful for filtering user attachments by entity type (e.g., show only transaction
     * receipts).
     *
     * <p>Requirement REQ-2.12.3: Filter attachments by entity type
     *
     * @param userId ID of the user
     * @param entityType Type of entity to filter by
     * @return List of attachments matching the entity type, ordered by upload date descending (may
     *     be empty)
     */
    List<Attachment> findByUserIdAndEntityTypeOrderByUploadedAtDesc(
            Long userId, EntityType entityType);

    /**
     * Finds an attachment by ID, ensuring it belongs to the specified user.
     *
     * <p>This method provides authorization check at the repository level, preventing users from
     * accessing attachments they don't own.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own data
     *
     * @param id Attachment ID
     * @param userId User ID (for ownership verification)
     * @return Optional containing the attachment if found and owned by user, empty otherwise
     */
    Optional<Attachment> findByIdAndUserId(Long id, Long userId);

    /**
     * Checks if an attachment exists and belongs to the specified user.
     *
     * <p>More efficient than findByIdAndUserId when only existence check is needed.
     *
     * <p>Requirement REQ-3.2: Authorization - Verify ownership before operations
     *
     * @param id Attachment ID
     * @param userId User ID (for ownership verification)
     * @return true if attachment exists and belongs to user, false otherwise
     */
    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Deletes an attachment by ID, ensuring it belongs to the specified user.
     *
     * <p>Provides secure deletion with automatic authorization check.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only delete their own attachments
     *
     * @param id Attachment ID
     * @param userId User ID (for ownership verification)
     */
    void deleteByIdAndUserId(Long id, Long userId);

    /**
     * Counts the total number of attachments for a user.
     *
     * <p>Useful for displaying summary statistics or enforcing attachment limits.
     *
     * <p>Requirement REQ-2.12.4: Track total attachment count per user
     *
     * @param userId User ID
     * @return Count of attachments owned by the user
     */
    long countByUserId(Long userId);

    /**
     * Counts attachments of a specific entity type for a user.
     *
     * <p>Used for statistics and analysis of attachment distribution across entity types.
     *
     * @param userId User ID
     * @param entityType Entity type to count
     * @return Count of attachments of the specified entity type
     */
    long countByUserIdAndEntityType(Long userId, EntityType entityType);

    /**
     * Calculates the total storage size used by all attachments for a user.
     *
     * <p>Sums the file sizes of all user attachments to track storage usage. Used for displaying
     * storage statistics and enforcing storage quotas.
     *
     * <p>Requirement REQ-2.12.5: Track total storage usage per user
     *
     * @param userId User ID
     * @return Total storage size in bytes, or 0 if user has no attachments
     */
    @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM Attachment a WHERE a.userId = :userId")
    Long getTotalStorageByUserId(@Param("userId") Long userId);

    /**
     * Finds attachments uploaded within a specific date range for a user.
     *
     * <p>Useful for reporting and analytics on attachment upload patterns.
     *
     * @param userId User ID
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of attachments uploaded within the date range, ordered by upload date descending
     */
    List<Attachment> findByUserIdAndUploadedAtBetweenOrderByUploadedAtDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Finds attachments by file type for a user.
     *
     * <p>Useful for filtering attachments by MIME type (e.g., show only PDFs or images).
     *
     * <p>Requirement REQ-2.12.6: Filter attachments by file type
     *
     * @param userId User ID
     * @param fileType MIME type to filter by (e.g., "application/pdf", "image/jpeg")
     * @return List of attachments matching the file type, ordered by upload date descending
     */
    List<Attachment> findByUserIdAndFileTypeOrderByUploadedAtDesc(Long userId, String fileType);

    /**
     * Finds attachments by file type pattern for a user.
     *
     * <p>Uses LIKE query to match file type patterns. For example, "image/%" matches all image
     * types (image/jpeg, image/png, etc.).
     *
     * <p>Requirement REQ-2.12.6: Filter attachments by file type category
     *
     * @param userId User ID
     * @param fileTypePattern File type pattern with SQL wildcard (e.g., "image/%",
     *     "application/pdf")
     * @return List of attachments matching the file type pattern, ordered by upload date descending
     */
    @Query(
            "SELECT a FROM Attachment a WHERE a.userId = :userId AND a.fileType LIKE :fileTypePattern ORDER BY a.uploadedAt DESC")
    List<Attachment> findByUserIdAndFileTypeLike(
            @Param("userId") Long userId, @Param("fileTypePattern") String fileTypePattern);

    /**
     * Finds attachments larger than a specific size for a user.
     *
     * <p>Useful for identifying large files that consume significant storage space.
     *
     * <p>Requirement REQ-2.12.7: Identify large attachments for storage management
     *
     * @param userId User ID
     * @param minSize Minimum file size in bytes
     * @return List of attachments larger than minSize, ordered by file size descending
     */
    @Query(
            "SELECT a FROM Attachment a WHERE a.userId = :userId AND a.fileSize > :minSize ORDER BY a.fileSize DESC")
    List<Attachment> findLargeAttachments(
            @Param("userId") Long userId, @Param("minSize") Long minSize);

    /**
     * Finds all attachments for multiple entities of the same type.
     *
     * <p>Useful for batch operations, such as displaying attachments for a filtered list of
     * transactions.
     *
     * <p>Requirement REQ-2.12.8: Efficiently load attachments for multiple entities
     *
     * @param entityType Type of entity
     * @param entityIds List of entity IDs
     * @return List of attachments for the specified entities, ordered by entity ID and upload date
     */
    @Query(
            "SELECT a FROM Attachment a WHERE a.entityType = :entityType AND a.entityId IN :entityIds ORDER BY a.entityId, a.uploadedAt DESC")
    List<Attachment> findByEntityTypeAndEntityIdIn(
            @Param("entityType") EntityType entityType, @Param("entityIds") List<Long> entityIds);

    /**
     * Finds attachments for a specific entity belonging to a user.
     *
     * <p>Combines entity filtering with user authorization check. Ensures that only attachments for
     * entities owned by the user are returned.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access attachments for their own
     * entities
     *
     * @param userId User ID
     * @param entityType Type of entity
     * @param entityId ID of the entity
     * @return List of attachments for the entity owned by the user, ordered by upload date
     *     descending
     */
    List<Attachment> findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
            Long userId, EntityType entityType, Long entityId);

    /**
     * Counts attachments for a specific entity.
     *
     * <p>Used to display attachment count badge on entity cards or lists.
     *
     * <p>Requirement REQ-2.12.9: Display attachment count for entities
     *
     * @param entityType Type of entity
     * @param entityId ID of the entity
     * @return Count of attachments for the entity
     */
    long countByEntityTypeAndEntityId(EntityType entityType, Long entityId);
}
