package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AIDto;
import org.openfinance.entity.AIConversation;
import org.openfinance.entity.User;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AIConversationRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.ai.AIProvider;
import org.openfinance.service.ai.FinancialContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/**
 * Service for managing AI assistant interactions and conversations.
 *
 * <p>
 * Orchestrates the AI assistant workflow:
 *
 * <ol>
 * <li>Load or create conversation
 * <li>Build financial context from user data
 * <li>Call Ollama with context + question + history
 * <li>Save conversation messages
 * <li>Return formatted response
 * </ol>
 *
 * @since Sprint 11 - AI Assistant Integration
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AIService {

        private final AIProvider aiProvider;
        private final FinancialContextBuilder contextBuilder;
        private final AIConversationRepository conversationRepository;
        private final UserRepository userRepository;
        private final UserSettingsRepository userSettingsRepository;
        private final ObjectMapper objectMapper;
        private final MessageSource messageSource;

        @Value("${application.ai.ollama.max-history-messages:10}")
        private int maxHistoryMessages;

        /**
         * Sends a question to the AI assistant and returns a response.
         *
         * <p>
         * <strong>Workflow:</strong>
         *
         * <ol>
         * <li>Load conversation (or create new)
         * <li>Build financial context
         * <li>Load conversation history (last N messages)
         * <li>Call Ollama with context + history + question
         * <li>Save user message and AI response
         * <li>Return formatted response DTO
         * </ol>
         *
         * @param userId        User ID making the request
         * @param request       Chat request containing question and optional
         *                      conversation ID
         * @param encryptionKey User's encryption key for decrypting financial data
         * @return Mono emitting ChatResponse with AI's answer
         * @throws ResourceNotFoundException if conversation not found or not owned by
         *                                   user
         */
        public AIDto.ChatResponse askQuestion(
                        Long userId, AIDto.ChatRequest request) {
                log.info(
                                "Processing AI question for user {}: {} (conversation: {})",
                                userId,
                                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())),
                                request.getConversationId());

                // 0. Resolve user locale
                Locale locale = resolveUserLocale(userId);

                // 1. Load or create conversation
                AIConversation conversation = loadOrCreateConversation(userId, request.getConversationId());

                // 2. Build financial context with locale
                String context = request.getIncludeFullContext()
                                ? contextBuilder.buildContext(userId, locale)
                                : contextBuilder.buildMinimalContext(userId, locale);

                // 2a. Add language instruction for non-English locales
                String languageInstruction = buildLanguageInstruction(locale);
                String fullContext = languageInstruction.isEmpty() ? context : languageInstruction + "\n\n" + context;

                // 3. Call AI provider (block on the reactive call to stay on the servlet
                // thread)
                String aiResponse = aiProvider.sendPrompt(request.getQuestion(), fullContext).block();

                // 4. Save conversation messages
                saveConversationMessages(conversation, request.getQuestion(), aiResponse);

                // 5. Generate title if first message
                if (conversation.getTitle() == null) {
                        conversation.setTitle(generateConversationTitle(request.getQuestion()));
                        conversationRepository.save(conversation);
                }

                // 6. Return formatted response
                return AIDto.ChatResponse.builder()
                                .conversationId(conversation.getId())
                                .response(aiResponse)
                                .timestamp(LocalDateTime.now())
                                .build();
        }

        /**
         * Streams AI response in real-time as it's generated.
         *
         * <p>
         * Use this for better UX - displays response as it's being generated instead of
         * waiting for
         * complete response.
         *
         * @param userId        User ID
         * @param request       Chat request
         * @param encryptionKey Encryption key
         * @return Flux emitting response chunks
         */
        public Flux<String> streamQuestion(
                        Long userId, AIDto.ChatRequest request) {
                log.info(
                                "Streaming AI question for user {}: {}",
                                userId,
                                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

                // Resolve user locale
                Locale locale = resolveUserLocale(userId);

                AIConversation conversation = loadOrCreateConversation(userId, request.getConversationId());

                // Build context with locale
                String context = request.getIncludeFullContext()
                                ? contextBuilder.buildContext(userId, locale)
                                : contextBuilder.buildMinimalContext(userId, locale);

                // Add language instruction for non-English locales
                String languageInstruction = buildLanguageInstruction(locale);
                String fullContext = languageInstruction.isEmpty() ? context : languageInstruction + "\n\n" + context;

                // Collect response chunks to save complete response
                List<String> chunks = new ArrayList<>();

                return aiProvider
                                .streamResponse(request.getQuestion(), fullContext)
                                .doOnNext(chunks::add)
                                .doOnComplete(
                                                () -> {
                                                        String fullResponse = String.join("", chunks);
                                                        saveConversationMessages(
                                                                        conversation,
                                                                        request.getQuestion(),
                                                                        fullResponse);

                                                        if (conversation.getTitle() == null) {
                                                                conversation.setTitle(
                                                                                generateConversationTitle(
                                                                                                request.getQuestion()));
                                                                conversationRepository.save(conversation);
                                                        }
                                                });
        }

        /**
         * Retrieves all conversations for a user.
         *
         * @param userId User ID
         * @return List of conversation summaries
         */
        @Transactional(readOnly = true)
        public List<AIDto.ConversationSummary> listConversations(Long userId) {
                log.debug("Listing conversations for user {}", userId);

                List<AIConversation> conversations = conversationRepository.findByUser_IdOrderByCreatedAtDesc(userId);

                return conversations.stream().map(this::toConversationSummary).collect(Collectors.toList());
        }

        /**
         * Retrieves a specific conversation with full message history.
         *
         * @param userId         User ID (for ownership verification)
         * @param conversationId Conversation ID
         * @return Conversation detail with messages
         * @throws ResourceNotFoundException if conversation not found or not owned by
         *                                   user
         */
        @Transactional(readOnly = true)
        public AIDto.ConversationDetail getConversation(Long userId, Long conversationId) {
                log.debug("Fetching conversation {} for user {}", conversationId, userId);

                AIConversation conversation = conversationRepository
                                .findByIdAndUser_Id(conversationId, userId)
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "Conversation not found: " + conversationId));

                return toConversationDetail(conversation);
        }

        /**
         * Deletes a conversation.
         *
         * @param userId         User ID (for ownership verification)
         * @param conversationId Conversation ID to delete
         * @throws ResourceNotFoundException if conversation not found or not owned by
         *                                   user
         */
        public void deleteConversation(Long userId, Long conversationId) {
                log.info("Deleting conversation {} for user {}", conversationId, userId);

                AIConversation conversation = conversationRepository
                                .findByIdAndUser_Id(conversationId, userId)
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "Conversation not found: " + conversationId));

                conversationRepository.delete(conversation);
        }

        /**
         * Checks if the AI provider service is available.
         *
         * @return true if available, false otherwise
         */
        public boolean isAIProviderAvailable() {
                Boolean result = aiProvider.isAvailable().block();
                return Boolean.TRUE.equals(result);
        }

        // ===========================
        // Private Helper Methods
        // ===========================

        private AIConversation loadOrCreateConversation(Long userId, Long conversationId) {
                if (conversationId != null) {
                        return conversationRepository
                                        .findByIdAndUser_Id(conversationId, userId)
                                        .orElseThrow(
                                                        () -> new ResourceNotFoundException(
                                                                        "Conversation not found: " + conversationId));
                } else {
                        // Create new conversation
                        User user = userRepository
                                        .findById(userId)
                                        .orElseThrow(
                                                        () -> new ResourceNotFoundException(
                                                                        "User not found: " + userId));

                        AIConversation conversation = AIConversation.builder().user(user).messages("[]").build();

                        return conversationRepository.save(conversation);
                }
        }

        private void saveConversationMessages(
                        AIConversation conversation,
                        String question,
                        String response) {
                try {
                        // Parse existing messages
                        List<AIDto.Message> messages = parseMessages(conversation.getMessages());

                        // Add user message
                        messages.add(
                                        AIDto.Message.builder()
                                                        .role("user")
                                                        .content(question)
                                                        .timestamp(LocalDateTime.now())
                                                        .build());

                        // Add assistant message
                        messages.add(
                                        AIDto.Message.builder()
                                                        .role("assistant")
                                                        .content(response)
                                                        .timestamp(LocalDateTime.now())
                                                        .build());

                        // Limit to last N messages to avoid token limits
                        if (messages.size() > maxHistoryMessages * 2) { // *2 because each exchange is 2 messages
                                messages = messages.subList(
                                                messages.size() - (maxHistoryMessages * 2), messages.size());
                        }

                        // Save back to conversation
                        conversation.setMessages(objectMapper.writeValueAsString(messages));
                        conversationRepository.save(conversation);

                } catch (JsonProcessingException e) {
                        log.error("Failed to save conversation messages: {}", e.getMessage());
                        throw new RuntimeException("Failed to save conversation", e);
                }
        }

        private List<AIDto.Message> parseMessages(String messagesJson) {
                try {
                        if (messagesJson == null
                                        || messagesJson.trim().isEmpty()
                                        || messagesJson.equals("[]")) {
                                return new ArrayList<>();
                        }
                        return objectMapper.readValue(
                                        messagesJson, new TypeReference<List<AIDto.Message>>() {
                                        });
                } catch (JsonProcessingException e) {
                        log.error("Failed to parse messages JSON: {}", e.getMessage());
                        return new ArrayList<>();
                }
        }

        private String generateConversationTitle(String firstQuestion) {
                // Generate title from first question (max 200 chars)
                String title = firstQuestion.trim();
                if (title.length() > 200) {
                        title = title.substring(0, 197) + "...";
                }
                return title;
        }

        private AIDto.ChatResponse buildChatResponse(AIConversation conversation, String response) {
                return AIDto.ChatResponse.builder()
                                .response(response)
                                .conversationId(conversation.getId())
                                .timestamp(LocalDateTime.now())
                                .tokenCount(estimateTokenCount(response))
                                .build();
        }

        private AIDto.ConversationSummary toConversationSummary(AIConversation conversation) {
                List<AIDto.Message> messages = parseMessages(conversation.getMessages());

                return AIDto.ConversationSummary.builder()
                                .id(conversation.getId())
                                .title(conversation.getTitle())
                                .messageCount(messages.size())
                                .createdAt(conversation.getCreatedAt())
                                .updatedAt(conversation.getUpdatedAt())
                                .build();
        }

        private AIDto.ConversationDetail toConversationDetail(AIConversation conversation) {
                List<AIDto.Message> messages = parseMessages(conversation.getMessages());

                return AIDto.ConversationDetail.builder()
                                .id(conversation.getId())
                                .title(conversation.getTitle())
                                .messages(messages)
                                .createdAt(conversation.getCreatedAt())
                                .updatedAt(conversation.getUpdatedAt())
                                .build();
        }

        private Integer estimateTokenCount(String text) {
                // Rough estimation: ~4 characters per token
                return text.length() / 4;
        }

        /**
         * Resolves user's preferred locale from UserSettings. Falls back to English if
         * not found.
         *
         * @param userId User ID
         * @return User's locale or English as fallback
         */
        private Locale resolveUserLocale(Long userId) {
                return userSettingsRepository
                                .findByUserId(userId)
                                .map(
                                                settings -> {
                                                        String lang = settings.getLanguage();
                                                        return new Locale(lang != null ? lang : "en");
                                                })
                                .orElse(Locale.ENGLISH);
        }

        /**
         * Builds language instruction for non-English locales. Returns empty string for
         * English.
         *
         * @param locale Target locale
         * @return Language instruction or empty string for English
         */
        private String buildLanguageInstruction(Locale locale) {
                if (locale.getLanguage().equals("en")) {
                        return "";
                }
                String languageName = locale.getDisplayLanguage(locale);
                return messageSource.getMessage(
                                "ai.language.instruction",
                                new Object[] { languageName },
                                "Important: Please respond in " + languageName,
                                locale);
        }
}
