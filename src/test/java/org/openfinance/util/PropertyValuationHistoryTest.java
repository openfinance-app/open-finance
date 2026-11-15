package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RealEstateValueHistory;

class PropertyValuationHistoryTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private final RealEstateProperty property =
            RealEstateProperty.builder().purchasePrice("1000").currency("EUR").build();

    @Test
    void improvementsWithoutAnAppraisalUsePurchaseCostAndNeverProduceNegativeWorth() {
        assertThat(
                        PropertyValuationHistory.atDate(
                                        property, List.of(entry(1, 0, "200", true)), START)
                                .amount())
                .isEqualByComparingTo("1200");
        assertThat(
                        PropertyValuationHistory.atDate(
                                        property, List.of(entry(1, 0, "-2000", true)), START)
                                .amount())
                .isZero();
        property.setPurchasePrice(null);
        assertThat(PropertyValuationHistory.atDate(property, List.of(), START).amount()).isZero();
    }

    @Test
    void aForeignCurrencyImprovementCannotBeAddedWithoutConversion() {
        RealEstateValueHistory adjustment = entry(2, 1, "100", true);
        adjustment.setCurrency("USD");
        assertThatThrownBy(
                        () ->
                                PropertyValuationHistory.atDate(
                                        property, List.of(adjustment), START.plusMonths(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currency");
    }

    @ParameterizedTest
    @CsvSource({"1,false,1500", "3,false,1600", "3,true,1500"})
    void legacyEntriesWithoutRecordingTimesUseStableIdsOnlyWhenNoMovementTimeExists(
            long id, boolean movementTime, String expected) {
        RealEstateValueHistory anchor = entry(2, 1, "1500", false);
        RealEstateValueHistory adjustment = entry(id, 0, "100", true);
        adjustment.setCreatedAt(null);
        if (movementTime) adjustment.setMovementRecordedAt(START.atStartOfDay());
        assertThat(
                        PropertyValuationHistory.atDate(
                                        property, List.of(anchor, adjustment), START.plusMonths(1))
                                .amount())
                .isEqualByComparingTo(expected);
    }

    private RealEstateValueHistory entry(long id, int month, String value, boolean adjustment) {
        return RealEstateValueHistory.builder()
                .id(id)
                .effectiveDate(START.plusMonths(month))
                .recordedValue(value)
                .currency("EUR")
                .adjustment(adjustment)
                .createdAt(START.atStartOfDay().plusSeconds(id))
                .build();
    }

    @Test
    void laterIndependentValuationSurvivesReversalOfAnEarlierImprovement() {
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
        RealEstateValueHistory reversal = entry(4, 1, "-200", true);
        reversal.setMovementRecordedAt(history.get(1).getCreatedAt());
        reversed.add(reversal);
        assertThat(
                        PropertyValuationHistory.atDate(property, reversed, START.plusMonths(1))
                                .amount())
                .isEqualByComparingTo("1000");
        assertThat(
                        PropertyValuationHistory.atDate(property, reversed, START.plusMonths(2))
                                .amount())
                .isEqualByComparingTo("1500");
        assertThat(PropertyValuationHistory.current(property, reversed).amount())
                .isEqualByComparingTo("1500");
    }

    @Test
    void newlyRecordedBackdatedContributionIsSupersededByTheNextValuation() {
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
