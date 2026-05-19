package org.openfinance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for AI chat requests and responses.
 *
 * @since Sprint 11 - AI Assistant Integration
 */
public class AIDto {

    /** Request DTO for sending a message to the AI assistant. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {

        /** User's question or message to the AI. */
        @NotBlank(message = "{ai.question.blank}")
        @Size(min = 3, max = 2000, message = "{ai.question.size}")
        private String question;

        /**
         * Optional conversation ID to continue an existing conversation. If null, a new
         * conversation will be created.
         */
        @JsonProperty("conversation_id")
        private Long conversationId;

        /**
         * Whether to include full context (accounts, transactions, etc). If false, only minimal
         * context (net worth, account summary) is included.
         */
        @Builder.Default
        @JsonProperty("include_full_context")
        private Boolean includeFullContext = true;
    }

    /** Response DTO containing AI assistant's reply. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponse {

        /** AI's response text. */
        private String response;

        /** Conversation ID for this exchange. */
        @JsonProperty("conversation_id")
        private Long conversationId;

        /** Timestamp when response was generated. */
        private LocalDateTime timestamp;

        /** Token count (approximate) for context and response. */
        @JsonProperty("token_count")
        private Integer tokenCount;
    }

    /** DTO representing a single conversation summary. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationSummary {

        /** Conversation ID. */
        private Long id;

        /** Conversation title (first question or auto-generated). */
        private String title;

        /** Number of messages in conversation. */
        @JsonProperty("message_count")
        private Integer messageCount;

        /** When conversation was created. */
        @JsonProperty("created_at")
        private LocalDateTime createdAt;

        /** When conversation was last updated. */
        @JsonProperty("updated_at")
        private LocalDateTime updatedAt;
    }

    /** DTO representing a full conversation with messages. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationDetail {

        /** Conversation ID. */
        private Long id;

        /** Conversation title. */
        private String title;

        /** List of messages in chronological order. */
        private List<Message> messages;

        /** When conversation was created. */
        @JsonProperty("created_at")
        private LocalDateTime createdAt;

        /** When conversation was last updated. */
        @JsonProperty("updated_at")
        private LocalDateTime updatedAt;
    }

    /** DTO representing a single message in a conversation. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {

        /** Message role: "user" or "assistant". */
        private String role;

        /** Message content/text. */
        private String content;

        /** When message was sent/received. */
        private LocalDateTime timestamp;
    }
}
