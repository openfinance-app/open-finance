package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated API, migrations, persistence and historical-accounting regressions. */
class BudgetInterestCorrectionIntegrationTest extends AuditApiTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    private org.openfinance.repository.BudgetAlertRepository alerts;

    @Test
    void previousPeriodNotificationsAreHiddenWithoutWritingDuringReadsAndTriggerAgain()
            throws Exception {
        long account = account(owner);
        long category = category("Monthly alerts", null);
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        Map<String, Object> plan = plan(category, start, start.plusMonths(2).minusDays(1));
        plan.put("rollover", false);
        long id = json("POST", "/budgets", plan, owner, 201).path("id").asLong();
        java.time.LocalDateTime old = start.atTime(12, 0);
        for (org.openfinance.entity.BudgetAlert alert : alerts.findByBudgetId(id)) {
            alert.setLastTriggered(old);
            alert.setRead(false);
            alerts.save(alert);
        }
        assertThat(json("GET", "/budgets/alerts/unread", null, owner, 200)).isEmpty();
        assertThat(json("GET", "/budgets/alerts/unread/count", null, owner, 200).asLong()).isZero();
        JsonNode configured = json("GET", "/budgets/alerts/" + id, null, owner, 200);
        assertThat(configured)
                .allSatisfy(
                        alert -> {
                            assertThat(alert.path("lastTriggered").isNull()).isTrue();
                            assertThat(alert.path("isRead").asBoolean()).isTrue();
                        });
        assertThat(alerts.findByBudgetId(id))
                .allSatisfy(alert -> assertThat(alert.getLastTriggered()).isEqualTo(old));
        spend(account, category, LocalDate.now(), "310", "EXPENSE");
        assertThat(json("GET", "/budgets/alerts/unread/count", null, owner, 200).asLong())
                .isEqualTo(3);
    }

    private long category(String name, Long parent) throws Exception {
        return json(
                        "POST",
                        "/categories",
                        data("name", name, "type", "EXPENSE", "parentId", parent),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    private Map<String, Object> plan(long category, LocalDate start, LocalDate end) {
        return data(
                "categoryId",
                category,
                "amount",
                "300",
                "currency",
                "EUR",
                "period",
                "MONTHLY",
                "startDate",
                start.toString(),
                "endDate",
                end.toString(),
                "rollover",
                true);
    }

    private void spend(long account, long category, LocalDate date, String amount, String type)
            throws Exception {
        Map<String, Object> payload = movement(account, 0, date);
        payload.putAll(data("categoryId", category, "amount", amount, "type", type));
        json("POST", "/transactions", payload, owner, 201);
    }

    @Test
    void annualPlanShowsMonthlyActualsDescendantsRefundsAndCurrentAllowance() throws Exception {
        long account = account(owner);
        long parent = category("Food", null);
        long child = category("Groceries", parent);
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(2);
        spend(account, parent, start, "100", "EXPENSE");
        spend(account, child, start.plusDays(1), "240.35", "EXPENSE");
        spend(account, child, start.plusDays(2), "40", "INCOME");
        spend(account, child, start.plusMonths(1), "400", "EXPENSE");
        spend(account, child, start.plusMonths(2), "154.44", "EXPENSE");
        long id =
                json(
                                "POST",
                                "/budgets",
                                plan(parent, start, start.plusYears(1).minusDays(1)),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        JsonNode history = json("GET", "/budgets/" + id + "/history", null, owner, 200);
        assertThat(history.path("history")).hasSize(12);
        money(history, "totalBudgeted", "3600");
        money(history, "totalSpent", "854.79");
        money(history.path("history").get(0), "spent", "300.35");
        money(history.path("history").get(1), "spent", "400");
        assertThat(history.path("history").get(3).path("future").asBoolean()).isTrue();
        JsonNode progress = json("GET", "/budgets/" + id + "/progress", null, owner, 200);
        money(progress, "allowance", "300");
        money(progress, "budgeted", "300");
        money(progress, "spent", "154.44");
        money(progress, "remaining", "145.56");
        assertThat(progress.path("startDate").asText()).isEqualTo(start.plusMonths(2).toString());
        JsonNode summary = json("GET", "/budgets/summary?period=MONTHLY", null, owner, 200);
        money(summary, "totalBudgeted", "300");
        money(summary, "totalSpent", "154.44");
    }

    @Test
    void carryIsSeparateFromNewAllowancesAndNegativeCarryDoesNotFabricatePercentages()
            throws Exception {
        long account = account(owner);
        long category = category("Carry", null);
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(2);
        spend(account, category, start, "100", "EXPENSE");
        spend(account, category, start.plusMonths(1), "900", "EXPENSE");
        Map<String, Object> plan = plan(category, start, start.plusMonths(3).minusDays(1));
        plan.put("rolloverOverspending", true);
        long id = json("POST", "/budgets", plan, owner, 201).path("id").asLong();
        JsonNode history = json("GET", "/budgets/" + id + "/history", null, owner, 200);
        money(history, "totalBudgeted", "900");
        money(history.path("history").get(1), "carryover", "200");
        money(history.path("history").get(1), "available", "500");
        JsonNode progress = json("GET", "/budgets/" + id + "/progress", null, owner, 200);
        money(progress, "budgeted", "-100");
        money(progress, "carryover", "-400");
        money(progress, "remaining", "-100");
        assertThat(progress.path("percentageSpent").isNull()).isTrue();
        assertThat(progress.path("status").asText()).isEqualTo("EXCEEDED");
        JsonNode alert =
                json(
                        "POST",
                        "/budgets/alerts?budgetId=" + id,
                        data("threshold", 80, "isEnabled", true),
                        owner,
                        201);
        assertThat(alert.path("lastTriggered").isNull()).isFalse();
        JsonNode alerts = json("GET", "/budgets/alerts/unread", null, owner, 200);
        assertThat(alerts.toString()).contains("depleted");
        money(
                json("GET", "/budgets/summary?period=MONTHLY", null, owner, 200),
                "totalRemaining",
                "-100");
    }

    @Test
    void singleAllowanceAndRecurringPartialPeriodsAreDistinctAndUndoable() throws Exception {
        long category = category("Once", null);
        LocalDate start = LocalDate.of(2025, 9, 1);
        Map<String, Object> plan = plan(category, start, start.plusMonths(1));
        plan.put("amount", "310");
        plan.put("recurring", false);
        long id = json("POST", "/budgets", plan, owner, 201).path("id").asLong();
        JsonNode once = json("GET", "/budgets/" + id + "/history", null, owner, 200);
        assertThat(once.path("history")).hasSize(1);
        money(once, "totalBudgeted", "310");
        plan.put("recurring", true);
        plan.put("rolloverOverspending", true);
        json("PUT", "/budgets/" + id, plan, owner, 200);
        JsonNode recurring = json("GET", "/budgets/" + id + "/history", null, owner, 200);
        assertThat(recurring.path("history")).hasSize(2);
        money(recurring, "totalBudgeted", "320");
        JsonNode event =
                json("GET", "/history?entityType=BUDGET", null, owner, 200).path("content").get(0);
        json("POST", "/history/" + event.path("id").asLong() + "/undo", null, owner, 200);
        JsonNode restored = json("GET", "/budgets/" + id, null, owner, 200);
        assertThat(restored.path("recurring").asBoolean()).isFalse();
        assertThat(restored.path("rolloverOverspending").asBoolean()).isFalse();
    }

    @Test
    void historicalInterestUsesImportedValueDatesWithoutChangingCashOrPostedInterest()
            throws Exception {
        long id =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Fortnight savings",
                                        "type",
                                        "SAVINGS",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        "0",
                                        "openingDate",
                                        "2024-12-20",
                                        "isInterestEnabled",
                                        true,
                                        "interestPeriod",
                                        "ANNUAL"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long category = category("Credit", null);
        spend(id, category, LocalDate.of(2024, 12, 20), "22950", "INCOME");
        spend(id, category, LocalDate.of(2025, 12, 31), "495.16", "INCOME");
        for (String[] rate :
                List.of(
                        new String[] {"2024-02-01", "3"},
                        new String[] {"2025-02-01", "2.4"},
                        new String[] {"2025-08-01", "1.7"},
                        new String[] {"2026-02-01", "1.5"},
                        new String[] {"2026-08-01", "1.7"})) {
            json(
                    "POST",
                    "/accounts/" + id + "/interest-variations",
                    data(
                            "rate",
                            rate[1],
                            "taxRate",
                            "0",
                            "validFrom",
                            rate[0],
                            "creditValueDateMode",
                            "FORTNIGHT",
                            "debitValueDateMode",
                            "FORTNIGHT",
                            "dayCountBasis",
                            "ACTUAL_365"),
                    owner,
                    201);
        }
        money(
                json("GET", "/accounts/" + id + "/interest-year?year=2024", null, owner, 200),
                "accrued",
                "0");
        JsonNode year2025 =
                json("GET", "/accounts/" + id + "/interest-year?year=2025", null, owner, 200);
        money(year2025, "accrued", "495.15");
        money(year2025, "calendarYearEstimate", "495.15");
        money(
                json("GET", "/accounts/" + id + "/interest-year?year=2026", null, owner, 200),
                "calendarYearEstimate",
                "375.32");
        money(json("GET", "/accounts/" + id, null, owner, 200), "ownBalance", "23445.16");
        JsonNode cash =
                json("GET", "/accounts/" + id + "/balance-history?period=ALL", null, owner, 200);
        assertThat(cash.toString()).contains("2024-12-20", "22950", "2025-12-31", "23445.16");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                id))
                .isEqualTo(2);
        Auth other = register();
        json("GET", "/accounts/" + id + "/interest-year?year=2025", null, other, 404);
    }

    @Test
    void editingInterestPoliciesPreservesExactRateAndBalanceAndRejectsConflicts() throws Exception {
        long id = account(owner);
        String path = "/accounts/" + id + "/interest-variations";
        String exact = "1.123456789012345678";
        Map<String, Object> settings =
                data("rate", exact, "taxRate", "0", "validFrom", "2025-01-01");
        long rate = json("POST", path, settings, owner, 201).path("id").asLong();
        settings.put("creditValueDateMode", "FORTNIGHT");
        settings.put("debitValueDateMode", "DAYS_2");
        settings.put("dayCountBasis", "THIRTY_360");
        JsonNode edited = json("PUT", path + "/" + rate, settings, owner, 200);
        assertThat(edited.path("exactValues").path("rate").asText()).isEqualTo(exact);
        assertThat(edited.path("debitValueDateMode").asText()).isEqualTo("DAYS_2");
        json("POST", path, settings, owner, 400);
        settings.put("rate", "0");
        json("PUT", path + "/" + rate, settings, owner, 200);
        settings.put("rate", "-1");
        json("PUT", path + "/" + rate, settings, owner, 400);
        money(json("GET", "/accounts/" + id, null, owner, 200), "ownBalance", "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                id))
                .isZero();
    }
}
