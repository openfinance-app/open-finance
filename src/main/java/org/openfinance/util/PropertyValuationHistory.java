package org.openfinance.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RealEstateValueHistory;

/** Separates dated improvements from valuations that already incorporate known improvements. */
public final class PropertyValuationHistory {
    private static final Comparator<RealEstateValueHistory> BY_DATE =
            Comparator.comparing(RealEstateValueHistory::getEffectiveDate)
                    .thenComparing(RealEstateValueHistory::getId);

    private PropertyValuationHistory() {}

    public record Valuation(BigDecimal amount, String currency) {}

    public static Valuation atDate(
            RealEstateProperty property, List<RealEstateValueHistory> history, LocalDate date) {
        List<RealEstateValueHistory> eligible =
                history.stream().filter(h -> !h.getEffectiveDate().isAfter(date)).toList();
        return calculate(property, eligible, BY_DATE);
    }

    /** Current and historical views use the same effective-date policy. */
    public static Valuation current(
            RealEstateProperty property, List<RealEstateValueHistory> history) {
        return atDate(property, history, LocalDate.now());
    }

    private static Valuation calculate(
            RealEstateProperty property,
            List<RealEstateValueHistory> history,
            Comparator<RealEstateValueHistory> order) {
        RealEstateValueHistory anchor =
                history.stream().filter(h -> !h.isAdjustment()).max(order).orElse(null);
        BigDecimal amount =
                anchor == null
                        ? property.getPurchasePriceDecimal()
                        : new BigDecimal(anchor.getRecordedValue());
        String currency = anchor == null ? property.getCurrency() : anchor.getCurrency();
        if (amount == null) amount = BigDecimal.ZERO;
        for (RealEstateValueHistory entry : history) {
            if (!entry.isAdjustment()) continue;
            // Later-entered backdated corrections also correct the anchor's previously known value.
            boolean applies =
                    anchor == null
                            || entry.getId() > anchor.getId()
                            || entry.getEffectiveDate().isAfter(anchor.getEffectiveDate());
            if (!applies) continue;
            if (!currency.equalsIgnoreCase(entry.getCurrency())) {
                throw new IllegalStateException(
                        "Property valuation adjustments must use the valuation currency");
            }
            amount = amount.add(new BigDecimal(entry.getRecordedValue()));
        }
        return new Valuation(amount.max(BigDecimal.ZERO), currency);
    }
}
