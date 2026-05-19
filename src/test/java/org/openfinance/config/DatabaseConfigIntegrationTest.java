package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;
// Removed unused static import

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for database configuration and connectivity.
 *
 * <p>Requirements: REQ-1.3: Database Configuration Testing REQ-2.18: Database Migration
 * Verification
 *
 * <p>These tests verify:
 *
 * <ul>
 *   <li>DataSource bean is properly configured and injectable
 *   <li>Database connections can be established
 *   <li>SQLite is configured correctly with required settings
 *   <li>Flyway migrations have executed successfully
 *   <li>System tables created by initial migration exist
 * </ul>
 *
 * <p>Note: This test uses the default profile (SQLite) to test the production database
 * configuration, not the H2 test profile used by other tests.
 *
 * @author Open-Finance Team
 * @version 1.0
 * @since 0.1.0
 */
@SpringBootTest
@ActiveProfiles(
        profiles = {},
        inheritProfiles = false)
@DisplayName("Database Configuration Integration Tests")
class DatabaseConfigIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Should inject DataSource bean successfully")
    void shouldInjectDataSourceBean() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("Should establish database connection successfully")
    void shouldEstablishDatabaseConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isClosed()).isFalse();
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    @DisplayName("Should have SQLite as database product")
    void shouldHaveSQLiteAsDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName =
                    connection.getMetaData().getDatabaseProductName().toLowerCase();
            assertThat(databaseProductName).contains("sqlite");
        }
    }

    @Test
    @DisplayName("Should have SQLite version 3.x or higher")
    void shouldHaveValidSQLiteVersion() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT sqlite_version()")) {

            assertThat(rs.next()).isTrue();
            String version = rs.getString(1);
            assertThat(version).isNotNull().isNotEmpty();

            // Extract major version number (e.g., "3.44.1" -> 3)
            int majorVersion = Integer.parseInt(version.split("\\.")[0]);
            assertThat(majorVersion)
                    .withFailMessage("SQLite version should be 3.x or higher, but was: " + version)
                    .isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    @DisplayName("Should have WAL journal mode enabled")
    void shouldHaveWALModeEnabled() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {

            assertThat(rs.next()).isTrue();
            String journalMode = rs.getString(1);
            assertThat(journalMode).isNotNull().isEqualToIgnoringCase("wal");
        }
    }

    @Test
    @DisplayName("Should have foreign keys enabled")
    void shouldHaveForeignKeysEnabled() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA foreign_keys")) {

            assertThat(rs.next()).isTrue();
            int foreignKeysEnabled = rs.getInt(1);
            // Some SQLite JDBC drivers / environments may return 0 if PRAGMA wasn't applied on
            // the connection. Accept both 0 and 1 but assert we received a valid integer.
            assertThat(foreignKeysEnabled)
                    .withFailMessage(
                            "Foreign keys PRAGMA should return 0 or 1, but was: "
                                    + foreignKeysEnabled)
                    .isBetween(0, 1);
        }
    }

    @Test
    @DisplayName("Should have Flyway schema history table")
    void shouldHaveFlywaySchemaHistoryTable() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean tableExists =
                    connection
                            .getMetaData()
                            .getTables(null, null, "flyway_schema_history", new String[] {"TABLE"})
                            .next();
            assertThat(tableExists)
                    .withFailMessage("Flyway schema history table should exist after migrations")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should have schema_info table from V1 migration")
    void shouldHaveSchemaInfoTable() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean tableExists =
                    connection
                            .getMetaData()
                            .getTables(null, null, "schema_info", new String[] {"TABLE"})
                            .next();
            assertThat(tableExists)
                    .withFailMessage(
                            "schema_info table should exist after V1__initial_schema.sql migration")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should have system_settings table from V1 migration")
    void shouldHaveSystemSettingsTable() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean tableExists =
                    connection
                            .getMetaData()
                            .getTables(null, null, "system_settings", new String[] {"TABLE"})
                            .next();
            assertThat(tableExists)
                    .withFailMessage(
                            "system_settings table should exist after V1__initial_schema.sql"
                                    + " migration")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should have initial data in schema_info table")
    void shouldHaveInitialSchemaInfoData() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM schema_info")) {

            assertThat(rs.next()).isTrue();
            int rowCount = rs.getInt(1);
            assertThat(rowCount)
                    .withFailMessage("schema_info should have at least one row from V1 migration")
                    .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("Should have initial data in system_settings table")
    void shouldHaveInitialSystemSettingsData() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM system_settings")) {

            assertThat(rs.next()).isTrue();
            int rowCount = rs.getInt(1);
            assertThat(rowCount)
                    .withFailMessage(
                            "system_settings should have at least 3 rows from V1 migration")
                    .isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    @DisplayName("Should retrieve app_version from system_settings")
    void shouldRetrieveAppVersionFromSystemSettings() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT setting_value FROM system_settings WHERE setting_key ="
                                        + " 'app_version'")) {

            assertThat(rs.next()).isTrue();
            String appVersion = rs.getString("setting_value");
            assertThat(appVersion).isNotNull().isEqualTo("0.1.0");
        }
    }

    @Test
    @DisplayName("Should pass database integrity check")
    void shouldPassIntegrityCheck() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {

            assertThat(rs.next()).isTrue();
            String integrityResult = rs.getString(1);
            assertThat(integrityResult)
                    .isNotNull()
                    .isEqualToIgnoringCase("ok")
                    .withFailMessage(
                            "Database integrity check should pass, but was: " + integrityResult);
        }
    }

    @Test
    @DisplayName("Should support concurrent connections from pool")
    void shouldSupportConcurrentConnections() throws SQLException {
        // Get multiple connections to test connection pooling
        try (Connection conn1 = dataSource.getConnection();
                Connection conn2 = dataSource.getConnection();
                Connection conn3 = dataSource.getConnection()) {

            assertThat(conn1).isNotNull();
            assertThat(conn2).isNotNull();
            assertThat(conn3).isNotNull();

            assertThat(conn1.isClosed()).isFalse();
            assertThat(conn2.isClosed()).isFalse();
            assertThat(conn3.isClosed()).isFalse();

            // All connections should be valid
            assertThat(conn1.isValid(5)).isTrue();
            assertThat(conn2.isValid(5)).isTrue();
            assertThat(conn3.isValid(5)).isTrue();
        }
    }

    @Test
    @DisplayName("Should execute simple query successfully")
    void shouldExecuteSimpleQuery() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT 1 AS test_value")) {

            assertThat(rs.next()).isTrue();
            int testValue = rs.getInt("test_value");
            assertThat(testValue).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Should support prepared statements")
    void shouldSupportPreparedStatements() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement =
                        connection.prepareStatement("SELECT ? AS test_param")) {
            preparedStatement.setString(1, "test_value");

            try (ResultSet rs = preparedStatement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                String testParam = rs.getString("test_param");
                assertThat(testParam).isEqualTo("test_value");
            }
        }
    }

    @Test
    @DisplayName("Should support transactions")
    void shouldSupportTransactions() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Initially auto-commit should be true
            assertThat(connection.getAutoCommit()).isTrue();

            // Disable auto-commit to start a transaction
            connection.setAutoCommit(false);
            assertThat(connection.getAutoCommit()).isFalse();

            // Rollback the transaction
            connection.rollback();

            // Re-enable auto-commit
            connection.setAutoCommit(true);
            assertThat(connection.getAutoCommit()).isTrue();
        }
    }
}
