package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StructuredOutputContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ollamaReceivesAnEnforcedTaskSchemaAndRejectsTruncation() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        AtomicReference<String> response =
                new AtomicReference<>(
                        "{\"done\":true,\"done_reason\":\"stop\",\"message\":{\"content\":\"{\\\"results\\\":[]}\"}}");
        HttpServer server = server("/api/chat", request, response);
        try {
            AIProvider provider =
                    new OllamaAIProvider(url(server), "fixture", 0.7, 123, 5, null, 1024);
            JsonNode schema = FinancialResponseGuard.responseSchema("");
            String output =
                    provider.sendStructuredPrompt("Classify", "Only classify.", schema)
                            .block(Duration.ofSeconds(5));
            assertThat(output).isEqualTo("{\"results\":[]}");
            assertThat(request.get().get("format")).isEqualTo(schema);
            assertThat(request.get().path("stream").asBoolean()).isFalse();
            assertThat(request.get().path("options").path("num_ctx").asInt()).isEqualTo(1024);
            assertThat(request.get().path("messages").get(0).path("content").asText())
                    .contains("Only classify")
                    .doesNotContain("financial advisor");
            response.set(
                    "{\"done\":true,\"done_reason\":\"length\",\"message\":{\"content\":\"{}\"}}");
            assertThatThrownBy(() -> provider.sendStructuredPrompt("Classify", "", schema).block())
                    .isInstanceOf(AIProviderException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void responsesUsesStrictTextSchemaWithoutWebSearch() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        HttpServer server =
                server(
                        "/responses",
                        request,
                        new AtomicReference<>(
                                "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{}\"}]}]}"));
        try {
            AIProvider provider =
                    new OpenAIProvider("fixture", "gpt-4o-mini", 0.7, 123, 5, url(server));
            JsonNode schema = FinancialResponseGuard.responseSchema("");
            assertThat(
                            provider.sendStructuredPrompt("Classify", "Only classify.", schema)
                                    .block(Duration.ofSeconds(5)))
                    .isEqualTo("{}");
            assertThat(request.get().path("text").path("format").path("schema")).isEqualTo(schema);
            assertThat(request.get().path("text").path("format").path("strict").asBoolean())
                    .isTrue();
            assertThat(request.get().has("tools")).isFalse();
            assertThat(request.get().path("store").asBoolean()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(
            String path, AtomicReference<JsonNode> request, AtomicReference<String> response)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                path,
                exchange -> {
                    request.set(json.readTree(exchange.getRequestBody()));
                    byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        return server;
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
