package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AccountDeletionMigrationTest {
    @Test
    void distinguishesExplicitDeletionsFromClosuresAndInvalidatesOnlyDerivedTotals()
            throws Exception {
        ClassPathResource sqlite =
                new ClassPathResource("db/migration/V111__separate_account_deletion.sql");
        ClassPathResource postgres =
                new ClassPathResource("db/postgresql-migration/V37__separate_account_deletion.sql");
        assertThat(sqlite.getContentAsByteArray()).isEqualTo(postgres.getContentAsByteArray());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE accounts (id INTEGER, user_id INTEGER, is_active BOOLEAN, balance TEXT)");
            sql.execute(
                    "CREATE TABLE operation_history (id INTEGER, user_id INTEGER, entity_type TEXT, entity_id INTEGER, operation_type TEXT, created_at TEXT, undone_at TEXT, redone_at TEXT)");
            sql.execute("CREATE TABLE net_worth (net_worth TEXT)");
            sql.execute("INSERT INTO net_worth VALUES ('121150.21')");
            sql.execute(
                    "INSERT INTO accounts VALUES (1,1,FALSE,'30.4651395'), (2,1,FALSE,'100'), (3,1,FALSE,'200'), (4,1,FALSE,'300'), (5,1,TRUE,'400'), (6,1,FALSE,'500')");
            sql.execute(
                    """
                    INSERT INTO operation_history VALUES
                    (1,1,'ACCOUNT',2,'DELETE','2026-01-02T10:00:00',NULL,NULL),
                    (2,1,'ACCOUNT',3,'DELETE','2026-01-02T10:00:00','2026-01-03T10:00:00',NULL),
                    (3,1,'ACCOUNT',4,'DELETE','2026-01-02T10:00:00','2026-01-03T10:00:00','2026-01-04T10:00:00'),
                    (4,1,'ACCOUNT',5,'DELETE','2026-01-02T10:00:00',NULL,NULL),
                    (5,1,'ACCOUNT',5,'UPDATE','2026-01-03T10:00:00',NULL,NULL),
                    (6,1,'ACCOUNT',6,'DELETE','2026-01-02T10:00:00',NULL,NULL),
                    (7,1,'ACCOUNT',6,'UPDATE','2026-01-03T10:00:00',NULL,NULL)
                    """);
            ScriptUtils.executeSqlScript(connection, sqlite);
            ClassPathResource refresh =
                    new ClassPathResource("db/migration/V112__refresh_net_worth_valuations.sql");
            assertThat(refresh.getContentAsByteArray())
                    .isEqualTo(
                            new ClassPathResource(
                                            "db/postgresql-migration/V38__refresh_net_worth_valuations.sql")
                                    .getContentAsByteArray());
            ScriptUtils.executeSqlScript(connection, refresh);
            try (ResultSet rows =
                    sql.executeQuery("SELECT deleted_date,balance FROM accounts ORDER BY id")) {
                String[] dates = {null, "2026-01-02", null, "2026-01-04", null, null};
                String[] balances = {"30.4651395", "100", "200", "300", "400", "500"};
                for (int i = 0; i < dates.length; i++) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(dates[i]);
                    assertThat(rows.getString(2)).isEqualTo(balances[i]);
                }
            }
            try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM net_worth")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
