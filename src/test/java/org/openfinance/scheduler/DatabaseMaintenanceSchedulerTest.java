package org.openfinance.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Database maintenance closes JDBC resources even after failures")
class DatabaseMaintenanceSchedulerTest {
    @Mock private DataSource source;
    @Mock private Connection connection;
    @Mock private Statement statement;
    @Mock private ResultSet results;
    @InjectMocks private DatabaseMaintenanceScheduler scheduler;

    @ParameterizedTest
    @ValueSource(strings = {"VACUUM", "ANALYZE"})
    void completesMaintenanceWithoutLeakingResources(String sql) throws Exception {
        prepare();
        if (sql.equals("VACUUM")) scheduler.runVacuum();
        else scheduler.runAnalyze();
        verify(statement).execute(sql);
        verify(statement).close();
        verify(connection).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ok", "corrupt page", ""})
    void integrityChecksAlwaysReleaseTheResultSet(String result) throws Exception {
        prepare();
        when(statement.executeQuery("PRAGMA integrity_check")).thenReturn(results);
        when(results.next()).thenReturn(!result.isEmpty());
        if (!result.isEmpty()) when(results.getString(1)).thenReturn(result);
        scheduler.runIntegrityCheck();
        verify(results).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void connectionFailuresDoNotTerminateScheduledExecution() throws Exception {
        when(source.getConnection()).thenThrow(new SQLException("Database busy"));
        scheduler.runVacuum();
        scheduler.runAnalyze();
        scheduler.runIntegrityCheck();
    }

    private void prepare() throws Exception {
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
    }
}
