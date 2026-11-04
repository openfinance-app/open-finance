package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Saved simulations preserve encrypted data and user ownership")
class SimulationStorageIntegrationTest extends AuditApiTestSupport {
    private static final String URL = "/real-estate-simulations";

    @Test
    void createListFilterReadUpdateAndDeletePreserveSimulationData() throws Exception {
        Map<String, Object> buy = simulation("Home", "buy_rent");
        long id = json("POST", URL, buy, owner, 201).path("id").asLong();
        json("POST", URL, simulation("Rental", "rental_investment"), owner, 201);
        assertThat(json("GET", URL, null, owner, 200)).hasSize(2);
        assertThat(json("GET", URL + "?simulationType=buy_rent", null, owner, 200)).hasSize(1);
        assertThat(json("GET", URL + "/" + id, null, owner, 200).path("data").asText())
                .isEqualTo(buy.get("data"));
        assertThat(json("GET", URL + "/check-name?name=Home", null, owner, 200).asBoolean())
                .isTrue();
        Map<String, Object> changed = simulation("Renamed", "buy_rent");
        assertThat(json("PUT", URL + "/" + id, changed, owner, 200).path("name").asText())
                .isEqualTo("Renamed");
        assertThat(json("GET", URL + "/check-name?name=Home", null, owner, 200).asBoolean())
                .isFalse();
        json("DELETE", URL + "/" + id, null, owner, 204);
        json("GET", URL + "/" + id, null, owner, 404);
    }

    @Test
    void duplicateNamesAreScopedToAUserAndUnauthorizedMutationsLeaveTheOriginalIntact()
            throws Exception {
        Map<String, Object> request = simulation("Home", "buy_rent");
        long id = json("POST", URL, request, owner, 201).path("id").asLong();
        json("POST", URL, request, owner, 409);
        long second =
                json("POST", URL, simulation("Second", "buy_rent"), owner, 201).path("id").asLong();
        json("PUT", URL + "/" + second, request, owner, 409);
        Auth other = register();
        assertThat(json("GET", URL, null, other, 200)).isEmpty();
        assertThat(json("GET", URL + "/check-name?name=Home", null, other, 200).asBoolean())
                .isFalse();
        json("GET", URL + "/" + id, null, other, 404);
        json("PUT", URL + "/" + id, request, other, 404);
        json("DELETE", URL + "/" + id, null, other, 404);
        json("POST", URL, request, other, 201);
        assertThat(json("GET", URL + "/" + id, null, owner, 200).path("name").asText())
                .isEqualTo("Home");
    }

    private Map<String, Object> simulation(String name, String type) throws Exception {
        String fixture = type.equals("buy_rent") ? "buy-rent" : "rental-investment";
        return data(
                "name",
                name,
                "simulationType",
                type,
                "data",
                Files.readString(
                        Path.of("src/test/resources/fixtures/" + fixture + "-simulation.json")));
    }
}
