package org.openfinance.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Gives every Spring test {@code ApplicationContext} its own isolated database underneath {@code
 * target/test-dbs/} (SQLite) or a fresh per-context PostgreSQL database, instead of the whole run
 * sharing one database across the ~30 distinct contexts Spring's test-context cache keeps alive
 * simultaneously - the shared state let concurrent HikariCP pools from different cached contexts
 * corrupt or pollute each other.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor
 * .imports}, so it runs once per distinct context creation (a cache hit skips it entirely), not
 * once per test class. Only rewrites {@code spring.datasource.url} when it matches the {@code
 * application-test.yml} marker (SQLite) or points at the shared PostgreSQL CI database, so non-test
 * Spring Boot runs are unaffected.
 */
@Slf4j
public class UniqueTestDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String MARKER_PREFIX = "jdbc:sqlite:target/openfinance-test.db";

    /** The CI Postgres job's datasource URL (backed by the openfinance PostgreSQL service). */
    private static final String PG_PREFIX = "jdbc:postgresql:";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String currentUrl = environment.getProperty("spring.datasource.url");
        if (currentUrl == null) {
            return;
        }

        if (currentUrl.startsWith(MARKER_PREFIX)) {
            rewriteSqliteUrl(environment);
        } else if (currentUrl.startsWith(PG_PREFIX)) {
            rewritePostgresUrl(environment, currentUrl);
        }
    }

    /**
     * Allocates a fresh SQLite file for each test context (historic behaviour).
     *
     * @param environment environment to inject the unique URL into
     */
    private void rewriteSqliteUrl(ConfigurableEnvironment environment) {
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

    /**
     * Allocates a fresh PostgreSQL database for each test context, mirroring the per-context SQLite
     * file. Without this, every context in the CI Postgres job shares the single connection service
     * database and test classes pollute each other (duplicate-key failures for shared fixtures such
     * as {@code users.username}).
     *
     * <p>If the configured user lacks {@code CREATEDB} privilege (e.g. local sandbox), falls back to
     * the original database and logs a warning - parallel contexts may then interfere.
     *
     * @param environment environment to inject the unique URL into
     * @param currentUrl the CI-provided PostgreSQL URL
     */
    private void rewritePostgresUrl(ConfigurableEnvironment environment, String currentUrl) {
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        String uniqueName = "openfinance_test_" + UUID.randomUUID().toString().replace("-", "");

        try {
            createUniqueDatabase(currentUrl, username, password, uniqueName);
        } catch (SQLException e) {
            log.warn(
                    "Unable to create per-context PostgreSQL test database '{}' ({} {}); falling back"
                            + " to the shared CI database",
                    uniqueName,
                    e.getClass().getSimpleName(),
                    e.getMessage());
            return;
        }

        String uniqueUrl = currentUrl.replaceFirst("/([^/?]+)(\\?|$)", "/" + uniqueName + "$2");
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "uniqueTestDatabase", Map.of("spring.datasource.url", uniqueUrl)));
    }

    /**
     * Creates the per-context database via a bare JDBC connection to the shared CI database.
     *
     * @param adminUrl the CI-provided PostgreSQL URL (bootstrap connection)
     * @param username the configured datasource user
     * @param password the configured datasource password
     * @param dbName the unique database name to create
     * @throws SQLException if creation fails (e.g. the user lacks CREATEDB)
     */
    private void createUniqueDatabase(String adminUrl, String username, String password, String dbName)
            throws SQLException {
        try (Connection conn = DriverManager.getConnection(adminUrl, username, password);
                Statement stmt = conn.createStatement()) {
            setStatementTimeout(stmt);
            stmt.execute("CREATE DATABASE \"" + dbName + "\"");
        }
    }

    private static void setStatementTimeout(Statement stmt) throws SQLException {
        stmt.setQueryTimeout(30);
    }
}
