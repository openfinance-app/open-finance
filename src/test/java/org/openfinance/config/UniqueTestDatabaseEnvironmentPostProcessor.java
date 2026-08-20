package org.openfinance.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Gives every Spring test {@code ApplicationContext} its own SQLite file under {@code
 * target/test-dbs/}, instead of the whole run sharing one file across the ~30 distinct contexts
 * Spring's test-context cache keeps alive simultaneously - the shared file let concurrent HikariCP
 * pools from different cached contexts corrupt it (SQLITE_CORRUPT).
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor
 * .imports}, so it runs once per distinct context creation (a cache hit skips it entirely), not
 * once per test class. Only rewrites {@code spring.datasource.url} when it matches the {@code
 * application-test.yml} marker, so non-test Spring Boot runs are unaffected.
 */
public class UniqueTestDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String MARKER_PREFIX = "jdbc:sqlite:target/openfinance-test.db";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String currentUrl = environment.getProperty("spring.datasource.url");
        if (currentUrl == null || !currentUrl.startsWith(MARKER_PREFIX)) {
            return;
        }

        try {
            Path dir = Path.of("target", "test-dbs");
            Files.createDirectories(dir);
            Path dbFile = Files.createTempFile(dir, "test-", ".db");
            String url =
                    "jdbc:sqlite:" + dbFile + "?foreign_keys=on&journal_mode=DELETE&busy_timeout=10000";
            environment
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "uniqueTestDatabase", Map.of("spring.datasource.url", url)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to allocate a unique test SQLite file", e);
        }
    }
}
