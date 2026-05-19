package org.openfinance.service.ai;

/**
 * Unified exception for AI provider errors.
 *
 * <p>Replaces the provider-specific {@link OllamaClient.OllamaException} so that calling code can
 * catch a single type regardless of the active provider.
 *
 * @since Sprint 11+ — AI Provider Abstraction
 */
public class AIProviderException extends RuntimeException {

    private final String providerName;

    public AIProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public AIProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    /**
     * Returns the name of the provider that raised the error.
     *
     * @return provider name (e.g. "Ollama", "OpenAI")
     */
    public String getProviderName() {
        return providerName;
    }
}
