package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

@DisplayName("Budget and History live-audit corrections")
class BudgetHistoryCorrectionsIntegrationTest extends AuditApiTestSupport {
    @Autowired private ExchangeRateRepository rates;
    @Autowired private CacheManager caches;

    @BeforeEach
    void useEuroBaseCurrency() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
    }

    @Test
    void deletingRolloverSourceTriggersAlertsAndUndoRedoRestoresThem() throws Exception {
        long category = category("Rollover food", "EXPENSE");
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        JsonNode previous = budget(category, currentMonth.minusMonths(1));
        JsonNode current = budget(category, currentMonth);
        long cash = account(owner);
        Map<String, Object> expense = movement(cash, 140, LocalDate.now());
        expense.put("categoryId", category);
        json("POST", "/transactions", expense, owner, 201);
        String progressPath = "/budgets/" + current.path("id").asLong() + "/progress";
        money(json("GET", progressPath, null, owner, 200), "budgeted", "200");
        assertThat(unreadAlerts()).isEmpty();

        json("DELETE", "/budgets/" + previous.path("id").asLong(), null, owner, 204);
        JsonNode deletion = budgetHistory().path("content").get(0);
        money(json("GET", progressPath, null, owner, 200), "budgeted", "100");
        JsonNode triggered = unreadAlerts();
        assertThat(triggered).hasSize(3);
        assertThat(triggered.findValues("threshold"))
                .extracting(value -> value.decimalValue().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("75", "90", "100");

        String reversalPath = "/history/" + deletion.path("id").asLong();
        json("POST", reversalPath + "/undo", null, owner, 200);
        money(json("GET", progressPath, null, owner, 200), "budgeted", "200");
        assertThat(unreadAlerts()).isEmpty();
        json("POST", reversalPath + "/redo", null, owner, 200);
        money(json("GET", progressPath, null, owner, 200), "budgeted", "100");
        assertThat(unreadAlerts().findValuesAsText("id"))
                .containsExactlyInAnyOrderElementsOf(triggered.findValuesAsText("id"));
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "860");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "EUR", "usd"})
    void suggestionsConvertTransactionsAndSplitsBeforeAveragingAndRounding(String currency)
            throws Exception {
        long food = suggestionLedger();
        JsonNode result =
                json(
                        "POST",
                        "/budgets/suggestions",
                        data(
                                "period",
                                "MONTHLY",
                                "lookbackMonths",
                                1,
                                "categoryIds",
                                List.of(food),
                                "currency",
                                currency),
                        owner,
                        200);
        assertThat(result).hasSize(1);
        JsonNode suggestion = result.get(0);
        boolean dollars = "usd".equals(currency);
        money(suggestion, "averageSpent", dollars ? "30.90" : "18.60");
        money(suggestion, "suggestedAmount", dollars ? "31" : "19");
        assertThat(suggestion.path("currency").asText()).isEqualTo(dollars ? "USD" : "EUR");
        assertThat(suggestion.path("transactionCount").asInt()).isEqualTo(2);
    }

    private long suggestionLedger() throws Exception {
        long food = category("Suggestion food", "EXPENSE");
        long other = category("Other split", "EXPENSE");
        long cash = account(owner);
        LocalDate older = LocalDate.now().minusDays(2);
        LocalDate newer = LocalDate.now().minusDays(1);
        rate(older, "2");
        rate(newer, "1.25");
        Map<String, Object> expense = movement(cash, 1, older);
        expense.put("categoryId", food);
        expense.put("amount", "10.20");
        json("POST", "/transactions", expense, owner, 201);
        Map<String, Object> split = movement(cash, 20, newer);
        split.put(
                "splits",
                List.of(
                        data("categoryId", food, "amount", "8.40"),
                        data("categoryId", other, "amount", "11.60")));
        json("POST", "/transactions", split, owner, 201);
        return food;
    }

    @Test
    void suggestionsRejectUnknownCurrency() throws Exception {
        json(
                "POST",
                "/budgets/suggestions",
                data("period", "MONTHLY", "lookbackMonths", 1, "currency", "NOT_A_CURRENCY"),
                owner,
                400);
    }

    @Test
    void incomeCategoryIsRejectedOnCreateAndUpdateWithoutChangingBudgetsOrHistory()
            throws Exception {
        long income = category("Salary", "INCOME");
        long food = category("Expense food", "EXPENSE");
        JsonNode budget = budget(food, LocalDate.now().withDayOfMonth(1));
        JsonNode beforeBudgets = json("GET", "/budgets", null, owner, 200);
        JsonNode beforeHistory = budgetHistory();
        Map<String, Object> invalid = budgetRequest(income, LocalDate.now().withDayOfMonth(1));
        json("POST", "/budgets", invalid, owner, 400);
        invalid.put("startDate", LocalDate.now().plusMonths(1).withDayOfMonth(1).toString());
        invalid.put(
                "endDate", LocalDate.now().plusMonths(2).withDayOfMonth(1).minusDays(1).toString());
        invalid.put("amount", "200");
        json("PUT", "/budgets/" + budget.path("id").asLong(), invalid, owner, 400);

        JsonNode unchanged = json("GET", "/budgets", null, owner, 200);
        assertThat(unchanged).hasSize(1);
        assertThat(unchanged).isEqualTo(beforeBudgets);
        assertThat(budgetHistory()).isEqualTo(beforeHistory);
        assertThat(json("GET", "/budgets/alerts/" + budget.path("id").asLong(), null, owner, 200))
                .hasSize(3);
    }

    private long category(String name, String type) throws Exception {
        return json("POST", "/categories", data("name", name, "type", type), owner, 201)
                .path("id")
                .asLong();
    }

    private Map<String, Object> budgetRequest(long category, LocalDate start) {
        return data(
                "categoryId",
                category,
                "amount",
                "100",
                "currency",
                "EUR",
                "period",
                "MONTHLY",
                "startDate",
                start.toString(),
                "endDate",
                start.plusMonths(1).minusDays(1).toString(),
                "rollover",
                true);
    }

    private JsonNode budget(long category, LocalDate start) throws Exception {
        return json("POST", "/budgets", budgetRequest(category, start), owner, 201);
    }

    private JsonNode unreadAlerts() throws Exception {
        return json("GET", "/budgets/alerts/unread", null, owner, 200);
    }

    private JsonNode budgetHistory() throws Exception {
        return json("GET", "/history?entityType=BUDGET", null, owner, 200);
    }

    private void rate(LocalDate date, String value) {
        ExchangeRate rate =
                rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("EUR", "USD", date)
                        .orElseGet(
                                () ->
                                        ExchangeRate.builder()
                                                .baseCurrency("EUR")
                                                .targetCurrency("USD")
                                                .rateDate(date)
                                                .build());
        rate.setRate(new BigDecimal(value));
        rate.setSource("budget-correction-test");
        rates.saveAndFlush(rate);
    }
}
