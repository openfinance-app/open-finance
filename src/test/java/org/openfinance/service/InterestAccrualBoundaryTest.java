package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.BalanceHistoryPoint;
import org.openfinance.entity.InterestDayCountBasis;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.entity.InterestValueDateMode;
import org.openfinance.repository.InterestRateVariationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Interest estimates respect date, account and value-date boundaries")
class InterestAccrualBoundaryTest {
    @Mock InterestRateVariationRepository rates;
    @Mock AccountService accounts;
    @InjectMocks InterestCalculatorService service;

    @ParameterizedTest
    @MethodSource("invalidRanges")
    void refusesInvalidRangesBeforeReadingAccounts(LocalDate start, LocalDate end) {
        assertThatThrownBy(() -> service.calculateHistoricalAccumulated(1L, 2L, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date range");
        verifyNoInteractions(accounts, rates);
    }

    static Stream<Arguments> invalidRanges() {
        LocalDate day = LocalDate.of(2024, 1, 1);
        return Stream.of(
                Arguments.of(null, day),
                Arguments.of(day, null),
                Arguments.of(day.plusDays(1), day));
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 9999})
    void refusesUnsupportedYearsBeforeReadingAccounts(int year) {
        assertThatThrownBy(() -> service.calculateYear(1L, 2L, year))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accounts, rates);
    }

    @ParameterizedTest
    @MethodSource("disabled")
    void accountsWithoutInterestDoNotAccrueOrLoadHistory(Boolean enabled) {
        givenAccount(enabled);
        assertThat(service.calculateHistoricalAccumulated(1L, 2L, "ALL")).isZero();
        assertThat(
                        service.calculateHistoricalAccumulated(
                                1L, 2L, LocalDate.of(2024, 1, 1), LocalDate.now()))
                .isZero();
        assertThat(service.calculateAccrualsByVariation(1L, 2L)).isEmpty();
        assertThat(service.calculateYear(1L, 2L, 2024).calendarYearEstimate()).isZero();
        verifyNoInteractions(rates);
        verify(accounts, never()).getAccountBalanceHistory(eq(1L), eq(2L), any(String.class));
    }

    static Stream<Arguments> disabled() {
        return Stream.of(Arguments.of(false), Arguments.of((Object) null));
    }

    @Test
    void missingRatesShortCircuitHistoricalBalanceLoading() {
        givenAccount(true);
        assertThat(service.calculateHistoricalAccumulated(1L, 2L, "ALL")).isZero();
        verify(accounts, never()).getAccountBalanceHistory(1L, 2L, "ALL");
    }

    @Test
    void missingBalanceHistoryProducesZeroEvenWhenRatesExist() {
        givenAccount(true);
        givenRate(
                LocalDate.of(2024, 1, 1),
                InterestValueDateMode.TRANSACTION_DATE,
                InterestValueDateMode.TRANSACTION_DATE);
        assertThat(service.calculateHistoricalAccumulated(1L, 2L, "ALL")).isZero();
    }

    @Test
    void aFutureRangeDoesNotFetchOrAccrueFutureMovements() {
        givenAccount(true);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertThat(service.calculateHistoricalAccumulated(1L, 2L, tomorrow, tomorrow.plusDays(2)))
                .isZero();
        verifyNoInteractions(rates);
    }

    @Test
    void selectedRangesAreCappedAtTodayAndNullTaxMeansNoWithholding() {
        givenAccount(true);
        LocalDate today = LocalDate.now();
        givenRate(
                today,
                InterestValueDateMode.TRANSACTION_DATE,
                InterestValueDateMode.TRANSACTION_DATE);
        when(accounts.getAccountBalanceHistory(1L, 2L, today, today))
                .thenReturn(List.of(new BalanceHistoryPoint(today, new BigDecimal("1000"))));
        assertThat(service.calculateHistoricalAccumulated(1L, 2L, today, today.plusDays(15)))
                .isEqualByComparingTo("1");
        verify(accounts).getAccountBalanceHistory(1L, 2L, today, today);
    }

    @Test
    void futureYearsHaveForecastsButNoAccruedInterest() {
        givenAccount(true);
        int year = LocalDate.now().getYear() + 1;
        LocalDate start = LocalDate.of(year, 1, 1), end = LocalDate.of(year, 12, 31);
        givenRate(
                start,
                InterestValueDateMode.TRANSACTION_DATE,
                InterestValueDateMode.TRANSACTION_DATE);
        when(accounts.getAccountBalanceHistory(1L, 2L, start, end))
                .thenReturn(List.of(new BalanceHistoryPoint(start, new BigDecimal("1000"))));
        InterestCalculatorService.YearEstimate result = service.calculateYear(1L, 2L, year);
        assertThat(result.accrued()).isZero();
        assertThat(result.accruals()).isEmpty();
        assertThat(result.calendarYearEstimate())
                .isEqualByComparingTo(BigDecimal.valueOf(start.lengthOfYear()));
        assertThat(result.through()).isEqualTo(LocalDate.now());
        assertThatThrownBy(() -> result.accruals().put(1L, InterestCalculatorService.Accrual.ZERO))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void debitValueDatesApplyEvenWhenCreditsUseBookingDates() {
        givenAccount(true);
        LocalDate start = LocalDate.of(2024, 3, 1), end = start.plusDays(4);
        givenRate(start, InterestValueDateMode.TRANSACTION_DATE, InterestValueDateMode.DAYS_2);
        when(accounts.getAccountBalanceHistory(1L, 2L, start, end))
                .thenReturn(
                        List.of(
                                new BalanceHistoryPoint(start, new BigDecimal("1000")),
                                new BalanceHistoryPoint(end, new BigDecimal("500"))));
        when(accounts.getCashMovements(1L, 2L, start.minusMonths(1), end.plusMonths(1)))
                .thenReturn(
                        List.of(
                                new AccountService.CashMovement(start, new BigDecimal("1000")),
                                new AccountService.CashMovement(end, new BigDecimal("-500"))));
        // Two full-balance days and three half-balance days after the debit value date.
        assertThat(service.calculateHistoricalAccumulated(1L, 2L, start, end))
                .isEqualByComparingTo("3.5");
    }

    @Test
    void movementsBeforeTheFirstRateDoNotInventAValueDateRule() {
        givenAccount(true);
        LocalDate start = LocalDate.of(2024, 3, 1), end = start.plusDays(2);
        givenRate(start.plusDays(1), InterestValueDateMode.DAYS_2, InterestValueDateMode.DAYS_2);
        when(accounts.getAccountBalanceHistory(1L, 2L, start, end))
                .thenReturn(List.of(new BalanceHistoryPoint(start, new BigDecimal("1000"))));
        when(accounts.getCashMovements(1L, 2L, start.minusMonths(1), end.plusMonths(1)))
                .thenReturn(
                        List.of(new AccountService.CashMovement(start, new BigDecimal("1000"))));
        assertThat(service.calculateHistoricalAccumulated(1L, 2L, start, end))
                .isEqualByComparingTo("2");
    }

    private void givenAccount(Boolean enabled) {
        when(accounts.getAccountById(1L, 2L))
                .thenReturn(
                        AccountResponse.builder()
                                .isInterestEnabled(enabled)
                                .interestPeriod(InterestPeriod.ANNUAL)
                                .build());
    }

    private void givenRate(
            LocalDate start, InterestValueDateMode credit, InterestValueDateMode debit) {
        when(rates.findByAccountIdOrderByValidFromDesc(1L))
                .thenReturn(
                        List.of(
                                InterestRateVariation.builder()
                                        .id(9L)
                                        .validFrom(start)
                                        .rate(new BigDecimal("36.5"))
                                        .taxRate(null)
                                        .dayCountBasis(InterestDayCountBasis.ACTUAL_365)
                                        .creditValueDateMode(credit)
                                        .debitValueDateMode(debit)
                                        .build()));
    }
}
