package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class AIProviderBoundaryTest {
    private HttpServer server;
    private String response = "{}";
    private String contentType = "application/json";
    private int status = 200;
    private CountDownLatch release = new CountDownLatch(0);
    private CountDownLatch received = new CountDownLatch(1);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    received.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.sendResponseHeaders(status, bytes.length);
                    try {
                        exchange.getResponseBody().write(bytes);
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
    }

    @AfterEach
    void stop() {
        release.countDown();
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private OpenAIProvider openai() {
        return new OpenAIProvider("fixture", "gpt-4o-mini", 0.2, 128, 5, url());
    }

    private OllamaAIProvider ollama() {
        return new OllamaAIProvider(url(), "fixture", 0.2, 128, 10, null);
    }

    @Test
    void validatesModelReadinessInsteadOfConfigurationOrAnyHttpSuccess() {
        response = "{\"id\":\"gpt-4o-mini\"}";
        assertThat(openai().isAvailable().block()).isTrue();
        status = 401;
        assertThat(openai().isAvailable().block()).isFalse();
        status = 200;
        response = "{\"models\":[]}";
        assertThat(ollama().isAvailable().block()).isFalse();
        response = "{\"models\":[{\"name\":\"fixture:latest\"}]}";
        assertThat(ollama().isAvailable().block()).isTrue();
    }

    @Test
    void rejectsIncompleteSynchronousAndStreamingResponses() {
        response =
                "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"Unfinished\"}]}]}";
        StepVerifier.create(openai().sendPrompt("Question", "Context"))
                .expectError(AIProviderException.class)
                .verify();
        contentType = "text/event-stream";
        response =
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Unfinished\"}\n\ndata: {\"type\":\"response.incomplete\"}\n\n";
        StepVerifier.create(openai().streamResponse("Question", "Context"))
                .expectNext("Unfinished")
                .expectError(AIProviderException.class)
                .verify();
        response = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Abrupt end\"}\n\n";
        StepVerifier.create(openai().streamResponse("Question", "Context"))
                .expectNext("Abrupt end")
                .expectError(AIProviderException.class)
                .verify();
    }

    @Test
    void ollamaHonoursTheCallersDeadlineWhileHttpIsStillPending() throws Exception {
        release = new CountDownLatch(1);
        response =
                "{\"model\":\"fixture\",\"message\":{\"role\":\"assistant\",\"content\":\"Done\"},\"done\":true}";
        long start = System.nanoTime();
        assertThatThrownBy(
                        () ->
                                ollama().sendPrompt("Question", "Context")
                                        .block(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timeout");
        assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        release.countDown();
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL|gpt-4o-mini",
                "''|gpt-4o-mini",
                "'  '|gpt-4o-mini",
                "fixture|NULL",
                "fixture|''",
                "fixture|'  '"
            },
            delimiter = '|',
            nullValues = "NULL")
    void incompleteConfigurationIsUnavailableWithoutAnHttpRequest(String key, String model) {
        OpenAIProvider provider = new OpenAIProvider(key, model, 0.2, 128, 1, url());
        assertThat(provider.isAvailable().block()).isFalse();
        assertThat(received.getCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"status\":\"completed\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"refusal\",\"refusal\":\"Cannot answer\"}]}]}",
                "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"reasoning\",\"text\":\"Hidden reasoning\"}]}]}"
            })
    void refusalsAndNonAnswerContentCannotMasqueradeAsSuccessfulAnswers(String body) {
        response = body;
        StepVerifier.create(openai().sendPrompt("Question", "Context"))
                .expectError(AIProviderException.class)
                .verify();
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "url_citation|https://example.test/a|true",
                        "url_citation|http://example.test/a|true",
                "url_citation|javascript:alert(1)|false", "url_citation|file:///tmp/report|false",
                "url_citation|/relative|false", "file_citation|https://example.test/a|false"
            },
            delimiter = '|')
    void onlyHttpCitationsAreEmittedAndBracketedTitlesCannotBreakTheirLabel(
            String kind, String link, boolean accepted) throws Exception {
        ObjectMapper json = new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode root =
                json.createObjectNode().put("status", "completed");
        com.fasterxml.jackson.databind.node.ObjectNode part =
                root.putArray("output")
                        .addObject()
                        .putArray("content")
                        .addObject()
                        .put("type", "output_text")
                        .put("text", "Answer");
        part.putArray("annotations")
                .addObject()
                .put("type", kind)
                .put("url", link)
                .put("title", "[Reference]");
        response = json.writeValueAsString(root);
        assertThat(openai().sendPrompt("Question", "Context").block())
                .isEqualTo(accepted ? "Answer\n[Reference](<" + link + ">)" : "Answer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"response.failed", "response.refusal.delta", "error"})
    void terminalStreamingErrorsCannotCompleteSuccessfully(String event) {
        contentType = "text/event-stream";
        response = "data: {\"type\":\"" + event + "\"}\n\n";
        StepVerifier.create(openai().streamResponse("Question", "Context"))
                .expectError(AIProviderException.class)
                .verify();
    }

    @Test
    void unknownModelsStillReceiveAConservativePositiveTokenBudget() {
        OpenAIProvider provider =
                new OpenAIProvider("fixture", "unregistered-local-model", 0.2, 128, 1, url());
        assertThat(
                        provider.countInputTokens(
                                "A lengthy question about recorded spending",
                                "A financial context"))
                .isGreaterThan(10);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "fixture|fixture|true",
                "fixture|fixture:latest|true",
                "fixture:small|fixture:small|true",
                "fixture:small|fixture:latest|false",
                "fixture|different|false"
            },
            delimiter = '|')
    void ollamaReadinessRequiresTheRequestedModelAndTag(
            String configured, String advertised, boolean ready) {
        response = "{\"models\":[{\"model\":\"" + advertised + "\"}]}";
        assertThat(new OllamaAIProvider(url(), configured, 0.2, 128, 2, "  ").isAvailable().block())
                .isEqualTo(ready);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"done\":false,\"message\":{\"content\":\"partial\"}}",
                "{\"done\":true,\"message\":{\"content\":\" \"}}",
                "{\"done\":true}"
            })
    void ollamaRejectsIncompleteAndEmptyStructuredAnswers(String body) throws Exception {
        response = body;
        StepVerifier.create(
                        ollama().sendStructuredPrompt(
                                        "Question",
                                        "Context",
                                        new ObjectMapper().readTree("{\"type\":\"object\"}")))
                .expectError(AIProviderException.class)
                .verify();
    }
}
