package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Direct lender payments must preserve cash, cost basis and dated property valuations together. */
class DirectPropertyCapitalizationIntegrationTest extends AuditApiTestSupport {
    private long mortgage(long propertyId) throws Exception {
        return json(
                        "POST",
                        "/liabilities",
                        data(
                                "name",
                                "Renovation loan",
                                "type",
                                "MORTGAGE",
                                "principal",
                                1000,
                                "currentBalance",
                                0,
                                "currency",
                                "EUR",
                                "interestRate",
                                0,
                                "startDate",
                                START.toString(),
                                "realEstateId",
                                propertyId),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    private Map<String, Object> draw(long property, String amount, LocalDate date, boolean value) {
        return data(
                "directRealEstateId",
                property,
                "amount",
                amount,
                "date",
                date.toString(),
                "capitalizePropertyCost",
                true,
                "increasePropertyValue",
                value);
    }

    private long postDraw(long loan, Map<String, Object> request) throws Exception {
        json("POST", "/liabilities/" + loan + "/disburse", request, owner, 200);
        JsonNode draws = json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200);
        return draws.get(draws.size() - 1).path("id").asLong();
    }

    private void reverse(long tranche, LocalDate date) throws Exception {
        json(
                "POST",
                "/tranches/" + tranche + "/reverse",
                data("date", date.toString()),
                owner,
                200);
    }

    private void assertProperty(JsonNode property, String cost, String value, String gain)
            throws Exception {
        String path = "/real-estate/" + property.path("id").asLong();
        JsonNode result = json("GET", path, null, owner, 200);
        money(result, "currentValue", value);
        money(result, "performanceBasis", cost);
        money(result, "appreciation", gain);
        money(json("GET", path + "/roi", null, owner, 200), "costBasis", cost);
        JsonNode asset =
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200);
        money(asset, "totalCost", cost);
        money(asset, "totalValue", value);
        money(asset, "unrealizedGain", gain);
    }

    @Test
    void multipleDirectPaymentsAddCostAndChosenValueWithoutCashOrFalseGains() throws Exception {
        long cash = account(owner);
        JsonNode property = property(owner);
        long id = property.path("id").asLong(), loan = mortgage(id);
        postDraw(loan, draw(id, "200.25", START.plusMonths(1), true));
        postDraw(loan, draw(id, "300.75", START.plusMonths(2), true));
        assertProperty(property, "1501", "1501", "0");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "501");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
        JsonNode draws = json("GET", "/real-estate/" + id + "/drawdowns", null, owner, 200);
        money(draws.get(0), "capitalizedAmount", "200.25");
        money(draws.get(0), "valuationChange", "200.25");
        for (LocalDate date : new LocalDate[] {START, START.plusMonths(1), START.plusMonths(2)}) {
            money(history(date, date).get(0), "netWorth", "2000");
        }
    }

    @Test
    void costCanBeAddedWithoutChangingAnIndependentEstimate() throws Exception {
        JsonNode property = property(owner);
        long id = property.path("id").asLong(), loan = mortgage(id);
        long tranche = postDraw(loan, draw(id, "225.50", START.plusMonths(1), false));
        assertProperty(property, "1225.50", "1000", "-225.50");
        reverse(tranche, START.plusMonths(2));
        assertProperty(property, "1000", "1000", "0");
    }

    @Test
    void reversalPreservesEarlierHistoryAndDoesNotReappearWhenAnotherImprovementIsAdded()
            throws Exception {
        long cash = account(owner);
        JsonNode property = property(owner);
        long id = property.path("id").asLong(), loan = mortgage(id);
        LocalDate drawn = START.plusMonths(1), reversed = START.plusMonths(2);
        long tranche = postDraw(loan, draw(id, "200", drawn, true));
        reverse(tranche, reversed);
        assertProperty(property, "1000", "1000", "0");
        JsonNode beforeReversal = history(drawn, drawn).get(0);
        money(beforeReversal, "totalAssets", "2200");
        money(beforeReversal, "totalLiabilities", "200");
        JsonNode afterReversal = history(reversed, reversed).get(0);
        money(afterReversal, "totalAssets", "2000");
        money(afterReversal, "totalLiabilities", "0");
        Map<String, Object> improvement = movement(cash, 50, START.plusMonths(3));
        improvement.putAll(
                data(
                        "realEstateId",
                        id,
                        "movementType",
                        "CAPITAL_IMPROVEMENT",
                        "valuationChange",
                        50));
        json("POST", "/transactions", improvement, owner, 201);
        assertProperty(property, "1050", "1050", "0");
        money(history(LocalDate.now(), LocalDate.now()).get(0), "netWorth", "2000");
        json(
                "POST",
                "/tranches/" + tranche + "/reverse",
                data("date", reversed.toString()),
                owner,
                400);
    }

    @Test
    void reversingOldWorkDoesNotReduceALaterIndependentValuation() throws Exception {
        JsonNode property = property(owner);
        long id = property.path("id").asLong(), loan = mortgage(id);
        long tranche = postDraw(loan, draw(id, "200", START.plusMonths(1), true));
        json("PUT", "/real-estate/" + id + "/value", 1600, owner, 200);
        reverse(tranche, LocalDate.now());
        assertProperty(property, "1000", "1600", "600");
        postDraw(loan, draw(id, "100", LocalDate.now(), true));
        assertProperty(property, "1100", "1700", "600");
    }

    @Test
    void invalidRoutesAndTrancheOverdrawsRollBackCostValueAndDebt() throws Exception {
        JsonNode property = property(owner);
        long id = property.path("id").asLong(), loan = mortgage(id), cash = account(owner);
        Map<String, Object> request = draw(id, "100", LocalDate.now(), true);
        request.put("capitalizePropertyCost", false);
        json("POST", "/liabilities/" + loan + "/disburse", request, owner, 400);
        request = draw(id, "100", LocalDate.now(), true);
        request.remove("directRealEstateId");
        request.put("toAccountId", cash);
        json("POST", "/liabilities/" + loan + "/disburse", request, owner, 400);
        long tranche =
                json(
                                "POST",
                                "/liabilities/" + loan + "/tranches",
                                data("trancheNo", 1, "plannedAmount", 1000, "currency", "EUR"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        request = draw(id, "1001", LocalDate.now(), true);
        request.put("trancheId", tranche);
        json("POST", "/liabilities/" + loan + "/disburse", request, owner, 409);
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                draw(id, "100", START.minusDays(1), true),
                owner,
                400);
        assertProperty(property, "1000", "1000", "0");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "0");
        JsonNode tranches = json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200);
        assertThat(tranches).hasSize(1);
        assertThat(tranches.get(0).path("status").asText()).isEqualTo("PLANNED");
        assertThat(tranches.get(0).path("capitalizedAmount").isNull()).isTrue();
    }

    @Test
    void saleGainUsesDirectPaymentsInBothPropertyAndAssetViews() throws Exception {
        long cash = account(owner);
        JsonNode property = property(owner);
        long id = property.path("id").asLong(), loan = mortgage(id);
        postDraw(loan, draw(id, "200", START.plusMonths(1), true));
        json(
                "POST",
                "/assets/" + property.path("assetId").asLong() + "/sale",
                data(
                        "operationId",
                        java.util.UUID.randomUUID().toString(),
                        "accountId",
                        cash,
                        "date",
                        LocalDate.now().toString(),
                        "quantity",
                        "1",
                        "proceeds",
                        "1500",
                        "fees",
                        "0"),
                owner,
                201);
        money(json("GET", "/real-estate/" + id, null, owner, 200), "realizedGain", "300");
        money(
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200),
                "realizedGain",
                "300");
    }

    @Test
    void propertyCorrectionsCannotChangeCurrencyOrMoveAcquisitionAfterCapitalizedPayments()
            throws Exception {
        JsonNode property = property(owner);
        long id = property.path("id").asLong(), loan = mortgage(id);
        long tranche = postDraw(loan, draw(id, "200", START.plusMonths(1), true));
        Map<String, Object> edit =
                data(
                        "name",
                        "Renovated home",
                        "propertyType",
                        "RESIDENTIAL",
                        "address",
                        "Synthetic address",
                        "purchasePrice",
                        1000,
                        "currentValue",
                        1200,
                        "purchaseDate",
                        START.toString(),
                        "currency",
                        "USD");
        json("PUT", "/real-estate/" + id, edit, owner, 400);
        edit.put("currency", "EUR");
        edit.put("purchaseDate", START.plusMonths(2).toString());
        json("PUT", "/real-estate/" + id, edit, owner, 400);
        assertProperty(property, "1200", "1200", "0");
        reverse(tranche, LocalDate.now());
        edit.put("currency", "USD");
        edit.put("currentValue", 1000);
        edit.put("purchaseDate", START.toString());
        assertThat(json("PUT", "/real-estate/" + id, edit, owner, 200).path("currency").asText())
                .isEqualTo("USD");
    }
}
