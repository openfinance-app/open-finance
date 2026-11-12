package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.AmountCurrencyMode;

class AmountComparisonTest {
    @Test
    void unavailableConversionIsExcludedFromBoundsButNativeModeStillWorks() {
        BigDecimal nativeAmount = new BigDecimal("40000");
        BigDecimal base =
                AmountComparison.select(
                        nativeAmount, "XOF", nativeAmount, "EUR", false, AmountCurrencyMode.BASE);
        assertThat(base).isNull();
        assertThat(AmountComparison.matches(base, null, null, BigDecimal.ZERO)).isFalse();
        assertThat(AmountComparison.matches(base, null, null, null)).isTrue();
        assertThat(
                        AmountComparison.select(
                                nativeAmount,
                                "XOF",
                                nativeAmount,
                                "EUR",
                                false,
                                AmountCurrencyMode.NATIVE))
                .isEqualByComparingTo("40000");
    }

    @Test
    void exactEqualityIgnoresDecimalScaleAndPreservesPrecisionAndSigns() {
        assertThat(
                        AmountComparison.matches(
                                new BigDecimal("500.00"), null, null, new BigDecimal("500")))
                .isTrue();
        assertThat(
                        AmountComparison.matches(
                                new BigDecimal("499.999999"), null, null, new BigDecimal("500")))
                .isFalse();
        assertThat(
                        AmountComparison.matches(
                                new BigDecimal("-500"), null, null, new BigDecimal("500")))
                .isFalse();
        assertThat(
                        AmountComparison.matches(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .isTrue();
    }

    @Test
    void unavailableAmountsSortLastInBothDirections() {
        ArrayList<BigDecimal> amounts =
                new ArrayList<>(Arrays.asList(null, BigDecimal.TEN, BigDecimal.ONE));
        amounts.sort(AmountComparison.comparator(value -> value, false));
        assertThat(amounts).containsExactly(BigDecimal.TEN, BigDecimal.ONE, null);
        amounts.sort(AmountComparison.comparator(value -> value, true));
        assertThat(amounts).containsExactly(BigDecimal.ONE, BigDecimal.TEN, null);
    }
}
