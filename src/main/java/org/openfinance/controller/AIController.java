package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AIDto;
import org.openfinance.entity.User;
import org.openfinance.service.AIService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * REST controller for AI Assistant interactions.
 *
 * <p>
 * Provides endpoints for chatting with the AI financial advisor, managing
 * conversation history,
 * and checking service availability.
 *
 * <p>
 * <strong>Base Path:</strong> {@code /api/v1/ai}
 *
 * <p>
 * <strong>Authentication:</strong> All endpoints require JWT authentication via
 * {@code
 * Authorization: Bearer {token}} header.
 *
 * <p>
 * <strong>Encryption:</strong> Endpoints require {@code X-Encryption-Session}
 * header for decrypting
 * user's financial data to build context.
 *
 * @since Sprint 11 - AI Assistant Integration
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {
    private final AIService aiService;

    /**
     * Sends a question to the AI assistant and receives a response.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     * <li>{@code Authorization: Bearer {jwt_token}}
     * <li>{@code X-Encryption-Session: {base64_encoded_key}}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "question": "What is my current net worth?",
     * "conversation_id": null, // or existing conversation ID
     * "include_full_context": true
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "response": "Based on your accounts, your current net worth is $45,250...",
     * "conversation_id": 123,
     * "timestamp": "2026-02-03T12:00:00",
     * "token_count": 156
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     * <li>{@code 400 Bad Request} - Invalid question or missing encryption key
     * <li>{@code 401 Unauthorized} - Missing or invalid JWT token
     * <li>{@code 404 Not Found} - Conversation ID not found
     * <li>{@code 503 Service Unavailable} - Ollama service unavailable
     * </ul>
     *
     * @param request Chat request containing question and optional conversation ID
     * @param authentication Spring Security authentication
     * @return Mono with ChatResponse
     */
    @PostMapping("/chat")
    public ResponseEntity<AIDto.ChatResponse> chat(
            @Valid @RequestBody AIDto.ChatRequest request,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        log.info(
                "User {} asking AI: {}",
                user.getId(),
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        try {
            AIDto.ChatResponse response = aiService.askQuestion(user.getId(), request);
            return ResponseEntity.ok(response);
        } catch (Exception error) {
            log.error("Error processing AI chat for user {}: {}", user.getId(), error.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Streams AI response in real-time using Server-Sent Events (SSE).
     *
     * <p>
     * <strong>Recommended for better UX:</strong> Displays response as it's being
     * generated
     * instead of waiting for complete response.
     *
     * <p>
     * <strong>Request Headers:</strong>
     *
     * <ul>
     * <li>{@code Authorization: Bearer {jwt_token}}
     * <li>{@code X-Encryption-Session: {base64_encoded_key}}
     * </ul>
     *
     * <p>
     * <strong>Request Body:</strong> Same as /chat endpoint
     *
     * <p>
     * <strong>Response:</strong> Server-Sent Events stream with chunks:
     *
     * <pre>{@code
     * data: Based
     * data:  on
     * data:  your
     * data:  accounts
     * ...
     * }</pre>
     *
     * @param request        Chat request
     * @param authentication Spring Security authentication
     * @return Flux of response chunks
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @Valid @RequestBody AIDto.ChatRequest request,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        log.info(
                "User {} streaming AI question: {}",
                user.getId(),
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        return aiService
                .streamQuestion(user.getId(), request)
                .doOnError(
                        error -> log.error(
                                "Error streaming AI response for user {}: {}",
                                user.getId(),
                                error.getMessage()));
    }

    /**
     * Lists all conversations for the authenticated user.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     * <li>{@code Authorization: Bearer {jwt_token}}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     * {
     * "id": 123,
     * "title": "What is my current net worth?",
     * "message_count": 4,
     * "created_at": "2026-02-03T10:00:00",
     * "updated_at": "2026-02-03T10:05:00"
     * },
     * {
     * "id": 122,
     * "title": "How can I reduce my expenses?",
     * "message_count": 6,
     * "created_at": "2026-02-02T15:30:00",
     * "updated_at": "2026-02-02T15:40:00"
     * }
     * ]
     * }</pre>
     *
     * @param authentication Spring Security authentication
     * @return List of conversation summaries
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<AIDto.ConversationSummary>> listConversations(
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.debug("Listing conversations for user {}", user.getId());

        List<AIDto.ConversationSummary> conversations = aiService.listConversations(user.getId());
        return ResponseEntity.ok(conversations);
    }

    /**
     * Retrieves a specific conversation with full message history.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     * <li>{@code Authorization: Bearer {jwt_token}}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 123,
     * "title": "What is my current net worth?",
     * "messages": [
     * {
     * "role": "user",
     * "content": "What is my current net worth?",
     * "timestamp": "2026-02-03T10:00:00"
     * },
     * {
     * "role": "assistant",
     * "content": "Based on your accounts...",
     * "timestamp": "2026-02-03T10:00:05"
     * }
     * ],
     * "created_at": "2026-02-03T10:00:00",
     * "updated_at": "2026-02-03T10:05:00"
     * }
     * }</pre>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     * <li>{@code 404 Not Found} - Conversation not found or not owned by user
     * </ul>
     *
     * @param conversationId Conversation ID
     * @param authentication Spring Security authentication
     * @return Conversation detail with messages
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<AIDto.ConversationDetail> getConversation(
            @PathVariable("id") Long conversationId, Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.debug("Fetching conversation {} for user {}", conversationId, user.getId());

        AIDto.ConversationDetail conversation = aiService.getConversation(user.getId(), conversationId);
        return ResponseEntity.ok(conversation);
    }

    /**
     * Deletes a conversation.
     *
     * <p>
     * <strong>Request Headers:</strong>
     *
     * <ul>
     * <li>{@code Authorization: Bearer {jwt_token}}
     * </ul>
     *
     * <p>
     * <strong>Success Response (HTTP 204 No Content):</strong>
     *
     * <p>
     * Empty body, conversation successfully deleted.
     *
     * <p>
     * <strong>Error Responses:</strong>
     *
     * <ul>
     * <li>{@code 404 Not Found} - Conversation not found or not owned by user
     * </ul>
     *
     * @param conversationId Conversation ID to delete
     * @param authentication Spring Security authentication
     * @return Empty response with HTTP 204
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable("id") Long conversationId, Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        log.info("Deleting conversation {} for user {}", conversationId, user.getId());

        aiService.deleteConversation(user.getId(), conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Checks if Ollama AI service is available.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     * <li>{@code Authorization: Bearer {jwt_token}}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "available": true
     * }
     * }</pre>
     *
     * <p>Use this endpoint to check if the AI assistant is operational before
     * sending chat
     * requests. Display a warning in UI if unavailable.
     *
     * @return Mono with availability status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Boolean>> checkHealth() {
        log.debug("Checking AI provider health");
        try {
            boolean available = aiService.isAIProviderAvailable();
            return ResponseEntity.ok(Map.of("available", available));
        } catch (Exception e) {
            log.debug("AI provider health check failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("available", false));
        }
    }
}
