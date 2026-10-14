package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplitTagsIntegrationTest extends AuditApiTestSupport {

    @Test
    void splitTagsSurviveBackupRestoreAndMasterPasswordRotation() throws Exception {
        Map<String, Object> request =
                data(
                        "accountId",
                        account(owner),
                        "type",
                        "EXPENSE",
                        "amount",
                        100,
                        "currency",
                        "EUR",
                        "date",
                        START.plusDays(1).toString(),
                        "splits",
                        List.of(
                                data("amount", 70, "tags", List.of("Holiday")),
                                data("amount", 30, "tags", List.of("Work"))));
        long originalId = json("POST", "/transactions", request, owner, 201).path("id").asLong();
        JsonNode backup =
                json("POST", "/backup/create", data("description", "Split tags"), owner, 201);
        request.put(
                "splits",
                List.of(
                        data("amount", 70, "tags", List.of()),
                        data("amount", 30, "tags", List.of())));
        json("PUT", "/transactions/" + originalId, request, owner, 200);
        json(
                "POST",
                "/backup/restore/" + backup.path("id").asLong(),
                data("masterPassword", MASTER),
                owner,
                200);

        JsonNode restored = json("GET", "/transactions", null, owner, 200).get(0);
        long restoredId = restored.path("id").asLong();
        assertThat(restored.path("splits").get(0).path("tags").get(0).asText())
                .isEqualTo("Holiday");
        assertThat(restored.path("splits").get(1).path("tags").get(0).asText()).isEqualTo("Work");
        List<String> ciphertext =
                jdbc.queryForList(
                        "SELECT tags FROM transaction_splits WHERE transaction_id = ? ORDER BY id",
                        String.class,
                        restoredId);
        JsonNode rotation =
                json(
                        "PUT",
                        "/users/me/master-password",
                        data(
                                "currentMasterPassword",
                                MASTER,
                                "newMasterPassword",
                                "SplitReplacement123!"),
                        owner,
                        200);
        owner = new Auth(owner.id(), owner.token(), rotation.path("encryptionKey").asText());
        assertThat(json("GET", "/transactions/" + restoredId, null, owner, 200).path("splits"))
                .isEqualTo(restored.path("splits"));
        assertThat(
                        jdbc.queryForList(
                                "SELECT tags FROM transaction_splits WHERE transaction_id = ? ORDER BY id",
                                String.class,
                                restoredId))
                .doesNotContainAnyElementsOf(ciphertext)
                .doesNotContain("Holiday", "Work");
    }

    @Test
    void preservesSplitTagsAndCategoriesAcrossCreateEditAndReload() throws Exception {
        long accountId = account(owner);
        long groceries = category("Groceries");
        long supplies = category("Office supplies");
        Map<String, Object> request =
                data(
                        "accountId",
                        accountId,
                        "type",
                        "EXPENSE",
                        "amount",
                        100,
                        "currency",
                        "EUR",
                        "date",
                        START.plusDays(1).toString(),
                        "tags",
                        List.of("Receipt"),
                        "splits",
                        List.of(
                                data(
                                        "categoryId",
                                        groceries,
                                        "amount",
                                        70,
                                        "tags",
                                        List.of("Holiday")),
                                data(
                                        "categoryId",
                                        supplies,
                                        "amount",
                                        30,
                                        "tags",
                                        List.of("Work", "Refund"))));
        JsonNode created = json("POST", "/transactions", request, owner, 201);
        long id = created.path("id").asLong();
        assertSplits(id, groceries, supplies, "Holiday", "Work,Refund");
        assertThat(json("GET", "/transactions/tags/popular", null, owner, 200))
                .contains(
                        mapper.getNodeFactory().textNode("Holiday"),
                        mapper.getNodeFactory().textNode("Work"));
        assertThat(json("GET", "/transactions/by-tag/Holiday", null, owner, 200)).hasSize(1);
        assertThat(
                        json("GET", "/transactions/search?tags=Holiday", null, owner, 200)
                                .path("content"))
                .hasSize(1);
        assertThat(
                        json("GET", "/transactions/search?keyword=Holiday", null, owner, 200)
                                .path("content"))
                .hasSize(1);

        request.put(
                "splits",
                List.of(
                        data("categoryId", groceries, "amount", 70, "tags", List.of()),
                        data("categoryId", supplies, "amount", 30, "tags", List.of("Work"))));
        json("PUT", "/transactions/" + id, request, owner, 200);
        assertSplits(id, groceries, supplies, "", "Work");
        assertThat(json("GET", "/transactions/by-tag/Holiday", null, owner, 200)).isEmpty();

        assertThat(
                        jdbc.queryForList(
                                "SELECT tags FROM transaction_splits WHERE transaction_id = ?",
                                String.class,
                                id))
                .noneMatch("Work"::equals);
        Auth anotherUser = register();
        json("GET", "/transactions/" + id + "/splits", null, anotherUser, 404);
    }

    private long category(String name) throws Exception {
        return json("POST", "/categories", data("name", name, "type", "EXPENSE"), owner, 201)
                .path("id")
                .asLong();
    }

    private void assertSplits(
            long id, long groceries, long supplies, String firstTag, String secondTag)
            throws Exception {
        JsonNode transaction = json("GET", "/transactions/" + id, null, owner, 200);
        assertThat(transaction.path("tags").asText()).isEqualTo("Receipt");
        JsonNode splits = json("GET", "/transactions/" + id + "/splits", null, owner, 200);
        assertThat(splits).hasSize(2);
        assertThat(splits.get(0).path("categoryId").asLong()).isEqualTo(groceries);
        assertThat(splits.get(0).path("amount").decimalValue()).isEqualByComparingTo("70");
        assertThat(splits.get(0).path("tags"))
                .isEqualTo(
                        mapper.valueToTree(
                                firstTag.isEmpty() ? List.of() : List.of(firstTag.split(","))));
        assertThat(splits.get(1).path("categoryId").asLong()).isEqualTo(supplies);
        assertThat(splits.get(1).path("amount").decimalValue()).isEqualByComparingTo("30");
        assertThat(splits.get(1).path("tags"))
                .isEqualTo(
                        mapper.valueToTree(
                                secondTag.isEmpty() ? List.of() : List.of(secondTag.split(","))));
        assertThat(transaction.path("splits")).isEqualTo(splits);
    }
}
