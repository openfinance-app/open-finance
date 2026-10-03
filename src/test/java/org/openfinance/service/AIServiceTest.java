package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AIDto;
import org.openfinance.entity.AIConversation;
import org.openfinance.entity.User;
import org.openfinance.entity.UserSettings;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AIConversationRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.ai.AIProvider;
import org.openfinance.service.ai.FinancialContextBuilder;
import org.springframework.context.MessageSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for AIService Task 11.1.7c: Write AIService unit tests
 *
 * <p>Tests cover: - askQuestion with new and existing conversations - streamQuestion with real-time
 * response - listConversations - getConversation - deleteConversation - isAvailable health check -
 * Error handling and edge cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIService Tests")
class AIServiceTest {

    @Mock private AIProvider aiProvider;
    @Mock private AIConversationWriter conversationWriter;

    @org.mockito.Spy
    private org.openfinance.service.ai.AIRequestLimits requestLimits =
            new org.openfinance.service.ai.AIRequestLimits();

    private org.openfinance.service.ai.AIContextBudget contextBudget;

    @Mock private FinancialContextBuilder contextBuilder;

    @Mock private AIConversationRepository conversationRepository;

    @Mock private UserRepository userRepository;

    @Mock private UserSettingsRepository userSettingsRepository;

    @Mock private MessageSource messageSource;

    @org.mockito.Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private AIService aiService;

    private Long userId;
    private User testUser;

    private String jsonAnswer(String text) {
        com.fasterxml.jackson.databind.node.ObjectNode answer =
                new ObjectMapper().createObjectNode();
        answer.put("explanation", text);
        answer.putArray("factIds");
        return answer.toString();
    }

    @Test
    void retriesAnInvalidClaimAndSavesOnlyTheVerifiedAnswer() {
        when(contextBuilder.buildMinimalContext(eq(userId), any(Locale.class)))
                .thenReturn("Current finances");
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(
                        Mono.just(jsonAnswer("You have 999 euros.")),
                        Mono.just(jsonAnswer("One practical step is to review your budget.")));
        AIDto.ChatResponse response =
                aiService.askQuestion(
                        userId,
                        AIDto.ChatRequest.builder()
                                .question("Help me budget")
                                .includeFullContext(false)
                                .build());
        assertThat(response.getResponse())
                .isEqualTo("One practical step is to review your budget.");
        verify(aiProvider, times(2)).sendStructuredPrompt(anyString(), anyString(), any());
        ArgumentCaptor<AIConversation> saved = ArgumentCaptor.forClass(AIConversation.class);
        verify(conversationWriter).save(saved.capture());
        assertThat(saved.getValue().getMessages())
                .doesNotContain("999")
                .contains("One practical step");
    }

    @Test
    void neverPersistsAnUnverifiedAnswerAsSuccess() {
        when(contextBuilder.buildMinimalContext(eq(userId), any(Locale.class)))
                .thenReturn("Current finances");
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just(jsonAnswer("You have 999 euros.")));
        assertThatThrownBy(
                        () ->
                                aiService.askQuestion(
                                        userId,
                                        AIDto.ChatRequest.builder()
                                                .question("Help me budget")
                                                .includeFullContext(false)
                                                .build()))
                .isInstanceOf(org.openfinance.service.ai.AIProviderException.class);
        verifyNoInteractions(conversationWriter);
    }

    @BeforeEach
    void setUp() {
        userId = 1L;
        lenient()
                .when(
                        contextBuilder.forQuestion(
                                anyLong(), any(Locale.class), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        contextBudget =
                new org.openfinance.service.ai.AIContextBudget(
                        aiProvider, new ObjectMapper().findAndRegisterModules());
        org.springframework.test.util.ReflectionTestUtils.setField(
                aiService, "contextBudget", contextBudget);
        lenient()
                .when(conversationWriter.save(any()))
                .thenAnswer(
                        invocation -> {
                            AIConversation conversation = invocation.getArgument(0);
                            if (conversation != null && conversation.getId() == null)
                                conversation.setId(1L);
                            return conversation;
                        });

        testUser = User.builder().id(userId).email("test@example.com").username("testuser").build();

        // Mock userRepository to return test user (needed for new conversation
        // creation)
        // Use lenient() because not all tests create new conversations
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
    }

    @Test
    void shouldSendBothRolesFromSavedConversationToProvider() {
        ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();
        org.springframework.test.util.ReflectionTestUtils.setField(
                aiService, "objectMapper", realMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(
                aiService, "maxHistoryMessages", 2);
        AIConversation conversation =
                AIConversation.builder()
                        .id(42L)
                        .user(testUser)
                        .title("Budget")
                        .messages(
                                "[{\"role\":\"user\",\"content\":\"My target is 500\"},{\"role\":\"assistant\",\"content\":\"We can use that target\"}]")
                        .build();
        when(conversationRepository.findByIdAndUser_Id(42L, userId))
                .thenReturn(Optional.of(conversation));
        when(contextBuilder.buildMinimalContext(eq(userId), any(Locale.class)))
                .thenReturn("Current finances");
        when(aiProvider.sendStructuredPrompt(eq("What was my target?"), anyString(), any()))
                .thenReturn(Mono.just(jsonAnswer("We can discuss your previous target.")));
        aiService.askQuestion(
                userId,
                AIDto.ChatRequest.builder()
                        .conversationId(42L)
                        .question("What was my target?")
                        .includeFullContext(false)
                        .build());
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(aiProvider)
                .sendStructuredPrompt(eq("What was my target?"), context.capture(), any());
        assertThat(context.getValue())
                .contains(
                        "My target is 500",
                        "We can use that target",
                        "\"role\":\"user\"",
                        "\"role\":\"assistant\"");
    }

    @Test
    void retainsAllTwelveExchangesWhileOnlySendingRecentHistory() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(
                aiService, "maxHistoryMessages", 1);
        java.util.concurrent.atomic.AtomicReference<AIConversation> stored =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(conversationWriter.save(any()))
                .thenAnswer(
                        inv -> {
                            AIConversation conversation = inv.getArgument(0);
                            conversation.setId(42L);
                            stored.set(conversation);
                            return conversation;
                        });
        when(conversationRepository.findByIdAndUser_Id(42L, userId))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(contextBuilder.buildMinimalContext(eq(userId), any(Locale.class)))
                .thenReturn("Context");
        when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(Mono.just(jsonAnswer("Acknowledged")));
        for (int turn = 0; turn < 12; turn++) {
            aiService.askQuestion(
                    userId,
                    AIDto.ChatRequest.builder()
                            .question("Turn " + turn)
                            .conversationId(turn == 0 ? null : 42L)
                            .includeFullContext(false)
                            .build());
        }
        assertThat(aiService.getConversation(userId, 42L).getMessages()).hasSize(24);
        assertThat(aiService.getConversation(userId, 42L).getMessages().getFirst().getContent())
                .isEqualTo("Turn 0");
        ArgumentCaptor<String> contexts = ArgumentCaptor.forClass(String.class);
        verify(aiProvider, times(12)).sendStructuredPrompt(anyString(), contexts.capture(), any());
        assertThat(contexts.getAllValues().getLast()).contains("Turn 10").doesNotContain("Turn 0");
    }

    @Nested
    @DisplayName("askQuestion Tests")
    class AskQuestionTests {

        @Test
        @DisplayName("should ask question with new conversation")
        void shouldAskQuestionWithNewConversation() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("What is my net worth?")
                            .includeFullContext(true)
                            .conversationId(null)
                            .build();

            String context = "=== FINANCIAL SUMMARY ===\nNet Worth: $45,000.00";
            String aiResponse = "Here is your financial summary.";

            when(contextBuilder.buildContext(eq(userId), any(Locale.class))).thenReturn(context);
            when(aiProvider.sendStructuredPrompt(
                            eq("What is my net worth?"), eq(context + "\n"), any()))
                    .thenReturn(Mono.just(jsonAnswer(aiResponse)));
            when(conversationWriter.save(any(AIConversation.class)))
                    .thenAnswer(
                            invocation -> {
                                AIConversation conv = invocation.getArgument(0);
                                conv.setId(1L);
                                return conv;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getResponse()).isEqualTo(aiResponse);
            assertThat(response.getConversationId()).isNotNull();

            verify(contextBuilder).buildContext(eq(userId), any(Locale.class));
            verify(aiProvider)
                    .sendStructuredPrompt(eq("What is my net worth?"), eq(context + "\n"), any());
            verify(conversationWriter, atLeastOnce()).save(any(AIConversation.class));
        }

        @Test
        @DisplayName("should ask question with existing conversation")
        void shouldAskQuestionWithExistingConversation() {
            // Given
            Long conversationId = 1L;
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("Show me my expenses")
                            .includeFullContext(false)
                            .conversationId(conversationId)
                            .build();

            AIConversation existingConversation =
                    createConversation(conversationId, userId, "My Finances");
            String context = "=== QUICK SUMMARY ===\nNet Worth: $45,000";
            String aiResponse = "Here are your expenses...";

            when(conversationRepository.findByIdAndUser_Id(conversationId, userId))
                    .thenReturn(Optional.of(existingConversation));
            when(contextBuilder.buildMinimalContext(eq(userId), any(Locale.class)))
                    .thenReturn(context);
            when(aiProvider.sendStructuredPrompt(
                            eq("Show me my expenses"), eq(context + "\n"), any()))
                    .thenReturn(Mono.just(jsonAnswer(aiResponse)));

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getResponse()).isEqualTo(aiResponse);
            assertThat(response.getConversationId()).isEqualTo(conversationId);

            verify(contextBuilder).buildMinimalContext(eq(userId), any(Locale.class));
            verify(aiProvider)
                    .sendStructuredPrompt(eq("Show me my expenses"), eq(context + "\n"), any());
        }

        @Test
        @DisplayName("should handle Ollama error gracefully")
        void shouldHandleOllamaError() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("Test question")
                            .includeFullContext(true)
                            .build();

            when(contextBuilder.buildContext(eq(userId), any(Locale.class))).thenReturn("context");
            when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Ollama service unavailable")));

            // When / Then
            assertThatThrownBy(() -> aiService.askQuestion(userId, request))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should generate conversation title for first message")
        void shouldGenerateConversationTitleForFirstMessage() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("What are my investment options for retirement planning?")
                            .includeFullContext(true)
                            .build();

            when(contextBuilder.buildContext(eq(userId), any(Locale.class))).thenReturn("context");
            when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                    .thenReturn(Mono.just(jsonAnswer("Response")));

            ArgumentCaptor<AIConversation> conversationCaptor =
                    ArgumentCaptor.forClass(AIConversation.class);
            when(conversationWriter.save(conversationCaptor.capture()))
                    .thenAnswer(
                            invocation -> {
                                AIConversation conv = invocation.getArgument(0);
                                conv.setId(1L);
                                return conv;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response).isNotNull();

            List<AIConversation> savedConversations = conversationCaptor.getAllValues();
            assertThat(savedConversations).isNotEmpty();

            // Find the conversation with a title (final save after title generation)
            AIConversation conversationWithTitle =
                    savedConversations.stream()
                            .filter(c -> c.getTitle() != null)
                            .findFirst()
                            .orElse(null);

            assertThat(conversationWithTitle).isNotNull();
            assertThat(conversationWithTitle.getTitle()).isNotEmpty();
            assertThat(conversationWithTitle.getTitle().length()).isLessThanOrEqualTo(100);
        }
    }

    @Nested
    @DisplayName("streamQuestion Tests")
    class StreamQuestionTests {

        @Test
        @DisplayName("should stream response chunks")
        void shouldStreamResponseChunks() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("Explain my budget")
                            .includeFullContext(true)
                            .build();

            when(contextBuilder.buildContext(eq(userId), any(Locale.class))).thenReturn("context");
            when(aiProvider.sendStructuredPrompt(eq("Explain my budget"), anyString(), any()))
                    .thenReturn(Mono.just(jsonAnswer("Your budget is balanced.")));
            when(conversationWriter.save(any(AIConversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Flux<String> responseFlux = aiService.streamQuestion(userId, request);

            // Then
            StepVerifier.create(responseFlux)
                    .expectNext("Your budget is balanced.")
                    .verifyComplete();

            verify(contextBuilder).buildContext(eq(userId), any(Locale.class));
            verify(aiProvider).sendStructuredPrompt(eq("Explain my budget"), anyString(), any());
        }

        @Test
        @DisplayName("should save complete response after streaming")
        void shouldSaveCompleteResponseAfterStreaming() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder().question("Test").includeFullContext(false).build();

            when(contextBuilder.buildMinimalContext(eq(userId), any(Locale.class)))
                    .thenReturn("context");
            when(aiProvider.sendStructuredPrompt(eq("Test"), anyString(), any()))
                    .thenReturn(Mono.just(jsonAnswer("First part, second part")));

            ArgumentCaptor<AIConversation> conversationCaptor =
                    ArgumentCaptor.forClass(AIConversation.class);
            when(conversationWriter.save(conversationCaptor.capture()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StepVerifier.create(aiService.streamQuestion(userId, request))
                    .expectNext("First part, second part")
                    .verifyComplete();

            // Then
            // Verify that conversation was saved with complete response
            verify(conversationWriter, atLeastOnce()).save(any(AIConversation.class));
        }
    }

    @Nested
    @DisplayName("listConversations Tests")
    class ListConversationsTests {

        @Test
        @DisplayName("should list all user conversations")
        void shouldListAllUserConversations() {
            // Given
            List<AIConversation> conversations =
                    Arrays.asList(
                            createConversation(1L, userId, "Net Worth Discussion"),
                            createConversation(2L, userId, "Budget Planning"),
                            createConversation(3L, userId, "Investment Advice"));

            when(conversationRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                    .thenReturn(conversations);

            // When
            List<AIDto.ConversationSummary> result = aiService.listConversations(userId);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getTitle()).isEqualTo("Net Worth Discussion");
            assertThat(result.get(1).getTitle()).isEqualTo("Budget Planning");
            assertThat(result.get(2).getTitle()).isEqualTo("Investment Advice");

            verify(conversationRepository).findByUser_IdOrderByCreatedAtDesc(userId);
        }

        @Test
        @DisplayName("should return empty list when no conversations")
        void shouldReturnEmptyListWhenNoConversations() {
            // Given
            when(conversationRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of());

            // When
            List<AIDto.ConversationSummary> result = aiService.listConversations(userId);

            // Then
            assertThat(result).isEmpty();
            verify(conversationRepository).findByUser_IdOrderByCreatedAtDesc(userId);
        }
    }

    @Nested
    @DisplayName("getConversation Tests")
    class GetConversationTests {

        @Test
        @DisplayName("should get conversation with messages")
        void shouldGetConversationWithMessages() {
            // Given
            Long conversationId = 1L;
            AIConversation conversation =
                    createConversation(conversationId, userId, "My Conversation");
            conversation.setMessages(
                    "[{\"role\":\"user\",\"content\":\"Hello\"},{\"role\":\"assistant\",\"content\":\"Hi!\"}]");

            when(conversationRepository.findByIdAndUser_Id(conversationId, userId))
                    .thenReturn(Optional.of(conversation));

            // When
            AIDto.ConversationDetail result = aiService.getConversation(userId, conversationId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(conversationId);
            assertThat(result.getTitle()).isEqualTo("My Conversation");

            verify(conversationRepository).findByIdAndUser_Id(conversationId, userId);
        }

        @Test
        @DisplayName("should throw exception when conversation not found")
        void shouldThrowExceptionWhenConversationNotFound() {
            // Given
            Long conversationId = 999L;
            when(conversationRepository.findByIdAndUser_Id(conversationId, userId))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> aiService.getConversation(userId, conversationId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Conversation not found");

            verify(conversationRepository).findByIdAndUser_Id(conversationId, userId);
        }

        @Test
        @DisplayName("should not allow access to other user's conversation")
        void shouldNotAllowAccessToOtherUsersConversation() {
            // Given
            Long conversationId = 1L;
            Long otherUserId = 999L;

            when(conversationRepository.findByIdAndUser_Id(conversationId, otherUserId))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> aiService.getConversation(otherUserId, conversationId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteConversation Tests")
    class DeleteConversationTests {

        @Test
        @DisplayName("should delete conversation")
        void shouldDeleteConversation() {
            // Given
            Long conversationId = 1L;
            AIConversation conversation = createConversation(conversationId, userId, "To Delete");

            when(conversationRepository.findByIdAndUser_Id(conversationId, userId))
                    .thenReturn(Optional.of(conversation));

            // When
            aiService.deleteConversation(userId, conversationId);

            // Then
            verify(conversationRepository).findByIdAndUser_Id(conversationId, userId);
            verify(conversationRepository).delete(conversation);
        }

        @Test
        @DisplayName("should throw exception when deleting non-existent conversation")
        void shouldThrowExceptionWhenDeletingNonExistentConversation() {
            // Given
            Long conversationId = 999L;
            when(conversationRepository.findByIdAndUser_Id(conversationId, userId))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> aiService.deleteConversation(userId, conversationId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Conversation not found");

            verify(conversationRepository).findByIdAndUser_Id(conversationId, userId);
            verify(conversationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should not allow deleting other user's conversation")
        void shouldNotAllowDeletingOtherUsersConversation() {
            // Given
            Long conversationId = 1L;
            Long otherUserId = 999L;

            when(conversationRepository.findByIdAndUser_Id(conversationId, otherUserId))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> aiService.deleteConversation(otherUserId, conversationId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(conversationRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("isAIProviderAvailable Tests")
    class IsOllamaAvailableTests {

        @Test
        @DisplayName("should return true when Ollama is available")
        void shouldReturnTrueWhenOllamaIsAvailable() {
            // Given
            when(aiProvider.isAvailable()).thenReturn(Mono.just(true));

            // When
            boolean result = aiService.isAIProviderAvailable();

            // Then
            assertThat(result).isTrue();
            verify(aiProvider).isAvailable();
        }

        @Test
        @DisplayName("should return false when Ollama is unavailable")
        void shouldReturnFalseWhenOllamaIsUnavailable() {
            // Given
            when(aiProvider.isAvailable()).thenReturn(Mono.just(false));

            // When
            boolean result = aiService.isAIProviderAvailable();

            // Then
            assertThat(result).isFalse();
            verify(aiProvider).isAvailable();
        }
    }

    // Helper methods

    private AIConversation createConversation(Long id, Long userId, String title) {
        User user = User.builder().id(userId).build();

        return AIConversation.builder()
                .id(id)
                .user(user)
                .title(title)
                .messages("[]")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Locale Resolution Tests (i18n)")
    class LocaleResolutionTests {

        @Test
        @DisplayName("should resolve English locale from UserSettings")
        void shouldResolveEnglishLocaleFromUserSettings() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("What is my net worth?")
                            .includeFullContext(false)
                            .build();

            UserSettings settings = new UserSettings();
            settings.setUser(testUser);
            settings.setLanguage("en");

            when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));
            when(contextBuilder.buildMinimalContext(eq(userId), eq(Locale.ENGLISH)))
                    .thenReturn("Minimal context in English");
            when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                    .thenReturn(Mono.just(jsonAnswer("Here is your financial summary.")));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationWriter.save(any(AIConversation.class)))
                    .thenAnswer(
                            inv -> {
                                AIConversation saved = inv.getArgument(0);
                                saved.setId(1L);
                                return saved;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response.getResponse()).isEqualTo("Here is your financial summary.");

            verify(userSettingsRepository).findByUserId(userId);
            verify(contextBuilder).buildMinimalContext(eq(userId), eq(Locale.ENGLISH));
            verify(messageSource, never())
                    .getMessage(anyString(), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should resolve French locale from UserSettings")
        void shouldResolveFrenchLocaleFromUserSettings() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("Quel est mon patrimoine net?")
                            .includeFullContext(false)
                            .build();

            UserSettings settings = new UserSettings();
            settings.setUser(testUser);
            settings.setLanguage("fr");

            when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));
            when(contextBuilder.buildMinimalContext(eq(userId), eq(Locale.FRENCH)))
                    .thenReturn("Contexte minimal en français");
            when(messageSource.getMessage(
                            eq("ai.language.instruction"), any(), anyString(), eq(Locale.FRENCH)))
                    .thenReturn("Important : Veuillez répondre en français");
            when(aiProvider.sendStructuredPrompt(
                            anyString(),
                            contains("Important : Veuillez répondre en français"),
                            any()))
                    .thenReturn(Mono.just(jsonAnswer("Voici votre situation financière.")));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationWriter.save(any(AIConversation.class)))
                    .thenAnswer(
                            inv -> {
                                AIConversation saved = inv.getArgument(0);
                                saved.setId(1L);
                                return saved;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response.getResponse()).isEqualTo("Voici votre situation financière.");

            verify(userSettingsRepository).findByUserId(userId);
            verify(contextBuilder).buildMinimalContext(eq(userId), eq(Locale.FRENCH));
            verify(messageSource)
                    .getMessage(
                            eq("ai.language.instruction"), any(), anyString(), eq(Locale.FRENCH));
        }

        @Test
        @DisplayName("should fallback to English when UserSettings not found")
        void shouldFallbackToEnglishWhenUserSettingsNotFound() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("What is my balance?")
                            .includeFullContext(false)
                            .build();

            when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(contextBuilder.buildMinimalContext(eq(userId), eq(Locale.ENGLISH)))
                    .thenReturn("Minimal context");
            when(aiProvider.sendStructuredPrompt(anyString(), anyString(), any()))
                    .thenReturn(Mono.just(jsonAnswer("Here is your account summary.")));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationWriter.save(any(AIConversation.class)))
                    .thenAnswer(
                            inv -> {
                                AIConversation saved = inv.getArgument(0);
                                saved.setId(1L);
                                return saved;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response.getResponse()).isEqualTo("Here is your account summary.");

            verify(userSettingsRepository).findByUserId(userId);
            verify(contextBuilder).buildMinimalContext(eq(userId), eq(Locale.ENGLISH));
        }

        @Test
        @DisplayName("should not add language instruction for English locale")
        void shouldNotAddLanguageInstructionForEnglish() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder()
                            .question("Show my budget")
                            .includeFullContext(true)
                            .build();

            UserSettings settings = new UserSettings();
            settings.setUser(testUser);
            settings.setLanguage("en");

            ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);

            when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));
            when(contextBuilder.buildContext(eq(userId), eq(Locale.ENGLISH)))
                    .thenReturn("Full financial context");
            when(aiProvider.sendStructuredPrompt(anyString(), contextCaptor.capture(), any()))
                    .thenReturn(Mono.just(jsonAnswer("Here is your budget")));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationWriter.save(any(AIConversation.class)))
                    .thenAnswer(
                            inv -> {
                                AIConversation saved = inv.getArgument(0);
                                saved.setId(1L);
                                return saved;
                            });

            // When
            AIDto.ChatResponse result = aiService.askQuestion(userId, request);

            // Then
            assertThat(result).isNotNull();

            // Verify context does NOT contain language instruction
            String capturedContext = contextCaptor.getValue();
            assertThat(capturedContext).isEqualTo("Full financial context\n");
            assertThat(capturedContext).doesNotContain("Important:");
            assertThat(capturedContext).doesNotContain("respond in");

            verify(messageSource, never())
                    .getMessage(anyString(), any(), anyString(), any(Locale.class));
        }
    }
}
