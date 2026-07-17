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

    @Mock private FinancialContextBuilder contextBuilder;

    @Mock private AIConversationRepository conversationRepository;

    @Mock private UserRepository userRepository;

    @Mock private UserSettingsRepository userSettingsRepository;

    @Mock private MessageSource messageSource;

    @Mock private ObjectMapper objectMapper;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private AIService aiService;

    private Long userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = 1L;

        testUser = User.builder().id(userId).email("test@example.com").username("testuser").build();

        // Mock userRepository to return test user (needed for new conversation
        // creation)
        // Use lenient() because not all tests create new conversations
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
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
            String aiResponse = "Your net worth is $45,000.";

            when(contextBuilder.buildContext(eq(userId), any(Locale.class))).thenReturn(context);
            when(aiProvider.sendPrompt("What is my net worth?", context))
                    .thenReturn(Mono.just(aiResponse));
            when(conversationRepository.save(any(AIConversation.class)))
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
            verify(aiProvider).sendPrompt("What is my net worth?", context);
            verify(conversationRepository, atLeastOnce()).save(any(AIConversation.class));
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
            when(aiProvider.sendPrompt("Show me my expenses", context))
                    .thenReturn(Mono.just(aiResponse));

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getResponse()).isEqualTo(aiResponse);
            assertThat(response.getConversationId()).isEqualTo(conversationId);

            verify(contextBuilder).buildMinimalContext(eq(userId), any(Locale.class));
            verify(aiProvider).sendPrompt("Show me my expenses", context);
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
            when(aiProvider.sendPrompt(anyString(), anyString()))
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
            when(aiProvider.sendPrompt(anyString(), anyString())).thenReturn(Mono.just("Response"));

            ArgumentCaptor<AIConversation> conversationCaptor =
                    ArgumentCaptor.forClass(AIConversation.class);
            when(conversationRepository.save(conversationCaptor.capture()))
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
            when(aiProvider.streamResponse(eq("Explain my budget"), anyString()))
                    .thenReturn(Flux.just("Your ", "budget ", "is ", "balanced."));
            when(conversationRepository.save(any(AIConversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Flux<String> responseFlux = aiService.streamQuestion(userId, request);

            // Then
            StepVerifier.create(responseFlux)
                    .expectNext("Your ")
                    .expectNext("budget ")
                    .expectNext("is ")
                    .expectNext("balanced.")
                    .verifyComplete();

            verify(contextBuilder).buildContext(eq(userId), any(Locale.class));
            verify(aiProvider).streamResponse(eq("Explain my budget"), anyString());
        }

        @Test
        @DisplayName("should save complete response after streaming")
        void shouldSaveCompleteResponseAfterStreaming() {
            // Given
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder().question("Test").includeFullContext(false).build();

            when(contextBuilder.buildMinimalContext(eq(userId), any(Locale.class)))
                    .thenReturn("context");
            when(aiProvider.streamResponse(eq("Test"), anyString()))
                    .thenReturn(Flux.just("Part ", "1, ", "Part ", "2"));

            ArgumentCaptor<AIConversation> conversationCaptor =
                    ArgumentCaptor.forClass(AIConversation.class);
            when(conversationRepository.save(conversationCaptor.capture()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            StepVerifier.create(aiService.streamQuestion(userId, request))
                    .expectNext("Part ", "1, ", "Part ", "2")
                    .verifyComplete();

            // Then
            // Verify that conversation was saved with complete response
            verify(conversationRepository, atLeastOnce()).save(any(AIConversation.class));
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
            when(aiProvider.sendPrompt(anyString(), anyString()))
                    .thenReturn(Mono.just("Your net worth is $1000"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationRepository.save(any(AIConversation.class)))
                    .thenAnswer(
                            inv -> {
                                AIConversation saved = inv.getArgument(0);
                                saved.setId(1L);
                                return saved;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response.getResponse()).isEqualTo("Your net worth is $1000");

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
            when(aiProvider.sendPrompt(
                            anyString(), contains("Important : Veuillez répondre en français")))
                    .thenReturn(Mono.just("Votre patrimoine net est de 1000 $"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationRepository.save(any(AIConversation.class)))
                    .thenAnswer(
                            inv -> {
                                AIConversation saved = inv.getArgument(0);
                                saved.setId(1L);
                                return saved;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response.getResponse()).isEqualTo("Votre patrimoine net est de 1000 $");

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
            when(aiProvider.sendPrompt(anyString(), anyString()))
                    .thenReturn(Mono.just("Your balance is $500"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationRepository.save(any(AIConversation.class)))
                    .thenAnswer(
                            inv -> {
                                AIConversation saved = inv.getArgument(0);
                                saved.setId(1L);
                                return saved;
                            });

            // When
            AIDto.ChatResponse response = aiService.askQuestion(userId, request);

            // Then
            assertThat(response.getResponse()).isEqualTo("Your balance is $500");

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
            when(aiProvider.sendPrompt(anyString(), contextCaptor.capture()))
                    .thenReturn(Mono.just("Here is your budget"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            AIConversation newConversation = createConversation(null, userId, null);
            newConversation.setMessages("[]");
            when(conversationRepository.save(any(AIConversation.class)))
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
            assertThat(capturedContext).isEqualTo("Full financial context");
            assertThat(capturedContext).doesNotContain("Important:");
            assertThat(capturedContext).doesNotContain("respond in");

            verify(messageSource, never())
                    .getMessage(anyString(), any(), anyString(), any(Locale.class));
        }
    }
}
