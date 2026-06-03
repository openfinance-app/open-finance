package org.openfinance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.openfinance.converter.EncryptedStringConverter;

/**
 * Entity representing an AI conversation session with message history.
 *
 * <p>
 * Stores conversation messages as JSON text for flexibility in message
 * structure. Each
 * conversation belongs to a single user and tracks creation/update timestamps.
 *
 * <p>
 * <strong>Message Format (JSON):</strong>
 *
 * <pre>{@code
 * [
 *   {"role": "user", "content": "What is my net worth?", "timestamp": "2024-02-03T10:30:00"},
 *   {"role": "assistant", "content": "Your current net worth is...", "timestamp": "2024-02-03T10:30:05"}
 * ]
 * }</pre>
 *
 * @since Sprint 11 - AI Assistant Integration
 */
@Entity
@Table(name = "ai_conversations", indexes = {
        @Index(name = "idx_ai_conversation_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_conversation_created_at", columnList = "created_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIConversation {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who owns this conversation.
     *
     * <p>
     * Required field. Conversation is deleted when user is deleted (cascade).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Message history stored as JSON array.
     *
     * <p>
     * Each message contains: role (user/assistant), content, timestamp. Stored as
     * TEXT to
     * support large conversation histories.
     *
     * <p>
     * <strong>Format:</strong>
     *
     * <pre>{@code
     * [
     *   {"role": "user", "content": "question", "timestamp": "ISO-8601"},
     *   {"role": "assistant", "content": "response", "timestamp": "ISO-8601"}
     * ]
     * }</pre>
     */
    @Column(name = "messages", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String messages;

    /** Optional conversation title (auto-generated from first question). */
    @Column(name = "title", length = 512)
    @Convert(converter = EncryptedStringConverter.class)
    private String title;

    /** Timestamp when the conversation was created. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when the conversation was last updated. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Helper method to get user ID directly.
     *
     * @return User ID or null if user not set
     */
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    @Override
    public String toString() {
        return "AIConversation{"
                + "id="
                + id
                + ", userId="
                + getUserId()
                + ", title='"
                + title
                + '\''
                + ", messageCount="
                + (messages != null ? messages.split("\"role\"").length - 1 : 0)
                + ", createdAt="
                + createdAt
                + ", updatedAt="
                + updatedAt
                + '}';
    }
}
