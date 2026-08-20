package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("SearchTokenService Tests")
class SearchTokenServiceTest {

    private SearchTokenService searchTokenService;
    private SecretKey searchKey;

    @TempDir private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // A real temp file is used (rather than an in-memory DB) because DriverManagerDataSource
        // opens and closes a new physical connection per call, and a SQLite in-memory database
        // is destroyed the moment its last open connection closes.
        Path dbFile = Files.createTempFile(tempDir, "search-tokens-", ".db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbFile);
        dataSource.setUsername("");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                "CREATE TABLE search_tokens ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "user_id BIGINT NOT NULL, "
                        + "entity_type VARCHAR(30) NOT NULL, "
                        + "entity_id BIGINT NOT NULL, "
                        + "field_name VARCHAR(30) NOT NULL, "
                        + "token VARCHAR(16) NOT NULL"
                        + ")");

        searchTokenService = new SearchTokenService(jdbcTemplate);
        SecretKey encryptionKey =
                new SecretKeySpec(
                        "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8), "AES");
        searchKey = searchTokenService.deriveSearchKey(encryptionKey);
    }

    @Test
    @DisplayName("Search returns only results for the current user and entity type")
    void searchReturnsOnlyResultsForCurrentUserAndEntityType() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Mortgage Account", searchKey);
        searchTokenService.indexField(1L, "ASSET", 301L, "name", "Mortgage Account", searchKey);
        searchTokenService.indexField(2L, "ACCOUNT", 201L, "name", "Mortgage Account", searchKey);

        List<Long> results = searchTokenService.search(1L, "ACCOUNT", "mortgage", searchKey, 10);

        assertThat(results).containsExactly(101L);
    }

    @Test
    @DisplayName("Search supports partial matches through n-gram tokens")
    void searchSupportsPartialMatchesThroughNGramTokens() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Mortgage Account", searchKey);
        searchTokenService.indexField(1L, "ACCOUNT", 102L, "name", "Travel Fund", searchKey);

        List<Long> results = searchTokenService.search(1L, "ACCOUNT", "gage", searchKey, 10);

        assertThat(results).containsExactly(101L);
    }

    @Test
    @DisplayName("Search ranks entities by number of matching tokens")
    void searchRanksEntitiesByNumberOfMatchingTokens() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Salary Bonus", searchKey);
        searchTokenService.indexField(1L, "ACCOUNT", 102L, "name", "Salary", searchKey);

        List<Long> results =
                searchTokenService.search(1L, "ACCOUNT", "salary bonus", searchKey, 10);

        assertThat(results).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("Removing an entity deletes its search tokens")
    void removingAnEntityDeletesItsSearchTokens() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Travel Fund", searchKey);
        assertThat(searchTokenService.search(1L, "ACCOUNT", "travel", searchKey, 10))
                .containsExactly(101L);

        searchTokenService.removeEntity("ACCOUNT", 101L);

        assertThat(searchTokenService.search(1L, "ACCOUNT", "travel", searchKey, 10)).isEmpty();
    }
}
