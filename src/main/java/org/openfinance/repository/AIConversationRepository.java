package org.openfinance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.AIConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing AI conversation data.
 *
 * <p>Provides methods to query, persist, and delete conversation records. All queries are scoped to
 * user ID to ensure data isolation.
 *
 * @since Sprint 11 - AI Assistant Integration
 */
@Repository
public interface AIConversationRepository extends JpaRepository<AIConversation, Long> {

    /**
     * Finds all conversations for a specific user, ordered by creation date (newest first).
     *
     * @param userId User ID to filter by
     * @return List of conversations ordered by created_at DESC
     */
    List<AIConversation> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * Finds conversations for a specific user created after a given timestamp.
     *
     * <p>Useful for retrieving recent conversations (e.g., last 7 days).
     *
     * @param userId User ID to filter by
     * @param after Timestamp to filter conversations created after
     * @return List of conversations created after the specified timestamp
     */
    List<AIConversation> findByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime after);

    /**
     * Finds a specific conversation by ID and user ID.
     *
     * <p>Ensures users can only access their own conversations.
     *
     * @param id Conversation ID
     * @param userId User ID to verify ownership
     * @return Optional containing the conversation if found and owned by user
     */
    Optional<AIConversation> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Deletes all conversations for a user except the specified one.
     *
     * <p>Used to implement "keep only current conversation" functionality.
     *
     * @param userId User ID whose conversations should be deleted
     * @param conversationId ID of conversation to keep
     */
    @Modifying
    @Query("DELETE FROM AIConversation c WHERE c.user.id = :userId AND c.id != :conversationId")
    void deleteByUserIdAndIdNot(
            @Param("userId") Long userId, @Param("conversationId") Long conversationId);

    /**
     * Deletes all conversations for a specific user.
     *
     * @param userId User ID whose conversations should be deleted
     */
    @Modifying
    @Query("DELETE FROM AIConversation c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Counts the total number of conversations for a user.
     *
     * @param userId User ID to count conversations for
     * @return Total count of conversations
     */
    long countByUser_Id(Long userId);

    /**
     * Finds the most recent conversation for a user.
     *
     * @param userId User ID to find conversation for
     * @return Optional containing the most recent conversation if exists
     */
    @Query(
            "SELECT c FROM AIConversation c WHERE c.user.id = :userId ORDER BY c.updatedAt DESC LIMIT 1")
    Optional<AIConversation> findMostRecentByUserId(@Param("userId") Long userId);

    /**
     * Deletes old conversations created before a specific date.
     *
     * <p>Used for cleanup/maintenance of old conversation data.
     *
     * @param before Timestamp - conversations created before this will be deleted
     * @return Number of conversations deleted
     */
    @Modifying
    @Query("DELETE FROM AIConversation c WHERE c.createdAt < :before")
    int deleteOldConversations(@Param("before") LocalDateTime before);
}
