package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Financial regressions reproduced in the October 22 management audit. */
class ManagementAuditRegressionTest extends AuditApiTestSupport {
    private JsonNode propertyWithAcquisition(
            String acquisition, String price, String value, String date, String receivedValue)
            throws Exception {
        return json(
                "POST",
                "/real-estate",
                data(
                        "name",
                        "Acquisition review",
                        "address",
                        "Synthetic address",
                        "propertyType",
                        "RESIDENTIAL",
                        "currency",
                        "EUR",
                        "purchasePrice",
                        price,
                        "currentValue",
                        value,
                        "purchaseDate",
                        date,
                        "acquisitionType",
                        acquisition,
                        "receivedValue",
                        receivedValue,
                        "isActive",
                        true),
                owner,
                201);
    }

    @Test
    void aPlanHasNoOwnedPerformance() throws Exception {
        JsonNode property =
                propertyWithAcquisition(
                        "PLANNED",
                        "250000",
                        "0",
                        java.time.LocalDate.now().plusMonths(5).toString(),
                        null);
        assertThat(property.path("appreciation").isNull()).isTrue();
        JsonNode roi =
                json(
                        "GET",
                        "/real-estate/" + property.path("id").asLong() + "/roi",
                        null,
                        owner,
                        200);
        assertThat(roi.path("totalROI").isNull()).isTrue();
        JsonNode asset =
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200);
        assertThat(asset.path("unrealizedGain").isNull()).isTrue();
    }

    @Test
    void aGiftMeasuresChangeFromReceivedValueWithoutChangingPurchaseCost() throws Exception {
        JsonNode property =
                propertyWithAcquisition("GIFT", "0", "165000", START.toString(), "150000");
        money(property, "appreciation", "15000");
        money(property, "appreciationPercentage", "10");
        JsonNode asset =
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200);
        money(asset, "unrealizedGain", "15000");
        money(asset, "totalCost", "0");
        money(asset, "performanceBasis", "150000");
    }

    @Test
    void aGiftWithoutReceivedValueHasUnknownPerformance() throws Exception {
        JsonNode property = propertyWithAcquisition("GIFT", "0", "150000", START.toString(), null);
        assertThat(property.path("appreciation").isNull()).isTrue();
        JsonNode asset =
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200);
        assertThat(asset.path("unrealizedGain").isNull()).isTrue();
        assertThat(asset.path("gainPercentage").isNull()).isTrue();
    }

    @Test
    void checkingCashDoesNotIncludeALinkedCar() throws Exception {
        long cash = account(owner);
        json(
                "POST",
                "/assets",
                data(
                        "name",
                        "Car",
                        "type",
                        "VEHICLE",
                        "quantity",
                        "1",
                        "purchasePrice",
                        "15000",
                        "currentPrice",
                        "15000",
                        "currency",
                        "EUR",
                        "purchaseDate",
                        START.toString(),
                        "accountId",
                        cash),
                owner,
                201);
        JsonNode result = json("GET", "/accounts/" + cash, null, owner, 200);
        money(result, "balance", "1000");
        money(result, "ownBalance", "1000");
        money(result, "holdingsValue", "0");
    }

    @Test
    void cashAccountsDoNotNeedHoldingExchangeRatesToReportCash() throws Exception {
        long cash = account(owner);
        json(
                "POST",
                "/assets",
                data(
                        "name",
                        "Foreign holding association",
                        "type",
                        "STOCK",
                        "quantity",
                        "1",
                        "purchasePrice",
                        "10",
                        "currentPrice",
                        "10",
                        "currency",
                        "USD",
                        "purchaseDate",
                        START.toString(),
                        "accountId",
                        cash),
                owner,
                201);
        JsonNode result = json("GET", "/accounts/" + cash, null, owner, 200);
        money(result, "balance", "1000");
        money(result, "holdingsValue", "0");
    }

    @Test
    void giftAcquisitionCostsReducePerformanceAndUnknownBaselineCanBeRestored() throws Exception {
        JsonNode property =
                propertyWithAcquisition("GIFT", "10000", "165000", START.toString(), "150000");
        money(property, "appreciation", "5000");
        long id = property.path("id").asLong();
        JsonNode updated =
                json(
                        "PUT",
                        "/real-estate/" + id,
                        data(
                                "name",
                                "Gift review",
                                "address",
                                "Synthetic address",
                                "propertyType",
                                "RESIDENTIAL",
                                "currency",
                                "EUR",
                                "purchasePrice",
                                "10000",
                                "currentValue",
                                "165000",
                                "purchaseDate",
                                START.toString(),
                                "acquisitionType",
                                "GIFT",
                                "receivedValue",
                                null,
                                "isActive",
                                true),
                        owner,
                        200);
        assertThat(updated.path("appreciation").isNull()).isTrue();
        JsonNode asset =
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200);
        assertThat(asset.path("receivedValue").isNull()).isTrue();
        assertThat(asset.path("unrealizedGain").isNull()).isTrue();
        money(asset, "totalCost", "10000");
    }

    @Test
    void investmentAccountSeparatesCashFromFinancialHoldings() throws Exception {
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Brokerage",
                                        "type",
                                        "INVESTMENT",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        "100",
                                        "openingDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        json(
                "POST",
                "/assets",
                data(
                        "name",
                        "Stock",
                        "type",
                        "STOCK",
                        "quantity",
                        "2",
                        "purchasePrice",
                        "20",
                        "currentPrice",
                        "30",
                        "currency",
                        "EUR",
                        "purchaseDate",
                        START.toString(),
                        "accountId",
                        account),
                owner,
                201);
        JsonNode result = json("GET", "/accounts/" + account, null, owner, 200);
        money(result, "balance", "160");
        money(result, "ownBalance", "100");
        money(result, "holdingsValue", "60");
    }

    @Test
    void aPrimaryMortgageIsVisibleEditableAndRemovableFromFinancing() throws Exception {
        long mortgage =
                json(
                                "POST",
                                "/liabilities",
                                data(
                                        "name",
                                        "Primary mortgage",
                                        "type",
                                        "MORTGAGE",
                                        "principal",
                                        "80000",
                                        "currentBalance",
                                        "80000",
                                        "interestRate",
                                        "3",
                                        "currency",
                                        "EUR",
                                        "startDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        JsonNode property =
                json(
                        "POST",
                        "/real-estate",
                        data(
                                "name",
                                "Financed home",
                                "address",
                                "Synthetic address",
                                "propertyType",
                                "RESIDENTIAL",
                                "currency",
                                "EUR",
                                "purchasePrice",
                                "100000",
                                "currentValue",
                                "100000",
                                "purchaseDate",
                                START.toString(),
                                "mortgageId",
                                mortgage),
                        owner,
                        201);
        long asset = property.path("assetId").asLong();
        JsonNode linked = json("GET", "/assets/" + asset + "/liabilities", null, owner, 200);
        assertThat(linked.size()).isEqualTo(1);
        money(linked.get(0), "allocatedBalance", "80000");
        money(property, "allocatedDebt", "80000");
        json(
                "PUT",
                "/liabilities/" + mortgage + "/asset-links",
                java.util.List.of(
                        data(
                                "assetId",
                                asset,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                "60")),
                owner,
                200);
        JsonNode updated =
                json("GET", "/real-estate/" + property.path("id").asLong(), null, owner, 200);
        money(updated, "allocatedDebt", "48000");
        json("PUT", "/liabilities/" + mortgage + "/asset-links", java.util.List.of(), owner, 200);
        JsonNode unlinked =
                json("GET", "/real-estate/" + property.path("id").asLong(), null, owner, 200);
        money(unlinked, "allocatedDebt", "0");
        assertThat(unlinked.path("mortgageId").isNull()).isTrue();
        money(json("GET", "/liabilities/" + mortgage, null, owner, 200), "currentBalance", "80000");
    }

    @Test
    void missingLoanTermsDoNotInventZeroForecasts() throws Exception {
        long loan =
                json(
                                "POST",
                                "/liabilities",
                                data(
                                        "name",
                                        "Mortgage without terms",
                                        "type",
                                        "MORTGAGE",
                                        "principal",
                                        "80000",
                                        "currentBalance",
                                        "80000",
                                        "interestRate",
                                        "3",
                                        "currency",
                                        "EUR",
                                        "startDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        JsonNode breakdown = json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200);
        money(breakdown, "currentBalance", "80000");
        assertThat(breakdown.path("forecastAvailable").asBoolean()).isFalse();
        assertThat(breakdown.path("projectedInterest").isNull()).isTrue();
        assertThat(breakdown.path("totalProjectedCost").isNull()).isTrue();
    }
}
