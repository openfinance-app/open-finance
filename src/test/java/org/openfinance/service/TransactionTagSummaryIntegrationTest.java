package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("Split-aware transaction summaries and category totals")
class TransactionTagSummaryIntegrationTest extends AuditApiTestSupport {
    @Autowired private TransactionRepository transactions;
    @Autowired private TransactionSplitRepository splits;
    @Autowired private EncryptionKeyCache keys;
    private long cash;

    @BeforeEach
    void prepareAccount() throws Exception {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        cash = account(owner);
    }

    @Test
    void totalsOnlyMatchingSplitsWithoutDoubleCountingParentTags() throws Exception {
        create(
                "EXPENSE",
                100,
                null,
                List.of(
                        data("amount", 40, "tags", "trip"),
                        data("amount", 10, "tags", "TRIP,trip"),
                        data("amount", 50, "tags", "daily")));
        create(
                "INCOME",
                30,
                null,
                List.of(data("amount", 10, "tags", "trip"), data("amount", 20, "tags", "daily")));
        create("EXPENSE", 15, "trip", List.of());
        create(
                "EXPENSE",
                100,
                "trip",
                List.of(data("amount", 40, "tags", "trip"), data("amount", 60, "tags", "daily")));
        create("EXPENSE", 20, "trip-extra", List.of());

        JsonNode exact = summary("tag=TrIp&size=1");
        assertThat(exact.path("transactionCount").asInt()).isEqualTo(4);
        money(exact, "expenseAmount", "165");
        money(exact, "incomeAmount", "10");
        money(exact, "netAmount", "-155");
        money(exact, "transferNetAmount", "0");
        JsonNode search = json("GET", "/transactions/search?tag=TrIp&size=1", null, owner, 200);
        assertThat(search.path("totalElements").asInt()).isEqualTo(4);

        JsonNode partial = summary("tags=TRIP");
        money(partial, "expenseAmount", "185");
        money(partial, "incomeAmount", "10");
        money(summary(""), "expenseAmount", "235");
        money(summary(""), "incomeAmount", "30");
        money(summary("tag=missing"), "netAmount", "0");
    }

    @Test
    void categoryAndTagFiltersSelectTheSameAllocationIncludingDescendants() throws Exception {
        Categories categories = categoryMovements();
        String food = "categoryId=" + categories.food();
        money(summary(food), "expenseAmount", "40");
        money(summary(food), "incomeAmount", "0");
        money(summary(food + "&includeSubcategories=true"), "expenseAmount", "70");
        money(summary(food + "&includeSubcategories=true"), "incomeAmount", "5");

        String combined = food + "&includeSubcategories=true&tag=trip";
        JsonNode selected = summary(combined);
        money(selected, "expenseAmount", "40");
        money(selected, "incomeAmount", "5");
        money(selected, "netAmount", "-35");
        assertCount(combined, 2);
        assertCount(food + "&tag=home", 0);
        money(summary(food + "&includeSubcategories=true&tags=TRIP"), "netAmount", "-35");
    }

    @Test
    void uncategorizedFilterIncludesOnlyUncategorizedAllocations() throws Exception {
        long food = category("Food", null);
        create(
                "EXPENSE",
                100,
                null,
                List.of(
                        data("amount", 40, "categoryId", food),
                        data("amount", 60, "categoryId", food)));
        JsonNode split =
                create(
                        "EXPENSE",
                        50,
                        null,
                        List.of(
                                data("amount", 20, "categoryId", food, "tags", "food"),
                                data("amount", 30, "tags", "unknown")));
        // A legacy parent category must not override its split allocations.
        jdbc.update(
                "UPDATE transactions SET category_id = ? WHERE id = ?",
                food,
                split.path("id").asLong());
        create("EXPENSE", 7, null, List.of());

        money(summary("noCategory=true"), "expenseAmount", "37");
        assertCount("noCategory=true", 2);
        money(summary("noCategory=true&tag=unknown"), "expenseAmount", "30");
        assertCount("noCategory=true&tag=food", 0);
        assertCount("noCategory=true&categoryId=" + food, 0);
    }

    @Test
    void categoryTreeBudgetsAndDashboardReconcileSplitsWithoutCountingParentsTwice()
            throws Exception {
        Categories categories = categoryMovements();
        JsonNode tree = json("GET", "/categories/tree", null, owner, 200);
        JsonNode food = categoryNode(tree, categories.food());
        money(food, "directAmount", "-40");
        money(food, "expenseAmount", "70");
        money(food, "incomeAmount", "5");
        money(food, "totalAmount", "-65");
        assertThat(food.path("transactionCount").asInt()).isEqualTo(3);
        money(categoryNode(tree, categories.housing()), "totalAmount", "-105");
        money(budgetProgress(categories.food()), "spent", "65");
        money(budgetProgress(categories.housing()), "spent", "105");

        String dates = "?startDate=" + LocalDate.now() + "&endDate=" + LocalDate.now();
        JsonNode spending = json("GET", "/dashboard/spending" + dates, null, owner, 200);
        money(spending, "Category_" + categories.food(), "40");
        money(spending, "Category_" + categories.groceries(), "30");
        money(spending, "Category_" + categories.housing(), "120");
        JsonNode cashflow = json("GET", "/dashboard/cashflow" + dates, null, owner, 200);
        money(cashflow, "income", "20");
        money(cashflow, "expenses", "190");
        money(cashflow, "netCashFlow", "-170");
        JsonNode sankey = json("GET", "/dashboard/cashflow-sankey" + dates, null, owner, 200);
        money(sankey, "totalIncome", "20");
        money(sankey, "totalExpenses", "190");
        money(
                categoryNode(sankey.path("expenseCategories"), categories.housing()),
                "amount",
                "120");
        money(summary(""), "incomeAmount", "20");
        money(summary(""), "expenseAmount", "190");
    }

    private record Categories(long food, long groceries, long housing) {}

    @Test
    void categoryQueriesExcludeSplitParentsAndLoadSplitContextWithoutAnOuterTransaction()
            throws Exception {
        Categories categories = categoryMovements();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        EncryptionContext.setKey(keys.getKeyBySessionToken(owner.session()).orElseThrow());
        try {
            assertThat(
                            transactions.findByCategoryIdInAndDateRange(
                                    List.of(categories.groceries()),
                                    LocalDate.now(),
                                    LocalDate.now(),
                                    owner.id()))
                    .isEmpty();
            List<TransactionSplit> allocations =
                    splits.findByCategoryIdInAndDateRange(
                            List.of(categories.groceries()),
                            LocalDate.now(),
                            LocalDate.now(),
                            owner.id());
            assertThat(allocations)
                    .extracting(split -> split.getAmount().intValueExact())
                    .containsExactlyInAnyOrder(5, 30);
            assertThat(allocations)
                    .allSatisfy(
                            split -> {
                                assertThat(split.getTransaction().getCurrency()).isEqualTo("EUR");
                                assertThat(split.getTransaction().getDate())
                                        .isEqualTo(LocalDate.now());
                                assertThat(split.getTransaction().getUserId())
                                        .isEqualTo(owner.id());
                            });
        } finally {
            EncryptionContext.clear();
        }
    }

    private Categories categoryMovements() throws Exception {
        long food = category("Food", null);
        long groceries = category("Groceries", food);
        long housing = category("Housing", null);
        JsonNode first =
                create(
                        "EXPENSE",
                        100,
                        null,
                        List.of(
                                data("amount", 40, "categoryId", food, "tags", "trip"),
                                data("amount", 60, "categoryId", housing, "tags", "home")));
        jdbc.update(
                "UPDATE transactions SET category_id = ? WHERE id = ?",
                groceries,
                first.path("id").asLong());
        create(
                "INCOME",
                20,
                null,
                List.of(
                        data("amount", 5, "categoryId", groceries, "tags", "trip"),
                        data("amount", 15, "categoryId", housing)));
        create(
                "EXPENSE",
                90,
                null,
                List.of(
                        data("amount", 30, "categoryId", groceries, "tags", "home"),
                        data("amount", 60, "categoryId", housing, "tags", "trip")));
        return new Categories(food, groceries, housing);
    }

    private long category(String name, Long parent) throws Exception {
        return json("POST", "/categories", data("name", name, "parentId", parent), owner, 201)
                .path("id")
                .asLong();
    }

    private JsonNode categoryNode(JsonNode nodes, long id) {
        for (JsonNode node : nodes) {
            if (node.path("id").asLong() == id || node.path("categoryId").asLong() == id)
                return node;
        }
        throw new AssertionError("Missing category " + id + " in " + nodes);
    }

    private JsonNode budgetProgress(long category) throws Exception {
        LocalDate today = LocalDate.now();
        long budget =
                json(
                                "POST",
                                "/budgets",
                                data(
                                        "categoryId",
                                        category,
                                        "amount",
                                        200,
                                        "currency",
                                        "EUR",
                                        "period",
                                        "MONTHLY",
                                        "startDate",
                                        today.withDayOfMonth(1).toString(),
                                        "endDate",
                                        today.withDayOfMonth(today.lengthOfMonth()).toString(),
                                        "rollover",
                                        false),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        return json("GET", "/budgets/" + budget + "/progress", null, owner, 200);
    }

    private void assertCount(String query, int expected) throws Exception {
        assertThat(summary(query).path("transactionCount").asInt()).isEqualTo(expected);
        assertThat(
                        json("GET", "/transactions/search?size=1&" + query, null, owner, 200)
                                .path("totalElements")
                                .asInt())
                .isEqualTo(expected);
    }

    private JsonNode create(String type, int amount, String tags, List<Map<String, Object>> splits)
            throws Exception {
        Map<String, Object> request = movement(cash, amount, LocalDate.now());
        request.put("type", type);
        request.put("tags", tags);
        request.put("splits", splits);
        return json("POST", "/transactions", request, owner, 201);
    }

    private JsonNode summary(String query) throws Exception {
        return json("GET", "/transactions/summary?" + query, null, owner, 200);
    }
}
