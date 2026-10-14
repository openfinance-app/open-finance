package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class ImportedCategoryIconMigrationTest {

    @Test
    void repairsLegacyImportIconsWithoutChangingCustomOrSystemIcons() throws Exception {
        ClassPathResource sqlite =
                new ClassPathResource("db/migration/V99__repair_imported_category_icons.sql");
        ClassPathResource postgresql =
                new ClassPathResource(
                        "db/postgresql-migration/V25__repair_imported_category_icons.sql");
        assertThat(sqlite.getContentAsByteArray()).isEqualTo(postgresql.getContentAsByteArray());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE categories (id INTEGER, icon TEXT, is_system BOOLEAN)");
            statement.execute(
                    "INSERT INTO categories VALUES (1, 'tag', FALSE), (2, '🚗', FALSE), (3, NULL, FALSE), (4, 'tag', TRUE)");

            ScriptUtils.executeSqlScript(connection, sqlite);

            try (ResultSet rows =
                    statement.executeQuery("SELECT icon FROM categories ORDER BY id")) {
                for (String expected : new String[] {"📁", "🚗", null, "tag"}) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(expected);
                }
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
