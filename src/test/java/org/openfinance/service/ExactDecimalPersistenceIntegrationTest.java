package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Fresh HTTP reads exercise SQLite's column affinity, not just the in-memory save response. */
@SpringBootTest(properties = "application.encryption.enabled=false")
class ExactDecimalPersistenceIntegrationTest extends AuditApiTestSupport {
    private static final String EXACT = "10006858.9112567483";

    @Test
    void metadataOnlyAccountEditPreservesExactBalanceWithoutCreatingAdjustment() throws Exception {
        Map<String, Object> request =
                data(
                        "name",
                        "Precision",
                        "type",
                        "SAVINGS",
                        "currency",
                        "EUR",
                        "initialBalance",
                        EXACT,
                        "openingDate",
                        "2020-01-01");
        long id = json("POST", "/accounts", request, owner, 201).path("id").asLong();
        request.put("initialBalance", "10006858.91125675");
        request.put("adjustBalance", false);
        request.put("isInterestEnabled", true);
        request.put("interestPeriod", "ANNUAL");
        json("PUT", "/accounts/" + id, request, owner, 200);
        exact(json("GET", "/accounts/" + id, null, owner, 200), "ownBalance", EXACT);
        assertThat(json("GET", "/transactions?accountId=" + id, null, owner, 200)).isEmpty();
    }

    @Test
    void splitAmountsSurviveCommittedSaveAndFreshReadExactly() throws Exception {
        long account = account(owner);
        Map<String, Object> request =
                data(
                        "accountId",
                        account,
                        "type",
                        "EXPENSE",
                        "currency",
                        "EUR",
                        "amount",
                        EXACT,
                        "date",
                        "2026-01-01",
                        "description",
                        "Precise split",
                        "splits",
                        List.of(data("amount", "10006857.9112567483"), data("amount", "1")));
        long id = json("POST", "/transactions", request, owner, 201).path("id").asLong();
        JsonNode stored = json("GET", "/transactions/" + id, null, owner, 200);
        exact(stored, "amount", EXACT);
        exact(stored.path("splits").get(0), "amount", "10006857.9112567483");
        exact(stored.path("splits").get(1), "amount", "1");
        request.put("description", "Metadata only");
        json("PUT", "/transactions/" + id, request, owner, 200);
        exact(
                json("GET", "/transactions/" + id, null, owner, 200).path("splits").get(0),
                "amount",
                "10006857.9112567483");
    }

    @Test
    void assetQuantitiesAndPricesSurviveFreshReadsAndPricesStillSortNumerically() throws Exception {
        Map<String, Object> request = assetRequest("Precise asset", EXACT);
        request.put("quantity", EXACT);
        request.put("purchasePrice", EXACT);
        long id = json("POST", "/assets", request, owner, 201).path("id").asLong();
        exact(json("GET", "/assets/" + id, null, owner, 200), "currentPrice", EXACT);
        request.put("name", "Renamed precise asset");
        json("PUT", "/assets/" + id, request, owner, 200);
        JsonNode stored = json("GET", "/assets/" + id, null, owner, 200);
        for (String field : List.of("quantity", "purchasePrice", "currentPrice")) {
            exact(stored, field, EXACT);
        }
        json("POST", "/assets", assetRequest("Ten", "10.123456789012345678"), owner, 201);
        json("POST", "/assets", assetRequest("Two", "2.123456789012345678"), owner, 201);
        JsonNode sorted = json("GET", "/assets/search?sort=currentPrice,asc", null, owner, 200);
        assertThat(sorted.path("content"))
                .extracting(node -> node.path("name").asText())
                .containsExactly("Two", "Ten", "Renamed precise asset");
    }

    @Test
    void interestRatesSurviveFreshReadsWithoutFixedScaleRounding() throws Exception {
        long account = account(owner);
        String path = "/accounts/" + account + "/interest-variations";
        json(
                "POST",
                path,
                data(
                        "rate",
                        "1.123456789012345678",
                        "taxRate",
                        "9.876543210987654321",
                        "validFrom",
                        "2020-01-01"),
                owner,
                201);
        JsonNode stored = json("GET", path, null, owner, 200).get(0);
        exact(stored, "rate", "1.123456789012345678");
        exact(stored, "taxRate", "9.876543210987654321");
    }

    private Map<String, Object> assetRequest(String name, String price) {
        return data(
                "name",
                name,
                "type",
                "STOCK",
                "quantity",
                "1",
                "purchasePrice",
                "1",
                "currentPrice",
                price,
                "currency",
                "EUR",
                "purchaseDate",
                "2020-01-01");
    }

    private void exact(JsonNode response, String field, String expected) {
        assertThat(new BigDecimal(response.path("exactValues").path(field).asText()))
                .isEqualByComparingTo(expected);
    }
}
