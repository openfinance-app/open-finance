package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import db.migration.V102__remaining_exact_decimals;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemainingDecimalMigrationTest {
    @TempDir Path directory;

    @Test
    void preservesReferencesCiphertextTagsIndexesAndDeletedSequenceHighWaterMarks()
            throws Exception {
        try (Connection connection = legacyDatabase();
                Statement sql = connection.createStatement()) {
            new V102__remaining_exact_decimals().migrate(context(connection));
            assertThat(value(sql, "SELECT quantity FROM assets WHERE id=1"))
                    .isEqualTo("cipher-quantity");
            assertThat(value(sql, "SELECT purchase_price FROM assets WHERE id=1"))
                    .isEqualTo("cipher-price");
            assertThat(value(sql, "SELECT amount FROM transaction_splits WHERE id=1"))
                    .isEqualTo("cipher-split");
            assertThat(value(sql, "SELECT tags FROM transaction_splits WHERE id=1"))
                    .isEqualTo("cipher-tags");
            assertThat(value(sql, "SELECT asset_id FROM transactions WHERE id=1")).isEqualTo("1");
            assertThat(value(sql, "SELECT total_assets FROM net_worth WHERE id=1"))
                    .isEqualTo("cipher-assets");
            assertThat(value(sql, "SELECT seq FROM sqlite_sequence WHERE name='assets'"))
                    .isEqualTo("1000");
            String exact = "10006858.9112567483";
            sql.execute(
                    "UPDATE assets SET quantity='"
                            + exact
                            + "',purchase_price='"
                            + exact
                            + "',current_price='"
                            + exact
                            + "'");
            sql.execute("UPDATE transaction_splits SET amount='" + exact + "'");
            sql.execute(
                    "UPDATE net_worth SET total_assets='"
                            + exact
                            + "',total_liabilities='"
                            + exact
                            + "',net_worth='"
                            + exact
                            + "'");
            sql.execute(
                    "UPDATE interest_rate_variations SET rate='1.123456789012345678',tax_rate='9.876543210987654321'");
            for (String column : new String[] {"quantity", "purchase_price", "current_price"}) {
                assertThat(value(sql, "SELECT " + column + " FROM assets WHERE id=1"))
                        .isEqualTo(exact);
            }
            assertThat(value(sql, "SELECT amount FROM transaction_splits WHERE id=1"))
                    .isEqualTo(exact);
            assertThat(value(sql, "SELECT net_worth FROM net_worth WHERE id=1")).isEqualTo(exact);
            assertThat(value(sql, "SELECT rate FROM interest_rate_variations WHERE id=1"))
                    .isEqualTo("1.123456789012345678");
            assertThat(value(sql, "SELECT tax_rate FROM interest_rate_variations WHERE id=1"))
                    .isEqualTo("9.876543210987654321");
            assertThat(
                            value(
                                    sql,
                                    "SELECT COUNT(*) FROM sqlite_master WHERE name='idx_asset_user_id'"))
                    .isEqualTo("1");
            assertThat(value(sql, "PRAGMA foreign_keys")).isEqualTo("1");
            assertThat(value(sql, "PRAGMA integrity_check")).isEqualTo("ok");
            try (ResultSet references = sql.executeQuery("PRAGMA foreign_key_check")) {
                assertThat(references.next()).isFalse();
            }
        }
    }

    @Test
    void rollsBackTheEntireMigrationAndRestoresForeignKeysOnFailure() throws Exception {
        try (Connection connection = legacyDatabase();
                Statement sql = connection.createStatement()) {
            sql.execute("DROP TABLE interest_rate_variations");
            assertThatThrownBy(
                            () -> new V102__remaining_exact_decimals().migrate(context(connection)))
                    .isInstanceOf(Exception.class);
            assertThat(
                            value(
                                    sql,
                                    "SELECT type FROM pragma_table_info('assets') WHERE name='quantity'"))
                    .isEqualTo("DECIMAL(19, 8)");
            assertThat(value(sql, "SELECT quantity FROM assets WHERE id=1"))
                    .isEqualTo("cipher-quantity");
            assertThat(value(sql, "SELECT asset_id FROM transactions WHERE id=1")).isEqualTo("1");
            assertThat(value(sql, "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE '%_exact'"))
                    .isEqualTo("0");
            assertThat(value(sql, "PRAGMA foreign_keys")).isEqualTo("1");
            assertThat(connection.getAutoCommit()).isTrue();
        }
    }

    private Connection legacyDatabase() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("upgrade.db");
        Flyway.configure()
                .dataSource(url, "", "")
                .locations("classpath:db/migration")
                .mixed(true)
                .target("101")
                .load()
                .migrate();
        Connection connection = DriverManager.getConnection(url);
        try (Statement sql = connection.createStatement()) {
            sql.execute("PRAGMA foreign_keys=ON");
            sql.execute(
                    "INSERT INTO users(id,username,email,password_hash,master_password_salt) VALUES(1,'migration','migration@example.invalid','synthetic','synthetic')");
            sql.execute(
                    "INSERT INTO accounts(id,user_id,name,account_type,currency,balance) VALUES(1,1,'Migration','CHECKING','EUR','1')");
            sql.execute(
                    "INSERT INTO assets(id,user_id,name,asset_type,quantity,purchase_price,current_price,currency,purchase_date) VALUES(1,1,'Asset','STOCK','cipher-quantity','cipher-price',2.5,'EUR','2020-01-01')");
            sql.execute(
                    "INSERT INTO assets(id,user_id,name,asset_type,quantity,purchase_price,current_price,currency,purchase_date) VALUES(1000,1,'Deleted','STOCK',1,1,1,'EUR','2020-01-01')");
            sql.execute("DELETE FROM assets WHERE id=1000");
            sql.execute(
                    "INSERT INTO transactions(id,user_id,account_id,transaction_type,amount,currency,transaction_date,created_at,asset_id) VALUES(1,1,1,'INCOME','1','EUR','2020-01-01','2020-01-01',1)");
            sql.execute(
                    "INSERT INTO transaction_splits(id,transaction_id,amount,tags) VALUES(1,1,'cipher-split','cipher-tags')");
            sql.execute(
                    "INSERT INTO net_worth(id,user_id,snapshot_date,total_assets,total_liabilities,net_worth) VALUES(1,1,'2020-01-01','cipher-assets','cipher-liabilities','cipher-net-worth')");
            sql.execute(
                    "INSERT INTO interest_rate_variations(id,account_id,rate,tax_rate,valid_from) VALUES(1,1,1.25,30,'2020-01-01')");
        }
        return connection;
    }

    private Context context(Connection connection) {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        return context;
    }

    private String value(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
