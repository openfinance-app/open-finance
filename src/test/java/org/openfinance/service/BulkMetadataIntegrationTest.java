package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bulk metadata edits preserve financial state and ownership")
class BulkMetadataIntegrationTest extends AuditApiTestSupport {
    private JsonNode asset(long accountId) throws Exception {
        return json(
                "POST",
                "/assets",
                data(
                        "name",
                        "Holding",
                        "type",
                        "STOCK",
                        "quantity",
                        "2.5",
                        "purchasePrice",
                        "1.123456789123456789",
                        "currentPrice",
                        "12.123456789123456789",
                        "purchaseDate",
                        START.toString(),
                        "currency",
                        "EUR",
                        "accountId",
                        accountId),
                owner,
                201);
    }

    private long category(Auth user, String name) throws Exception {
        return json("POST", "/categories", data("name", name), user, 201).path("id").asLong();
    }

    private long institution(Auth user) throws Exception {
        return json(
                        "POST",
                        "/institutions",
                        data("name", "Institution", "country", "FR"),
                        user,
                        201)
                .path("id")
                .asLong();
    }

    private JsonNode patch(String resource, long id, Map<String, Object> values) throws Exception {
        return json("PATCH", "/" + resource + "/" + id + "/metadata", values, owner, 200);
    }

    private void unchanged(JsonNode before, JsonNode after, String... fields) {
        for (String field : fields)
            assertThat(after.path(field)).as(field).isEqualTo(before.path(field));
    }

    @Test
    void assetNotesAndLinksPreserveExactAmountsAndCanBeUndone() throws Exception {
        long cash = account(owner);
        JsonNode before = asset(cash);
        long id = before.path("id").asLong();
        JsonNode edited = patch("assets", id, data("notes", "Shared notes"));
        unchanged(
                before,
                edited,
                "name",
                "quantity",
                "purchasePrice",
                "currentPrice",
                "exactValues",
                "accountId",
                "purchaseDate",
                "lastUpdated");
        assertThat(edited.path("notes").asText()).isEqualTo("Shared notes");
        JsonNode unlinked = patch("assets", id, data("accountId", null));
        assertThat(unlinked.path("accountId").isNull()).isTrue();
        assertThat(unlinked.path("notes").asText()).isEqualTo("Shared notes");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "balance", "1000");
        JsonNode entry =
                json("GET", "/history?entityType=ASSET", null, owner, 200).path("content").get(0);
        assertThat(entry.path("canUndo").asBoolean()).isTrue();
        json("POST", "/history/" + entry.path("id").asLong() + "/undo", data(), owner, 200);
        assertThat(json("GET", "/assets/" + id, null, owner, 200).path("accountId").asLong())
                .isEqualTo(cash);
    }

    @Test
    void accountMetadataDoesNotTurnTheAssetInclusiveBalanceIntoAnAdjustment() throws Exception {
        long cash = account(owner);
        asset(cash);
        JsonNode before = json("GET", "/accounts/" + cash, null, owner, 200);
        long bank = institution(owner);
        JsonNode after =
                patch(
                        "accounts",
                        cash,
                        data(
                                "institutionId",
                                bank,
                                "type",
                                "SAVINGS",
                                "description",
                                "Shared purpose"));
        unchanged(
                before,
                after,
                "name",
                "balance",
                "ownBalance",
                "openingBalance",
                "openingDate",
                "currency");
        assertThat(after.path("type").asText()).isEqualTo("SAVINGS");
        assertThat(after.path("institution").path("id").asLong()).isEqualTo(bank);
        after = patch("accounts", cash, data("description", ""));
        assertThat(after.path("institution").path("id").asLong()).isEqualTo(bank);
        after = patch("accounts", cash, data("institutionId", null));
        assertThat(after.path("institution").isNull()).isTrue();
        money(after, "ownBalance", "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                cash))
                .isZero();
    }

    @Test
    void liabilityAndRepaymentMetadataDoNotReplayFinancialMovements() throws Exception {
        long cash = account(owner);
        long debt = loan(START);
        Map<String, Object> request = movement(cash, 100, START.plusDays(1));
        request.putAll(
                data("liabilityId", debt, "movementType", "REPAYMENT", "principalAmount", 80));
        JsonNode payment = json("POST", "/transactions", request, owner, 201);
        JsonNode before = json("GET", "/liabilities/" + debt, null, owner, 200);
        long bank = institution(owner);
        JsonNode after =
                patch("liabilities", debt, data("notes", "Loan note", "institutionId", bank));
        unchanged(
                before,
                after,
                "principal",
                "currentBalance",
                "interestRate",
                "startDate",
                "minimumPayment",
                "representedByAccountId",
                "fundedAmount");
        after = patch("liabilities", debt, data("notes", ""));
        assertThat(after.path("institution").path("id").asLong()).isEqualTo(bank);
        assertThat(
                        patch("liabilities", debt, data("institutionId", null))
                                .path("institution")
                                .isNull())
                .isTrue();
        JsonNode updated =
                patch(
                        "transactions",
                        payment.path("id").asLong(),
                        data("notes", "Payment note", "isReconciled", true));
        unchanged(
                payment,
                updated,
                "amount",
                "currency",
                "liabilityId",
                "movementType",
                "principalAmount",
                "trancheId",
                "exactValues");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
        money(json("GET", "/liabilities/" + debt, null, owner, 200), "currentBalance", "920");
    }

    @Test
    void splitMetadataPreservesSplitsAndCategoryFailuresAreAtomic() throws Exception {
        long cash = account(owner);
        long category = category(owner, "Expenses");
        Map<String, Object> request = movement(cash, 100, LocalDate.now());
        request.put(
                "splits",
                List.of(
                        data("categoryId", category, "amount", 60),
                        data("categoryId", category, "amount", 40)));
        JsonNode before = json("POST", "/transactions", request, owner, 201);
        long id = before.path("id").asLong();
        before = json("GET", "/transactions/" + id, null, owner, 200);
        JsonNode after =
                patch(
                        "transactions",
                        id,
                        data("notes", "Keep splits", "tags", "shared", "payee", "Merchant"));
        unchanged(before, after, "splits", "amount", "accountId", "date");
        json(
                "PATCH",
                "/transactions/" + id + "/metadata",
                data("categoryId", category, "notes", "Must not apply"),
                owner,
                400);
        assertThat(json("GET", "/transactions/" + id, null, owner, 200).path("notes").asText())
                .isEqualTo("Keep splits");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
    }

    @Test
    void ordinaryTransactionMetadataSetsAndClearsOnlyTheChosenFields() throws Exception {
        long cash = account(owner);
        long category = category(owner, "Expenses");
        JsonNode before =
                json("POST", "/transactions", movement(cash, 100, LocalDate.now()), owner, 201);
        long id = before.path("id").asLong();
        JsonNode after =
                patch(
                        "transactions",
                        id,
                        data(
                                "categoryId",
                                category,
                                "payee",
                                "Merchant",
                                "paymentMethod",
                                "CASH",
                                "isReconciled",
                                true));
        assertThat(after.path("categoryId").asLong()).isEqualTo(category);
        assertThat(after.path("payeeId").isNumber()).isTrue();
        unchanged(before, after, "amount", "description", "date", "accountId", "currency");
        after =
                patch(
                        "transactions",
                        id,
                        data(
                                "categoryId",
                                null,
                                "payee",
                                "",
                                "paymentMethod",
                                null,
                                "isReconciled",
                                false));
        assertThat(after.path("categoryId").isNull()).isTrue();
        assertThat(after.path("payeeId").isNull()).isTrue();
        assertThat(after.path("paymentMethod").isNull()).isTrue();
        assertThat(after.path("isReconciled").asBoolean()).isFalse();
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
    }

    @Test
    void transferMetadataPreservesBothLegsAndBalances() throws Exception {
        long source = account(owner);
        long destination = account(owner);
        Map<String, Object> request = movement(source, 100, LocalDate.now());
        request.putAll(data("type", "TRANSFER", "toAccountId", destination));
        JsonNode before = json("POST", "/transactions/transfer", request, owner, 201);
        long id = before.path("id").asLong();
        JsonNode after = patch("transactions", id, data("notes", "Transfer note"));
        unchanged(before, after, "transferId", "amount", "accountId", "type");
        json("PATCH", "/transactions/" + id + "/metadata", data("categoryId", null), owner, 400);
        money(json("GET", "/accounts/" + source, null, owner, 200), "ownBalance", "900");
        money(json("GET", "/accounts/" + destination, null, owner, 200), "ownBalance", "1100");
    }

    @Test
    void payeeDefaultCategoryCanBeClearedWithoutRecategorizingExistingTransactions()
            throws Exception {
        long cash = account(owner);
        long oldCategory = category(owner, "Old");
        long newCategory = category(owner, "New");
        JsonNode payee =
                json(
                        "POST",
                        "/payees",
                        data("name", "Merchant", "logo", "logo", "categoryId", oldCategory),
                        owner,
                        201);
        Map<String, Object> request = movement(cash, 100, LocalDate.now());
        request.putAll(data("payee", "Merchant", "categoryId", oldCategory));
        long transaction = json("POST", "/transactions", request, owner, 201).path("id").asLong();
        long id = payee.path("id").asLong();
        JsonNode after = patch("payees", id, data("categoryId", newCategory));
        assertThat(after.path("categoryId").asLong()).isEqualTo(newCategory);
        unchanged(payee, after, "name", "logo", "isActive");
        assertThat(patch("payees", id, data("categoryId", null)).path("categoryId").isNull())
                .isTrue();
        assertThat(
                        json("GET", "/transactions/" + transaction, null, owner, 200)
                                .path("categoryId")
                                .asLong())
                .isEqualTo(oldCategory);
    }

    @Test
    void metadataEndpointsRejectForeignRecordsAndForeignAssociations() throws Exception {
        long cash = account(owner);
        long asset = asset(cash).path("id").asLong();
        long debt = loan(START);
        long transaction =
                json("POST", "/transactions", movement(cash, 10, LocalDate.now()), owner, 201)
                        .path("id")
                        .asLong();
        long payee =
                json("POST", "/payees", data("name", "Merchant"), owner, 201).path("id").asLong();
        Auth other = register();
        for (Map.Entry<String, Long> entry :
                Map.of(
                                "assets",
                                asset,
                                "accounts",
                                cash,
                                "liabilities",
                                debt,
                                "transactions",
                                transaction)
                        .entrySet()) {
            json(
                    "PATCH",
                    "/" + entry.getKey() + "/" + entry.getValue() + "/metadata",
                    data("notes", "Foreign"),
                    other,
                    404);
        }
        json("PATCH", "/payees/" + payee + "/metadata", data("categoryId", null), other, 400);
        long foreignCash = account(other);
        long foreignCategory = category(other, "Foreign");
        long foreignBank = institution(other);
        json("PATCH", "/assets/" + asset + "/metadata", data("accountId", foreignCash), owner, 404);
        json(
                "PATCH",
                "/accounts/" + cash + "/metadata",
                data("institutionId", foreignBank),
                owner,
                404);
        json(
                "PATCH",
                "/liabilities/" + debt + "/metadata",
                data("institutionId", foreignBank),
                owner,
                404);
        json(
                "PATCH",
                "/transactions/" + transaction + "/metadata",
                data("categoryId", foreignCategory),
                owner,
                404);
        json(
                "PATCH",
                "/payees/" + payee + "/metadata",
                data("categoryId", foreignCategory),
                owner,
                404);
    }

    @Test
    void metadataKeepsImportedHoldingAndCreditCardLinkGuards() throws Exception {
        long cash = account(owner);
        long holding = asset(cash).path("id").asLong();
        jdbc.update("UPDATE assets SET import_source = ? WHERE id = ?", "test-import", holding);
        assertThat(patch("assets", holding, data("notes", "Allowed")).path("notes").asText())
                .isEqualTo("Allowed");
        json("PATCH", "/assets/" + holding + "/metadata", data("accountId", null), owner, 400);
        json("PATCH", "/assets/" + holding + "/metadata", data("brand", "Invalid"), owner, 400);

        patch("accounts", cash, data("type", "CREDIT_CARD"));
        json(
                "POST",
                "/liabilities",
                data(
                        "name",
                        "Card debt",
                        "type",
                        "CREDIT_CARD",
                        "principal",
                        0,
                        "currentBalance",
                        0,
                        "representedByAccountId",
                        cash,
                        "currency",
                        "EUR",
                        "startDate",
                        START.toString()),
                owner,
                201);
        patch("accounts", cash, data("description", "Allowed"));
        json("PATCH", "/accounts/" + cash + "/metadata", data("type", "SAVINGS"), owner, 400);
        assertThat(json("GET", "/accounts/" + cash, null, owner, 200).path("type").asText())
                .isEqualTo("CREDIT_CARD");
    }

    @Test
    void metadataPayloadsAreValidatedAtTheControllerBoundary() throws Exception {
        long cash = account(owner);
        json(
                "PATCH",
                "/accounts/" + cash + "/metadata",
                data("description", "x".repeat(501)),
                owner,
                400);
        json("PATCH", "/accounts/" + cash + "/metadata", data("type", "INVALID"), owner, 400);
        json("PATCH", "/accounts/" + cash + "/metadata", data("institutionId", -1), owner, 400);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
    }
}
