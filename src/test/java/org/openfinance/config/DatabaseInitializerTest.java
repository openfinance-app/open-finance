package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.util.QueryPlanAnalyzer;

class DatabaseInitializerTest {

    private QueryPlanAnalyzer queryPlanAnalyzer;

    @BeforeEach
    void setUp() {
        queryPlanAnalyzer = mock(QueryPlanAnalyzer.class);
    }

    @Test
    @DisplayName("Should verify database connection on init when connection is valid")
    void shouldVerifyDatabaseConnectionOnInitWhenConnectionValid() throws Exception {
        // Arrange
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.isClosed()).thenReturn(false);
        when(conn.getMetaData()).thenReturn(meta);

        DatabaseInitializer initializer = new DatabaseInitializer(ds, queryPlanAnalyzer);

        // Act & Assert - should not throw
        assertDoesNotThrow(initializer::init);
    }

    @Test
    @DisplayName("Should throw RuntimeException on init when SQLException occurs")
    void shouldThrowRuntimeExceptionOnInitWhenSQLException() throws Exception {
        // Arrange
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("boom"));

        DatabaseInitializer initializer = new DatabaseInitializer(ds, queryPlanAnalyzer);

        // Act / Assert
        RuntimeException ex = assertThrows(RuntimeException.class, initializer::init);
        assertThat(ex.getMessage()).contains("Database initialization failed");
        assertThat(ex.getCause()).isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("Should perform health check without exceptions when all queries succeed")
    void shouldPerformDatabaseHealthCheckWhenQueriesSucceed() throws Exception {
        // Arrange
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rsVersion = mock(ResultSet.class);
        ResultSet rsJournal = mock(ResultSet.class);
        ResultSet rsForeign = mock(ResultSet.class);
        ResultSet rsIntegrity = mock(ResultSet.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        ResultSet rsTables = mock(ResultSet.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        // sqlite_version
        when(stmt.executeQuery("SELECT sqlite_version()")).thenReturn(rsVersion);
        when(rsVersion.next()).thenReturn(true);
        when(rsVersion.getString(1)).thenReturn("3.40.0");

        // journal_mode
        when(stmt.executeQuery("PRAGMA journal_mode")).thenReturn(rsJournal);
        when(rsJournal.next()).thenReturn(true);
        when(rsJournal.getString(1)).thenReturn("wal");

        // foreign_keys
        when(stmt.executeQuery("PRAGMA foreign_keys")).thenReturn(rsForeign);
        when(rsForeign.next()).thenReturn(true);
        when(rsForeign.getInt(1)).thenReturn(1);

        // integrity_check
        when(stmt.executeQuery("PRAGMA integrity_check")).thenReturn(rsIntegrity);
        when(rsIntegrity.next()).thenReturn(true);
        when(rsIntegrity.getString(1)).thenReturn("ok");

        // meta data and tables
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getTables(null, null, "flyway_schema_history", new String[] {"TABLE"}))
                .thenReturn(rsTables);
        when(rsTables.next()).thenReturn(true);

        when(meta.getTables(null, null, "schema_info", new String[] {"TABLE"}))
                .thenReturn(rsTables);
        when(meta.getTables(null, null, "system_settings", new String[] {"TABLE"}))
                .thenReturn(rsTables);

        DatabaseInitializer initializer = new DatabaseInitializer(ds, queryPlanAnalyzer);

        // Use reflection to call private method performDatabaseHealthCheck
        assertDoesNotThrow(
                () -> {
                    var method =
                            DatabaseInitializer.class.getDeclaredMethod(
                                    "performDatabaseHealthCheck");
                    method.setAccessible(true);
                    method.invoke(initializer);
                });
    }

    @Test
    @DisplayName("Should handle SQLException in onApplicationReady without throwing")
    void shouldHandleSQLExceptionInOnApplicationReady() throws Exception {
        // Arrange
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("fail"));
        DatabaseInitializer initializer = new DatabaseInitializer(ds, queryPlanAnalyzer);

        // Should not throw because onApplicationReady catches SQLException
        assertDoesNotThrow(initializer::onApplicationReady);
    }
}
