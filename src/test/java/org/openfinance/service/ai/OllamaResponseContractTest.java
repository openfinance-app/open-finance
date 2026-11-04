package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@DisplayName("Ollama request and streaming contracts use offline responses")
@SuppressWarnings("removal")
class OllamaResponseContractTest {
    private final OllamaClient client =
            new OllamaClient("http://localhost", "test-model", 5, 512, 0.2);
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();

    @Test
    void sendsPromptAndFinancialContextWithConfiguredGenerationOptions() throws Exception {
        respond(
                200,
                "application/json",
                "{\"message\":{\"content\":\"Plan ready\"},\"done\":true}");
        assertThat(client.sendPrompt("How can I save?", "Budget: EUR 500").block())
                .isEqualTo("Plan ready");
        JsonNode request = new ObjectMapper().readTree(requestBody.get());
        assertThat(requestPath.get()).isEqualTo("/api/chat");
        assertThat(request.path("model").asText()).isEqualTo("test-model");
        assertThat(request.path("stream").asBoolean()).isFalse();
        assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(512);
        assertThat(request.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(request.path("messages").get(0).path("content").asText())
                .contains("Budget: EUR 500");
        assertThat(request.path("messages").get(1).path("content").asText())
                .isEqualTo("How can I save?");
    }

    @Test
    void emitsTextChunksInOrderAndIgnoresEmptyAndFinalMetadataFrames() throws Exception {
        respond(
                200,
                "application/x-ndjson",
                """
            {"message":{"content":"Hello "},"done":false}
            {"message":{"content":""},"done":false}
            {"message":{"content":"world"},"done":false}
            {"done":true}
            """);
        assertThat(client.streamResponse("hello", "context").collectList().block())
                .containsExactly("Hello ", "world");
        assertThat(new ObjectMapper().readTree(requestBody.get()).path("stream").asBoolean())
                .isTrue();
    }

    @Test
    void exposesHttpFailuresAsDomainErrorsAndAvailabilityAsFalse() {
        respond(503, "application/json", "{}");
        assertThatThrownBy(() -> client.sendPrompt("hello", "context").block())
                .isInstanceOf(OllamaClient.OllamaException.class)
                .hasMessageContaining("503");
        assertThatThrownBy(() -> client.streamResponse("hello", "context").collectList().block())
                .isInstanceOf(OllamaClient.OllamaException.class)
                .hasMessageContaining("503");
        assertThat(client.isAvailable().block()).isFalse();
    }

    @Test
    void malformedJsonIsWrappedAndSuccessfulAvailabilityUsesTheTagsEndpoint() {
        respond(200, "application/json", "not json");
        assertThatThrownBy(() -> client.sendPrompt("hello", "context").block())
                .isInstanceOf(OllamaClient.OllamaException.class)
                .hasCauseInstanceOf(org.springframework.core.codec.DecodingException.class);
        assertThatThrownBy(() -> client.streamResponse("hello", "context").collectList().block())
                .isInstanceOf(OllamaClient.OllamaException.class);
        respond(200, "application/json", "{\"models\":[]}");
        assertThat(client.isAvailable().block()).isTrue();
        assertThat(requestPath.get()).isEqualTo("/api/tags");
    }

    private void respond(int status, String contentType, String body) {
        WebClient webClient =
                WebClient.builder()
                        .exchangeFunction(
                                request -> {
                                    requestPath.set(request.url().getPath());
                                    ClientResponse response =
                                            ClientResponse.create(HttpStatus.valueOf(status))
                                                    .header("Content-Type", contentType)
                                                    .body(body)
                                                    .build();
                                    if (request.method() == HttpMethod.GET)
                                        return Mono.just(response);
                                    MockClientHttpRequest captured =
                                            new MockClientHttpRequest(
                                                    request.method(), request.url());
                                    return request.writeTo(
                                                    captured, ExchangeStrategies.withDefaults())
                                            .then(Mono.defer(captured::getBodyAsString))
                                            .doOnNext(requestBody::set)
                                            .thenReturn(response);
                                })
                        .build();
        ReflectionTestUtils.setField(client, "webClient", webClient);
    }
}
