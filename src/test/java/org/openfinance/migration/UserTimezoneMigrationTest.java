package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class UserTimezoneMigrationTest {
    @Test
    void defaultsToDeviceAndPreservesExistingRegionalOverrides() throws Exception {
        ClassPathResource sqlite =
                new ClassPathResource("db/migration/V122__user_display_timezone.sql");
        ClassPathResource postgres =
                new ClassPathResource("db/postgresql-migration/V48__user_display_timezone.sql");
        assertThat(sqlite.getContentAsByteArray()).isEqualTo(postgres.getContentAsByteArray());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE user_settings (id INTEGER PRIMARY KEY, timezone VARCHAR(255) NOT NULL DEFAULT 'UTC')");
            sql.execute("INSERT INTO user_settings VALUES (1, 'UTC'), (2, 'Europe/Paris')");
            ScriptUtils.executeSqlScript(connection, sqlite);
            ClassPathResource deviceSqlite =
                    new ClassPathResource("db/migration/V123__reporting_device_timezone.sql");
            ClassPathResource devicePostgres =
                    new ClassPathResource(
                            "db/postgresql-migration/V49__reporting_device_timezone.sql");
            assertThat(deviceSqlite.getContentAsByteArray())
                    .isEqualTo(devicePostgres.getContentAsByteArray());
            ScriptUtils.executeSqlScript(connection, deviceSqlite);
            try (ResultSet zones =
                    sql.executeQuery("SELECT last_device_timezone FROM user_settings")) {
                while (zones.next()) assertThat(zones.getString(1)).isEqualTo("UTC");
            }
            sql.execute("INSERT INTO user_settings (id) VALUES (3)");
            try (ResultSet result =
                    sql.executeQuery(
                            "SELECT timezone, use_device_timezone FROM user_settings ORDER BY id")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("UTC");
                assertThat(result.getBoolean(2)).isTrue();
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("Europe/Paris");
                assertThat(result.getBoolean(2)).isFalse();
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(2)).isTrue();
            }
        }
    }
}
