package org.openfinance.service.ai;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstraction for AI language model providers.
 *
 * <p>Implementations wrap a specific AI backend (Ollama, OpenAI, etc.) and expose a uniform
 * interface for sending prompts and checking availability. The active implementation is selected at
 * startup via the {@code application.ai.provider} configuration property.
 *
 * @since Sprint 11+ — AI Provider Abstraction (Langchain4J)
 */
public interface AIProvider {

    /**
     * Sends a prompt with financial context and returns the complete response.
     *
     * @param prompt User question or instruction
     * @param context Financial context to include in the system message
     * @return Mono emitting the AI-generated response text
     */
    Mono<String> sendPrompt(String prompt, String context);

    /**
     * Streams the AI response in real-time as it is generated.
     *
     * @param prompt User question or instruction
     * @param context Financial context to include in the system message
     * @return Flux emitting response chunks
     */
    Flux<String> streamResponse(String prompt, String context);

    /**
     * Checks whether the underlying AI service is reachable and ready.
     *
     * @return Mono emitting {@code true} if the service is available
     */
    Mono<Boolean> isAvailable();

    /**
     * Returns a human-readable name for this provider (e.g. "Ollama", "OpenAI").
     *
     * @return provider display name
     */
    String getProviderName();
}
