package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Status filtering must use decrypted ownership state and paginate the matching records. */
class AssetStatusFilterIntegrationTest extends AuditApiTestSupport {
    private long asset(Auth user, String name, String type, String acquisition, String quantity)
            throws Exception {
        return json(
                        "POST",
                        "/assets",
                        data(
                                "name",
                                name,
                                "type",
                                type,
                                "acquisitionType",
                                acquisition,
                                "quantity",
                                quantity,
                                "purchasePrice",
                                "100",
                                "currentPrice",
                                "PLANNED".equals(acquisition) ? "0" : "120",
                                "currency",
                                "EUR",
                                "purchaseDate",
                                START.toString()),
                        user,
                        201)
                .path("id")
                .asLong();
    }

    @Test
    void statusFiltersBeforePaginationAndCombinesWithGroupAndEncryptedName() throws Exception {
        asset(owner, "A owned car", "VEHICLE", "PURCHASE", "1");
        long first = asset(owner, "B planned car", "VEHICLE", "PLANNED", "1");
        long second = asset(owner, "C planned car", "VEHICLE", "PLANNED", "1");
        asset(owner, "D planned investment", "STOCK", "PLANNED", "1");
        asset(register(), "E planned car", "VEHICLE", "PLANNED", "1");
        String query =
                "/assets/search?ownershipStatus=PLANNED&group=PHYSICAL&keyword=car&size=1&sort=name,asc";
        JsonNode page = json("GET", query, null, owner, 200);
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(first);
        page = json("GET", query + "&page=1", null, owner, 200);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(second);
        assertThat(page.path("content").get(0).path("ownershipStatus").asText())
                .isEqualTo("PLANNED");
        assertThat(json("GET", query + "&page=2", null, owner, 200).path("content")).isEmpty();
        assertThat(
                        json("GET", "/assets/search?ownershipStatus=OWNED", null, owner, 200)
                                .path("totalElements")
                                .asInt())
                .isEqualTo(1);
        assertThat(json("GET", "/assets/search", null, owner, 200).path("totalElements").asInt())
                .isEqualTo(4);
    }

    @Test
    void closedIncludesSoldPhysicalItemsAndTrackedZeroHoldingsAndUpdatesAfterReversal()
            throws Exception {
        long cash = account(owner);
        long car = asset(owner, "Sold car", "VEHICLE", "PURCHASE", "1");
        long holding = asset(owner, "Zero holding", "STOCK", "PURCHASE", "0");
        // Imported investment metadata marks this encrypted zero quantity as a tracked position.
        jdbc.update("UPDATE assets SET import_source = ? WHERE id = ?", "TEST_IMPORT", holding);
        asset(owner, "Manual zero", "STOCK", "PURCHASE", "0");
        Map<String, Object> sale =
                data(
                        "operationId",
                        UUID.randomUUID().toString(),
                        "accountId",
                        cash,
                        "date",
                        START.plusDays(5).toString(),
                        "quantity",
                        "1",
                        "proceeds",
                        "90",
                        "fees",
                        "0");
        long payment =
                json("POST", "/assets/" + car + "/sale", sale, owner, 201)
                        .path("transactionId")
                        .asLong();
        JsonNode closed =
                json("GET", "/assets/search?ownershipStatus=CLOSED&sort=id,asc", null, owner, 200);
        assertThat(closed.path("totalElements").asInt()).isEqualTo(2);
        assertThat(closed.path("content").get(0).path("id").asLong()).isEqualTo(car);
        assertThat(closed.path("content").get(1).path("id").asLong()).isEqualTo(holding);
        assertThat(
                        json(
                                        "GET",
                                        "/assets/search?ownershipStatus=CLOSED&group=FINANCIAL",
                                        null,
                                        owner,
                                        200)
                                .path("totalElements")
                                .asInt())
                .isEqualTo(1);
        json("DELETE", "/transactions/" + payment, null, owner, 204);
        assertThat(
                        json(
                                        "GET",
                                        "/assets/search?ownershipStatus=CLOSED&group=PHYSICAL",
                                        null,
                                        owner,
                                        200)
                                .path("content"))
                .isEmpty();
        assertThat(
                        json(
                                        "GET",
                                        "/assets/search?ownershipStatus=OWNED&group=PHYSICAL",
                                        null,
                                        owner,
                                        200)
                                .path("content")
                                .get(0)
                                .path("id")
                                .asLong())
                .isEqualTo(car);
    }

    @Test
    void rejectsInvalidStatusInsteadOfSilentlyReturningAllAssets() throws Exception {
        json("GET", "/assets/search?ownershipStatus=INVALID", null, owner, 400);
    }
}
