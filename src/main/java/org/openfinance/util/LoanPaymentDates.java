package org.openfinance.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.openfinance.entity.Liability;

/** Monthly contractual dates, including deferment of principal on already drawn opening debt. */
public final class LoanPaymentDates {
    private LoanPaymentDates() {}

    public static LocalDate nextPayment(Liability loan, LocalDate today) {
        LocalDate firstPrincipal = loan.getAmortizationStartDate();
        if (firstPrincipal == null) return today;
        int day = firstPrincipal.getDayOfMonth();
        LocalDate next = inMonth(YearMonth.from(today), day);
        return next.isBefore(today) ? inMonth(YearMonth.from(today).plusMonths(1), day) : next;
    }

    public static LocalDate paymentAt(Liability loan, LocalDate first, int monthOffset) {
        int day =
                loan.getAmortizationStartDate() == null
                        ? first.getDayOfMonth()
                        : loan.getAmortizationStartDate().getDayOfMonth();
        return inMonth(YearMonth.from(first).plusMonths(monthOffset), day);
    }

    private static LocalDate inMonth(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    public static boolean principalDeferred(Liability loan, LocalDate date) {
        return loan.getAmortizationStartDate() != null
                && date.isBefore(loan.getAmortizationStartDate());
    }

    public static long amortizingMonths(Liability loan) {
        if (loan.getAmortizationStartDate() != null)
            return ChronoUnit.MONTHS.between(
                            YearMonth.from(loan.getAmortizationStartDate()),
                            YearMonth.from(loan.getEndDate()))
                    + 1;
        return ChronoUnit.MONTHS.between(
                YearMonth.from(loan.getStartDate()), YearMonth.from(loan.getEndDate()));
    }
}
