package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AIProviderException}. */
@DisplayName("AIProviderException Tests")
class AIProviderExceptionTest {

    @Test
    @DisplayName("should create exception with provider name and message")
    void shouldCreateWithProviderNameAndMessage() {
        // When
        AIProviderException ex = new AIProviderException("Ollama", "Connection refused");

        // Then
        assertThat(ex.getProviderName()).isEqualTo("Ollama");
        assertThat(ex.getMessage()).isEqualTo("Connection refused");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("should create exception with provider name, message, and cause")
    void shouldCreateWithProviderNameMessageAndCause() {
        // Given
        RuntimeException cause = new RuntimeException("socket timeout");

        // When
        AIProviderException ex = new AIProviderException("OpenAI", "API error", cause);

        // Then
        assertThat(ex.getProviderName()).isEqualTo("OpenAI");
        assertThat(ex.getMessage()).isEqualTo("API error");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        AIProviderException ex = new AIProviderException("TestProvider", "test");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
