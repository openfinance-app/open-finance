package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.service.ai.AIProvider;
import org.openfinance.service.ai.OllamaAIProvider;
import org.openfinance.service.ai.OpenAIProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AIProviderConfig}.
 *
 * <p>Tests the provider selection logic without loading the full Spring context. Uses
 * ReflectionTestUtils to inject field values.
 */
@DisplayName("AIProviderConfig Tests")
class AIProviderConfigTest {

    private AIProviderConfig createConfig(String provider) {
        AIProviderConfig config = new AIProviderConfig();
        ReflectionTestUtils.setField(config, "providerName", provider);

        // Set default Ollama fields
        ReflectionTestUtils.setField(config, "ollamaBaseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(config, "ollamaModel", "qwen2.5:0.5b");
        ReflectionTestUtils.setField(config, "ollamaTemperature", 0.7);
        ReflectionTestUtils.setField(config, "ollamaMaxTokens", 2048);
        ReflectionTestUtils.setField(config, "ollamaTimeout", 60);

        // Set default OpenAI fields
        ReflectionTestUtils.setField(config, "openaiApiKey", "sk-test-key");
        ReflectionTestUtils.setField(config, "openaiModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(config, "openaiTemperature", 0.7);
        ReflectionTestUtils.setField(config, "openaiMaxTokens", 2048);
        ReflectionTestUtils.setField(config, "openaiTimeout", 60);
        ReflectionTestUtils.setField(config, "openaiBaseUrl", "");

        return config;
    }

    @Test
    @DisplayName("should create OllamaAIProvider when provider is 'ollama'")
    void shouldCreateOllamaProvider() {
        // Given
        AIProviderConfig config = createConfig("ollama");

        // When
        AIProvider provider = config.aiProvider();

        // Then
        assertThat(provider).isInstanceOf(OllamaAIProvider.class);
        assertThat(provider.getProviderName()).isEqualTo("Ollama");
    }

    @Test
    @DisplayName("should create OpenAIProvider when provider is 'openai'")
    void shouldCreateOpenAIProvider() {
        // Given
        AIProviderConfig config = createConfig("openai");

        // When
        AIProvider provider = config.aiProvider();

        // Then
        assertThat(provider).isInstanceOf(OpenAIProvider.class);
        assertThat(provider.getProviderName()).isEqualTo("OpenAI");
    }

    @Test
    @DisplayName("should handle uppercase provider name")
    void shouldHandleUppercaseProviderName() {
        // Given
        AIProviderConfig config = createConfig("OLLAMA");

        // When
        AIProvider provider = config.aiProvider();

        // Then
        assertThat(provider).isInstanceOf(OllamaAIProvider.class);
    }

    @Test
    @DisplayName("should handle provider name with whitespace")
    void shouldHandleWhitespaceProviderName() {
        // Given
        AIProviderConfig config = createConfig("  openai  ");

        // When
        AIProvider provider = config.aiProvider();

        // Then
        assertThat(provider).isInstanceOf(OpenAIProvider.class);
    }

    @Test
    @DisplayName("should throw on unknown provider name")
    void shouldThrowOnUnknownProvider() {
        // Given
        AIProviderConfig config = createConfig("unknown-provider");

        // When & Then
        assertThatThrownBy(config::aiProvider)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown AI provider")
                .hasMessageContaining("unknown-provider");
    }

    @Test
    @DisplayName("should throw when OpenAI API key is blank")
    void shouldThrowWhenOpenAIKeyBlank() {
        // Given
        AIProviderConfig config = createConfig("openai");
        ReflectionTestUtils.setField(config, "openaiApiKey", "");

        // When & Then
        assertThatThrownBy(config::aiProvider)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key must be set");
    }

    @Test
    @DisplayName("should throw when OpenAI API key is null")
    void shouldThrowWhenOpenAIKeyNull() {
        // Given
        AIProviderConfig config = createConfig("openai");
        ReflectionTestUtils.setField(config, "openaiApiKey", (String) null);

        // When & Then
        assertThatThrownBy(config::aiProvider)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key must be set");
    }
}
