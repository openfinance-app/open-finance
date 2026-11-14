package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("List, global and advanced searches use consistent fields and amounts")
class SearchSurfaceConsistencyIntegrationTest extends AuditApiTestSupport {
    @ParameterizedTest
    @CsvSource({
        "accounts, ACCOUNT, keyword, name|description|accountNumber",
        "assets, ASSET, keyword, name|symbol|notes|brand|model|serialNumber",
        "real-estate, REAL_ESTATE, keyword, name|address|notes",
        "liabilities, LIABILITY, search, name|notes",
        "recurring-transactions, RECURRING_TRANSACTION, search, description|notes|payee"
    })
    void textFieldsMatchAcrossSurfacesWithoutLeakingOtherUsersRecords(
            String resource, String resultType, String filter, String fields) throws Exception {
        create(resource, register(), "Needle");
        create(resource, owner, "Unrelated");
        long match = create(resource, owner, "Needle");
        String endpoint = "/" + resource + (filter.equals("search") ? "/paged" : "/search");

        for (String field : fields.split("\\|")) {
            for (boolean regex : List.of(false, true)) {
                String query = "needle" + field.toLowerCase(Locale.ROOT);
                if (regex) query = "^" + query + "$";
                String params = filter + "=" + query + "&" + filter + "Regex=" + regex;
                JsonNode page = json("GET", endpoint + "?" + params + "&size=1", null, owner, 200);
                assertThat(page.path("totalElements").asInt()).as(params).isEqualTo(1);
                assertThat(ids(page.path("content"))).as(params).containsExactly(match);
                assertThat(
                                json(
                                                "GET",
                                                endpoint + "?" + params + "&size=1&page=1",
                                                null,
                                                owner,
                                                200)
                                        .path("content"))
                        .as(params)
                        .isEmpty();
                assertSearchResult(resultType, query, regex, match);
            }
        }
        assertThat(
                        json(
                                        "GET",
                                        endpoint + "?" + filter + "=[&" + filter + "Regex=true",
                                        null,
                                        owner,
                                        200)
                                .path("totalElements")
                                .asInt())
                .isZero();
    }

    @Test
    void institutionNamesMatchInListGlobalAndAdvancedAccountSearch() throws Exception {
        long institution =
                json("POST", "/institutions", data("name", "Surface institution"), owner, 201)
                        .path("id")
                        .asLong();
        Map<String, Object> request = body("accounts", owner, "Needle");
        request.put("institutionId", institution);
        long account = json("POST", "/accounts", request, owner, 201).path("id").asLong();

        assertThat(
                        ids(
                                json("GET", "/accounts/search?keyword=surface", null, owner, 200)
                                        .path("content")))
                .containsExactly(account);
        assertSearchResult("ACCOUNT", "surface", false, account);
        assertSearchResult("ACCOUNT", "^Surface institution$", true, account);
    }

    @Test
    void investmentAccountSearchUsesTheDisplayedBalanceIncludingHoldings() throws Exception {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        Map<String, Object> accountRequest = body("accounts", owner, "Portfolio");
        accountRequest.put("type", "INVESTMENT");
        accountRequest.put("initialBalance", 75);
        long account = json("POST", "/accounts", accountRequest, owner, 201).path("id").asLong();
        Map<String, Object> holding = body("assets", owner, "Holding");
        holding.putAll(data("accountId", account, "quantity", 2, "currentPrice", 500));
        json("POST", "/assets", holding, owner, 201);

        JsonNode page = json("GET", "/accounts/search?balanceExact=1075", null, owner, 200);
        assertThat(ids(page.path("content"))).containsExactly(account);
        money(page.path("content").get(0), "balance", "1075");
        accountRequest.putAll(data("name", "Portfolio sibling", "initialBalance", 25));
        long sibling = json("POST", "/accounts", accountRequest, owner, 201).path("id").asLong();
        JsonNode global = json("GET", "/search?q=Portfolio", null, owner, 200);
        JsonNode accounts = global.path("resultsByType").path("ACCOUNT");
        assertThat(ids(accounts)).containsExactlyInAnyOrder(account, sibling);
        accounts.forEach(
                row -> money(row, "amount", row.path("id").asLong() == account ? "1075" : "25"));
        for (String mode : List.of("BASE", "NATIVE")) {
            Map<String, Object> search =
                    data(
                            "query",
                            "Portfolioname",
                            "entityTypes",
                            List.of("ACCOUNT"),
                            "exactAmount",
                            1075,
                            "amountCurrencyMode",
                            mode);
            assertThat(
                            ids(
                                    json("POST", "/search/advanced", search, owner, 200)
                                            .path("resultsByType")
                                            .path("ACCOUNT")))
                    .containsExactly(account);
            search.put("exactAmount", 75);
            assertThat(
                            json("POST", "/search/advanced", search, owner, 200)
                                    .path("totalResults")
                                    .asInt())
                    .isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"accountId", "assetId", "realEstateId", "liabilityId"})
    void scopedTransactionSearchAndSummaryIncludeSplitFields(String scope) throws Exception {
        long account = account(owner);
        long scopeId =
                switch (scope) {
                    case "accountId" -> account;
                    case "assetId" -> create("assets", owner, "Linked");
                    case "realEstateId" -> create("real-estate", owner, "Linked");
                    case "liabilityId" -> create("liabilities", owner, "Linked");
                    default -> throw new IllegalArgumentException(scope);
                };
        Map<String, Object> request = movement(account, 1200, START.plusDays(1));
        request.put(
                "splits",
                List.of(data("amount", 1075, "description", "ScopeSplit"), data("amount", 125)));
        if (scope.equals("accountId")) request.put("accountId", account(owner));
        json("POST", "/transactions", request, owner, 201);
        request.put("accountId", account);
        request.put(scope, scopeId);
        long match = json("POST", "/transactions", request, owner, 201).path("id").asLong();

        String params = scope + "=" + scopeId + "&keyword=ScopeSplit&amountExact=1075";
        JsonNode result = json("GET", "/transactions/search?" + params, null, owner, 200);
        assertThat(ids(result.path("content"))).containsExactly(match);
        assertThat(result.path("totalElements").asInt()).isEqualTo(1);
        JsonNode summary = json("GET", "/transactions/summary?" + params, null, owner, 200);
        assertThat(summary.path("transactionCount").asInt()).isEqualTo(1);
        money(summary, "expenseAmount", "1200");
    }

    private void assertSearchResult(String resultType, String query, boolean regex, long match)
            throws Exception {
        JsonNode global = json("GET", "/search?q=" + query + "&regex=" + regex, null, owner, 200);
        assertThat(ids(global.path("resultsByType").path(resultType)))
                .as("global: %s", query)
                .containsExactly(match);
        JsonNode advanced =
                json(
                        "POST",
                        "/search/advanced",
                        data(
                                "query",
                                query,
                                "regex",
                                regex,
                                "entityTypes",
                                List.of(resultType),
                                "limit",
                                1),
                        owner,
                        200);
        assertThat(ids(advanced.path("resultsByType").path(resultType)))
                .as("advanced: %s", query)
                .containsExactly(match);
    }

    private long create(String resource, Auth user, String prefix) throws Exception {
        return json("POST", "/" + resource, body(resource, user, prefix), user, 201)
                .path("id")
                .asLong();
    }

    private Map<String, Object> body(String resource, Auth user, String prefix) throws Exception {
        Map<String, Object> request = data("name", prefix + "name", "currency", "EUR");
        switch (resource) {
            case "accounts" -> request.putAll(
                    data(
                            "type",
                            "CHECKING",
                            "initialBalance",
                            100,
                            "openingDate",
                            START.toString(),
                            "description",
                            prefix + "description",
                            "accountNumber",
                            prefix + "accountNumber"));
            case "assets" -> request.putAll(assetFields(prefix));
            case "real-estate" -> request.putAll(propertyFields(prefix));
            case "liabilities" -> request.putAll(
                    data(
                            "type",
                            "PERSONAL_LOAN",
                            "principal",
                            10000,
                            "currentBalance",
                            10000,
                            "interestRate",
                            0,
                            "startDate",
                            START.toString(),
                            "notes",
                            prefix + "notes"));
            case "recurring-transactions" -> {
                request.remove("name");
                request.putAll(recurringFields(user, prefix));
            }
            default -> throw new IllegalArgumentException(resource);
        }
        return request;
    }

    private Map<String, Object> assetFields(String prefix) {
        return data(
                "type",
                "STOCK",
                "quantity",
                1,
                "purchasePrice",
                100,
                "currentPrice",
                100,
                "purchaseDate",
                START.toString(),
                "symbol",
                prefix + "symbol",
                "notes",
                prefix + "notes",
                "brand",
                prefix + "brand",
                "model",
                prefix + "model",
                "serialNumber",
                prefix + "serialNumber");
    }

    private Map<String, Object> propertyFields(String prefix) {
        return data(
                "propertyType",
                "RESIDENTIAL",
                "purchasePrice",
                100,
                "currentValue",
                100,
                "purchaseDate",
                START.toString(),
                "address",
                prefix + "address",
                "notes",
                prefix + "notes");
    }

    private Map<String, Object> recurringFields(Auth user, String prefix) throws Exception {
        return data(
                "accountId",
                account(user),
                "type",
                "EXPENSE",
                "amount",
                100,
                "description",
                prefix + "description",
                "notes",
                prefix + "notes",
                "payee",
                prefix + "payee",
                "frequency",
                "MONTHLY",
                "nextOccurrence",
                LocalDate.now().plusDays(5).toString());
    }

    private List<Long> ids(JsonNode rows) {
        List<Long> result = new ArrayList<>();
        rows.forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }
}
