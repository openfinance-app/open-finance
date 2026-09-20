package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class RecurrenceAnchorMigrationTest {
    @Test
    void upgradesIsoDatesAndBothLegacyEpochFormatsWithoutLosingTheScheduledDay() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY)");
            statement.execute("INSERT INTO users VALUES (1)");
            statement.execute(
                    "CREATE TABLE recurring_transactions (id INTEGER PRIMARY KEY, next_occurrence TEXT NOT NULL)");
            long seconds =
                    LocalDate.of(2099, 1, 15)
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toEpochSecond();
            long millis =
                    LocalDate.of(2099, 1, 30)
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
            try (PreparedStatement insert =
                    connection.prepareStatement(
                            "INSERT INTO recurring_transactions(next_occurrence) VALUES (?)")) {
                for (String date :
                        new String[] {
                            "2099-01-31", Long.toString(seconds), Long.toString(millis)
                        }) {
                    insert.setString(1, date);
                    insert.executeUpdate();
                }
            }
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource(
                            "db/migration/V88__recurrence_anchor_and_session_revocation.sql"));
            try (ResultSet rows =
                    statement.executeQuery(
                            "SELECT anchor_day FROM recurring_transactions ORDER BY id")) {
                for (int expected : new int[] {31, 15, 30}) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(expected);
                }
                assertThat(rows.next()).isFalse();
            }
            try (ResultSet users = statement.executeQuery("SELECT token_version FROM users")) {
                assertThat(users.next()).isTrue();
                assertThat(users.getLong(1)).isZero();
            }
        }
    }
}
