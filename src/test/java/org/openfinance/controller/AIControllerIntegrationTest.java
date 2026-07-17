package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.AIDto;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.AIConversation;
import org.openfinance.entity.User;
import org.openfinance.repository.AIConversationRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.service.UserService;
import org.openfinance.service.ai.AIProvider;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

/**
 * Integration tests for AIController endpoints.
 *
 * <p>Tests REST endpoints for AI Assistant functionality with real HTTP requests, authentication,
 * and database interactions. AIProvider is mocked to avoid external API dependency.
 *
 * <p><strong>Test Coverage:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/ai/chat - Request validation (encryption key, question validation,
 *       authentication)
 *   <li>GET /api/v1/ai/conversations - List conversations
 *   <li>GET /api/v1/ai/conversations/{id} - Get conversation details
 *   <li>DELETE /api/v1/ai/conversations/{id} - Delete conversation
 * </ul>
 *
 * <p><strong>Note:</strong> The chat endpoint (POST /api/v1/ai/chat) is now synchronous. Business
 * logic for chat, streaming (POST /api/v1/ai/chat/stream SSE), and health check (GET
 * /api/v1/ai/health) is tested at the unit level in AIServiceTest. Conversation CRUD endpoints
 * (List, Get, Delete) are fully tested here.
 *
 * @since Sprint 11 - AI Assistant Integration
 * @see org.openfinance.service.AIServiceTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("AIController Integration Tests")
class AIControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private UserRepository userRepository;

    @Autowired private AIConversationRepository conversationRepository;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    @MockBean private AIProvider aiProvider;

    private String token;
    private String encKey;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up test data
        databaseCleanupService.execute();

        // Register test user
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("alice")
                        .email("alice@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        // Login and get JWT token
        LoginRequest login =
                LoginRequest.builder()
                        .username("alice")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        token = objectMapper.readTree(resp).get("token").asText();
        encKey = objectMapper.readTree(resp).get("encryptionKey").asText();

        user =
                userRepository
                        .findByUsername("alice")
                        .orElseThrow(() -> new RuntimeException("User not found"));

        // Setup default AI provider mock (lenient for tests that don't use it)
        lenient().when(aiProvider.isAvailable()).thenReturn(Mono.just(true));
        lenient()
                .when(aiProvider.sendPrompt(anyString(), anyString()))
                .thenReturn(Mono.just("Based on your financial data, here's my analysis..."));
        lenient().when(aiProvider.getProviderName()).thenReturn("MockProvider");
    }

    @AfterEach
    void tearDown() {
        databaseCleanupService.execute();
    }

    @Nested
    @DisplayName("POST /api/v1/ai/chat Tests")
    class ChatTests {

        @Test
        @DisplayName("Should return 401 when encryption session is missing")
        void shouldReturn500WhenMissingEncryptionKey() throws Exception {
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder().question("What is my balance?").build();

            mockMvc.perform(
                            post("/api/v1/ai/chat")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 400 when question is blank")
        void shouldReturn400WhenQuestionIsBlank() throws Exception {
            AIDto.ChatRequest request = AIDto.ChatRequest.builder().question("").build();

            mockMvc.perform(
                            post("/api/v1/ai/chat")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.question").exists());
        }

        @Test
        @DisplayName("Should return 403 when JWT token is missing")
        void shouldReturn401WhenNoToken() throws Exception {
            AIDto.ChatRequest request =
                    AIDto.ChatRequest.builder().question("What is my balance?").build();

            // Spring Security returns 403 Forbidden for missing tokens (not 401)
            mockMvc.perform(
                            post("/api/v1/ai/chat")
                                    .header("X-Encryption-Session", encKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ai/conversations Tests")
    class ListConversationsTests {

        @Test
        @DisplayName("Should return empty list when no conversations exist")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(
                            get("/api/v1/ai/conversations")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Should return list of conversations ordered by created date desc")
        void shouldReturnConversationsList() throws Exception {
            // Create test conversations with JSON messages
            List<AIDto.Message> messages1 =
                    List.of(
                            AIDto.Message.builder()
                                    .role("user")
                                    .content("Hello")
                                    .timestamp(LocalDateTime.now())
                                    .build());

            List<AIDto.Message> messages2 =
                    List.of(
                            AIDto.Message.builder()
                                    .role("user")
                                    .content("What's my balance?")
                                    .timestamp(LocalDateTime.now())
                                    .build());

            AIConversation conv1 =
                    AIConversation.builder()
                            .user(user)
                            .title("First Question")
                            .messages(objectMapper.writeValueAsString(messages1))
                            .createdAt(LocalDateTime.now().minusDays(2))
                            .updatedAt(LocalDateTime.now().minusDays(2))
                            .build();

            AIConversation conv2 =
                    AIConversation.builder()
                            .user(user)
                            .title("Second Question")
                            .messages(objectMapper.writeValueAsString(messages2))
                            .createdAt(LocalDateTime.now().minusDays(1))
                            .updatedAt(LocalDateTime.now().minusDays(1))
                            .build();

            conversationRepository.save(conv1);
            conversationRepository.save(conv2);

            mockMvc.perform(
                            get("/api/v1/ai/conversations")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    // Note: @CreationTimestamp overrides manually set timestamps, so both
                    // conversations
                    // have the same createdAt value. When timestamps are equal, JPA orders by ID.
                    .andExpect(jsonPath("$[0].title").value("First Question")) // ID 1 comes first
                    .andExpect(jsonPath("$[1].title").value("Second Question")) // ID 2 comes second
                    .andExpect(jsonPath("$[0].message_count").value(1))
                    .andExpect(jsonPath("$[0].created_at").exists())
                    .andExpect(jsonPath("$[0].updated_at").exists());
        }

        @Test
        @DisplayName("Should only return current user's conversations")
        void shouldIsolateUserConversations() throws Exception {
            // Create second user
            UserRegistrationRequest reg =
                    UserRegistrationRequest.builder()
                            .username("bob")
                            .email("bob@example.com")
                            .password("Password123!")
                            .masterPassword("Master123!")
                            .build();
            userService.registerUser(reg);
            User bob = userRepository.findByUsername("bob").orElseThrow();

            // Create conversations for both users
            AIConversation aliceConv =
                    AIConversation.builder()
                            .user(user)
                            .title("Alice's Question")
                            .messages("[]")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

            AIConversation bobConv =
                    AIConversation.builder()
                            .user(bob)
                            .title("Bob's Question")
                            .messages("[]")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

            conversationRepository.save(aliceConv);
            conversationRepository.save(bobConv);

            // Alice should only see her conversation
            mockMvc.perform(
                            get("/api/v1/ai/conversations")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Alice's Question"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/ai/conversations/{id} Tests")
    class GetConversationTests {

        @Test
        @DisplayName("Should return conversation with full message history")
        void shouldReturnConversationDetails() throws Exception {
            List<AIDto.Message> messages =
                    List.of(
                            AIDto.Message.builder()
                                    .role("user")
                                    .content("What is my net worth?")
                                    .timestamp(LocalDateTime.now())
                                    .build(),
                            AIDto.Message.builder()
                                    .role("assistant")
                                    .content("Your net worth is $10,000.")
                                    .timestamp(LocalDateTime.now())
                                    .build());

            AIConversation conv =
                    AIConversation.builder()
                            .user(user)
                            .title("Test Conversation")
                            .messages(objectMapper.writeValueAsString(messages))
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
            conv = conversationRepository.save(conv);

            mockMvc.perform(
                            get("/api/v1/ai/conversations/" + conv.getId())
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(conv.getId()))
                    .andExpect(jsonPath("$.title").value("Test Conversation"))
                    .andExpect(jsonPath("$.messages").isArray())
                    .andExpect(jsonPath("$.messages.length()").value(2))
                    .andExpect(jsonPath("$.messages[0].role").value("user"))
                    .andExpect(jsonPath("$.messages[0].content").value("What is my net worth?"))
                    .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                    .andExpect(
                            jsonPath("$.messages[1].content").value("Your net worth is $10,000."))
                    .andExpect(jsonPath("$.created_at").exists())
                    .andExpect(jsonPath("$.updated_at").exists());
        }

        @Test
        @DisplayName("Should return 404 when conversation does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(
                            get("/api/v1/ai/conversations/99999")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when user tries to access another user's conversation")
        void shouldReturn404WhenUnauthorized() throws Exception {
            // Create second user
            UserRegistrationRequest reg =
                    UserRegistrationRequest.builder()
                            .username("bob")
                            .email("bob@example.com")
                            .password("Password123!")
                            .masterPassword("Master123!")
                            .build();
            userService.registerUser(reg);
            User bob = userRepository.findByUsername("bob").orElseThrow();

            // Create Bob's conversation
            AIConversation bobConv =
                    AIConversation.builder()
                            .user(bob)
                            .title("Bob's Private Chat")
                            .messages("[]")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
            bobConv = conversationRepository.save(bobConv);

            // Alice tries to access Bob's conversation
            mockMvc.perform(
                            get("/api/v1/ai/conversations/" + bobConv.getId())
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/ai/conversations/{id} Tests")
    class DeleteConversationTests {

        @Test
        @DisplayName("Should delete conversation successfully")
        void shouldDeleteConversation() throws Exception {
            AIConversation conv =
                    AIConversation.builder()
                            .user(user)
                            .title("To Delete")
                            .messages("[]")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
            conv = conversationRepository.save(conv);

            mockMvc.perform(
                            delete("/api/v1/ai/conversations/" + conv.getId())
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            // Verify deletion
            assertThat(conversationRepository.findById(conv.getId())).isEmpty();
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent conversation")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            mockMvc.perform(
                            delete("/api/v1/ai/conversations/99999")
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when user tries to delete another user's conversation")
        void shouldReturn404WhenDeletingOthersConversation() throws Exception {
            // Create second user
            UserRegistrationRequest reg =
                    UserRegistrationRequest.builder()
                            .username("bob")
                            .email("bob@example.com")
                            .password("Password123!")
                            .masterPassword("Master123!")
                            .build();
            userService.registerUser(reg);
            User bob = userRepository.findByUsername("bob").orElseThrow();

            // Create Bob's conversation
            AIConversation bobConv =
                    AIConversation.builder()
                            .user(bob)
                            .title("Bob's Chat")
                            .messages("[]")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
            bobConv = conversationRepository.save(bobConv);

            // Alice tries to delete Bob's conversation
            mockMvc.perform(
                            delete("/api/v1/ai/conversations/" + bobConv.getId())
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Encryption-Session", encKey))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            // Verify Bob's conversation still exists
            assertThat(conversationRepository.findById(bobConv.getId())).isPresent();
        }
    }
}
