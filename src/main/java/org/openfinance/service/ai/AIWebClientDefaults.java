package org.openfinance.service.ai;

/**
 * Shared constants for the AI provider WebClient configurations ({@link OllamaClient}, {@link
 * OpenAIProvider}) — single source of truth so the two clients' buffer sizes can never drift apart.
 */
final class AIWebClientDefaults {

    /**
     * Maximum in-memory buffer size for WebClient response codecs (10 MB). AI provider responses
     * (chat completions, embeddings) can exceed the 256KB default, so this is raised uniformly for
     * both providers.
     */
    static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;

    private AIWebClientDefaults() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
