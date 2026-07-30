package org.openfinance.config;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * Unit tests for {@link SecurityConfig}'s default CORS allowed-origins.
 *
 * <p>Pins that the default value of {@code application.cors.allowed-origins} (used when the
 * property/environment variable is absent) contains only origins the frontend actually uses. {@code
 * http://localhost:5173} was a stale entry — the frontend's Vite dev server is pinned to port 3000
 * everywhere (vite.config.ts, README, Playwright config, .env files); 5173 was never used and
 * needlessly widened the CORS allow-list.
 */
@DisplayName("SecurityConfig CORS default origins")
class SecurityConfigCorsDefaultTest {

    @Test
    @DisplayName("default allowed-origins contains only the port the frontend actually uses (3000)")
    void defaultAllowedOriginsExcludesUnusedPort() throws NoSuchFieldException {
        Field field = SecurityConfig.class.getDeclaredField("allowedOrigins");
        Value valueAnnotation = field.getAnnotation(Value.class);
        assertThat(valueAnnotation).isNotNull();

        // SpEL placeholder syntax: "${property.key:default,value}" — extract the default part.
        String spel = valueAnnotation.value();
        String defaultPart = spel.substring(spel.indexOf(':') + 1, spel.length() - 1);
        List<String> defaultOrigins =
                Arrays.stream(defaultPart.split(",")).map(String::trim).toList();

        assertThat(defaultOrigins).contains("http://localhost:3000");
        assertThat(defaultOrigins).doesNotContain("http://localhost:5173");
    }
}
