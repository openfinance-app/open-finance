package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnnualizedReturnsTest {
    @Test
    void compoundsOverCompleteYears() {
        assertThat(
                        AnnualizedReturns.compound(
                                new BigDecimal("100000"),
                                new BigDecimal("144000"),
                                LocalDate.of(2024, 9, 30),
                                LocalDate.of(2026, 9, 30)))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void usesFractionalYearsIncludingLeapYears() {
        assertThat(
                        AnnualizedReturns.compound(
                                new BigDecimal("100"),
                                new BigDecimal("110"),
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 7, 2)))
                .isEqualByComparingTo("21.00");
    }

    @Test
    void supportsLossAndTotalLoss() {
        assertThat(
                        AnnualizedReturns.compound(
                                new BigDecimal("100"),
                                new BigDecimal("64"),
                                LocalDate.of(2024, 9, 30),
                                LocalDate.of(2026, 9, 30)))
                .isEqualByComparingTo("-20.00");
        assertThat(
                        AnnualizedReturns.compound(
                                new BigDecimal("100"),
                                BigDecimal.ZERO,
                                LocalDate.of(2024, 9, 30),
                                LocalDate.of(2026, 9, 30)))
                .isEqualByComparingTo("-100.00");
    }

    @Test
    void hasNoReturnForMissingDurationOrZeroBasis() {
        LocalDate date = LocalDate.of(2026, 9, 30);
        assertThat(AnnualizedReturns.compound(BigDecimal.ONE, BigDecimal.ONE, date, date)).isNull();
        assertThat(
                        AnnualizedReturns.compound(
                                BigDecimal.ZERO, BigDecimal.ONE, date.minusYears(1), date))
                .isNull();
    }
}
