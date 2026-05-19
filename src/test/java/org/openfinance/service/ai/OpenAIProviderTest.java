package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link OpenAIProvider}.
 *
 * <p>Since the constructor builds real Langchain4J model instances (which require an API key but do
 * not connect until a request is made), we can test the structural behavior without actual API
 * calls.
 */
@DisplayName("OpenAIProvider Tests")
class OpenAIProviderTest {

    /**
     * Creates a provider with a dummy API key for testing. No actual API calls are made in these
     * tests.
     */
    private OpenAIProvider createProvider() {
        return new OpenAIProvider(
                "sk-test-dummy-key-for-unit-tests", "gpt-4o-mini", 0.7, 2048, 30, null);
    }

    @Test
    @DisplayName("should return 'OpenAI' as provider name")
    void shouldReturnOpenAIAsProviderName() {
        // When
        OpenAIProvider provider = createProvider();

        // Then
        assertThat(provider.getProviderName()).isEqualTo("OpenAI");
    }

    @Test
    @DisplayName("should always report as available")
    void shouldAlwaysBeAvailable() {
        // Given
        OpenAIProvider provider = createProvider();

        // When & Then
        StepVerifier.create(provider.isAvailable()).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("should construct with custom base URL")
    void shouldConstructWithCustomBaseUrl() {
        // When — no exception expected
        OpenAIProvider provider =
                new OpenAIProvider(
                        "sk-test-key",
                        "gpt-4o-mini",
                        0.5,
                        1024,
                        15,
                        "https://custom.openai-compatible.com/v1");

        // Then
        assertThat(provider.getProviderName()).isEqualTo("OpenAI");
    }

    @Test
    @DisplayName("should construct with blank base URL (uses default)")
    void shouldConstructWithBlankBaseUrl() {
        // When — no exception expected
        OpenAIProvider provider =
                new OpenAIProvider("sk-test-key", "gpt-4o-mini", 0.7, 2048, 60, "");

        // Then
        assertThat(provider.getProviderName()).isEqualTo("OpenAI");
    }

    @Test
    @DisplayName("should implement AIProvider interface")
    void shouldImplementAIProviderInterface() {
        // When
        OpenAIProvider provider = createProvider();

        // Then
        assertThat(provider).isInstanceOf(AIProvider.class);
    }
}
