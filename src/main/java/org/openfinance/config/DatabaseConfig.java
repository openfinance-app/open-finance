package org.openfinance.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Database configuration class for SQLite with HikariCP connection pooling.
 *
 * <p>Requirements: REQ-1.3: Database Configuration and Connection Pooling REQ-2.18: Data Migration
 * Management
 *
 * <p>This configuration sets up:
 *
 * <ul>
 *   <li>HikariCP connection pool with optimized settings for SQLite
 *   <li>SQLite-specific connection properties
 *   <li>Connection timeout and pool sizing
 *   <li>Statement caching for performance
 * </ul>
 *
 * <p>This configuration is active only for non-test profiles to avoid conflicts with test-specific
 * H2 database configuration.
 *
 * @author Open-Finance Team
 * @version 1.0
 * @since 0.1.0
 */
@Slf4j
@Configuration
@Profile("!test")
public class DatabaseConfig {

    private final String databaseUrl;

    private final String driverClassName;

    public DatabaseConfig(
            @Value("${spring.datasource.url}") String databaseUrl,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        this.databaseUrl = Objects.requireNonNull(databaseUrl, "spring.datasource.url must be set");
        this.driverClassName =
                Objects.requireNonNull(
                        driverClassName, "spring.datasource.driver-class-name must be set");
    }

    /**
     * Creates and configures a HikariCP DataSource bean for SQLite.
     *
     * <p>HikariCP is chosen for its high performance and reliability. Configuration is optimized
     * for SQLite which is a file-based database with specific constraints:
     *
     * <ul>
     *   <li>Limited concurrent write access (SQLite has single-writer limitation)
     *   <li>No server-side connection pooling required
     *   <li>Optimized for fast local file access
     * </ul>
     *
     * @return Configured HikariDataSource instance
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("Configuring HikariCP DataSource for SQLite database: {}", databaseUrl);

        HikariConfig config = new HikariConfig();

        // Basic connection settings
        config.setJdbcUrl(databaseUrl);
        config.setDriverClassName(driverClassName);
        config.setUsername(""); // SQLite doesn't require username
        config.setPassword(""); // SQLite doesn't require password

        // Connection pool settings optimized for SQLite WAL mode.
        // SQLite WAL supports multiple concurrent readers but only one concurrent writer.
        // A pool of 5 allows parallel read operations (e.g., SearchService async queries)
        // while serialisation of writes is enforced by SQLite itself.
        // SQLITE_BUSY_SNAPSHOT issues (stale WAL snapshots on the writer path) are
        // addressed by: (1) NOT holding long read transactions across write boundaries,
        // and (2) busy_timeout=10000 giving the writer time to retry.
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000); // 30 seconds timeout
        config.setIdleTimeout(600000); // 10 minutes idle timeout
        config.setMaxLifetime(1800000); // 30 minutes max lifetime

        // Performance settings
        config.setAutoCommit(true); // Enable auto-commit by default
        config.setConnectionTestQuery("SELECT 1"); // Lightweight test query
        config.setPoolName("OpenFinanceHikariPool");

        // SQLite-specific connection initialization: use connection init SQL to ensure
        // PRAGMA settings are applied on each new connection. This is more reliable than
        // attempting to pass driver properties via addDataSourceProperty which not all
        // SQLite drivers honor.
        StringBuilder initSql = new StringBuilder();
        initSql.append("PRAGMA journal_mode=WAL;");
        initSql.append("PRAGMA synchronous=NORMAL;");
        initSql.append("PRAGMA foreign_keys=ON;");
        initSql.append("PRAGMA busy_timeout=10000;");
        config.setConnectionInitSql(initSql.toString());

        // Statement caching for performance (if supported by the driver)
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        HikariDataSource dataSource = new HikariDataSource(config);

        log.info(
                "HikariCP DataSource configured successfully - Pool: {}, Max Pool Size: {}, Min"
                        + " Idle: {}",
                config.getPoolName(),
                config.getMaximumPoolSize(),
                config.getMinimumIdle());

        return dataSource;
    }
}
