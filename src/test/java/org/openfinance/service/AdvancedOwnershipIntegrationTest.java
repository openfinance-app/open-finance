package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Financial and lifecycle boundaries from the asset/property management audit. */
class AdvancedOwnershipIntegrationTest extends AuditApiTestSupport {
    private long possession() throws Exception {
        return json(
                        "POST",
                        "/assets",
                        data(
                                "name",
                                "Audit car",
                                "type",
                                "VEHICLE",
                                "quantity",
                                "1",
                                "purchasePrice",
                                "150",
                                "currentPrice",
                                "150",
                                "currency",
                                "EUR",
                                "purchaseDate",
                                START.toString(),
                                "acquisitionType",
                                "PURCHASE"),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    private Map<String, Object> improvement(long account, long asset) {
        return data(
                "accountId",
                account,
                "type",
                "EXPENSE",
                "amount",
                "20",
                "currency",
                "EUR",
                "date",
                START.plusDays(2).toString(),
                "description",
                "Improvement",
                "assetId",
                asset,
                "movementType",
                "CAPITAL_IMPROVEMENT");
    }

    @Test
    void improvementCostAndValuationAreIndependentAcrossEditsAndDeletion() throws Exception {
        long account = account(owner);
        long asset = possession();
        Map<String, Object> request = improvement(account, asset);
        json("POST", "/transactions", request, owner, 201);
        JsonNode first = json("GET", "/assets/" + asset, null, owner, 200);
        money(first, "totalCost", "170");
        money(first, "totalValue", "150");
        request.put("valuationChange", "10");
        long payment = json("POST", "/transactions", request, owner, 201).path("id").asLong();
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "160");
        request.put("amount", "25");
        request.remove("valuationChange");
        money(
                json("PUT", "/transactions/" + payment, request, owner, 200),
                "valuationChange",
                "10");
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "160");
        request.put("valuationChange", "4");
        json("PUT", "/transactions/" + payment, request, owner, 200);
        JsonNode corrected = json("GET", "/assets/" + asset, null, owner, 200);
        money(corrected, "totalCost", "195");
        money(corrected, "totalValue", "154");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "955");
        json("DELETE", "/transactions/" + payment, null, owner, 204);
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "150");
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalCost", "170");
        JsonNode history =
                json(
                        "GET",
                        "/dashboard/networth-history?startDate="
                                + START
                                + "&endDate="
                                + START.withDayOfMonth(START.lengthOfMonth())
                                + "&recalculate=true",
                        null,
                        owner,
                        200);
        money(history.get(history.size() - 1), "netWorth", "1130");
    }

    @Test
    void fxImprovementKeepsCashCostAndValuationSeparateAcrossEditsUndoAndDeletion()
            throws Exception {
        long account = account(owner);
        long asset =
                json(
                                "POST",
                                "/assets",
                                data(
                                        "name",
                                        "Dollar car",
                                        "type",
                                        "VEHICLE",
                                        "quantity",
                                        "1",
                                        "purchasePrice",
                                        "20000",
                                        "currentPrice",
                                        "20000",
                                        "currency",
                                        "USD",
                                        "purchaseDate",
                                        START.toString(),
                                        "acquisitionType",
                                        "PURCHASE"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> request = improvement(account, asset);
        request.putAll(
                data(
                        "amount",
                        "500",
                        "originalAmount",
                        "1000",
                        "originalCurrency",
                        "USD",
                        "conversionRate",
                        "0.5",
                        "valuationChange",
                        "250"));
        long payment = json("POST", "/transactions", request, owner, 201).path("id").asLong();
        assertPosition(account, asset, "500", "21000", "20250");
        long creation = latestAction("TRANSACTION");
        json("POST", "/history/" + creation + "/undo", data(), owner, 200);
        assertPosition(account, asset, "1000", "20000", "20000");
        json("POST", "/history/" + creation + "/redo", data(), owner, 200);
        assertPosition(account, asset, "500", "21000", "20250");
        request.put("amount", "600");
        request.put("originalAmount", "1200");
        request.remove("valuationChange");
        money(
                json("PUT", "/transactions/" + payment, request, owner, 200),
                "valuationChange",
                "250");
        assertPosition(account, asset, "400", "21200", "20250");
        request.put("valuationChange", "100");
        json("PUT", "/transactions/" + payment, request, owner, 200);
        assertPosition(account, asset, "400", "21200", "20100");
        json("DELETE", "/transactions/" + payment, null, owner, 204);
        assertPosition(account, asset, "1000", "20000", "20000");
        json("POST", "/history/" + latestAction("TRANSACTION") + "/undo", data(), owner, 200);
        assertPosition(account, asset, "400", "21200", "20100");
    }

    private long latestAction(String entityType) throws Exception {
        return json("GET", "/history?entityType=" + entityType + "&size=100", null, owner, 200)
                .path("content")
                .get(0)
                .path("id")
                .asLong();
    }

    private void assertPosition(long account, long asset, String cash, String cost, String value)
            throws Exception {
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", cash);
        JsonNode item = json("GET", "/assets/" + asset, null, owner, 200);
        money(item, "totalCost", cost);
        money(item, "totalValue", value);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void buyingAgainReopensSoldHoldingAndPreservesRealizedGainAcrossUndoAndRedo(int purchaseDay)
            throws Exception {
        long account = account(owner);
        long asset =
                json(
                                "POST",
                                "/assets",
                                data(
                                        "name",
                                        "Reopened shares",
                                        "type",
                                        "STOCK",
                                        "quantity",
                                        "2",
                                        "purchasePrice",
                                        "10",
                                        "currentPrice",
                                        "10",
                                        "currency",
                                        "EUR",
                                        "purchaseDate",
                                        START.toString(),
                                        "accountId",
                                        account),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        json(
                "POST",
                "/assets/" + asset + "/sale",
                data(
                        "operationId",
                        java.util.UUID.randomUUID().toString(),
                        "accountId",
                        account,
                        "date",
                        START.plusDays(1).toString(),
                        "quantity",
                        "2",
                        "proceeds",
                        "30",
                        "fees",
                        "0"),
                owner,
                201);
        JsonNode closed = json("GET", "/assets/" + asset, null, owner, 200);
        assertThat(closed.path("ownershipStatus").asText()).isEqualTo("CLOSED");
        money(closed, "realizedGain", "10");
        Map<String, Object> buy =
                data(
                        "operationId",
                        java.util.UUID.randomUUID().toString(),
                        "date",
                        START.plusDays(purchaseDay).toString(),
                        "quantity",
                        "2",
                        "amount",
                        "24");
        json("POST", "/assets/" + asset + "/buy", buy, owner, 201);
        json("POST", "/assets/" + asset + "/buy", buy, owner, 201);
        assertPosition(account, asset, "1006", "24", "20");
        JsonNode reopened = json("GET", "/assets/" + asset, null, owner, 200);
        assertThat(reopened.path("ownershipStatus").asText()).isEqualTo("OWNED");
        money(reopened, "quantity", "2");
        money(reopened, "realizedGain", "10");
        long action = latestAction("ASSET");
        json("POST", "/history/" + action + "/undo", data(), owner, 200);
        assertPosition(account, asset, "1030", "0", "0");
        json("POST", "/assets/" + asset + "/buy", buy, owner, 201);
        assertPosition(account, asset, "1030", "0", "0");
        json("POST", "/history/" + action + "/redo", data(), owner, 200);
        assertPosition(account, asset, "1006", "24", "20");
        money(json("GET", "/assets/" + asset, null, owner, 200), "realizedGain", "10");
    }

    @Test
    void propertyImprovementChangesValuationOnlyByTheChosenAmount() throws Exception {
        long account = account(owner);
        long property = property(owner).path("id").asLong();
        Map<String, Object> request = improvement(account, 0);
        request.remove("assetId");
        request.put("realEstateId", property);
        json("POST", "/transactions", request, owner, 201);
        money(json("GET", "/real-estate/" + property, null, owner, 200), "currentValue", "1000");
        request.put("valuationChange", "7");
        long payment = json("POST", "/transactions", request, owner, 201).path("id").asLong();
        money(json("GET", "/real-estate/" + property, null, owner, 200), "currentValue", "1007");
        json("DELETE", "/transactions/" + payment, null, owner, 204);
        money(json("GET", "/real-estate/" + property, null, owner, 200), "currentValue", "1000");
        request.remove("movementType");
        assertThat(json("POST", "/transactions", request, owner, 400)).isNotNull();
    }

    @Test
    void oldAutomaticValuationRemainsReversibleWithoutRevaluingACostEdit() throws Exception {
        long account = account(owner);
        long asset = possession();
        Map<String, Object> request = improvement(account, asset);
        request.put("valuationChange", "20");
        long payment = json("POST", "/transactions", request, owner, 201).path("id").asLong();
        jdbc.update("UPDATE transactions SET valuation_change = NULL WHERE id = ?", payment);
        request.remove("valuationChange");
        request.put("amount", "25");
        json("PUT", "/transactions/" + payment, request, owner, 200);
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "170");
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalCost", "175");
        json("DELETE", "/transactions/" + payment, null, owner, 204);
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "150");
    }

    @Test
    void upkeepMustFallWithinOwnershipRatherThanActAsAnUnlabelledDeposit() throws Exception {
        long account = account(owner);
        long asset = possession();
        Map<String, Object> request = improvement(account, asset);
        request.put("movementType", "MAINTENANCE");
        request.put("date", START.minusDays(1).toString());
        json("POST", "/transactions", request, owner, 400);
        request.put("date", java.time.LocalDate.now().plusDays(1).toString());
        json("POST", "/transactions", request, owner, 400);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
    }

    private Map<String, Object> purchase(long account, String route) {
        return data(
                "operationId",
                java.util.UUID.randomUUID().toString(),
                "accountId",
                account,
                "asset",
                data(
                        "name",
                        "Financed car",
                        "type",
                        "VEHICLE",
                        "quantity",
                        "1",
                        "purchasePrice",
                        "150",
                        "currentPrice",
                        "150",
                        "currency",
                        "EUR",
                        "purchaseDate",
                        START.plusDays(3).toString(),
                        "acquisitionType",
                        "PURCHASE"),
                "loanAmount",
                "100",
                "route",
                route,
                "newLoan",
                data(
                        "name",
                        "Car loan",
                        "type",
                        "LOAN",
                        "principal",
                        "100",
                        "currentBalance",
                        "0",
                        "currency",
                        "EUR",
                        "startDate",
                        START.plusDays(3).toString()));
    }

    @Test
    void carPurchaseSupportsBothLoanRoutesWithoutIncreasingItsValuation() throws Exception {
        long account = account(owner);
        for (String route : java.util.List.of("DIRECT", "ACCOUNT")) {
            Map<String, Object> request = purchase(account, route);
            long asset =
                    json("POST", "/assets/purchase", request, owner, 201).path("assetId").asLong();
            JsonNode item = json("GET", "/assets/" + asset, null, owner, 200);
            money(item, "totalValue", "150");
            JsonNode relationships =
                    json("GET", "/assets/" + asset + "/liabilities", null, owner, 200);
            assertThat(relationships.size()).isEqualTo(1);
            money(relationships.get(0), "allocatedBalance", "100");
            assertThat(
                            json("POST", "/assets/purchase", request, owner, 201)
                                    .path("assetId")
                                    .asLong())
                    .isEqualTo(asset);
        }
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "900");
    }

    @Test
    void purchaseReusesActualLoanReceiptWithoutDuplicatingCashOrDebt() throws Exception {
        long account = account(owner);
        Map<String, Object> request = purchase(account, "ACCOUNT");
        long loan =
                json("POST", "/liabilities", request.remove("newLoan"), owner, 201)
                        .path("id")
                        .asLong();
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                data("toAccountId", account, "amount", "100", "date", START.plusDays(3).toString()),
                owner,
                200);
        Long receipt =
                jdbc.queryForObject(
                        "SELECT id FROM transactions WHERE liability_id = ? AND movement_type = 'DISBURSEMENT'",
                        Long.class,
                        loan);
        request.put("existingLoanId", loan);
        request.put("existingLoanReceiptId", receipt);
        json("POST", "/assets/purchase", request, owner, 201);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "950");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "100");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE liability_id = ?",
                                Integer.class,
                                loan))
                .isEqualTo(1);
    }

    @Test
    void failedFinancedPurchaseRollsBackAndPlanCannotBeAcquiredThroughGenericEdit()
            throws Exception {
        long account = account(owner);
        Map<String, Object> request = purchase(account, "DIRECT");
        request.put("existingPaymentId", 999999);
        json("POST", "/assets/purchase", request, owner, 400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM assets WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM liabilities WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) request.get("asset");
        body.put("acquisitionType", "PLANNED");
        body.put("currentPrice", "0");
        long plan = json("POST", "/assets", body, owner, 201).path("id").asLong();
        body.put("acquisitionType", "PURCHASE");
        body.put("currentPrice", "150");
        json("PUT", "/assets/" + plan, body, owner, 400);
        request.remove("existingPaymentId");
        request.put("plannedAssetId", plan);
        assertThat(json("POST", "/assets/purchase", request, owner, 201).path("assetId").asLong())
                .isEqualTo(plan);
    }

    @Test
    void sharedFinancingAndCollateralRemainDistinctAndPrimaryEditsStayConsistent()
            throws Exception {
        JsonNode first = property(owner);
        JsonNode second = property(owner);
        long loan = loan(START);
        long a = first.path("assetId").asLong();
        long b = second.path("assetId").asLong();
        json(
                "PUT",
                "/liabilities/" + loan + "/asset-links",
                java.util.List.of(
                        data(
                                "assetId",
                                a,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                "60"),
                        data(
                                "assetId",
                                b,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                "40"),
                        data(
                                "assetId",
                                a,
                                "relationship",
                                "COLLATERAL",
                                "allocationPercentage",
                                "0")),
                owner,
                200);
        JsonNode balance = json("GET", "/liabilities/" + loan, null, owner, 200);
        java.math.BigDecimal debt = balance.path("currentBalance").decimalValue();
        money(
                json("GET", "/real-estate/" + first.path("id").asLong(), null, owner, 200),
                "allocatedDebt",
                debt.multiply(new java.math.BigDecimal("0.6")).toPlainString());
        money(
                json("GET", "/real-estate/" + second.path("id").asLong(), null, owner, 200),
                "allocatedDebt",
                debt.multiply(new java.math.BigDecimal("0.4")).toPlainString());
        Map<String, Object> body =
                data(
                        "name",
                        "Private property",
                        "address",
                        "Synthetic address",
                        "propertyType",
                        "RESIDENTIAL",
                        "purchasePrice",
                        "1000",
                        "currentValue",
                        "1000",
                        "currency",
                        "EUR",
                        "purchaseDate",
                        START.toString(),
                        "mortgageId",
                        loan);
        long propertyId = first.path("id").asLong();
        json("PUT", "/real-estate/" + propertyId, body, owner, 200);
        body.put("mortgageId", null);
        json("PUT", "/real-estate/" + propertyId, body, owner, 200);
        JsonNode remaining = json("GET", "/assets/" + a + "/liabilities", null, owner, 200);
        assertThat(remaining.size()).isEqualTo(1);
        assertThat(remaining.get(0).path("relationship").asText()).isEqualTo("COLLATERAL");
        money(json("GET", "/real-estate/" + propertyId, null, owner, 200), "allocatedDebt", "0");
        money(
                json("GET", "/real-estate/" + second.path("id").asLong(), null, owner, 200),
                "allocatedDebt",
                debt.multiply(new java.math.BigDecimal("0.4")).toPlainString());
    }

    @Test
    void directCarDrawReversalRestoresDebtWithoutChangingCashOrAsset() throws Exception {
        long account = account(owner);
        long asset =
                json("POST", "/assets/purchase", purchase(account, "DIRECT"), owner, 201)
                        .path("assetId")
                        .asLong();
        long loan =
                json("GET", "/assets/" + asset + "/liabilities", null, owner, 200)
                        .get(0)
                        .path("liabilityId")
                        .asLong();
        JsonNode tranche =
                json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200).get(0);
        assertThat(tranche.path("assetId").asLong()).isEqualTo(asset);
        json(
                "POST",
                "/tranches/" + tranche.path("id").asLong() + "/reverse",
                data("date", START.plusDays(4).toString()),
                owner,
                200);
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "0");
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "150");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "950");
    }

    @Test
    void buyMorePostsCashAndUnitsOnceAndRemainsReversible() throws Exception {
        long account = account(owner);
        long asset =
                json(
                                "POST",
                                "/assets",
                                data(
                                        "name",
                                        "Shares",
                                        "type",
                                        "STOCK",
                                        "quantity",
                                        "2",
                                        "purchasePrice",
                                        "10",
                                        "currentPrice",
                                        "10",
                                        "currency",
                                        "EUR",
                                        "purchaseDate",
                                        START.toString(),
                                        "accountId",
                                        account),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> request =
                data(
                        "operationId",
                        java.util.UUID.randomUUID().toString(),
                        "date",
                        START.plusDays(3).toString(),
                        "quantity",
                        "2",
                        "amount",
                        "30");
        long transaction =
                json("POST", "/assets/" + asset + "/buy", request, owner, 201)
                        .path("transactionId")
                        .asLong();
        assertThat(
                        json("POST", "/assets/" + asset + "/buy", request, owner, 201)
                                .path("transactionId")
                                .asLong())
                .isEqualTo(transaction);
        money(json("GET", "/assets/" + asset, null, owner, 200), "quantity", "4");
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalCost", "50");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "970");
        json("DELETE", "/transactions/" + transaction, null, owner, 204);
        money(json("GET", "/assets/" + asset, null, owner, 200), "quantity", "2");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
    }

    @Test
    void earlierPurchasePaymentCanBeLinkedWithoutAnotherDebit() throws Exception {
        long account = account(owner);
        long payment =
                json(
                                "POST",
                                "/transactions",
                                data(
                                        "accountId",
                                        account,
                                        "type",
                                        "EXPENSE",
                                        "amount",
                                        "150",
                                        "currency",
                                        "EUR",
                                        "date",
                                        START.plusDays(1).toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> request = purchase(account, "DIRECT");
        request.remove("newLoan");
        request.put("loanAmount", "0");
        request.put("existingPaymentId", payment);
        long asset = json("POST", "/assets/purchase", request, owner, 201).path("assetId").asLong();
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "850");
        json("PUT", "/assets/" + asset, request.get("asset"), owner, 200);
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalCost", "150");
    }
}
