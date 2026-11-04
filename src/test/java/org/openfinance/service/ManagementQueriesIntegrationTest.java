package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Encrypted management queries preserve ownership, filters, and paging")
class ManagementQueriesIntegrationTest extends AuditApiTestSupport {
    @Test
    void customPayeesCanBeFoundUpdatedAndDeletedWithoutLeakingAcrossUsers() throws Exception {
        long category =
                json(
                                "POST",
                                "/categories",
                                data("name", "Private category", "type", "EXPENSE"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> request =
                data(
                        "name",
                        "Private bakery",
                        "categoryId",
                        category,
                        "logo",
                        "data:image/png;base64,AA==");
        long id = json("POST", "/payees", request, owner, 201).path("id").asLong();
        assertThat(json("GET", "/payees/" + id, null, owner, 200).path("categoryName").asText())
                .isEqualTo("Private category");
        assertThat(json("GET", "/payees/search?query=BAKERY", null, owner, 200))
                .singleElement()
                .satisfies(p -> assertThat(p.path("id").asLong()).isEqualTo(id));
        assertThat(json("GET", "/payees/category/" + category, null, owner, 200))
                .singleElement()
                .satisfies(p -> assertThat(p.path("name").asText()).isEqualTo("Private bakery"));
        assertThat(json("GET", "/payees/custom", null, owner, 200))
                .anySatisfy(p -> assertThat(p.path("id").asLong()).isEqualTo(id));
        assertThat(json("GET", "/payees/active", null, owner, 200))
                .anySatisfy(p -> assertThat(p.path("id").asLong()).isEqualTo(id));
        assertThat(json("GET", "/payees", null, owner, 200))
                .anySatisfy(p -> assertThat(p.path("id").asLong()).isEqualTo(id));
        assertThat(
                        json("POST", "/payees/find-or-create?name=Private bakery", null, owner, 201)
                                .path("id")
                                .asLong())
                .isEqualTo(id);
        Auth other = register();
        assertThat(json("GET", "/payees/search?query=BAKERY", null, other, 200)).isEmpty();
        assertThat(json("GET", "/payees/category/" + category, null, other, 200)).isEmpty();
        json("GET", "/payees/" + id, null, other, 404);
        request.put("name", "Renamed bakery");
        assertThat(json("PUT", "/payees/" + id, request, owner, 200).path("name").asText())
                .isEqualTo("Renamed bakery");
        json("DELETE", "/payees/" + id, null, owner, 204);
        json("GET", "/payees/" + id, null, owner, 404);
    }

    @Test
    void recurringSearchFiltersDecryptedNotesBeforePaginationAndCountsOnlyOwnedRows()
            throws Exception {
        long cash = account(owner);
        recurring(owner, cash, "Zulu rent", "special building", "MONTHLY", 2);
        recurring(owner, cash, "Other utility", "special building", "MONTHLY", 1);
        recurring(owner, cash, "Unrelated", "outside match", "WEEKLY", 3);
        Auth other = register();
        recurring(other, account(other), "Foreign special", "special building", "MONTHLY", 1);
        String query =
                "/recurring-transactions/paged?search=special&frequency=MONTHLY&type=EXPENSE&isActive=true&accountId="
                        + cash
                        + "&size=1&sort=nextOccurrence,asc";
        JsonNode first = json("GET", query, null, owner, 200);
        assertThat(first.path("totalElements").asInt()).isEqualTo(2);
        assertThat(first.path("content").get(0).path("description").asText())
                .isEqualTo("Other utility");
        JsonNode second = json("GET", query + "&page=1", null, owner, 200);
        assertThat(second.path("content").get(0).path("description").asText())
                .isEqualTo("Zulu rent");
        assertThat(json("GET", query + "&page=5", null, owner, 200).path("content")).isEmpty();
        assertThat(
                        json(
                                        "GET",
                                        "/recurring-transactions/paged?searchRegex=true&search=Zulu.*",
                                        null,
                                        owner,
                                        200)
                                .path("totalElements")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        json("GET", "/recurring-transactions/paged", null, owner, 200)
                                .path("totalElements")
                                .asInt())
                .isEqualTo(3);
    }

    @Test
    void locallyGeneratedInsightsAreReadablePrioritizedAndDismissedOnlyByTheirOwner()
            throws Exception {
        long cash = account(owner);
        json("POST", "/transactions", movement(cash, 950, LocalDate.now()), owner, 201);
        JsonNode generated = json("POST", "/insights/generate", null, owner, 201);
        JsonNode warning = null;
        for (JsonNode insight : generated) {
            if (insight.path("type").asText().equals("CASH_FLOW_WARNING")) warning = insight;
        }
        assertThat(warning).isNotNull();
        long id = warning.path("id").asLong();
        assertThat(json("GET", "/insights", null, owner, 200))
                .anySatisfy(i -> assertThat(i.path("id").asLong()).isEqualTo(id));
        assertThat(json("GET", "/insights/top/1", null, owner, 200)).hasSize(1);
        json("GET", "/insights/top/0", null, owner, 400);
        json("GET", "/insights/top/21", null, owner, 400);
        Auth other = register();
        assertThat(json("GET", "/insights", null, other, 200)).isEmpty();
        json("POST", "/insights/" + id + "/dismiss", null, other, 404);
        json("POST", "/insights/" + id + "/dismiss", null, owner, 204);
        assertThat(json("GET", "/insights", null, owner, 200))
                .noneMatch(i -> i.path("id").asLong() == id);
    }

    private void recurring(
            Auth auth, long cash, String description, String notes, String frequency, int days)
            throws Exception {
        json(
                "POST",
                "/recurring-transactions",
                data(
                        "accountId",
                        cash,
                        "type",
                        "EXPENSE",
                        "amount",
                        "12.34",
                        "currency",
                        "EUR",
                        "description",
                        description,
                        "notes",
                        notes,
                        "frequency",
                        frequency,
                        "nextOccurrence",
                        LocalDate.now().plusDays(days).toString()),
                auth,
                201);
    }

    @Test
    void profileImageRoundTripAffectsOnlyTheAuthenticatedUser() throws Exception {
        byte[] png =
                java.util.Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3n0AAAAASUVORK5CYII=");
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "image", "avatar.png", "image/png", png);
        String response =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .multipart("/api/v1/users/me/profile-image")
                                        .file(file)
                                        .header("Authorization", "Bearer " + owner.token())
                                        .header("X-Encryption-Session", owner.session()))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .status()
                                        .isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String expected =
                "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png);
        assertThat(mapper.readTree(response).path("profileImage").asText()).isEqualTo(expected);
        assertThat(json("GET", "/users/me", null, owner, 200).path("profileImage").asText())
                .isEqualTo(expected);
        Auth other = register();
        assertThat(json("GET", "/users/me", null, other, 200).path("profileImage").isNull())
                .isTrue();
        json("DELETE", "/users/me/profile-image", null, owner, 200);
        assertThat(json("GET", "/users/me", null, owner, 200).path("profileImage").isNull())
                .isTrue();
    }
}
