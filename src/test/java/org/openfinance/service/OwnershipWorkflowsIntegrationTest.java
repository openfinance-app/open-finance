package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Ownership events must preserve the ledger, reuse plans and survive failed requests/retries. */
class OwnershipWorkflowsIntegrationTest extends AuditApiTestSupport {
    private Map<String, Object> item(String acquisition, String value) {
        return data(
                "name",
                "Workflow car",
                "type",
                "VEHICLE",
                "quantity",
                "1",
                "purchasePrice",
                "150",
                "currentPrice",
                value,
                "currency",
                "EUR",
                "purchaseDate",
                START.toString(),
                "acquisitionType",
                acquisition);
    }

    private Map<String, Object> purchase(long account, Map<String, Object> item) {
        return data(
                "operationId", UUID.randomUUID().toString(), "accountId", account, "asset", item);
    }

    private Map<String, Object> sale(long account, String quantity, String proceeds, String fees) {
        return data(
                "operationId",
                UUID.randomUUID().toString(),
                "accountId",
                account,
                "date",
                START.plusDays(5).toString(),
                "quantity",
                quantity,
                "proceeds",
                proceeds,
                "fees",
                fees);
    }

    @Test
    void purchaseCreatesOnePaymentAndRetryDoesNotBuyTwice() throws Exception {
        long account = account(owner);
        Map<String, Object> request = purchase(account, item("PURCHASE", "150"));
        JsonNode result = json("POST", "/assets/purchase", request, owner, 201);
        JsonNode retry = json("POST", "/assets/purchase", request, owner, 201);
        assertThat(retry).isEqualTo(result);
        money(json("GET", "/accounts/" + account, null, owner, 200), "balance", "850");
        long id = result.path("assetId").asLong();
        money(json("GET", "/assets/" + id, null, owner, 200), "totalValue", "150");
        assertThat(
                        json("GET", "/transactions/search?assetId=" + id, null, owner, 200)
                                .path("totalElements")
                                .asInt())
                .isEqualTo(1);
        request.put("paymentDescription", "Different purchase");
        json("POST", "/assets/purchase", request, owner, 400);
    }

    @Test
    void importedPaymentCanFundAPlanWithoutAnotherCashDebit() throws Exception {
        long account = account(owner);
        JsonNode plan = json("POST", "/assets", item("PLANNED", "0"), owner, 201);
        JsonNode payment =
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
                                START.toString(),
                                "description",
                                "Imported car payment"),
                        owner,
                        201);
        Map<String, Object> request = purchase(account, item("PURCHASE", "150"));
        request.put("plannedAssetId", plan.path("id").asLong());
        request.put("existingPaymentId", payment.path("id").asLong());
        JsonNode result = json("POST", "/assets/purchase", request, owner, 201);
        assertThat(result.path("assetId").asLong()).isEqualTo(plan.path("id").asLong());
        money(json("GET", "/accounts/" + account, null, owner, 200), "balance", "850");
        JsonNode linked =
                json("GET", "/transactions/" + payment.path("id").asLong(), null, owner, 200);
        assertThat(linked.path("assetId").asLong()).isEqualTo(plan.path("id").asLong());
        assertThat(
                        json("GET", "/assets/" + plan.path("id").asLong(), null, owner, 200)
                                .path("acquisitionType")
                                .asText())
                .isEqualTo("PURCHASE");
    }

    @Test
    void invalidPaymentRollsBackCreationAndCannotStealAnExistingLink() throws Exception {
        long account = account(owner);
        Map<String, Object> request = purchase(account, item("PURCHASE", "150"));
        request.put("existingPaymentId", 999999L);
        json("POST", "/assets/purchase", request, owner, 400);
        assertThat(json("GET", "/assets", null, owner, 200).size()).isZero();
        money(json("GET", "/accounts/" + account, null, owner, 200), "balance", "1000");
    }

    @Test
    void saleRetainsTheItemAndDeletingItsPaymentRestoresOwnership() throws Exception {
        long account = account(owner);
        long id =
                json(
                                "POST",
                                "/assets/purchase",
                                purchase(account, item("PURCHASE", "150")),
                                owner,
                                201)
                        .path("assetId")
                        .asLong();
        Map<String, Object> request = sale(account, "1", "120", "20");
        JsonNode result = json("POST", "/assets/" + id + "/sale", request, owner, 201);
        assertThat(json("POST", "/assets/" + id + "/sale", request, owner, 201)).isEqualTo(result);
        JsonNode closed = json("GET", "/assets/" + id, null, owner, 200);
        assertThat(closed.path("ownershipStatus").asText()).isEqualTo("CLOSED");
        money(closed, "totalCost", "150");
        money(closed, "saleProceeds", "100");
        money(closed, "realizedGain", "-50");
        money(json("GET", "/accounts/" + account, null, owner, 200), "balance", "950");
        json("DELETE", "/transactions/" + result.path("transactionId").asLong(), null, owner, 204);
        JsonNode restored = json("GET", "/assets/" + id, null, owner, 200);
        assertThat(restored.path("ownershipStatus").asText()).isEqualTo("OWNED");
        money(json("GET", "/accounts/" + account, null, owner, 200), "balance", "850");
    }

    @Test
    void investmentSaleRetainsHistoryAndClearsFeesOnlyWhenReclassified() throws Exception {
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Broker",
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
        Map<String, Object> stock = item("PURCHASE", "120");
        stock.put("type", "STOCK");
        stock.put("quantity", "10");
        stock.put("purchasePrice", "100");
        stock.put("accountId", account);
        long id = json("POST", "/assets", stock, owner, 201).path("id").asLong();
        long payment =
                json(
                                "POST",
                                "/assets/" + id + "/sale",
                                sale(account, "10", "1200", "20"),
                                owner,
                                201)
                        .path("transactionId")
                        .asLong();
        JsonNode closed = json("GET", "/assets/" + id, null, owner, 200);
        money(closed, "quantity", "0");
        assertThat(closed.path("ownershipStatus").asText()).isEqualTo("CLOSED");
        money(closed, "realizedGain", "180");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1280");
        Map<String, Object> edit =
                data(
                        "accountId", account,
                        "type", "INCOME",
                        "amount", "1180",
                        "currency", "EUR",
                        "date", START.plusDays(5).toString(),
                        "description", "Corrected sale description");
        JsonNode stillSale = json("PUT", "/transactions/" + payment, edit, owner, 200);
        money(stillSale, "saleFees", "20");
        money(json("GET", "/assets/" + id, null, owner, 200), "quantity", "0");

        edit.put("investment", null);
        edit.put("description", "Ordinary income instead of a sale");
        JsonNode income = json("PUT", "/transactions/" + payment, edit, owner, 200);
        assertThat(income.path("saleFees").isNull() || income.path("saleFees").isMissingNode())
                .isTrue();
        JsonNode restored = json("GET", "/assets/" + id, null, owner, 200);
        money(restored, "quantity", "10");
        assertThat(restored.path("ownershipStatus").asText()).isEqualTo("OWNED");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1280");
    }

    private Map<String, Object> home(String acquisition, String value) {
        return data(
                "name",
                "Workflow home",
                "address",
                "Synthetic address",
                "propertyType",
                "RESIDENTIAL",
                "purchasePrice",
                "1000",
                "currentValue",
                value,
                "currency",
                "EUR",
                "purchaseDate",
                START.toString(),
                "acquisitionType",
                acquisition,
                "isActive",
                true);
    }

    @Test
    void propertyPlanKeepsItsIdentityAndSaleDoesNotRepayItsLoan() throws Exception {
        long account = account(owner);
        JsonNode plan = json("POST", "/real-estate", home("PLANNED", "0"), owner, 201);
        long existing =
                json(
                                "POST",
                                "/transactions",
                                data(
                                        "accountId",
                                        account,
                                        "type",
                                        "EXPENSE",
                                        "amount",
                                        "200",
                                        "currency",
                                        "EUR",
                                        "date",
                                        START.toString(),
                                        "description",
                                        "Already paid deposit"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> request =
                data(
                        "operationId",
                        UUID.randomUUID().toString(),
                        "plannedPropertyId",
                        plan.path("id").asLong(),
                        "property",
                        home("PURCHASE", "1000"),
                        "newMortgage",
                        data(
                                "name",
                                "Workflow mortgage",
                                "type",
                                "MORTGAGE",
                                "principal",
                                "800",
                                "currentBalance",
                                "0",
                                "currency",
                                "EUR",
                                "startDate",
                                START.toString()),
                        "loanAmount",
                        "800",
                        "downPayment",
                        "200",
                        "route",
                        "DIRECT",
                        "accountId",
                        account,
                        "existingPaymentId",
                        existing);
        JsonNode result = json("POST", "/real-estate/purchase", request, owner, 201);
        assertThat(result.path("propertyId")).isEqualTo(plan.path("id"));
        JsonNode property =
                json("GET", "/real-estate/" + plan.path("id").asLong(), null, owner, 200);
        assertThat(property.path("assetId")).isEqualTo(plan.path("assetId"));
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "800");
        money(
                json("GET", "/liabilities/" + result.path("mortgageId").asLong(), null, owner, 200),
                "currentBalance",
                "800");
        long assetId = property.path("assetId").asLong();
        long payment =
                json(
                                "POST",
                                "/assets/" + assetId + "/sale",
                                sale(account, "1", "1200", "100"),
                                owner,
                                201)
                        .path("transactionId")
                        .asLong();
        JsonNode closed = json("GET", "/real-estate/" + plan.path("id").asLong(), null, owner, 200);
        assertThat(closed.path("ownershipStatus").asText()).isEqualTo("CLOSED");
        money(closed, "realizedGain", "100");
        money(json("GET", "/transactions/" + payment, null, owner, 200), "saleFees", "100");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1900");
        money(
                json("GET", "/liabilities/" + result.path("mortgageId").asLong(), null, owner, 200),
                "currentBalance",
                "800");
        assertThat(json("GET", "/real-estate", null, owner, 200)).isEmpty();
        json("DELETE", "/transactions/" + payment, null, owner, 204);
        assertThat(
                        json("GET", "/real-estate/" + plan.path("id").asLong(), null, owner, 200)
                                .path("ownershipStatus")
                                .asText())
                .isEqualTo("OWNED");
    }

    @Test
    void linkingOpeningPurchaseIsMetadataOnlyAndCannotTakeAnotherOwnersPayment() throws Exception {
        long account = account(owner);
        long id = json("POST", "/assets", item("PURCHASE", "150"), owner, 201).path("id").asLong();
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
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
                                        START.toString(),
                                        "description",
                                        "Imported purchase"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        json(
                "POST",
                "/assets/" + id + "/purchase-payment",
                data("transactionId", payment),
                owner,
                204);
        json(
                "POST",
                "/assets/" + id + "/purchase-payment",
                data("transactionId", payment),
                owner,
                204);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "850");
        long otherItem =
                json("POST", "/assets", item("PURCHASE", "150"), owner, 201).path("id").asLong();
        json(
                "POST",
                "/assets/" + otherItem + "/purchase-payment",
                data("transactionId", payment),
                owner,
                400);
        Auth other = register();
        json("POST", "/assets/" + id + "/sale", sale(account, "1", "100", "0"), other, 400);
        json(
                "POST",
                "/assets/" + id + "/purchase-payment",
                data("transactionId", payment),
                other,
                400);
    }

    @Test
    void oversellOrMismatchedCurrencyDoesNotCreditCash() throws Exception {
        long account = account(owner);
        long id = json("POST", "/assets", item("PURCHASE", "150"), owner, 201).path("id").asLong();
        json("POST", "/assets/" + id + "/sale", sale(account, "2", "300", "0"), owner, 400);
        long foreign =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "USD",
                                        "type",
                                        "CHECKING",
                                        "currency",
                                        "USD",
                                        "initialBalance",
                                        "100",
                                        "openingDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        json("POST", "/assets/" + id + "/sale", sale(foreign, "1", "150", "0"), owner, 400);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
        assertThat(json("GET", "/assets/" + id, null, owner, 200).path("ownershipStatus").asText())
                .isEqualTo("OWNED");
    }

    @Test
    void undoAndRedoRestoreTheWholePurchaseAndSale() throws Exception {
        long account = account(owner);
        long id =
                json(
                                "POST",
                                "/assets/purchase",
                                purchase(account, item("PURCHASE", "150")),
                                owner,
                                201)
                        .path("assetId")
                        .asLong();
        long action =
                json("GET", "/history?entityType=ASSET&size=100", null, owner, 200)
                        .path("content")
                        .get(0)
                        .path("id")
                        .asLong();
        json("POST", "/history/" + action + "/undo", data(), owner, 200);
        assertThat(json("GET", "/assets", null, owner, 200)).isEmpty();
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
        json("POST", "/history/" + action + "/redo", data(), owner, 200);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "850");
        json("POST", "/assets/" + id + "/sale", sale(account, "1", "120", "20"), owner, 201);
        long saleAction =
                json("GET", "/history?entityType=ASSET&size=100", null, owner, 200)
                        .path("content")
                        .get(0)
                        .path("id")
                        .asLong();
        json("POST", "/history/" + saleAction + "/undo", data(), owner, 200);
        assertThat(json("GET", "/assets/" + id, null, owner, 200).path("ownershipStatus").asText())
                .isEqualTo("OWNED");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "850");
        json("POST", "/history/" + saleAction + "/redo", data(), owner, 200);
        assertThat(json("GET", "/assets/" + id, null, owner, 200).path("ownershipStatus").asText())
                .isEqualTo("CLOSED");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "950");
    }
}
