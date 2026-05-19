package org.openfinance.config;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.util.QueryPlanAnalyzer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Database initializer component that verifies database connectivity and schema on application
 * startup.
 *
 * <p>Requirements: REQ-1.3: Database Initialization and Health Checks REQ-2.18: Database Migration
 * Verification
 *
 * <p>This component performs the following checks:
 *
 * <ul>
 *   <li>Verifies database connection is successful
 *   <li>Checks SQLite version and configuration
 *   <li>Validates that required tables exist
 *   <li>Logs database metadata for troubleshooting
 *   <li>Ensures WAL mode is enabled for better concurrency
 * </ul>
 *
 * <p>This component is active only for non-test profiles to avoid SQLite-specific checks running
 * against H2 test database.
 *
 * @author Open-Finance Team
 * @version 1.0
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class DatabaseInitializer {

    private final DataSource dataSource;
    private final QueryPlanAnalyzer queryPlanAnalyzer;

    /**
     * Initializes database connection and performs startup checks. This method runs after all beans
     * are initialized but before the application is fully started.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing database connection...");
        try {
            verifyDatabaseConnection();
        } catch (SQLException e) {
            log.error("Failed to initialize database connection", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Performs comprehensive database verification after the application is fully started. This
     * event listener runs after all Spring components are ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready - performing database health checks");
        try {
            performDatabaseHealthCheck();
            queryPlanAnalyzer.analyzeCriticalQueries();
        } catch (SQLException e) {
            log.error("Database health check failed", e);
            // Don't throw exception here - application is already started
            // Just log the error for monitoring
        }
    }

    /**
     * Verifies that a database connection can be established.
     *
     * @throws SQLException if connection fails
     */
    private void verifyDatabaseConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (connection != null && !connection.isClosed()) {
                log.info("Database connection established successfully");
                logDatabaseMetadata(connection);
            } else {
                throw new SQLException("Failed to establish database connection");
            }
        }
    }

    /**
     * Logs database metadata information for troubleshooting and monitoring.
     *
     * @param connection Active database connection
     * @throws SQLException if metadata retrieval fails
     */
    private void logDatabaseMetadata(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        log.info(
                "Database Product: {} {}",
                metaData.getDatabaseProductName(),
                metaData.getDatabaseProductVersion());
        log.info("JDBC Driver: {} {}", metaData.getDriverName(), metaData.getDriverVersion());
        log.info("Database URL: {}", metaData.getURL());
        log.info("Max Connections: {}", metaData.getMaxConnections());
        log.info("Transaction Isolation: {}", connection.getTransactionIsolation());
    }

    /**
     * Performs comprehensive database health checks including:
     *
     * <ul>
     *   <li>Verifying SQLite configuration (WAL mode, foreign keys)
     *   <li>Checking that required system tables exist
     *   <li>Validating database file integrity
     * </ul>
     *
     * @throws SQLException if health check fails
     */
    private void performDatabaseHealthCheck() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            // Check SQLite version
            try (ResultSet rs = statement.executeQuery("SELECT sqlite_version()")) {
                if (rs.next()) {
                    String sqliteVersion = rs.getString(1);
                    log.info("SQLite version: {}", sqliteVersion);
                }
            }

            // Verify WAL mode is enabled for better concurrency
            try (ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    String journalMode = rs.getString(1);
                    log.info("SQLite journal mode: {}", journalMode);
                    if (!"wal".equalsIgnoreCase(journalMode)) {
                        log.warn(
                                "WAL mode is not enabled. This may affect concurrent access"
                                        + " performance.");
                    }
                }
            }

            // Verify foreign keys are enabled
            try (ResultSet rs = statement.executeQuery("PRAGMA foreign_keys")) {
                if (rs.next()) {
                    int foreignKeysEnabled = rs.getInt(1);
                    log.info("Foreign keys enabled: {}", foreignKeysEnabled == 1);
                    if (foreignKeysEnabled == 0) {
                        log.warn(
                                "Foreign key constraints are not enabled. Data integrity may be at"
                                        + " risk.");
                    }
                }
            }

            // Check if Flyway schema history table exists
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs =
                    metaData.getTables(
                            null, null, "flyway_schema_history", new String[] {"TABLE"})) {
                if (rs.next()) {
                    log.info("Flyway schema history table exists - migrations are being tracked");
                } else {
                    log.info(
                            "Flyway schema history table not found - will be created on first"
                                    + " migration");
                }
            }

            // Check if system tables exist
            checkSystemTables(connection);

            // Run database integrity check
            try (ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
                if (rs.next()) {
                    String integrityResult = rs.getString(1);
                    if ("ok".equalsIgnoreCase(integrityResult)) {
                        log.info("Database integrity check: PASSED");
                    } else {
                        log.error("Database integrity check FAILED: {}", integrityResult);
                    }
                }
            }

            log.info("Database health check completed successfully");
        }
    }

    /**
     * Checks if system tables created by initial migration exist.
     *
     * @param connection Active database connection
     * @throws SQLException if table check fails
     */
    private void checkSystemTables(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        // Check schema_info table
        try (ResultSet rs = metaData.getTables(null, null, "schema_info", new String[] {"TABLE"})) {
            if (rs.next()) {
                log.info("System table 'schema_info' exists");
            } else {
                log.warn(
                        "System table 'schema_info' not found - initial migration may not have"
                                + " run");
            }
        }

        // Check system_settings table
        try (ResultSet rs =
                metaData.getTables(null, null, "system_settings", new String[] {"TABLE"})) {
            if (rs.next()) {
                log.info("System table 'system_settings' exists");
            } else {
                log.warn(
                        "System table 'system_settings' not found - initial migration may not have"
                                + " run");
            }
        }
    }
}
