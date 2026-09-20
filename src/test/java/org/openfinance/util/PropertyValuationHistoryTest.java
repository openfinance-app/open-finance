package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RealEstateValueHistory;

class PropertyValuationHistoryTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private final RealEstateProperty property =
            RealEstateProperty.builder().purchasePrice("1000").currency("EUR").build();

    private RealEstateValueHistory entry(long id, int month, String value, boolean adjustment) {
        return RealEstateValueHistory.builder()
                .id(id)
                .effectiveDate(START.plusMonths(month))
                .recordedValue(value)
                .currency("EUR")
                .adjustment(adjustment)
                .build();
    }

    @Test
    void laterValuationIncludesKnownImprovementsAndTheirSubsequentReversal() {
        List<RealEstateValueHistory> history =
                List.of(
                        entry(1, 0, "1000", false),
                        entry(2, 1, "200", true),
                        entry(3, 2, "1500", false));
        assertThat(PropertyValuationHistory.atDate(property, history, START.plusMonths(1)).amount())
                .isEqualByComparingTo("1200");
        assertThat(PropertyValuationHistory.atDate(property, history, START.plusMonths(2)).amount())
                .isEqualByComparingTo("1500");
        List<RealEstateValueHistory> reversed = new java.util.ArrayList<>(history);
        reversed.add(entry(4, 1, "-200", true));
        assertThat(
                        PropertyValuationHistory.atDate(property, reversed, START.plusMonths(1))
                                .amount())
                .isEqualByComparingTo("1000");
        assertThat(
                        PropertyValuationHistory.atDate(property, reversed, START.plusMonths(2))
                                .amount())
                .isEqualByComparingTo("1300");
        assertThat(PropertyValuationHistory.current(property, reversed).amount())
                .isEqualByComparingTo("1300");
    }

    @Test
    void backdatedAdjustmentCorrectsEarlierValuationWithoutBeingCountedTwice() {
        List<RealEstateValueHistory> history =
                List.of(
                        entry(1, 0, "1000", false),
                        entry(2, 2, "1500", false),
                        entry(3, 1, "100", true),
                        entry(4, 3, "1700", false));
        assertThat(PropertyValuationHistory.atDate(property, history, START).amount())
                .isEqualByComparingTo("1000");
        assertThat(PropertyValuationHistory.atDate(property, history, START.plusMonths(1)).amount())
                .isEqualByComparingTo("1100");
        assertThat(PropertyValuationHistory.atDate(property, history, START.plusMonths(2)).amount())
                .isEqualByComparingTo("1600");
        assertThat(PropertyValuationHistory.atDate(property, history, START.plusMonths(3)).amount())
                .isEqualByComparingTo("1700");
    }

    @Test
    void currentAndHistoricalValuesChooseEffectiveDateBeforeEntryOrder() {
        List<RealEstateValueHistory> history =
                List.of(
                        entry(1, 0, "1000", false),
                        entry(2, 3, "200", false),
                        entry(3, 1, "100", false));
        assertThat(PropertyValuationHistory.atDate(property, history, START.plusMonths(1)).amount())
                .isEqualByComparingTo("100");
        assertThat(PropertyValuationHistory.atDate(property, history, START.plusMonths(3)).amount())
                .isEqualByComparingTo("200");
        assertThat(PropertyValuationHistory.current(property, history).amount())
                .isEqualByComparingTo("200");
    }
}
