package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link OllamaAIProvider}.
 *
 * <p>Since the constructor builds real Langchain4J model instances that point to a (likely
 * unavailable) Ollama endpoint, tests focus on structural behavior rather than real API calls.
 */
@DisplayName("OllamaAIProvider Tests")
class OllamaAIProviderTest {

    /**
     * Creates a provider pointing to a non-existent Ollama instance. Tests that rely on actual
     * connectivity must handle the failure gracefully.
     */
    private OllamaAIProvider createProvider() {
        return new OllamaAIProvider("http://localhost:11434", "qwen2.5:0.5b", 0.7, 2048, 5, null);
    }

    @Test
    @DisplayName("should return 'Ollama' as provider name")
    void shouldReturnOllamaAsProviderName() {
        // When
        OllamaAIProvider provider = createProvider();

        // Then
        assertThat(provider.getProviderName()).isEqualTo("Ollama");
    }

    @Test
    @DisplayName("should implement AIProvider interface")
    void shouldImplementAIProviderInterface() {
        // When
        OllamaAIProvider provider = createProvider();

        // Then
        assertThat(provider).isInstanceOf(AIProvider.class);
    }

    @Test
    @DisplayName("isAvailable should return false when Ollama is not running")
    void shouldReturnFalseWhenOllamaNotRunning() {
        // Given — provider pointing to a non-existent endpoint
        OllamaAIProvider provider =
                new OllamaAIProvider(
                        "http://localhost:19999", // unlikely to be running
                        "test-model",
                        0.7,
                        1024,
                        2,
                        null);

        // When & Then
        StepVerifier.create(provider.isAvailable()).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("sendPrompt should return error when Ollama is unavailable")
    void shouldReturnErrorOnSendWhenUnavailable() {
        // Given — provider pointing to a non-existent endpoint
        OllamaAIProvider provider =
                new OllamaAIProvider("http://localhost:19999", "test-model", 0.7, 1024, 2, null);

        // When & Then
        StepVerifier.create(provider.sendPrompt("Hello", "context"))
                .expectErrorMatches(
                        e ->
                                e instanceof AIProviderException
                                        && ((AIProviderException) e)
                                                .getProviderName()
                                                .equals("Ollama")
                                        && e.getMessage().contains("Ollama API error"))
                .verify(Duration.ofSeconds(10));
    }
}
