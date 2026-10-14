package org.openfinance.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openfinance.service.InstitutionSeeder;
import org.openfinance.service.PayeeSeeder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;

class SplitTagsAndLogosMigrationTest {
    @Test
    void everySeededLocalLogoExistsInTheFrontendBundle() {
        for (Class<?> seeder : new Class<?>[] {PayeeSeeder.class, InstitutionSeeder.class}) {
            Map<?, ?> logos = (Map<?, ?>) ReflectionTestUtils.getField(seeder, "LOGO_PATHS");
            assertThat(logos).isNotEmpty();
            for (Object value : logos.values()) {
                if (value instanceof String path && path.startsWith("/logos/")) {
                    assertThat(Files.isRegularFile(Path.of("openfinance-ui/public" + path)))
                            .as("Bundled asset %s", path)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void preservesExistingSplitsAndCustomLogos() throws Exception {
        ClassPathResource tags =
                resourcePair("V100__add_split_tags.sql", "V26__add_split_tags.sql");
        ClassPathResource logos =
                resourcePair(
                        "V101__repair_missing_seed_logos.sql",
                        "V27__repair_missing_seed_logos.sql");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE transaction_splits (id INTEGER, amount TEXT, description TEXT)");
            sql.execute("INSERT INTO transaction_splits VALUES (1, '70', 'original')");
            sql.execute("CREATE TABLE payees (id INTEGER, logo TEXT)");
            sql.execute("CREATE TABLE institutions (id INTEGER, logo TEXT)");
            sql.execute(
                    "INSERT INTO payees VALUES (1, '/logos/payees/axa.png'), (2, '/logos/payees/rakuten.png'), (3, '/custom.png')");
            sql.execute(
                    "INSERT INTO institutions VALUES (1, '/logos/institutions/unicredit-austria.png'), (2, '/logos/institutions/caisse-epargne.png'), (3, '/custom.png')");
            ScriptUtils.executeSqlScript(connection, tags);
            ScriptUtils.executeSqlScript(connection, logos);
            try (ResultSet row =
                    sql.executeQuery("SELECT amount, description, tags FROM transaction_splits")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("70");
                assertThat(row.getString(2)).isEqualTo("original");
                assertThat(row.getString(3)).isNull();
            }
            for (String table : new String[] {"payees", "institutions"}) {
                try (ResultSet rows =
                        sql.executeQuery("SELECT logo FROM " + table + " ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1))
                            .isEqualTo(
                                    table.equals("payees")
                                            ? "/logos/institutions/axa-bank.png"
                                            : "/logos/institutions/unicredit.png");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNull();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("/custom.png");
                }
            }
        }
    }

    private ClassPathResource resourcePair(String sqlite, String postgres) throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/" + sqlite);
        assertThat(resource.getContentAsByteArray())
                .isEqualTo(
                        new ClassPathResource("db/postgresql-migration/" + postgres)
                                .getContentAsByteArray());
        return resource;
    }
}
