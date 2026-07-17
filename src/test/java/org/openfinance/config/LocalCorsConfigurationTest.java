package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalCorsConfigurationTest {

    @Test
    @DisplayName("Local CORS examples should allow the Vite dev server origin")
    void localCorsExamplesShouldAllowViteDevServerOrigin() throws IOException {
        assertThat(Files.readString(Path.of(".env.example")))
                .contains("APPLICATION_CORS_ALLOWED_ORIGINS=")
                .contains("http://localhost:3000");

        assertThat(Files.readString(Path.of("docker-compose.yml")))
                .contains("APPLICATION_CORS_ALLOWED_ORIGINS")
                .contains("http://localhost:3000");
    }
}
