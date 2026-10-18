package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;

/** Anchored, inclusive budget periods. Carry never creates another allowance. */
public final class BudgetSchedule {
    private BudgetSchedule() {}

    public record Window(LocalDate start, LocalDate end, BigDecimal allowance) {}

    public static List<Window> windows(Budget budget) {
        BigDecimal amount = new BigDecimal(budget.getAmount());
        if (Boolean.FALSE.equals(budget.getRecurring())) {
            return List.of(new Window(budget.getStartDate(), budget.getEndDate(), amount));
        }
        List<Window> result = new ArrayList<>();
        for (int index = 0; ; index++) {
            LocalDate start = boundary(budget.getStartDate(), budget.getPeriod(), index);
            if (start.isAfter(budget.getEndDate())) return result;
            LocalDate next = boundary(budget.getStartDate(), budget.getPeriod(), index + 1);
            LocalDate end = next.minusDays(1);
            BigDecimal allowance = amount;
            if (end.isAfter(budget.getEndDate())) {
                end = budget.getEndDate();
                allowance =
                        amount.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1))
                                .divide(
                                        BigDecimal.valueOf(ChronoUnit.DAYS.between(start, next)),
                                        18,
                                        RoundingMode.HALF_EVEN);
            }
            result.add(new Window(start, end, allowance));
        }
    }

    private static LocalDate boundary(LocalDate anchor, BudgetPeriod period, int index) {
        return switch (period) {
            case WEEKLY -> anchor.plusWeeks(index);
            case MONTHLY -> anchor.plusMonths(index);
            case QUARTERLY -> anchor.plusMonths(3L * index);
            case YEARLY -> anchor.plusYears(index);
        };
    }

    public static BigDecimal carry(Budget budget, BigDecimal remaining) {
        if (!Boolean.TRUE.equals(budget.getRollover())) return BigDecimal.ZERO;
        return Boolean.TRUE.equals(budget.getRolloverOverspending())
                ? remaining
                : remaining.max(BigDecimal.ZERO);
    }

    /** Read-time filtering only: a prior period's notification is not a current alert. */
    public static boolean currentTrigger(Budget budget, LocalDateTime triggered) {
        if (triggered == null) return false;
        if (!Boolean.TRUE.equals(budget.getRecurring())) return true;
        LocalDate today = LocalDate.now();
        if (today.isBefore(budget.getStartDate()) || today.isAfter(budget.getEndDate()))
            return false;
        LocalDate start = budget.getStartDate();
        for (int index = 1; ; index++) {
            LocalDate next = boundary(budget.getStartDate(), budget.getPeriod(), index);
            if (today.isBefore(next)) {
                return !triggered.toLocalDate().isBefore(start)
                        && triggered.toLocalDate().isBefore(next)
                        && !triggered.toLocalDate().isAfter(budget.getEndDate());
            }
            start = next;
        }
    }
}
