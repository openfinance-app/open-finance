package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Portfolio searches filter decrypted values before sorting and pagination")
class PortfolioSearchIntegrationTest extends AuditApiTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"accounts", "assets", "real-estate", "liabilities"})
    void searchAndPaginationNeverExposeAnotherUsersRecords(String domain) throws Exception {
        Auth other = register();
        create(domain, other, "Alpha foreign", 1000);
        long alpha = create(domain, owner, "Alpha", 10);
        long beta = create(domain, owner, "beta", 100);
        long gamma = create(domain, owner, "Gamma", 1);
        String endpoint = endpoint(domain);
        assertIds(
                json("GET", endpoint + "?sort=name,asc&size=2", null, owner, 200), 3, alpha, beta);
        assertIds(
                json("GET", endpoint + "?sort=name,asc&size=2&page=1", null, owner, 200), 3, gamma);
        assertIds(
                json("GET", endpoint + "?sort=name,desc&size=2", null, owner, 200), 3, gamma, beta);
        assertIds(json("GET", endpoint + "?sort=name,asc&page=9&size=2", null, owner, 200), 3);
        String keyword = domain.equals("liabilities") ? "search" : "keyword";
        assertIds(json("GET", endpoint + "?" + keyword + "=ALPHA", null, owner, 200), 1, alpha);
        assertIds(
                json(
                        "GET",
                        endpoint
                                + "?"
                                + keyword
                                + "=^(alpha|gamma)$&"
                                + keyword
                                + "Regex=true&sort=name,asc",
                        null,
                        owner,
                        200),
                2,
                alpha,
                gamma);
        assertIds(json("GET", endpoint + "?" + keyword + "=missing", null, owner, 200), 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "assets"})
    void amountBoundsAreInclusiveAndAppliedBeforePagination(String domain) throws Exception {
        create(domain, owner, "Below", 1);
        long lower = create(domain, owner, "Lower", 10);
        long upper = create(domain, owner, "Upper", 100);
        create(domain, owner, "Above", 1000);
        String field = domain.equals("accounts") ? "balance" : "value";
        String endpoint = endpoint(domain);
        assertIds(
                json(
                        "GET",
                        endpoint + "?" + field + "Min=10&" + field + "Max=100&sort=name,asc&size=1",
                        null,
                        owner,
                        200),
                2,
                lower);
        assertIds(
                json(
                        "GET",
                        endpoint
                                + "?"
                                + field
                                + "Min=10&"
                                + field
                                + "Max=100&sort=name,asc&size=1&page=1",
                        null,
                        owner,
                        200),
                2,
                upper);
        assertIds(
                json(
                        "GET",
                        endpoint + "?" + field + "Min=100&" + field + "Max=10",
                        null,
                        owner,
                        200),
                0);
        if (domain.equals("accounts")) {
            assertIds(
                    json(
                            "GET",
                            endpoint + "?lowBalance=true&balanceMin=10&sort=balance,desc",
                            null,
                            owner,
                            200),
                    2,
                    upper,
                    lower);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "createdAt"})
    void propertyValueFiltersWorkWithBothDatabaseAndDecryptedSorts(String sort) throws Exception {
        create("real-estate", owner, "Below", 1);
        long match = create("real-estate", owner, "Matching", 100);
        create("real-estate", owner, "Above", 1000);
        assertIds(
                json(
                        "GET",
                        "/real-estate/search?valueMin=100&valueMax=100&sort=" + sort + ",asc",
                        null,
                        owner,
                        200),
                1,
                match);
        assertIds(
                json(
                        "GET",
                        "/real-estate/search?keyword=Matching&priceMin=50&priceMax=50&rentalIncomeMin=1",
                        null,
                        owner,
                        200),
                1,
                match);
        assertIds(
                json("GET", "/real-estate/search?keyword=Matching&priceMin=51", null, owner, 200),
                0);
        assertIds(
                json(
                        "GET",
                        "/real-estate/search?keyword=Matching&rentalIncomeMin=2",
                        null,
                        owner,
                        200),
                0);
    }

    @Test
    void propertyFiltersCombineOwnershipTypeCurrencyDatesAndMortgagePresence() throws Exception {
        long match = create("real-estate", owner, "Paris flat", 100);
        create("real-estate", register(), "Paris foreign", 100);
        String filter =
                "/real-estate/search?propertyType=RESIDENTIAL&currency=EUR&isActive=true&hasMortgage=false&purchaseDateFrom="
                        + START
                        + "&purchaseDateTo="
                        + START;
        assertIds(json("GET", filter, null, owner, 200), 1, match);
        assertIds(
                json(
                        "GET",
                        filter.replace("hasMortgage=false", "hasMortgage=true"),
                        null,
                        owner,
                        200),
                0);
        assertIds(json("GET", filter.replace("RESIDENTIAL", "COMMERCIAL"), null, owner, 200), 0);
        assertIds(
                json("GET", filter.replace("isActive=true", "isActive=false"), null, owner, 200),
                0);
        assertIds(json("GET", filter.replace("currency=EUR", "currency=GBP"), null, owner, 200), 0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "currentBalance",
                "principal",
                "interestRate",
                "minimumPayment",
                "startDate",
                "endDate",
                "createdAt"
            })
    void liabilitySearchSortsTheCompleteMatchingSet(String sort) throws Exception {
        long low = create("liabilities", owner, "Loan A", 10);
        long high = create("liabilities", owner, "Loan B", 100);
        String url =
                "/liabilities/paged?search=Loan&type=PERSONAL_LOAN&sort=" + sort + ",asc&size=1";
        assertIds(json("GET", url, null, owner, 200), 2, low);
        if (!sort.equals("startDate") && !sort.equals("endDate")) {
            assertIds(json("GET", url.replace(",asc", ",desc"), null, owner, 200), 2, high);
        }
    }

    private String endpoint(String domain) {
        return "/" + domain + (domain.equals("liabilities") ? "/paged" : "/search");
    }

    private long create(String domain, Auth user, String name, int amount) throws Exception {
        Map<String, Object> body =
                switch (domain) {
                    case "accounts" -> data(
                            "name",
                            name,
                            "type",
                            "CHECKING",
                            "currency",
                            "EUR",
                            "initialBalance",
                            amount,
                            "openingDate",
                            START.toString(),
                            "accountNumber",
                            "number-" + name);
                    case "assets" -> data(
                            "name",
                            name,
                            "type",
                            "STOCK",
                            "currency",
                            "EUR",
                            "quantity",
                            2,
                            "purchasePrice",
                            BigDecimal.valueOf(amount).divide(BigDecimal.TWO),
                            "currentPrice",
                            BigDecimal.valueOf(amount).divide(BigDecimal.TWO),
                            "purchaseDate",
                            START.toString());
                    case "real-estate" -> data(
                            "name",
                            name,
                            "address",
                            "Address " + name,
                            "propertyType",
                            "RESIDENTIAL",
                            "currency",
                            "EUR",
                            "purchasePrice",
                            BigDecimal.valueOf(amount).divide(BigDecimal.TWO),
                            "currentValue",
                            amount,
                            "rentalIncome",
                            BigDecimal.valueOf(amount).movePointLeft(2),
                            "purchaseDate",
                            START.toString());
                    case "liabilities" -> data(
                            "name",
                            name,
                            "type",
                            "PERSONAL_LOAN",
                            "currency",
                            "EUR",
                            "principal",
                            amount * 2,
                            "currentBalance",
                            amount,
                            "interestRate",
                            BigDecimal.valueOf(amount).movePointLeft(2),
                            "minimumPayment",
                            BigDecimal.valueOf(amount).movePointLeft(1),
                            "startDate",
                            START.toString(),
                            "endDate",
                            START.plusYears(5).toString());
                    default -> throw new IllegalArgumentException(domain);
                };
        return json("POST", "/" + domain, body, user, 201).path("id").asLong();
    }

    private void assertIds(JsonNode page, long total, Long... ids) {
        assertThat(page.path("totalElements").asLong()).as(page.toPrettyString()).isEqualTo(total);
        List<Long> actual =
                StreamSupport.stream(page.path("content").spliterator(), false)
                        .map(node -> node.path("id").asLong())
                        .toList();
        assertThat(actual).containsExactly(ids);
    }
}
