package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("SearchTokenService Tests")
class SearchTokenServiceTest {

    private SearchTokenService searchTokenService;
    private SecretKey searchKey;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:search-tokens-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                "CREATE TABLE search_tokens ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
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
