package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class HistoricalInvestmentValuationMigrationTest {
    @Test
    void preservesOpeningBalancesAndObservedSnapshotsWhileSeedingEncryptedDatedPrices()
            throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("PRAGMA foreign_keys=ON");
            sql.execute("CREATE TABLE users(id INTEGER PRIMARY KEY)");
            sql.execute(
                    "CREATE TABLE accounts(id INTEGER PRIMARY KEY, opening_date DATE, opening_balance TEXT)");
            sql.execute(
                    "CREATE TABLE assets(id INTEGER PRIMARY KEY, user_id INTEGER, asset_type TEXT, last_updated TEXT, current_price TEXT, currency TEXT)");
            sql.execute(
                    "CREATE TABLE net_worth(id INTEGER PRIMARY KEY, snapshot_date DATE, created_at TEXT, net_worth TEXT)");
            sql.execute("INSERT INTO users VALUES(1)");
            sql.execute("INSERT INTO accounts VALUES(1,'2026-11-11','cipher-opening-balance')");
            sql.execute(
                    "INSERT INTO assets VALUES(1,1,'CRYPTO','2026-11-11T08:00:00','cipher-price','EUR'),(2,1,'VEHICLE','2026-11-11','physical-price','EUR'),(3,1,'COMMODITY','2026-11-10','commodity-price','USD')");
            sql.execute(
                    "INSERT INTO net_worth VALUES(1,'2024-12-31','2026-11-11T08:00:00','derived'),(2,'2025-12-31','2025-12-31T08:00:00','observed')");
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource(
                            "db/migration/V114__historical_investment_valuations.sql"));
            assertThat(value(sql, "SELECT opening_date || ':' || opening_balance FROM accounts"))
                    .isEqualTo("2026-11-11:cipher-opening-balance");
            assertThat(value(sql, "SELECT COUNT(*) FROM net_worth")).isEqualTo("1");
            assertThat(value(sql, "SELECT net_worth FROM net_worth")).isEqualTo("observed");
            assertThat(value(sql, "SELECT reconstructed FROM net_worth")).isEqualTo("0");
            assertThat(value(sql, "SELECT COUNT(*) FROM asset_price_history")).isEqualTo("2");
            assertThat(
                            value(
                                    sql,
                                    "SELECT price_date || ':' || price || ':' || currency FROM asset_price_history WHERE asset_id=1"))
                    .isEqualTo("2026-11-11:cipher-price:EUR");
            sql.execute("DELETE FROM assets WHERE id=1");
            assertThat(value(sql, "SELECT COUNT(*) FROM asset_price_history")).isEqualTo("1");
            try (ResultSet invalid = sql.executeQuery("PRAGMA foreign_key_check")) {
                assertThat(invalid.next()).isFalse();
            }
        }
    }

    private String value(Statement sql, String query) throws Exception {
        try (ResultSet rows = sql.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
