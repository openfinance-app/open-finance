package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openfinance.entity.InterestDayCountBasis;
import org.openfinance.entity.InterestValueDateMode;

class InterestConventionTest {
    @ParameterizedTest
    @CsvSource({
        "2024-02-01,true,2024-02-16", "2024-02-15,true,2024-02-16",
        "2024-02-16,true,2024-03-01", "2024-02-29,true,2024-03-01",
        "2024-12-31,true,2025-01-01", "2024-02-15,false,2024-02-01",
        "2024-02-16,false,2024-02-16", "2024-02-29,false,2024-02-16"
    })
    void fortnightBoundariesIncludeLeapDaysAndYearRollover(
            String booked, boolean credit, String value) {
        assertThat(
                        InterestValueDateMode.fromSkrooge("F")
                                .valueDate(LocalDate.parse(booked), credit))
                .isEqualTo(LocalDate.parse(value));
    }

    @ParameterizedTest
    @CsvSource({"0,2025-01-01,2025-01-01", "1,2025-01-02,2024-12-31", "5,2025-01-06,2024-12-27"})
    void calendarOffsetsDelayCreditsAndAdvanceDebits(String code, String credit, String debit) {
        InterestValueDateMode mode = InterestValueDateMode.fromSkrooge(code);
        assertThat(mode.valueDate(LocalDate.of(2025, 1, 1), true))
                .isEqualTo(LocalDate.parse(credit));
        assertThat(mode.valueDate(LocalDate.of(2025, 1, 1), false))
                .isEqualTo(LocalDate.parse(debit));
    }

    @ParameterizedTest
    @CsvSource({
        "365,2025,365",
        "365,2024,366",
        "360,2024,365",
        "360,2025,365",
        "24,2024,365",
        "24,2025,365"
    })
    void annualDayWeightsFollowTheSelectedBasis(String code, int year, String expected) {
        InterestDayCountBasis basis = InterestDayCountBasis.fromSkrooge(code);
        BigDecimal interest = BigDecimal.ZERO;
        for (LocalDate day = LocalDate.of(year, 1, 1);
                day.getYear() == year;
                day = day.plusDays(1)) {
            interest = interest.add(basis.dailyInterest(new BigDecimal("365"), day));
        }
        assertThat(interest.setScale(2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo(expected);
    }
}
