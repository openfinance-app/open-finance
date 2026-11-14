package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Factory contract of the generic {@link ApiResponse} envelope. */
@DisplayName("ApiResponse factories")
class ApiResponseTest {

    @Test
    @DisplayName("success(data) marks success, leaves message/error empty and stamps the time")
    void successWithData() {
        LocalDateTime before = LocalDateTime.now();
        ApiResponse<Integer> response = ApiResponse.success(42);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(42);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getError()).isNull();
        assertThat(response.getTimestamp()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("Overload trap: a bare String argument binds to success(message), never to data")
    void stringArgumentBindsToMessageNotData() {
        // success(String) is more specific than success(T), so String payloads silently land in
        // `message`. Callers needing String data must use the two-arg factory. (No production
        // callers exist today; pinning this so the trap is explicit if the DTO is adopted.)
        ApiResponse<String> trap = ApiResponse.success("payload");
        assertThat(trap.getData()).isNull();
        assertThat(trap.getMessage()).isEqualTo("payload");

        ApiResponse<String> explicit = ApiResponse.success(null, "payload");
        assertThat(explicit.getData()).isEqualTo("payload");
    }

    @Test
    @DisplayName("success(message) carries no data payload")
    void successWithMessageOnly() {
        ApiResponse<Object> response = ApiResponse.success("done");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("done");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("Error factories never mark success and use stable machine-readable codes")
    void errorFactoriesUseStableCodes() {
        assertThat(ApiResponse.error("bad").getError()).isNull();
        assertThat(ApiResponse.error("bad").isSuccess()).isFalse();
        assertThat(ApiResponse.error("bad", "CUSTOM").getError()).isEqualTo("CUSTOM");
        assertThat(ApiResponse.validationError("bad").getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(ApiResponse.notFound("gone").getError()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ApiResponse.unauthorized("who").getError()).isEqualTo("UNAUTHORIZED");
        assertThat(ApiResponse.forbidden("no").getError()).isEqualTo("FORBIDDEN");
        ApiResponse<Object> internal = ApiResponse.internalError();
        assertThat(internal.getError()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(internal.getMessage()).doesNotContain("Exception").doesNotContain("stack");
        assertThat(internal.isSuccess()).isFalse();
        assertThat(internal.getData()).isNull();
    }

    @Test
    @DisplayName("Null fields are omitted from JSON (NON_NULL inclusion)")
    void nullFieldsOmittedFromJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(ApiResponse.success(null, "payload"));
        assertThat(json).contains("\"success\":true").contains("\"data\":\"payload\"");
        assertThat(json).doesNotContain("\"message\"").doesNotContain("\"error\"");
    }
}
