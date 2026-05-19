package org.openfinance.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Langchain4J-backed AI provider using the OpenAI API (or any compatible endpoint).
 *
 * <p>Configured via {@code application.ai.openai.*} properties. Supports both blocking and
 * streaming chat completions.
 *
 * @since Sprint 11+ — AI Provider Abstraction (Langchain4J)
 */
@Slf4j
public class OpenAIProvider implements AIProvider {

    private static final String PROVIDER_NAME = "OpenAI";

    private final WebClient webClient;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String systemPromptTemplate;

    /**
     * Creates an OpenAI provider from explicit configuration values.
     *
     * @param apiKey OpenAI API key
     * @param model Model name (e.g. {@code gpt-4o-mini})
     * @param temperature Sampling temperature (0.0 – 2.0)
     * @param maxTokens Maximum response tokens
     * @param timeoutSeconds Request timeout in seconds
     * @param baseUrl Optional custom base URL (null for default OpenAI API)
     */
    public OpenAIProvider(
            String apiKey,
            String model,
            double temperature,
            int maxTokens,
            int timeoutSeconds,
            String baseUrl) {
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.systemPromptTemplate = buildSystemPromptTemplate();

        String apiBaseUrl =
                (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "https://api.openai.com/v1";

        this.webClient =
                WebClient.builder()
                        .baseUrl(apiBaseUrl)
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(10 * 1024 * 1024))
                        .build();

        log.info(
                "Initialized OpenAIProvider with WebClient (web_search) — model={}, timeout={}",
                model,
                timeoutSeconds);
    }

    private String buildRequestBody(String prompt, String context, boolean stream) {
        String systemMessage =
                systemPromptTemplate.formatted(context).replace("\"", "\\\"").replace("\n", "\\n");
        String safePrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n");

        return """
        {
          "model": "%s",
          "temperature": %s,
          "max_tokens": %s,
          "stream": %b,
          "messages": [
            {
              "role": "system",
              "content": "%s"
            },
            {
              "role": "user",
              "content": "%s"
            }
          ],
          "tools": [
            {
              "type": "web_search"
            }
          ]
        }
        """
                .formatted(model, temperature, maxTokens, stream, systemMessage, safePrompt);
    }

    @Override
    public Mono<String> sendPrompt(String prompt, String context) {
        return Mono.fromCallable(() -> buildRequestBody(prompt, context, false))
                .flatMap(
                        body ->
                                webClient
                                        .post()
                                        .uri("/chat/completions")
                                        .bodyValue(body)
                                        .retrieve()
                                        .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                                        .map(
                                                node ->
                                                        node.path("choices")
                                                                .path(0)
                                                                .path("message")
                                                                .path("content")
                                                                .asText())
                                        .onErrorMap(
                                                e ->
                                                        new AIProviderException(
                                                                PROVIDER_NAME,
                                                                "OpenAI API error: "
                                                                        + e.getMessage(),
                                                                e)));
    }

    @Override
    public Flux<String> streamResponse(String prompt, String context) {
        // Warning: minimal streaming implementation matching standard SSE format
        String body = buildRequestBody(prompt, context, true);
        return webClient
                .post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(com.fasterxml.jackson.databind.JsonNode.class)
                .map(
                        node -> {
                            com.fasterxml.jackson.databind.JsonNode choices = node.path("choices");
                            if (choices.isArray() && choices.size() > 0) {
                                com.fasterxml.jackson.databind.JsonNode delta =
                                        choices.get(0).path("delta");
                                if (delta.has("content")) {
                                    return delta.get("content").asText();
                                }
                            }
                            return "";
                        })
                .filter(s -> !s.isEmpty())
                .onErrorMap(
                        e ->
                                new AIProviderException(
                                        PROVIDER_NAME,
                                        "OpenAI API streaming error: " + e.getMessage(),
                                        e));
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return Mono.just(true);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private String buildSystemPromptTemplate() {
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
        """;
    }
}
