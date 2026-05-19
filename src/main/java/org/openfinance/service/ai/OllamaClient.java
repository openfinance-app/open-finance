package org.openfinance.service.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client service for interacting with Ollama AI API.
 *
 * <p>This service provides methods to send prompts to Ollama and receive responses, supporting both
 * single-shot completions and streaming responses.
 *
 * <p><strong>Ollama API Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/generate - Generate completion for a prompt
 *   <li>POST /api/chat - Chat with conversation history
 *   <li>GET /api/tags - List available models
 * </ul>
 *
 * @deprecated Use {@link AIProvider} with {@link OllamaAIProvider} instead. This class is retained
 *     for backward compatibility and will be removed in a future release. The {@code AIProvider}
 *     abstraction supports multiple AI backends (Ollama, OpenAI) via the {@code
 *     application.ai.provider} configuration property.
 * @see AIProvider
 * @see OllamaAIProvider
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md">Ollama API
 *     Documentation</a>
 * @since Sprint 11
 */
@Deprecated(since = "Sprint 11.5 — AI Provider Abstraction", forRemoval = true)
@Slf4j
@Service
public class OllamaClient {

    private final WebClient webClient;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    /**
     * Constructs OllamaClient with configuration from application.properties.
     *
     * @param baseUrl Base URL for Ollama API (default: http://localhost:11434)
     * @param model Model name to use (default: llama3.2:3b)
     * @param timeoutSeconds Request timeout in seconds (default: 60)
     * @param maxTokens Maximum tokens in response (default: 2048)
     * @param temperature Sampling temperature 0.0-1.0 (default: 0.7)
     */
    public OllamaClient(
            @Value("${application.ollama.base-url}") String baseUrl,
            @Value("${application.ollama.model}") String model,
            @Value("${application.ollama.timeout-seconds}") int timeoutSeconds,
            @Value("${application.ollama.max-tokens}") int maxTokens,
            @Value("${application.ollama.temperature}") double temperature) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        this.webClient =
                WebClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Content-Type", "application/json")
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
                        .build();

        log.info(
                "Initialized OllamaClient with base URL: {}, model: {}, timeout: {}s",
                baseUrl,
                model,
                timeoutSeconds);
    }

    /**
     * Sends a prompt to Ollama and returns the complete response.
     *
     * <p><strong>Example Usage:</strong>
     *
     * <pre>{@code
     * String response = ollamaClient.sendPrompt(
     *     "What is my current net worth?",
     *     "User has 3 accounts totaling $50,000..."
     * ).block();
     * }</pre>
     *
     * @param prompt User question or prompt
     * @param context Financial context to provide to the AI
     * @return Mono containing the AI response text
     * @throws OllamaException if the API call fails
     */
    public Mono<String> sendPrompt(String prompt, String context) {
        log.debug(
                "Sending prompt to Ollama: {} (context length: {} chars)",
                prompt.substring(0, Math.min(50, prompt.length())),
                context.length());

        ChatRequest request = new ChatRequest();
        request.setModel(model);
        request.setStream(false);

        // Add system message with financial context
        ChatMessage systemMsg = new ChatMessage();
        systemMsg.setRole("system");
        systemMsg.setContent(buildSystemPrompt(context));

        // Add user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent(prompt);

        request.setMessages(List.of(systemMsg, userMsg));

        // Options for generation
        request.setOptions(new GenerationOptions(temperature, maxTokens));

        return webClient
                .post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .map(response -> response.getMessage().getContent())
                .timeout(Duration.ofSeconds(60))
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "Received response from Ollama: {} chars",
                                        response != null ? response.length() : 0))
                .doOnError(error -> log.error("Error calling Ollama API: {}", error.getMessage()))
                .onErrorMap(
                        WebClientResponseException.class,
                        e -> new OllamaException("Ollama API error: " + e.getStatusCode(), e))
                .onErrorMap(
                        e -> !(e instanceof OllamaException),
                        e ->
                                new OllamaException(
                                        "Failed to connect to Ollama: " + e.getMessage(), e));
    }

    /**
     * Streams the AI response in real-time as it's generated.
     *
     * <p>Each emitted string represents a chunk of the response. Concatenate all chunks to get the
     * complete response.
     *
     * <p><strong>Example Usage:</strong>
     *
     * <pre>{@code
     * ollamaClient.streamResponse(prompt, context)
     *     .doOnNext(chunk -> System.out.print(chunk))
     *     .collectList()
     *     .map(chunks -> String.join("", chunks))
     *     .subscribe(completeResponse -> {...});
     * }</pre>
     *
     * @param prompt User question or prompt
     * @param context Financial context to provide to the AI
     * @return Flux emitting response chunks as they're generated
     * @throws OllamaException if the API call fails
     */
    public Flux<String> streamResponse(String prompt, String context) {
        log.debug(
                "Streaming response from Ollama for prompt: {}",
                prompt.substring(0, Math.min(50, prompt.length())));

        ChatRequest request = new ChatRequest();
        request.setModel(model);
        request.setStream(true);

        ChatMessage systemMsg = new ChatMessage();
        systemMsg.setRole("system");
        systemMsg.setContent(buildSystemPrompt(context));

        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent(prompt);

        request.setMessages(List.of(systemMsg, userMsg));
        request.setOptions(new GenerationOptions(temperature, maxTokens));

        return webClient
                .post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(ChatResponse.class)
                .map(response -> response.getMessage().getContent())
                .filter(content -> content != null && !content.isEmpty())
                .timeout(Duration.ofSeconds(120))
                .doOnComplete(() -> log.debug("Streaming response completed"))
                .doOnError(
                        error -> log.error("Error streaming from Ollama: {}", error.getMessage()))
                .onErrorMap(
                        WebClientResponseException.class,
                        e -> new OllamaException("Ollama API error: " + e.getStatusCode(), e))
                .onErrorMap(
                        e -> !(e instanceof OllamaException),
                        e ->
                                new OllamaException(
                                        "Failed to stream from Ollama: " + e.getMessage(), e));
    }

    /**
     * Checks if Ollama is available and responding.
     *
     * @return Mono<Boolean> true if Ollama is available, false otherwise
     */
    public Mono<Boolean> isAvailable() {
        return webClient
                .get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(false)
                .doOnSuccess(available -> log.debug("Ollama availability check: {}", available));
    }

    /**
     * Builds the system prompt with financial context.
     *
     * @param context Financial data context
     * @return Formatted system prompt
     */
    private String buildSystemPrompt(String context) {
        return """
                You are a knowledgeable financial advisor assistant helping users manage their personal finances.

                Your role is to:
                - Provide clear, actionable financial advice
                - Analyze spending patterns and suggest improvements
                - Help users understand their financial health
                - Recommend budget adjustments and savings strategies
                - Explain financial concepts in simple terms

                Important guidelines:
                - Base your advice on the user's actual financial data provided below
                - Be conservative and risk-aware in recommendations
                - Never recommend specific investments or securities
                - Always remind users to consult a licensed financial advisor for major decisions
                - Format currency amounts clearly (e.g., $1,234.56)
                - Keep responses concise but informative

                Current Financial Context:
                %s

                If the context is insufficient to answer a question, acknowledge this and ask for clarification.
                """
                .formatted(context);
    }

    // ===========================
    // DTOs for Ollama API
    // ===========================

    @Data
    private static class ChatRequest {
        private String model;
        private List<ChatMessage> messages;
        private boolean stream;
        private GenerationOptions options;
    }

    @Data
    private static class ChatMessage {
        private String role; // "system", "user", "assistant"
        private String content;
    }

    @Data
    private static class GenerationOptions {
        private double temperature;

        @JsonProperty("num_predict")
        private int numPredict;

        public GenerationOptions(double temperature, int numPredict) {
            this.temperature = temperature;
            this.numPredict = numPredict;
        }
    }

    @Data
    private static class ChatResponse {
        private String model;
        private ChatMessage message;
        private boolean done;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("total_duration")
        private Long totalDuration;
    }

    /** Custom exception for Ollama API errors. */
    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) {
            super(message);
        }

        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
