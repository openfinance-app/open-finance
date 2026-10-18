package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openfinance.entity.InterestDayCountBasis;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.util.CalendarInterestProjection;

class CalendarInterestProjectionTest {
    @ParameterizedTest
    @CsvSource({"ANNUAL,40.00", "HALF_YEARLY,40.40", "QUARTERLY,40.60", "MONTHLY,40.74"})
    void allCalendarFrequenciesHonorTheirBoundaries(InterestPeriod period, String expected) {
        assertNet(calculate(period, InterestDayCountBasis.THIRTY_360, 2026, Map.of()), expected);
    }

    @Test
    void dailyCompoundingUsesAllActualDaysIncludingLeapDay() {
        assertNet(
                calculate(InterestPeriod.DAILY, InterestDayCountBasis.ACTUAL_365, 2026, Map.of()),
                "40.81");
        assertNet(
                calculate(InterestPeriod.DAILY, InterestDayCountBasis.ACTUAL_365, 2024, Map.of()),
                "40.92");
    }

    @Test
    void taxIsWithheldBeforeEachReinvestment() {
        InterestRateVariation rate =
                rate("4", LocalDate.of(2026, 1, 1), InterestDayCountBasis.THIRTY_360);
        rate.setTaxRate(new BigDecimal("20"));
        CalendarInterestProjection.Result result =
                CalendarInterestProjection.calculate(
                        cash(2026, "1000"), List.of(rate), InterestPeriod.QUARTERLY, Map.of());
        // 1000 * ((1 + .04 * .8 / 4)^4 - 1) = 32.386052096.
        assertNet(result, "32.39");
        assertThat(result.grossInterest().subtract(result.taxAmount()))
                .isEqualByComparingTo(result.netInterest());
    }

    @Test
    void postedCapitalIsNotEstimatedAgainAndExcessCreditDoesNotCancelLaterPeriods() {
        TreeMap<LocalDate, BigDecimal> cash = cash(2026, "1010");
        // A previous year's credit at January 1 is already in the opening cash. It cannot cancel
        // Q1.
        Map<LocalDate, BigDecimal> credits = Map.of(LocalDate.of(2026, 1, 1), new BigDecimal("10"));
        assertNet(
                CalendarInterestProjection.calculate(
                        cash,
                        List.of(
                                rate(
                                        "4",
                                        LocalDate.of(2026, 1, 1),
                                        InterestDayCountBasis.THIRTY_360)),
                        InterestPeriod.QUARTERLY,
                        credits),
                "41.01");
    }

    @Test
    void annualFixtureRetainsCalendarEstimateAndEarnedAmount() {
        TreeMap<LocalDate, BigDecimal> cash = cash(2026, "23445.16");
        List<InterestRateVariation> rates =
                List.of(
                        rate("1.7", LocalDate.of(2026, 1, 1), InterestDayCountBasis.ACTUAL_365),
                        rate("1.5", LocalDate.of(2026, 2, 1), InterestDayCountBasis.ACTUAL_365),
                        rate("1.7", LocalDate.of(2026, 8, 1), InterestDayCountBasis.ACTUAL_365));
        assertNet(
                CalendarInterestProjection.calculate(cash, rates, InterestPeriod.ANNUAL, Map.of()),
                "375.32");
        assertNet(
                CalendarInterestProjection.calculate(
                        cash.headMap(LocalDate.of(2026, 10, 19)),
                        rates,
                        InterestPeriod.ANNUAL,
                        Map.of()),
                "294.51");
    }

    @Test
    void interestCreditAtPeriodEndDoesNotCompoundTwice() {
        TreeMap<LocalDate, BigDecimal> cash = cash(2026, "1000");
        cash.tailMap(LocalDate.of(2026, 3, 31)).replaceAll((date, value) -> new BigDecimal("1010"));
        CalendarInterestProjection.Result result =
                CalendarInterestProjection.calculate(
                        cash,
                        List.of(
                                rate(
                                        "4",
                                        LocalDate.of(2026, 1, 1),
                                        InterestDayCountBasis.THIRTY_360)),
                        InterestPeriod.QUARTERLY,
                        Map.of(LocalDate.of(2026, 3, 31), new BigDecimal("10")));
        assertNet(result, "40.60");
    }

    private CalendarInterestProjection.Result calculate(
            InterestPeriod period,
            InterestDayCountBasis basis,
            int year,
            Map<LocalDate, BigDecimal> credits) {
        return CalendarInterestProjection.calculate(
                cash(year, "1000"),
                List.of(rate("4", LocalDate.of(year, 1, 1), basis)),
                period,
                credits);
    }

    private InterestRateVariation rate(String rate, LocalDate from, InterestDayCountBasis basis) {
        return InterestRateVariation.builder()
                .rate(new BigDecimal(rate))
                .validFrom(from)
                .dayCountBasis(basis)
                .build();
    }

    private TreeMap<LocalDate, BigDecimal> cash(int year, String balance) {
        TreeMap<LocalDate, BigDecimal> cash = new TreeMap<>();
        for (LocalDate date = LocalDate.of(year, 1, 1);
                date.getYear() == year;
                date = date.plusDays(1)) {
            cash.put(date, new BigDecimal(balance));
        }
        return cash;
    }

    private void assertNet(CalendarInterestProjection.Result result, String expected) {
        assertThat(result.netInterest().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(expected);
    }
}
