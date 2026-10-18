package org.openfinance.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;

/** Calendar-year projection. Estimated capital stays separate from the recorded cash ledger. */
public final class CalendarInterestProjection {
    private CalendarInterestProjection() {}

    public record Result(BigDecimal grossInterest, BigDecimal taxAmount, BigDecimal netInterest) {}

    public static Result calculate(
            SortedMap<LocalDate, BigDecimal> cash,
            List<InterestRateVariation> rates,
            InterestPeriod period,
            Map<LocalDate, BigDecimal> postedInterest) {
        BigDecimal capitalized = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> entry : cash.entrySet()) {
            LocalDate date = entry.getKey();
            // Posted net interest is already in cash. Replace outstanding estimates with it;
            // never treat ordinary deposits as interest, or carry excess credits into later
            // periods.
            BigDecimal credit = postedInterest.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal replaced = credit.min(capitalized);
            capitalized = capitalized.subtract(replaced);
            credit = credit.subtract(replaced);
            replaced = credit.min(pending);
            pending = pending.subtract(replaced);
            credit = credit.subtract(replaced);

            InterestRateVariation rate = rateOn(rates, date);
            if (rate != null) {
                BigDecimal dailyGross =
                        rate.getDayCountBasis()
                                .dailyInterest(
                                        entry.getValue()
                                                .add(capitalized)
                                                .max(BigDecimal.ZERO)
                                                .multiply(rate.getRate().movePointLeft(2)),
                                        date);
                BigDecimal tax = rate.getTaxRate() == null ? BigDecimal.ZERO : rate.getTaxRate();
                BigDecimal dailyNet =
                        dailyGross.multiply(BigDecimal.ONE.subtract(tax.movePointLeft(2)));
                gross = gross.add(dailyGross);
                net = net.add(dailyNet);
                pending = pending.add(dailyNet);
            }
            if (endsPeriod(period, date)) {
                // A credit on the capitalization date can also cover that day's accrual.
                capitalized = capitalized.add(pending.subtract(credit).max(BigDecimal.ZERO));
                pending = BigDecimal.ZERO;
            }
        }
        return new Result(gross, gross.subtract(net), net);
    }

    private static InterestRateVariation rateOn(List<InterestRateVariation> rates, LocalDate date) {
        return rates.stream()
                .filter(rate -> !rate.getValidFrom().isAfter(date))
                .max(java.util.Comparator.comparing(InterestRateVariation::getValidFrom))
                .orElse(null);
    }

    private static boolean endsPeriod(InterestPeriod period, LocalDate date) {
        if (period == null) return false;
        if (period == InterestPeriod.DAILY) return true;
        if (date.getDayOfMonth() != date.lengthOfMonth()) return false;
        return switch (period) {
            case MONTHLY -> true;
            case QUARTERLY -> date.getMonthValue() % 3 == 0;
            case HALF_YEARLY -> date.getMonthValue() % 6 == 0;
            case ANNUAL -> date.getMonthValue() == 12;
            case DAILY -> true;
        };
    }
}
