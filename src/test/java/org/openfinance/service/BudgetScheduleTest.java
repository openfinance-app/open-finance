package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;

class BudgetScheduleTest {
    private Budget plan(String start, String end) {
        return Budget.builder()
                .amount("300")
                .period(BudgetPeriod.MONTHLY)
                .startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end))
                .build();
    }

    @Test
    void yearlyCoverageRenewsTwelveMonthlyAllowances() {
        java.util.List<BudgetSchedule.Window> windows =
                BudgetSchedule.windows(plan("2026-01-01", "2026-12-31"));
        assertThat(windows).hasSize(12);
        assertThat(windows).allSatisfy(w -> assertThat(w.allowance()).isEqualByComparingTo("300"));
        assertThat(windows.getLast().end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void inclusiveExtraDayIsProratedInsteadOfCreatingAnotherFullAllowance() {
        Budget budget = plan("2026-09-01", "2026-10-01");
        budget.setAmount("310");
        java.util.List<BudgetSchedule.Window> windows = BudgetSchedule.windows(budget);
        assertThat(windows).hasSize(2);
        assertThat(windows.get(1).allowance()).isEqualByComparingTo("10");
        budget.setRecurring(false);
        assertThat(BudgetSchedule.windows(budget)).hasSize(1);
        assertThat(BudgetSchedule.windows(budget).getFirst().allowance())
                .isEqualByComparingTo("310");
    }

    @Test
    void monthEndAnchorsDoNotDriftAfterFebruary() {
        java.util.List<BudgetSchedule.Window> windows =
                BudgetSchedule.windows(plan("2024-01-31", "2024-04-29"));
        assertThat(windows)
                .extracting(BudgetSchedule.Window::start)
                .containsExactly(
                        LocalDate.of(2024, 1, 31),
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2024, 3, 31));
        assertThat(windows).allSatisfy(w -> assertThat(w.allowance()).isEqualByComparingTo("300"));
    }

    @Test
    void carryingOverspendingRequiresAnExplicitChoice() {
        Budget budget = plan("2026-01-01", "2026-12-31");
        assertThat(BudgetSchedule.carry(budget, new BigDecimal("40"))).isZero();
        budget.setRollover(true);
        assertThat(BudgetSchedule.carry(budget, new BigDecimal("40"))).isEqualByComparingTo("40");
        assertThat(BudgetSchedule.carry(budget, new BigDecimal("-40"))).isZero();
        budget.setRolloverOverspending(true);
        assertThat(BudgetSchedule.carry(budget, new BigDecimal("-40"))).isEqualByComparingTo("-40");
    }

    @ParameterizedTest
    @CsvSource({
        "WEEKLY,2024-02-22,2024-03-06,2024-02-29",
        "QUARTERLY,2024-01-31,2024-07-30,2024-04-30",
        "YEARLY,2024-02-29,2026-02-27,2025-02-28"
    })
    void cadenceWindowsKeepTheOriginalAnchorAcrossLeapYears(
            BudgetPeriod period, String start, String end, String second) {
        Budget budget = plan(start, end);
        budget.setPeriod(period);
        assertThat(BudgetSchedule.windows(budget))
                .hasSize(2)
                .extracting(BudgetSchedule.Window::start)
                .containsExactly(LocalDate.parse(start), LocalDate.parse(second));
        assertThat(BudgetSchedule.windows(budget))
                .allSatisfy(window -> assertThat(window.allowance()).isEqualByComparingTo("300"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "before-start",
                "start",
                "today",
                "end",
                "after-end",
                "next-window",
                "future-plan",
                "expired-plan",
                "once",
                "null"
            })
    void onlyAlertsInsideTheActiveRecurringWindowRemainCurrent(String scenario) {
        LocalDate today = LocalDate.now();
        Budget budget = plan(today.minusDays(2).toString(), today.plusDays(2).toString());
        budget.setRecurring(true);
        budget.setPeriod(BudgetPeriod.WEEKLY);
        LocalDate trigger =
                switch (scenario) {
                    case "before-start" -> today.minusDays(3);
                    case "start" -> today.minusDays(2);
                    case "end" -> today.plusDays(2);
                    case "after-end" -> today.plusDays(3);
                    case "next-window" -> today.plusDays(5);
                    default -> today;
                };
        if (scenario.equals("future-plan")) budget.setStartDate(today.plusDays(1));
        if (scenario.equals("expired-plan")) budget.setEndDate(today.minusDays(1));
        if (scenario.equals("once")) budget.setRecurring(false);
        boolean expected = java.util.Set.of("start", "today", "end", "once").contains(scenario);
        assertThat(
                        BudgetSchedule.currentTrigger(
                                budget,
                                scenario.equals("null")
                                        ? null
                                        : org.openfinance.util.ReportingClock.startOfDay(trigger)))
                .isEqualTo(expected);
    }
}
