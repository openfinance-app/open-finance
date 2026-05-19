package org.openfinance.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test database configuration that overrides the production SQLite configuration.
 *
 * <p>This configuration provides an H2 in-memory database for integration tests, avoiding JDBC
 * driver conflicts with SQLite. The {@code @Primary} annotation ensures this DataSource bean takes
 * precedence over the production DataSource.
 *
 * <p>Usage: Include this configuration in test classes with
 * {@code @Import(TestDatabaseConfig.class)} or activate the 'test' profile which auto-configures
 * H2.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@TestConfiguration
public class TestDatabaseConfig {

    /**
     * Creates an H2 in-memory DataSource for testing.
     *
     * <p>Configuration:
     *
     * <ul>
     *   <li>JDBC URL: jdbc:h2:mem:testdb (in-memory database)
     *   <li>DB_CLOSE_DELAY=-1: Keep database alive until JVM exits
     *   <li>MODE=MySQL: Use MySQL compatibility mode for broader SQL support
     *   <li>Connection pool size: 5 (sufficient for tests)
     * </ul>
     *
     * <p>The {@code @Primary} annotation ensures this bean takes precedence over the production
     * SQLite DataSource defined in {@link DatabaseConfig}.
     *
     * @return configured H2 DataSource for testing
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;MODE=MySQL");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setAutoCommit(true);
        config.setPoolName("HikariPool-Test");

        return new HikariDataSource(config);
    }
}
