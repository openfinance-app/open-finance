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
import org.openfinance.service.ai.AIContextBudget;
import org.openfinance.service.ai.AIProvider;
import org.openfinance.service.ai.AIRequestLimits;
import org.openfinance.service.ai.FinancialContextBuilder;
import org.openfinance.service.ai.FinancialResponseGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/**
 * Service for managing AI assistant interactions and conversations.
 *
 * <p>Orchestrates the AI assistant workflow:
 *
 * <ol>
 *   <li>Load or create conversation
 *   <li>Build financial context from user data
 *   <li>Call Ollama with context + question + history
 *   <li>Save conversation messages
 *   <li>Return formatted response
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
    private final AIConversationWriter conversationWriter;
    private final AIRequestLimits requestLimits;
    private final AIContextBudget contextBudget;
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
     * <p><strong>Workflow:</strong>
     *
     * <ol>
     *   <li>Load conversation (or create new)
     *   <li>Build financial context
     *   <li>Load conversation history (last N messages)
     *   <li>Call Ollama with context + history + question
     *   <li>Save user message and AI response
     *   <li>Return formatted response DTO
     * </ol>
     *
     * @param userId User ID making the request
     * @param request Chat request containing question and optional conversation ID
     * @return Mono emitting ChatResponse with AI's answer
     * @throws ResourceNotFoundException if conversation not found or not owned by user
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AIDto.ChatResponse askQuestion(Long userId, AIDto.ChatRequest request) {
        log.info(
                "Processing AI request for user {} (conversation: {}, question length: {})",
                userId,
                request.getConversationId(),
                request.getQuestion().length());

        long deadline = requestLimits.deadline();

        // 0. Resolve user locale
        Locale locale = resolveUserLocale(userId);

        // 1. Load or create conversation
        AIConversation conversation = loadOrCreateConversation(userId, request.getConversationId());

        // 2. Build financial context with locale
        String context =
                Boolean.TRUE.equals(request.getIncludeFullContext())
                        ? contextBuilder.buildContext(userId, locale)
                        : contextBuilder.buildMinimalContext(userId, locale);

        List<AIDto.Message> history = parseMessages(conversation.getMessages());
        context =
                contextBuilder.forQuestion(
                        userId,
                        locale,
                        request.getQuestion(),
                        context,
                        history.stream()
                                .filter(message -> "user".equals(message.getRole()))
                                .map(AIDto.Message::getContent)
                                .toList());

        String clarification =
                context.startsWith("[CLARIFICATION] ")
                        ? context.substring("[CLARIFICATION] ".length())
                        : null;

        // 2a. Add language instruction for non-English locales
        String languageInstruction = buildLanguageInstruction(locale);
        String fullContext =
                contextBudget.compose(
                        request.getQuestion(),
                        languageInstruction.isEmpty()
                                ? context
                                : languageInstruction + "\n\n" + context,
                        history,
                        maxHistoryMessages);

        // 3. Call AI provider (block on the reactive call to stay on the servlet
        // thread). Safe re: SecurityContextHolder: userId/locale/context are all resolved
        // above on this servlet thread *before* the reactive chain runs, and no provider
        // implementation reads SecurityContextHolder inside a Mono/Flux operator. If that
        // ever changes, don't rely on ThreadLocal SecurityContext propagating onto the
        // WebClient's Netty event-loop threads — pass the needed value in explicitly instead.
        String aiResponse;
        try {
            aiResponse =
                    clarification != null
                            ? clarification
                            : verifiedAnswer(request.getQuestion(), fullContext, locale, deadline);
        } catch (RuntimeException ex) {
            throw ex instanceof org.openfinance.service.ai.AIProviderException providerError
                    ? providerError
                    : new org.openfinance.service.ai.AIProviderException(
                            aiProvider.getProviderName(), "AI request failed", ex);
        }

        // 4. Save conversation messages
        saveConversationMessages(conversation, request.getQuestion(), aiResponse);

        // 5. Generate title if first message
        if (conversation.getTitle() == null) {
            conversation.setTitle(generateConversationTitle(request.getQuestion()));
        }
        conversation = conversationWriter.save(conversation);

        // 6. Return formatted response
        return buildChatResponse(conversation, aiResponse);
    }

    private String verifiedAnswer(String question, String context, Locale locale, long deadline) {
        com.fasterxml.jackson.databind.JsonNode schema =
                FinancialResponseGuard.responseSchema(context);
        String instructions = context;
        for (int attempt = 0; attempt < 2; attempt++) {
            String response =
                    aiProvider
                            .sendStructuredPrompt(question, instructions, schema)
                            .block(requestLimits.remaining(deadline));
            try {
                return FinancialResponseGuard.verifyRequestedFacts(response, context, locale);
            } catch (org.openfinance.service.ai.AIProviderException invalid) {
                if (attempt == 1) throw invalid;
                instructions =
                        context
                                + "\nYour previous answer did not satisfy the contract. Keep explanation qualitative: no digits, monetary amounts, currencies, or numbers spelled out. Select the relevant factIds for numeric answers; the application will display their exact values. Return JSON only.";
            }
        }
        throw new org.openfinance.service.ai.AIProviderException(
                aiProvider.getProviderName(), "Unverified answer");
    }

    /** Buffered compatibility API. Resolve and persist on the authenticated servlet thread. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Flux<String> streamQuestion(Long userId, AIDto.ChatRequest request) {
        return Flux.just(askQuestion(userId, request).getResponse());
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

        List<AIConversation> conversations =
                conversationRepository.findByUser_IdOrderByCreatedAtDesc(userId);

        return conversations.stream().map(this::toConversationSummary).collect(Collectors.toList());
    }

    /**
     * Retrieves a specific conversation with full message history.
     *
     * @param userId User ID (for ownership verification)
     * @param conversationId Conversation ID
     * @return Conversation detail with messages
     * @throws ResourceNotFoundException if conversation not found or not owned by user
     */
    @Transactional(readOnly = true)
    public AIDto.ConversationDetail getConversation(Long userId, Long conversationId) {
        log.debug("Fetching conversation {} for user {}", conversationId, userId);

        AIConversation conversation =
                conversationRepository
                        .findByIdAndUser_Id(conversationId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Conversation not found: " + conversationId));

        return toConversationDetail(conversation);
    }

    /**
     * Deletes a conversation.
     *
     * @param userId User ID (for ownership verification)
     * @param conversationId Conversation ID to delete
     * @throws ResourceNotFoundException if conversation not found or not owned by user
     */
    public void deleteConversation(Long userId, Long conversationId) {
        log.info("Deleting conversation {} for user {}", conversationId, userId);

        AIConversation conversation =
                conversationRepository
                        .findByIdAndUser_Id(conversationId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Conversation not found: " + conversationId));

        conversationRepository.delete(conversation);
    }

    /**
     * Checks if the AI provider service is available.
     *
     * @return true if available, false otherwise
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean isAIProviderAvailable() {
        // Same SecurityContextHolder caveat as askQuestion() above: no reactive operator here
        // reads SecurityContextHolder, so blocking on the servlet thread is safe.
        Boolean result = aiProvider.isAvailable().block(java.time.Duration.ofSeconds(4));
        return Boolean.TRUE.equals(result);
    }

    // ===========================
    // Private Helper Methods
    // ===========================

    private AIConversation loadOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            AIConversation existing =
                    conversationRepository
                            .findByIdAndUser_Id(conversationId, userId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Conversation not found: " + conversationId));
            // Do not mutate an entity retained by OpenEntityManagerInView across inference.
            return AIConversation.builder()
                    .id(existing.getId())
                    .version(existing.getVersion())
                    .user(existing.getUser())
                    .messages(existing.getMessages())
                    .title(existing.getTitle())
                    .createdAt(existing.getCreatedAt())
                    .updatedAt(existing.getUpdatedAt())
                    .build();
        } else {
            // Create new conversation
            User user =
                    userRepository
                            .findById(userId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "User not found: " + userId));

            AIConversation conversation =
                    AIConversation.builder().user(user).messages("[]").build();

            return conversation;
        }
    }

    private void saveConversationMessages(
            AIConversation conversation, String question, String response) {
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

            // Save back to conversation
            conversation.setMessages(objectMapper.writeValueAsString(messages));

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
                    messagesJson, new TypeReference<List<AIDto.Message>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read saved conversation history", e);
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
     * Resolves user's preferred locale from UserSettings. Falls back to English if not found.
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
     * Builds language instruction for non-English locales. Returns empty string for English.
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
                new Object[] {languageName},
                "Important: Please respond in " + languageName,
                locale);
    }
}
