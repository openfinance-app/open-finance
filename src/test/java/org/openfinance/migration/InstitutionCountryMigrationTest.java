package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class InstitutionCountryMigrationTest {
    @Test
    @DisplayName("Backfill system countries without changing custom, known or ambiguous values")
    void backfillsOnlyUnambiguousMissingSystemCountries() throws Exception {
        ClassPathResource sqlite =
                new ClassPathResource(
                        "db/migration/V113__backfill_system_institution_countries.sql");
        ClassPathResource postgres =
                new ClassPathResource(
                        "db/postgresql-migration/V39__backfill_system_institution_countries.sql");
        assertThat(sqlite.getContentAsByteArray()).isEqualTo(postgres.getContentAsByteArray());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE institutions (id INTEGER PRIMARY KEY, name TEXT, bic TEXT, country TEXT, is_system BOOLEAN)");
            sql.execute(
                    """
                    INSERT INTO institutions VALUES
                    (1, 'BNP Paribas', NULL, NULL, TRUE),
                    (2, 'Société Générale', 'SOGEFRPP', '  ', TRUE),
                    (3, 'ING', 'ingdesmm', NULL, TRUE),
                    (4, 'ING', 'INGBNL2A', NULL, TRUE),
                    (5, 'ING', NULL, NULL, TRUE),
                    (6, 'Deutsche Bank', 'DEUTDEFF', 'CH', TRUE),
                    (7, 'BNP Paribas', 'BNPAFRPP', NULL, FALSE),
                    (8, 'Unknown Bank', 'BNPAFRPP', NULL, TRUE),
                    (9, 'Trading 212', NULL, '', TRUE)
                    """);
            ScriptUtils.executeSqlScript(connection, sqlite);
            // Running the same repair again must preserve its result.
            ScriptUtils.executeSqlScript(connection, sqlite);
            Map<Integer, String> countries = new LinkedHashMap<>();
            try (ResultSet result =
                    sql.executeQuery("SELECT id, country FROM institutions ORDER BY id")) {
                while (result.next()) countries.put(result.getInt(1), result.getString(2));
            }
            assertThat(countries)
                    .containsEntry(1, "FR")
                    .containsEntry(2, "FR")
                    .containsEntry(3, "ES")
                    .containsEntry(4, "NL")
                    .containsEntry(5, null)
                    .containsEntry(6, "CH")
                    .containsEntry(7, null)
                    .containsEntry(8, null)
                    .containsEntry(9, "GB");
        }
    }
}
