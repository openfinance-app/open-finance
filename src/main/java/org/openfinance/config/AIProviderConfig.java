package org.openfinance.config;

import lombok.extern.slf4j.Slf4j;
import org.openfinance.service.ai.AIProvider;
import org.openfinance.service.ai.OllamaAIProvider;
import org.openfinance.service.ai.OpenAIProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that selects and wires the active {@link AIProvider} based on the {@code
 * application.ai.provider} property.
 *
 * <p>Supported provider values:
 *
 * <ul>
 *   <li>{@code ollama} (default) — local Ollama instance
 *   <li>{@code openai} — OpenAI API (or compatible endpoint)
 * </ul>
 *
 * @since Sprint 11+ — AI Provider Abstraction (Langchain4J)
 */
@Configuration
@Slf4j
public class AIProviderConfig {

    // ---- provider selector ----
    @Value("${application.ai.provider:ollama}")
    private String providerName;

    // ---- Ollama settings (reuses existing keys for backward compatibility) ----
    @Value(
            "${application.ai.ollama.base-url:${application.ollama.base-url:http://localhost:11434}}")
    private String ollamaBaseUrl;

    @Value("${application.ai.ollama.model:${application.ollama.model:qwen2.5:0.5b}}")
    private String ollamaModel;

    @Value("${application.ai.ollama.temperature:${application.ollama.temperature:0.7}}")
    private double ollamaTemperature;

    @Value("${application.ai.ollama.max-tokens:${application.ollama.max-tokens:2048}}")
    private int ollamaMaxTokens;

    @Value("${application.ai.ollama.timeout-seconds:${application.ollama.timeout-seconds:60}}")
    private int ollamaTimeout;

    @Value("${application.ai.ollama.searxng.base-url:}")
    private String searxngBaseUrl;

    // ---- OpenAI settings ----
    @Value("${application.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${application.ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${application.ai.openai.temperature:0.7}")
    private double openaiTemperature;

    @Value("${application.ai.openai.max-tokens:2048}")
    private int openaiMaxTokens;

    @Value("${application.ai.openai.timeout-seconds:60}")
    private int openaiTimeout;

    @Value("${application.ai.openai.base-url:}")
    private String openaiBaseUrl;

    /**
     * Creates the active {@link AIProvider} bean based on configuration.
     *
     * @return configured AI provider
     */
    @Bean
    public AIProvider aiProvider() {
        String selected = providerName.trim().toLowerCase();
        log.info("Configuring AI provider: {}", selected);

        return switch (selected) {
            case "openai" -> {
                if (openaiApiKey == null || openaiApiKey.isBlank()) {
                    throw new IllegalStateException(
                            "application.ai.openai.api-key must be set when provider is 'openai'");
                }
                yield new OpenAIProvider(
                        openaiApiKey,
                        openaiModel,
                        openaiTemperature,
                        openaiMaxTokens,
                        openaiTimeout,
                        (openaiBaseUrl == null || openaiBaseUrl.isBlank()) ? null : openaiBaseUrl);
            }
            case "ollama" -> new OllamaAIProvider(
                    ollamaBaseUrl,
                    ollamaModel,
                    ollamaTemperature,
                    ollamaMaxTokens,
                    ollamaTimeout,
                    (searxngBaseUrl == null || searxngBaseUrl.isBlank()) ? null : searxngBaseUrl);
            default -> throw new IllegalStateException(
                    "Unknown AI provider: '" + selected + "'. Supported values: ollama, openai");
        };
    }
}
