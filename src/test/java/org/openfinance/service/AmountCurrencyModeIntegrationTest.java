package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Monetary filters and ordering use one currency basis across encrypted surfaces")
class AmountCurrencyModeIntegrationTest extends AuditApiTestSupport {
    @BeforeEach
    void euroBase() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
    }

    @ParameterizedTest
    @CsvSource({
        "accounts, search, balance, balance, ACCOUNT",
        "assets, search, value, totalValue, ASSET",
        "real-estate, search, value, currentValue, REAL_ESTATE",
        "liabilities, paged, balance, currentBalance, LIABILITY"
    })
    void filtersExactAmountsAndSortsBeforePagination(
            String resource, String endpoint, String filter, String sort, String resultType)
            throws Exception {
        long euro = create(resource, "EUR", "3000");
        long foreign = create(resource, "XOF", "40000");
        long zero = create(resource, "EUR", "0");
        String path = "/" + resource + "/" + endpoint + "?";

        assertThat(ids(get(path + filter + "Min=2500").path("content"))).containsExactly(euro);
        assertThat(ids(get(path + filter + "Min=2500&amountCurrencyMode=NATIVE").path("content")))
                .containsExactlyInAnyOrder(euro, foreign);
        assertThat(
                        ids(
                                get(path + filter + "Exact=40000&amountCurrencyMode=NATIVE")
                                        .path("content")))
                .containsExactly(foreign);
        assertThat(ids(get(path + filter + "Exact=3000.00").path("content"))).containsExactly(euro);
        assertThat(ids(get(path + filter + "Exact=0").path("content"))).containsExactly(zero);
        assertThat(ids(get(path + filter + "Max=100").path("content")))
                .containsExactlyInAnyOrder(foreign, zero);

        String ordered = path + "size=1&sort=" + sort + ",desc";
        JsonNode basePage = get(ordered);
        assertThat(ids(basePage.path("content"))).containsExactly(euro);
        assertThat(basePage.path("totalElements").asInt()).isEqualTo(3);
        assertThat(ids(get(ordered + "&amountCurrencyMode=NATIVE").path("content")))
                .containsExactly(foreign);
        assertThat(ids(get(ordered + "&amountCurrencyMode=NATIVE&page=1").path("content")))
                .containsExactly(euro);
        assertThat(get(path + filter + "Exact=3000&page=1&size=1").path("content")).isEmpty();

        Map<String, Object> advanced =
                data(
                        "query",
                        "Amount comparison",
                        "entityTypes",
                        List.of(resultType),
                        "exactAmount",
                        3000,
                        "limit",
                        1);
        JsonNode results = json("POST", "/search/advanced", advanced, owner, 200);
        assertThat(ids(results.path("resultsByType").path(resultType))).containsExactly(euro);
        advanced.put("amountCurrencyMode", "NATIVE");
        advanced.put("exactAmount", 40000);
        results = json("POST", "/search/advanced", advanced, owner, 200);
        assertThat(ids(results.path("resultsByType").path(resultType))).containsExactly(foreign);
    }

    @Test
    void propertyPriceAndRentalBoundsFollowTheSameCurrencyMode() throws Exception {
        long property = create("real-estate", "XOF", "40000");
        assertThat(get("/real-estate/search?priceMin=2500").path("totalElements").asInt()).isZero();
        assertThat(
                        ids(
                                get("/real-estate/search?priceMin=2500&amountCurrencyMode=NATIVE")
                                        .path("content")))
                .containsExactly(property);
        assertThat(get("/real-estate/search?rentalIncomeMin=2500").path("totalElements").asInt())
                .isZero();
        assertThat(
                        ids(
                                get("/real-estate/search?rentalIncomeMin=2500&amountCurrencyMode=NATIVE")
                                        .path("content")))
                .containsExactly(property);
    }

    private JsonNode get(String path) throws Exception {
        return json("GET", path, null, owner, 200);
    }

    private long create(String resource, String currency, String value) throws Exception {
        BigDecimal amount = new BigDecimal(value);
        Map<String, Object> request =
                data("name", "Amount comparison " + currency + value, "currency", currency);
        switch (resource) {
            case "accounts" -> request.putAll(
                    data(
                            "type",
                            "CHECKING",
                            "initialBalance",
                            amount,
                            "openingDate",
                            START.toString()));
            case "assets" -> request.putAll(
                    data(
                            "type",
                            "STOCK",
                            "acquisitionType",
                            "PURCHASE",
                            "quantity",
                            2,
                            "currentPrice",
                            amount.divide(new BigDecimal("2")),
                            "purchasePrice",
                            1,
                            "purchaseDate",
                            START.toString()));
            case "real-estate" -> request.putAll(
                    data(
                            "propertyType",
                            "RESIDENTIAL",
                            "address",
                            "Test address",
                            "purchasePrice",
                            amount.max(BigDecimal.ONE),
                            "currentValue",
                            amount,
                            "rentalIncome",
                            amount,
                            "purchaseDate",
                            START.toString()));
            case "liabilities" -> request.putAll(
                    data(
                            "type",
                            "PERSONAL_LOAN",
                            "principal",
                            amount.max(BigDecimal.ONE),
                            "currentBalance",
                            amount,
                            "interestRate",
                            0,
                            "startDate",
                            START.toString()));
            default -> throw new IllegalArgumentException(resource);
        }
        return json("POST", "/" + resource, request, owner, 201).path("id").asLong();
    }

    private List<Long> ids(JsonNode rows) {
        List<Long> result = new ArrayList<>();
        rows.forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }
}
