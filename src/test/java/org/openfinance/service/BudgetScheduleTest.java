package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
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
}
