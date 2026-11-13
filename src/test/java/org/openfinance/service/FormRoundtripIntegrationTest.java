package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Real JSON requests, encryption and database reloads for ordinary editor contracts. */
class FormRoundtripIntegrationTest extends AuditApiTestSupport {
    @Test
    void optionalCategoryAndPayeeRelationshipsDistinguishOmittedFromCleared() throws Exception {
        long parent =
                json("POST", "/categories", data("name", "Parent"), owner, 201).path("id").asLong();
        long child =
                json("POST", "/categories", data("name", "Child", "parentId", parent), owner, 201)
                        .path("id")
                        .asLong();
        assertThat(
                        json("PUT", "/categories/" + child, data("name", "Renamed"), owner, 200)
                                .path("parentId")
                                .asLong())
                .isEqualTo(parent);
        json("PUT", "/categories/" + child, data("name", "Renamed", "parentId", null), owner, 200);
        assertThat(json("GET", "/categories/" + child, null, owner, 200).path("parentId").isNull())
                .isTrue();

        long payee =
                json("POST", "/payees", data("name", "Vendor", "categoryId", parent), owner, 201)
                        .path("id")
                        .asLong();
        assertThat(
                        json("PUT", "/payees/" + payee, data("name", "Vendor"), owner, 200)
                                .path("categoryId")
                                .asLong())
                .isEqualTo(parent);
        json("PUT", "/payees/" + payee, data("name", "Vendor", "categoryId", null), owner, 200);
        assertThat(json("GET", "/payees/" + payee, null, owner, 200).path("categoryId").isNull())
                .isTrue();
    }

    @Test
    void clearingParentStillChecksDuplicateNamesAtRoot() throws Exception {
        long root =
                json("POST", "/categories", data("name", "Same"), owner, 201).path("id").asLong();
        long child =
                json("POST", "/categories", data("name", "Same", "parentId", root), owner, 201)
                        .path("id")
                        .asLong();
        json("PUT", "/categories/" + child, data("name", "Same", "parentId", null), owner, 400);
        assertThat(json("GET", "/categories/" + child, null, owner, 200).path("parentId").asLong())
                .isEqualTo(root);
    }

    @Test
    void ordinaryAssetEditClearsOptionalMetadataAndRetainsValidLongValues() throws Exception {
        Map<String, Object> body =
                data(
                        "name",
                        "Asset " + "a".repeat(180),
                        "type",
                        "ELECTRONICS",
                        "quantity",
                        "1",
                        "purchasePrice",
                        "500",
                        "currentPrice",
                        "400",
                        "currency",
                        "EUR",
                        "purchaseDate",
                        START.toString(),
                        "notes",
                        "n".repeat(800),
                        "serialNumber",
                        "serial",
                        "brand",
                        "b".repeat(180),
                        "model",
                        "model",
                        "condition",
                        "GOOD",
                        "warrantyExpiration",
                        LocalDate.now().plusYears(1).toString(),
                        "usefulLifeYears",
                        5);
        long id = json("POST", "/assets", body, owner, 201).path("id").asLong();
        body.put("name", "Renamed asset");
        assertThat(json("PUT", "/assets/" + id, body, owner, 200).path("notes").asText())
                .hasSize(800);
        for (String field : new String[] {"notes", "serialNumber", "brand", "model"})
            body.put(field, "");
        for (String field : new String[] {"condition", "warrantyExpiration", "usefulLifeYears"})
            body.put(field, null);
        json("PUT", "/assets/" + id, body, owner, 200);
        JsonNode result = json("GET", "/assets/" + id, null, owner, 200);
        for (String field :
                new String[] {
                    "notes",
                    "serialNumber",
                    "brand",
                    "model",
                    "condition",
                    "warrantyExpiration",
                    "usefulLifeYears"
                }) assertThat(result.path(field).isNull()).as(field).isTrue();
        money(result, "currentPrice", "400");
    }

    @Test
    void accountCanClearTextAndEditMetadataWhileOverdrawn() throws Exception {
        Map<String, Object> body =
                data(
                        "name",
                        "Checking",
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        "0",
                        "openingDate",
                        START.toString(),
                        "description",
                        "Old description",
                        "accountNumber",
                        "1234");
        long id = json("POST", "/accounts", body, owner, 201).path("id").asLong();
        json("POST", "/transactions", movement(id, 50, LocalDate.now()), owner, 201);
        body.put("name", "Account " + "a".repeat(180));
        body.put("initialBalance", "-50");
        body.put("adjustBalance", false);
        body.put("description", "");
        body.put("accountNumber", "");
        json("PUT", "/accounts/" + id, body, owner, 200);
        JsonNode result = json("GET", "/accounts/" + id, null, owner, 200);
        money(result, "ownBalance", "-50");
        assertThat(result.path("description").asText("")).isEmpty();
        assertThat(result.path("accountNumber").asText("")).isEmpty();
    }

    @Test
    void recurringPayeeIsEncryptedAndCopiedToTheFinalInclusiveOccurrence() throws Exception {
        long account = account(owner);
        Map<String, Object> body = recurring(account);
        body.put("endDate", LocalDate.now().toString());
        body.put("payee", "Private recurring vendor");
        body.put("notes", "Private notes");
        JsonNode template = json("POST", "/recurring-transactions", body, owner, 201);
        long id = template.path("id").asLong();
        assertThat(template.path("payee").asText()).isEqualTo("Private recurring vendor");
        String stored =
                jdbc.queryForObject(
                        "SELECT payee FROM recurring_transactions WHERE id = ?", String.class, id);
        assertThat(stored).isNotBlank().doesNotContain("Private recurring vendor");
        JsonNode processing = json("POST", "/recurring-transactions/process", null, owner, 200);
        assertThat(processing.path("processedCount").asInt()).isEqualTo(1);
        assertThat(processing.path("failedCount").asInt()).isZero();
        JsonNode generated =
                json("GET", "/transactions/search?accountId=" + account, null, owner, 200)
                        .path("content");
        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).path("payee").asText()).isEqualTo("Private recurring vendor");
        template = json("GET", "/recurring-transactions/" + id, null, owner, 200);
        assertThat(template.path("isActive").asBoolean()).isFalse();
        body.put("nextOccurrence", template.path("nextOccurrence").asText());
        body.put("description", "Renamed completed template");
        body.put("notes", null);
        body.put("payee", null);
        json("PUT", "/recurring-transactions/" + id, body, owner, 200);
        JsonNode reloaded = json("GET", "/recurring-transactions/" + id, null, owner, 200);
        assertThat(reloaded.path("payee").isNull()).isTrue();
        assertThat(reloaded.path("notes").isNull()).isTrue();
        body.put("frequency", "YEARLY");
        json("PUT", "/recurring-transactions/" + id, body, owner, 400);
    }

    @Test
    void endedRecurringTemplatesCanBeRenamedButNewInvalidSchedulesAreRejected() throws Exception {
        Map<String, Object> body = recurring(account(owner));
        long id = json("POST", "/recurring-transactions", body, owner, 201).path("id").asLong();
        LocalDate past = LocalDate.now().minusMonths(2);
        jdbc.update(
                "UPDATE recurring_transactions SET next_occurrence = date(?), end_date = date(?), is_active = false WHERE id = ?",
                past.plusMonths(1).toString(),
                past.toString(),
                id);
        body.put("nextOccurrence", past.plusMonths(1).toString());
        body.put("endDate", past.toString());
        body.put("description", "Updated old subscription");
        assertThat(
                        json("PUT", "/recurring-transactions/" + id, body, owner, 200)
                                .path("description")
                                .asText())
                .isEqualTo("Updated old subscription");
        json("POST", "/recurring-transactions", body, owner, 400);
        body.put("nextOccurrence", LocalDate.now().plusDays(2).toString());
        body.put("endDate", LocalDate.now().plusDays(1).toString());
        json("PUT", "/recurring-transactions/" + id, body, owner, 400);
    }

    @Test
    void transferMethodIsUpdatedOnBothLegsAndSupportsExplicitClear() throws Exception {
        long source = account(owner);
        long target = account(owner);
        Map<String, Object> body = movement(source, 100, LocalDate.now());
        body.put("type", "TRANSFER");
        body.put("toAccountId", target);
        body.put("paymentMethod", "BANK_TRANSFER");
        JsonNode transfer = json("POST", "/transactions/transfer", body, owner, 201);
        String path = "/transactions/transfers/" + transfer.path("transferId").asText();
        Map<String, Object> edit =
                data(
                        "fromAccountId",
                        source,
                        "toAccountId",
                        target,
                        "amount",
                        "100",
                        "currency",
                        "EUR",
                        "date",
                        LocalDate.now().toString());
        json("PUT", path, edit, owner, 200);
        assertPaymentMethod(source, target, "BANK_TRANSFER");
        edit.put("paymentMethod", "CASH");
        json("PUT", path, edit, owner, 200);
        assertPaymentMethod(source, target, "CASH");
        edit.put("paymentMethod", null);
        edit.put(
                "amount",
                "125"); // Exercise the financial edit path as well as metadata-only edits.
        json("PUT", path, edit, owner, 200);
        assertPaymentMethod(source, target, null);
    }

    private void assertPaymentMethod(long source, long target, String method) throws Exception {
        for (long account : new long[] {source, target}) {
            JsonNode txs =
                    json("GET", "/transactions/search?accountId=" + account, null, owner, 200)
                            .path("content");
            assertThat(txs).hasSize(1);
            assertThat(txs.get(0).path("paymentMethod").asText(null)).isEqualTo(method);
        }
    }

    private Map<String, Object> recurring(long account) {
        return data(
                "accountId",
                account,
                "type",
                "EXPENSE",
                "amount",
                "10",
                "currency",
                "EUR",
                "frequency",
                "MONTHLY",
                "description",
                "Recurring",
                "nextOccurrence",
                LocalDate.now().toString());
    }
}
