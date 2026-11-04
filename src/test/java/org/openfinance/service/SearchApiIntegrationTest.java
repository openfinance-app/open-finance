package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Authenticated search over encrypted database records")
class SearchApiIntegrationTest extends AuditApiTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void identicalSearchTermsCannotExposeAnotherUsersRecords(boolean advanced) throws Exception {
        Auth other = register();
        long ownAccount = account(owner);
        long otherAccount = account(other);
        long ownTransaction = payment(owner, ownAccount);
        long otherTransaction = payment(other, otherAccount);
        String storedDescription =
                jdbc.queryForObject(
                        "SELECT description FROM transactions WHERE id = ?",
                        String.class,
                        ownTransaction);
        assertThat(storedDescription).isNotEqualTo("Private payment").doesNotContain("Private");

        assertPrivateResults(search(owner, advanced), ownAccount, ownTransaction);
        assertPrivateResults(search(other, advanced), otherAccount, otherTransaction);
        // Switch back to the first encryption session after reusing the search worker pool.
        assertPrivateResults(search(owner, advanced), ownAccount, ownTransaction);
    }

    @Test
    void searchExcludesDeletedTransactionsAfterTheyHaveBeenIndexed() throws Exception {
        long account = account(owner);
        long transaction = payment(owner, account);
        assertPrivateResults(search(owner, false), account, transaction);

        json("DELETE", "/transactions/" + transaction, null, owner, 204);

        for (boolean advanced : List.of(false, true)) {
            JsonNode result = search(owner, advanced);
            assertThat(result.path("totalResults").asInt()).isEqualTo(1);
            assertThat(result.path("resultsByType").has("TRANSACTION")).isFalse();
            assertThat(result.path("resultsByType").path("ACCOUNT").get(0).path("id").asLong())
                    .isEqualTo(account);
        }
    }

    @Test
    void budgetSearchDecryptsNotesAndLoadsItsCategoryOutsideTheRequestThread() throws Exception {
        long category =
                json(
                                "POST",
                                "/categories",
                                data("name", "Travel reserve", "color", "#123456"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long budget =
                json(
                                "POST",
                                "/budgets",
                                data(
                                        "categoryId",
                                        category,
                                        "amount",
                                        "400.05",
                                        "currency",
                                        "EUR",
                                        "period",
                                        "MONTHLY",
                                        "rollover",
                                        false,
                                        "startDate",
                                        START.toString(),
                                        "endDate",
                                        START.plusMonths(1).minusDays(1).toString(),
                                        "notes",
                                        "budgetneedle"),
                                owner,
                                201)
                        .path("id")
                        .asLong();

        JsonNode result = json("GET", "/search?q=budgetneedle", null, owner, 200);
        assertThat(result.path("totalResults").asInt()).isEqualTo(1);
        JsonNode found = result.path("resultsByType").path("BUDGET").get(0);
        assertThat(found.path("id").asLong()).isEqualTo(budget);
        assertThat(found.path("title").asText()).isEqualTo("Travel reserve");
        assertThat(found.path("snippet").asText()).contains("budgetneedle");
        money(found, "amount", "400.05");
    }

    @Test
    void accountFiltersCannotSelectAnotherUsersMatchingTransactions() throws Exception {
        long ownAccount = account(owner);
        payment(owner, ownAccount);
        Auth other = register();
        long foreignAccount = account(other);
        payment(other, foreignAccount);

        JsonNode result =
                json(
                        "POST",
                        "/search/advanced",
                        data(
                                "query",
                                "Private",
                                "entityTypes",
                                List.of("TRANSACTION"),
                                "accountIds",
                                List.of(foreignAccount)),
                        owner,
                        200);

        assertThat(result.path("totalResults").asInt()).isZero();
        assertThat(result.path("resultsByType").isEmpty()).isTrue();
    }

    @Test
    void searchEndpointsRejectInvalidRequestsAtTheHttpBoundary() throws Exception {
        JsonNode invalidLimit = json("GET", "/search?q=Private&limit=0", null, owner, 400);
        assertThat(invalidLimit.path("validationErrors").path("limit").asText()).isNotBlank();
        json("GET", "/search?q=Private&limit=101", null, owner, 400);
        json("POST", "/search/advanced", data("query", " "), owner, 400);
        json("POST", "/search/advanced", data("query", "Private", "minAmount", "-1"), owner, 400);
        json("POST", "/search/advanced", data("query", "Private", "limit", 101), owner, 400);
        json("POST", "/search/advanced", data("query", "x".repeat(201)), owner, 400);
    }

    private long payment(Auth user, long account) throws Exception {
        Map<String, Object> request = movement(account, 25, START.plusDays(1));
        request.put("description", "Private payment");
        return json("POST", "/transactions", request, user, 201).path("id").asLong();
    }

    private JsonNode search(Auth user, boolean advanced) throws Exception {
        return advanced
                ? json("POST", "/search/advanced", data("query", "Private"), user, 200)
                : json("GET", "/search?q=Private", null, user, 200);
    }

    private void assertPrivateResults(JsonNode result, long account, long transaction) {
        assertThat(result.path("totalResults").asInt()).isEqualTo(2);
        JsonNode accounts = result.path("resultsByType").path("ACCOUNT");
        JsonNode transactions = result.path("resultsByType").path("TRANSACTION");
        assertThat(accounts.size()).isEqualTo(1);
        assertThat(transactions.size()).isEqualTo(1);
        assertThat(accounts.get(0).path("id").asLong()).isEqualTo(account);
        assertThat(accounts.get(0).path("title").asText()).isEqualTo("Private cash");
        assertThat(transactions.get(0).path("id").asLong()).isEqualTo(transaction);
        assertThat(transactions.get(0).path("title").asText()).isEqualTo("Private payment");
    }
}
