package org.openfinance.service.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Langchain4J-backed AI provider using a local Ollama instance.
 *
 * <p>Configured via {@code application.ai.ollama.*} properties. Uses {@link OllamaChatModel} for
 * synchronous calls and {@link OllamaStreamingChatModel} for streaming.
 *
 * @since Sprint 11+ — AI Provider Abstraction (Langchain4J)
 */
@Slf4j
public class OllamaAIProvider implements AIProvider {

    private static final String PROVIDER_NAME = "Ollama";

    private final WebClient healthClient;
    private final String systemPromptTemplate;

    /**
     * Creates an Ollama provider from explicit configuration values.
     *
     * @param baseUrl Ollama API base URL (e.g. {@code http://localhost:11434})
     * @param model Model name (e.g. {@code qwen2.5:0.5b})
     * @param temperature Sampling temperature (0.0 – 1.0)
     * @param maxTokens Maximum response tokens
     * @param timeoutSeconds Request timeout in seconds
     */
    interface Assistant {
        @dev.langchain4j.service.SystemMessage("{{sys}}")
        String chat(
                @dev.langchain4j.service.V("sys") String sys,
                @dev.langchain4j.service.UserMessage String query);

        @dev.langchain4j.service.SystemMessage("{{sys}}")
        TokenStream streamChat(
                @dev.langchain4j.service.V("sys") String sys,
                @dev.langchain4j.service.UserMessage String query);
    }

    private final Assistant assistant;

    public OllamaAIProvider(
            String baseUrl,
            String model,
            double temperature,
            int maxTokens,
            int timeoutSeconds,
            String searxngBaseUrl) {

        ChatLanguageModel chatModel =
                OllamaChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(model)
                        .temperature(temperature)
                        .numPredict(maxTokens)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .build();

        StreamingChatLanguageModel streamingModel =
                OllamaStreamingChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(model)
                        .temperature(temperature)
                        .numPredict(maxTokens)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .build();

        this.healthClient = WebClient.builder().baseUrl(baseUrl).build();

        this.systemPromptTemplate = buildSystemPromptTemplate();

        if (searxngBaseUrl != null && !searxngBaseUrl.isBlank()) {
            dev.langchain4j.web.search.WebSearchEngine webSearchEngine =
                    dev.langchain4j.community.web.search.searxng.SearXNGWebSearchEngine.builder()
                            .baseUrl(searxngBaseUrl)
                            .build();
            dev.langchain4j.web.search.WebSearchTool searchTool =
                    dev.langchain4j.web.search.WebSearchTool.from(webSearchEngine);

            this.assistant =
                    AiServices.builder(Assistant.class)
                            .chatLanguageModel(chatModel)
                            .streamingChatLanguageModel(streamingModel)
                            .tools(searchTool)
                            .build();
            log.info(
                    "Initialized OllamaAIProvider with SearXNG Tool — url={}, model={}",
                    searxngBaseUrl,
                    model);
        } else {
            this.assistant =
                    AiServices.builder(Assistant.class)
                            .chatLanguageModel(chatModel)
                            .streamingChatLanguageModel(streamingModel)
                            .build();
            log.info(
                    "Initialized OllamaAIProvider — baseUrl={}, model={}, temperature={}, maxTokens={}",
                    baseUrl,
                    model,
                    temperature,
                    maxTokens);
        }
    }

    @Override
    public Mono<String> sendPrompt(String prompt, String context) {
        return Mono.fromCallable(
                        () -> {
                            log.debug(
                                    "Sending prompt to Ollama: {} (context: {} chars)",
                                    prompt.substring(0, Math.min(50, prompt.length())),
                                    context.length());

                            String text =
                                    assistant.chat(systemPromptTemplate.formatted(context), prompt);

                            log.debug(
                                    "Ollama response: {} chars", text != null ? text.length() : 0);
                            return text;
                        })
                .onErrorMap(
                        e ->
                                new AIProviderException(
                                        PROVIDER_NAME, "Ollama API error: " + e.getMessage(), e));
    }

    @Override
    public Flux<String> streamResponse(String prompt, String context) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        try {
            assistant
                    .streamChat(systemPromptTemplate.formatted(context), prompt)
                    .onPartialResponse(token -> sink.tryEmitNext(token))
                    .onCompleteResponse(c -> sink.tryEmitComplete())
                    .onError(
                            e ->
                                    sink.tryEmitError(
                                            new AIProviderException(
                                                    PROVIDER_NAME,
                                                    "Ollama streaming error: " + e.getMessage(),
                                                    e)))
                    .start();
        } catch (Exception ex) {
            sink.tryEmitError(ex);
        }

        return sink.asFlux();
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return healthClient
                .get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .map(r -> true)
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(false)
                .doOnSuccess(ok -> log.debug("Ollama availability: {}", ok));
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
                - Format currency amounts clearly, always including the currency code (e.g., 1,234.56 EUR, 5,000.00 USD)
                - Keep responses concise but informative

                Current Financial Context:
                %s

                If the context is insufficient to answer a question, acknowledge this and ask for clarification.
                """;
    }
}
