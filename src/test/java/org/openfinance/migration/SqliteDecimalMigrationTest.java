package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import db.migration.V95__plaintext_decimal_storage;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDecimalMigrationTest {
    @TempDir Path directory;

    @Test
    void upgradesPopulatedTablesWithoutLosingReferencesCiphertextOrSequenceNumbers()
            throws Exception {
        try (Connection connection = legacyDatabase();
                Statement statement = connection.createStatement()) {
            new V95__plaintext_decimal_storage().migrate(context(connection));
            assertThat(value(statement, "SELECT balance FROM accounts WHERE id=1"))
                    .isEqualTo("ciphertext-account");
            assertThat(value(statement, "SELECT amount FROM transactions WHERE id=1"))
                    .isEqualTo("ciphertext-transaction");
            assertThat(value(statement, "SELECT amount FROM transactions_archive WHERE id=1"))
                    .isEqualTo("ciphertext-transaction");
            assertThat(value(statement, "SELECT amount FROM recurring_transactions WHERE id=1"))
                    .isEqualTo("ciphertext-recurring");
            statement.execute("UPDATE transactions SET amount='/ciphertext'");
            statement.execute("UPDATE recurring_transactions SET amount='+ciphertext'");
            assertThat(value(statement, "SELECT seq FROM sqlite_sequence WHERE name='accounts'"))
                    .isEqualTo("1000");
            String precise = "0.001000000000000001";
            statement.execute(
                    "UPDATE accounts SET balance='"
                            + precise
                            + "', opening_balance='"
                            + precise
                            + "'");
            for (String table :
                    new String[] {
                        "transactions", "transactions_archive", "recurring_transactions"
                    }) {
                statement.execute("UPDATE " + table + " SET amount='" + precise + "'");
                assertThat(value(statement, "SELECT amount FROM " + table + " WHERE id=1"))
                        .isEqualTo(precise);
            }
            assertThat(value(statement, "SELECT balance FROM accounts WHERE id=1"))
                    .isEqualTo(precise);
            assertThat(value(statement, "SELECT opening_balance FROM accounts WHERE id=1"))
                    .isEqualTo(precise);
            try (ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
                assertThat(violations.next()).isFalse();
            }
            assertThat(value(statement, "PRAGMA foreign_keys")).isEqualTo("1");
            assertThat(connection.getAutoCommit()).isTrue();
        }
    }

    @Test
    void rollsBackEarlierTableRebuildsWhenALaterTableCannotBeMigrated() throws Exception {
        try (Connection connection = legacyDatabase();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE recurring_transactions");
            assertThatThrownBy(
                            () -> new V95__plaintext_decimal_storage().migrate(context(connection)))
                    .isInstanceOf(Exception.class);
            assertThat(value(statement, "SELECT balance FROM accounts WHERE id=1"))
                    .isEqualTo("ciphertext-account");
            assertThat(value(statement, "SELECT amount FROM transactions WHERE id=1"))
                    .isEqualTo("ciphertext-transaction");
            assertThat(
                            value(
                                    statement,
                                    "SELECT type FROM pragma_table_info('accounts') WHERE name='balance'"))
                    .isEqualTo("NUMERIC(19, 4)");
            assertThat(
                            value(
                                    statement,
                                    "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE '%_decimal'"))
                    .isEqualTo("0");
            assertThat(value(statement, "PRAGMA foreign_keys")).isEqualTo("1");
            assertThat(connection.getAutoCommit()).isTrue();
        }
    }

    private Connection legacyDatabase() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("upgrade.db");
        Flyway.configure()
                .dataSource(url, "", "")
                .locations("classpath:db/migration")
                .mixed(true)
                .target("94")
                .load()
                .migrate();
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute(
                    "INSERT INTO users(id, username, email, password_hash, master_password_salt) VALUES (1, 'migration', 'migration@example.invalid', 'synthetic', 'synthetic')");
            statement.execute(
                    "INSERT INTO accounts(id,user_id,name,account_type,currency,balance) VALUES (1,1,'Migration','CHECKING','BTC','ciphertext-account')");
            statement.execute(
                    "INSERT INTO accounts(id,user_id,name,account_type,currency,balance) VALUES (1000,1,'Deleted','CHECKING','BTC','0')");
            statement.execute("DELETE FROM accounts WHERE id=1000");
            statement.execute(
                    "INSERT INTO transactions(id,user_id,account_id,transaction_type,amount,currency,transaction_date,created_at) VALUES (1,1,1,'INCOME','ciphertext-transaction','BTC','2026-01-01','2026-01-01')");
            statement.execute(
                    "INSERT INTO transactions_archive(id,user_id,account_id,transaction_type,amount,currency,transaction_date,created_at) VALUES (1,1,1,'INCOME','ciphertext-transaction','BTC','2026-01-01','2026-01-01')");
            statement.execute(
                    "INSERT INTO recurring_transactions(id,user_id,account_id,transaction_type,amount,currency,description,frequency,next_occurrence,created_at,updated_at) VALUES (1,1,1,'INCOME','ciphertext-recurring','BTC','Migration','MONTHLY','2099-01-01','2026-01-01','2026-01-01')");
        }
        return connection;
    }

    private Context context(Connection connection) {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        return context;
    }

    private String value(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
