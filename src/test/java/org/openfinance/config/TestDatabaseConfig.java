package org.openfinance.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test database configuration that overrides the production SQLite datasource properties.
 *
 * <p>This configuration builds a {@link DataSource} as an explicit {@code @Primary} bean. It
 * exists for {@code @SpringBootTest} classes that need a guaranteed-available bean reference
 * (e.g. for direct JDBC cleanup) rather than relying purely on property-driven auto-configuration.
 *
 * <p>Usage: Include this configuration in test classes with
 * {@code @Import(TestDatabaseConfig.class)} together with {@code @ActiveProfiles("test")}.
 *
 * <p>By default this builds a SQLite file unique to the Spring context (see {@link
 * UniqueTestDatabaseEnvironmentPostProcessor} for why). The CI Postgres job overrides {@code
 * spring.datasource.*} via environment variables (see {@code backend-postgres.yml}); when those
 * properties resolve to a non-SQLite URL, this bean connects to that datasource directly instead
 * of allocating a SQLite file, so the exact same test classes run unmodified against either
 * database.
 *
 * @author Open-Finance Development Team
 * @version 3.0
 * @since 2026-01-30
 */
@TestConfiguration
public class TestDatabaseConfig {

    @Value("${spring.datasource.url}")
    private String configuredUrl;

    @Value("${spring.datasource.driver-class-name:org.sqlite.JDBC}")
    private String driverClassName;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    /**
     * Creates the test DataSource. When {@code spring.datasource.url} has been overridden (e.g. by
     * the Postgres CI job's environment variables) to a non-SQLite URL, connects to it directly.
     * Otherwise allocates a SQLite file unique to this Spring context.
     *
     * @return configured DataSource for testing
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        boolean isSqlite = configuredUrl.startsWith("jdbc:sqlite:");
        config.setJdbcUrl(
                isSqlite
                        ? "jdbc:sqlite:"
                                + allocateDbFile()
                                + "?foreign_keys=on&journal_mode=DELETE&busy_timeout=10000"
                        : configuredUrl);
        config.setDriverClassName(driverClassName);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setAutoCommit(true);
        config.setPoolName("HikariPool-Test");

        return new HikariDataSource(config);
    }

    private static Path allocateDbFile() {
        try {
            Path dir = Path.of("target", "test-dbs");
            Files.createDirectories(dir);
            return Files.createTempFile(dir, "test-", ".db");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to allocate a unique test SQLite file", e);
        }
    }
}

