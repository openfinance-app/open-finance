package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
    @DisplayName("searchAll returns type+id pairs across entity types for the current user only")
    void searchAllAcrossTypes() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Mortgage Account", searchKey);
        searchTokenService.indexField(1L, "ASSET", 301L, "name", "Mortgage Bond", searchKey);
        searchTokenService.indexField(2L, "ACCOUNT", 201L, "name", "Mortgage Account", searchKey);

        List<Object[]> results = searchTokenService.searchAll(1L, "mortgage", searchKey, 10);

        assertThat(results).hasSize(2);
        assertThat(results)
                .anySatisfy(pair -> assertThat(pair).containsExactly("ACCOUNT", 101L))
                .anySatisfy(pair -> assertThat(pair).containsExactly("ASSET", 301L));
    }

    @Test
    @DisplayName("Search without entity type spans types; same id in two types appears twice")
    void searchWithoutTypeSpansTypes() {
        searchTokenService.indexField(1L, "ACCOUNT", 55L, "name", "Travel Fund", searchKey);
        searchTokenService.indexField(1L, "ASSET", 55L, "name", "Travel Stock", searchKey);

        // Ids alone are ambiguous across types — callers needing types must use searchAll.
        assertThat(searchTokenService.search(1L, null, "travel", searchKey, 10))
                .containsExactly(55L, 55L);
        assertThat(searchTokenService.searchAll(1L, "travel", searchKey, 10)).hasSize(2);
    }

    @Test
    @DisplayName("Blank and single-character queries match nothing")
    void degenerateQueriesMatchNothing() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "A Fund", searchKey);

        assertThat(searchTokenService.search(1L, "ACCOUNT", "", searchKey, 10)).isEmpty();
        assertThat(searchTokenService.search(1L, "ACCOUNT", "   ", searchKey, 10)).isEmpty();
        assertThat(searchTokenService.search(1L, "ACCOUNT", "a", searchKey, 10)).isEmpty();
        assertThat(searchTokenService.search(1L, "ACCOUNT", null, searchKey, 10)).isEmpty();
        assertThat(searchTokenService.searchAll(1L, "a", searchKey, 10)).isEmpty();
    }

    @Test
    @DisplayName("Limit truncates the ranked result list")
    void limitTruncatesResults() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Salary Bonus", searchKey);
        searchTokenService.indexField(1L, "ACCOUNT", 102L, "name", "Salary", searchKey);
        searchTokenService.indexField(1L, "ACCOUNT", 103L, "name", "Salary", searchKey);

        assertThat(searchTokenService.search(1L, "ACCOUNT", "salary bonus", searchKey, 1))
                .containsExactly(101L);
        assertThat(searchTokenService.search(1L, "ACCOUNT", "salary", searchKey, 0)).isEmpty();
    }

    @Test
    @DisplayName("Re-indexing a field replaces its stale tokens")
    void reindexReplacesStaleTokens() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Travel Fund", searchKey);
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Rent Money", searchKey);

        assertThat(searchTokenService.search(1L, "ACCOUNT", "travel", searchKey, 10)).isEmpty();
        assertThat(searchTokenService.search(1L, "ACCOUNT", "rent", searchKey, 10))
                .containsExactly(101L);
    }

    @Test
    @DisplayName("Indexing a blank value clears the field's tokens")
    void blankIndexClearsField() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Travel Fund", searchKey);
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", " ", searchKey);

        assertThat(searchTokenService.search(1L, "ACCOUNT", "travel", searchKey, 10)).isEmpty();
    }

    @Test
    @DisplayName("indexEntity indexes well-formed pairs and skips malformed ones")
    void indexEntitySkipsMalformedPairs() {
        searchTokenService.indexEntity(
                1L,
                "ACCOUNT",
                101L,
                List.of(
                        new String[] {"name", "Harbour View"},
                        new String[] {"only-one-element"},
                        new String[] {"notes", "Seaside", "extra"},
                        new String[] {"memo", null}),
                searchKey);

        assertThat(searchTokenService.search(1L, "ACCOUNT", "harbour", searchKey, 10))
                .containsExactly(101L);
        assertThat(searchTokenService.search(1L, "ACCOUNT", "seaside", searchKey, 10)).isEmpty();
    }

    @Test
    @DisplayName("Tokens are keyed: another user's search key finds nothing")
    void tokensAreKeyBound() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Secret Stash", searchKey);

        SecretKey otherKey =
                searchTokenService.deriveSearchKey(
                        new SecretKeySpec(
                                "99999999999999999999999999999999".getBytes(StandardCharsets.UTF_8),
                                "AES"));

        assertThat(searchTokenService.search(1L, "ACCOUNT", "secret", otherKey, 10)).isEmpty();
        assertThat(searchTokenService.generateTokens("Secret Stash", otherKey))
                .isNotEqualTo(searchTokenService.generateTokens("Secret Stash", searchKey));
    }

    @Test
    @DisplayName("Tokens are deterministic 16-char lowercase hex")
    void tokenShapeAndDeterminism() {
        Set<String> first = searchTokenService.generateTokens("Mortgage", searchKey);
        Set<String> second = searchTokenService.generateTokens("MORTGAGE", searchKey);
        assertThat(first).isEqualTo(second);
        assertThat(first).allMatch(token -> token.matches("[0-9a-f]{16}"));
    }

    @Test
    @DisplayName("Search is case-insensitive; accent matching only via shared trigrams")
    void accentSensitivity() {
        searchTokenService.indexField(1L, "ACCOUNT", 101L, "name", "Café Été", searchKey);

        assertThat(searchTokenService.search(1L, "ACCOUNT", "CAFÉ", searchKey, 10))
                .containsExactly(101L);
        // "cafe" still matches: it shares the trigram "caf" with "café" (partial matching).
        assertThat(searchTokenService.search(1L, "ACCOUNT", "cafe", searchKey, 10))
                .containsExactly(101L);
        // But tokens are exact HMACs: "ete" shares no token/trigram with "été".
        assertThat(searchTokenService.search(1L, "ACCOUNT", "ete", searchKey, 10)).isEmpty();
        assertThat(searchTokenService.search(1L, "ACCOUNT", "été", searchKey, 10))
                .containsExactly(101L);
    }

    @Test
    @DisplayName("Unusable keys fail with IllegalStateException, not silent empty tokens")
    void unusableKeyFailsLoudly() {
        SecretKey broken =
                new SecretKey() {
                    @Override
                    public String getAlgorithm() {
                        return "HmacSHA256";
                    }

                    @Override
                    public String getFormat() {
                        return "RAW";
                    }

                    @Override
                    public byte[] getEncoded() {
                        return null;
                    }
                };
        assertThatThrownBy(() -> searchTokenService.deriveSearchKey(broken))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> searchTokenService.generateTokens("anything", broken))
                .isInstanceOf(IllegalStateException.class);
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
