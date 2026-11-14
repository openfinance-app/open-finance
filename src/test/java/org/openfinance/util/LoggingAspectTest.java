package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LoggingAspect}'s slow-execution threshold, which must stay consistent with
 * {@code PerformanceMonitoringAspect} (500 ms) and the Hibernate slow-query setting.
 */
@DisplayName("LoggingAspect slow-execution threshold")
class LoggingAspectTest {

    private final LoggingAspect aspect = new LoggingAspect();

    @Test
    @DisplayName("Threshold is 500 ms, matching PerformanceMonitoringAspect")
    void thresholdIs500ms() {
        assertThat(LoggingAspect.SLOW_EXECUTION_THRESHOLD_MS).isEqualTo(500L);
    }

    @Test
    @DisplayName("Flags executions between 500 ms and the old 1000 ms threshold as slow")
    void flagsExecutionsSlowerThan500ms() {
        assertThat(aspect.isSlowExecution(501)).isTrue();
        assertThat(aspect.isSlowExecution(999)).isTrue(); // was NOT slow under the old 1000 ms rule
        assertThat(aspect.isSlowExecution(500)).isFalse();
        assertThat(aspect.isSlowExecution(0)).isFalse();
    }

    @Test
    @DisplayName("Sanitizer renders empty and null arguments safely")
    void sanitizerHandlesEmptyArguments() {
        assertThat(aspect.sanitizeArguments(null)).isEqualTo("[]");
        assertThat(aspect.sanitizeArguments(new Object[0])).isEqualTo("[]");
        assertThat(aspect.sanitizeArguments(new Object[] {null, "plain"}))
                .isEqualTo("[null, plain]");
    }

    @Test
    @DisplayName("Sanitizer truncates long non-sensitive arguments at 100 characters")
    void sanitizerTruncatesLongArguments() {
        String longValue = "a".repeat(150);
        String sanitized = aspect.sanitizeArguments(new Object[] {longValue});
        assertThat(sanitized).hasSize(105); // '[' + 100 chars + "..." + ']'
        assertThat(sanitized).endsWith("...]");
    }

    @Test
    @DisplayName("Sanitizer redacts credential-bearing arguments in all common spellings")
    void sanitizerRedactsCredentialSpellings() {
        String[] sensitive = {
            "password=hunter2",
            "motDePasse=hunter2",
            "mot de passe: hunter2",
            "api_key=sk-live-123",
            "api-key=sk-live-123",
            "apiKey=sk-live-123",
            "encryptionKey=abc123",
            "encryption_key=abc123",
            "encryption_session=abc123",
            "X-Encryption-Session: abc123",
            "privateKey=abc123",
            "private_key=abc123",
            "credentials={user:bob}",
            "Authorization: Bearer abc123",
            "clientSecret=abc123",
            "pwd=hunter2",
            "userToken=abc123",
        };
        for (String arg : sensitive) {
            assertThat(aspect.sanitizeArguments(new Object[] {arg}))
                    .as("argument should be redacted: %s", arg)
                    .isEqualTo("[[REDACTED]]");
        }
    }

    @Test
    @DisplayName("Sanitizer redacts raw JWT-shaped values even without a keyword")
    void sanitizerRedactsRawJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJib2IifQ.signaturepart";
        assertThat(aspect.sanitizeArguments(new Object[] {jwt})).isEqualTo("[[REDACTED]]");
    }

    @Test
    @DisplayName("Sanitizer keeps ordinary arguments readable")
    void sanitizerKeepsOrdinaryArguments() {
        assertThat(aspect.sanitizeArguments(new Object[] {"groceries", 42, true}))
                .isEqualTo("[groceries, 42, true]");
    }
}
