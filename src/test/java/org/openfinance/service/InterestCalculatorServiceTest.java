package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.BalanceHistoryPoint;
import org.openfinance.dto.InterestPreviewRequest;
import org.openfinance.entity.InterestDayCountBasis;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.entity.InterestValueDateMode;
import org.openfinance.repository.InterestRateVariationRepository;

/**
 * Exercises the calendar-year engine used by the account and dashboard endpoints. Nominal compound
 * formula cases use 30/360, so every complete month has equal weight.
 */
@ExtendWith(MockitoExtension.class)
class InterestCalculatorServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock private InterestRateVariationRepository variationRepository;
    @Mock private AccountService accountService;

    @InjectMocks private InterestCalculatorService service;

    private void givenAccount(BigDecimal balance, InterestPeriod period) {
        AccountResponse account =
                AccountResponse.builder()
                        .balance(balance)
                        .ownBalance(balance)
                        .isInterestEnabled(true)
                        .interestPeriod(period)
                        .build();
        when(accountService.getAccountById(ACCOUNT_ID, USER_ID)).thenReturn(account);
        lenient()
                .when(
                        accountService.getAccountBalanceHistory(
                                org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
                                        org.mockito.ArgumentMatchers.eq(USER_ID),
                                any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(
                        invocation ->
                                List.of(
                                        new BalanceHistoryPoint(
                                                invocation.getArgument(2),
                                                account.getOwnBalance() == null
                                                        ? BigDecimal.ZERO
                                                        : account.getOwnBalance())));
    }

    private void givenAccount(BigDecimal balance, InterestPeriod period, String currency) {
        AccountResponse account =
                AccountResponse.builder()
                        .balance(balance)
                        .ownBalance(balance)
                        .isInterestEnabled(true)
                        .interestPeriod(period)
                        .currency(currency)
                        .build();
        when(accountService.getAccountById(ACCOUNT_ID, USER_ID)).thenReturn(account);
        lenient()
                .when(
                        accountService.getAccountBalanceHistory(
                                org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
                                        org.mockito.ArgumentMatchers.eq(USER_ID),
                                any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(
                        invocation ->
                                List.of(
                                        new BalanceHistoryPoint(
                                                invocation.getArgument(2),
                                                account.getOwnBalance() == null
                                                        ? BigDecimal.ZERO
                                                        : account.getOwnBalance())));
    }

    private void givenRate(String ratePct, String taxPct) {
        InterestRateVariation variation =
                InterestRateVariation.builder()
                        .accountId(ACCOUNT_ID)
                        .id(1L)
                        .dayCountBasis(InterestDayCountBasis.THIRTY_360)
                        .rate(new BigDecimal(ratePct))
                        .taxRate(new BigDecimal(taxPct))
                        .validFrom(LocalDate.of(2026, 1, 1))
                        .build();
        when(variationRepository.findByAccountIdOrderByValidFromDesc(ACCOUNT_ID))
                .thenReturn(List.of(variation));
    }

    @Test
    @DisplayName("Four-decimal projection retains accrued fractions of a cent")
    void projectionRetainsSubCentInterestWithFortnightValueDates() {
        givenAccount(BigDecimal.ZERO, InterestPeriod.ANNUAL, "EUR");
        InterestRateVariation rate =
                InterestRateVariation.builder()
                        .id(1L)
                        .rate(new BigDecimal("0.5"))
                        .taxRate(new BigDecimal("31.4"))
                        .validFrom(LocalDate.of(2026, 1, 1))
                        .dayCountBasis(InterestDayCountBasis.ACTUAL_365)
                        .creditValueDateMode(InterestValueDateMode.FORTNIGHT)
                        .debitValueDateMode(InterestValueDateMode.FORTNIGHT)
                        .build();
        when(variationRepository.findByAccountIdOrderByValidFromDesc(ACCOUNT_ID))
                .thenReturn(List.of(rate));
        givenClosingSavingsMovements();

        BigDecimal estimate =
                service.calculateYear(ACCOUNT_ID, USER_ID, 2026).calendarYearEstimate();
        assertThat(estimate.setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("1.4754");
        assertThat(estimate).isBetween(new BigDecimal("1.47536328"), new BigDecimal("1.47536329"));
    }

    private void givenClosingSavingsMovements() {
        when(accountService.getAccountBalanceHistory(
                        ACCOUNT_ID, USER_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(
                        List.of(
                                new BalanceHistoryPoint(
                                        LocalDate.of(2026, 1, 1), new BigDecimal("20386.62")),
                                new BalanceHistoryPoint(
                                        LocalDate.of(2026, 1, 7), new BigDecimal("10386.62")),
                                new BalanceHistoryPoint(
                                        LocalDate.of(2026, 1, 26), new BigDecimal("10")),
                                new BalanceHistoryPoint(
                                        LocalDate.of(2026, 5, 28), new BigDecimal("11.47")),
                                new BalanceHistoryPoint(
                                        LocalDate.of(2026, 5, 29), BigDecimal.ZERO)));
        when(accountService.getCashMovements(
                        org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(
                        List.of(
                                new AccountService.CashMovement(
                                        LocalDate.of(2026, 1, 7), new BigDecimal("-10000")),
                                new AccountService.CashMovement(
                                        LocalDate.of(2026, 1, 26), new BigDecimal("-10376.62")),
                                new AccountService.CashMovement(
                                        LocalDate.of(2026, 5, 28), new BigDecimal("1.47")),
                                new AccountService.CashMovement(
                                        LocalDate.of(2026, 5, 29), new BigDecimal("-11.47"))));
    }

    @Test
    void historicalAndSelectedPeriodAccrualsRetainSubCentPrecision() {
        givenAccount(new BigDecimal("100"), InterestPeriod.ANNUAL, "EUR");
        LocalDate day = LocalDate.now().minusDays(1);
        when(variationRepository.findByAccountIdOrderByValidFromDesc(ACCOUNT_ID))
                .thenReturn(
                        List.of(
                                InterestRateVariation.builder()
                                        .id(1L)
                                        .rate(new BigDecimal("0.5"))
                                        .taxRate(new BigDecimal("31.4"))
                                        .validFrom(day)
                                        .build()));
        when(accountService.getAccountBalanceHistory(ACCOUNT_ID, USER_ID, "ALL"))
                .thenReturn(List.of(new BalanceHistoryPoint(day, new BigDecimal("100"))));
        BigDecimal lifetime = service.calculateHistoricalAccumulated(ACCOUNT_ID, USER_ID, "ALL");
        BigDecimal selected = service.calculateHistoricalAccumulated(ACCOUNT_ID, USER_ID, day, day);
        assertThat(selected.setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("0.0009");
        assertThat(lifetime.setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("0.0019");
        assertThat(lifetime).isEqualByComparingTo(selected.multiply(BigDecimal.TWO));
    }

    @Test
    void formPreviewRetainsGrossTaxAndNetEstimatePrecision() {
        LocalDate opening = LocalDate.now().withDayOfYear(1);
        InterestCalculatorService.Preview preview =
                service.preview(
                        new InterestPreviewRequest(
                                new BigDecimal("100"),
                                new BigDecimal("1.005"),
                                new BigDecimal("31.4"),
                                opening,
                                InterestPeriod.ANNUAL,
                                "EUR"));
        BigDecimal gross =
                new BigDecimal("1.005")
                        .multiply(BigDecimal.valueOf(opening.lengthOfYear()))
                        .divide(new BigDecimal("365"), 18, RoundingMode.HALF_UP);
        assertThat(preview.grossInterest())
                .isCloseTo(
                        gross,
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
        assertThat(preview.taxAmount())
                .isCloseTo(
                        gross.multiply(new BigDecimal("0.314")),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
        assertThat(preview.netInterest())
                .isCloseTo(
                        gross.multiply(new BigDecimal("0.686")),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
    }

    @Test
    @DisplayName("Annual compounding, no tax: 5% on 10,000 = 500.00")
    void annualNoTax() {
        givenAccount(new BigDecimal("10000"), InterestPeriod.ANNUAL);
        givenRate("5", "0");

        assertThat(
                        service.calculateYear(ACCOUNT_ID, USER_ID, 2026)
                                .calendarYearEstimate()
                                .setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Monthly compounding, no tax: 12% on 10,000 = 1268.25")
    void monthlyCompoundingNoTax() {
        givenAccount(new BigDecimal("10000"), InterestPeriod.MONTHLY);
        givenRate("12", "0");

        // (1 + 0.12/12)^12 − 1 = 1.01^12 − 1 = 0.12682503... × 10000 = 1268.2503 → 1268.25
        assertThat(
                        service.calculateYear(ACCOUNT_ID, USER_ID, 2026)
                                .calendarYearEstimate()
                                .setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("1268.25");
    }

    @Test
    @DisplayName("Annual compounding with 30% tax: net 5% on 10,000 = 350.00")
    void annualWithTax() {
        givenAccount(new BigDecimal("10000"), InterestPeriod.ANNUAL);
        givenRate("5", "30");

        assertThat(
                        service.calculateYear(ACCOUNT_ID, USER_ID, 2026)
                                .calendarYearEstimate()
                                .setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("Zero balance yields zero interest")
    void zeroBalance() {
        givenAccount(BigDecimal.ZERO, InterestPeriod.ANNUAL);

        assertThat(
                        service.calculateYear(ACCOUNT_ID, USER_ID, 2026)
                                .calendarYearEstimate()
                                .setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Interest-disabled account yields zero interest")
    void interestDisabled() {
        AccountResponse account =
                AccountResponse.builder()
                        .balance(new BigDecimal("10000"))
                        .isInterestEnabled(false)
                        .interestPeriod(InterestPeriod.ANNUAL)
                        .build();
        when(accountService.getAccountById(ACCOUNT_ID, USER_ID)).thenReturn(account);
        lenient()
                .when(
                        accountService.getAccountBalanceHistory(
                                org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
                                        org.mockito.ArgumentMatchers.eq(USER_ID),
                                any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(
                        invocation ->
                                List.of(
                                        new BalanceHistoryPoint(
                                                invocation.getArgument(2),
                                                account.getOwnBalance() == null
                                                        ? BigDecimal.ZERO
                                                        : account.getOwnBalance())));

        assertThat(
                        service.calculateYear(ACCOUNT_ID, USER_ID, 2026)
                                .calendarYearEstimate()
                                .setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Crypto estimates keep calculation precision before display rounding")
    void cryptoAccountPreservesPrecision() {
        givenAccount(new BigDecimal("1"), InterestPeriod.MONTHLY, "BTC");
        givenRate("12", "0");

        // 1 × ((1 + 0.12/12)^12 − 1) = 0.126825030... BTC
        // The API retains working precision; a display preference does not change the estimate.
        BigDecimal result = service.calculateYear(ACCOUNT_ID, USER_ID, 2026).calendarYearEstimate();
        assertThat(result.scale()).isEqualTo(18);
        assertThat(result)
                .isCloseTo(
                        new BigDecimal("0.126825030131969720"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000000001")));
    }

    @Test
    @DisplayName("JPY estimates retain fractions until display or settlement")
    void zeroDecimalAccountRetainsEstimatePrecision() {
        givenAccount(new BigDecimal("10000"), InterestPeriod.MONTHLY, "JPY");
        givenRate("12", "0");

        // Currency settlement precision must not discard the unposted estimate.
        BigDecimal result = service.calculateYear(ACCOUNT_ID, USER_ID, 2026).calendarYearEstimate();
        assertThat(result.scale()).isEqualTo(18);
        assertThat(result)
                .isCloseTo(
                        new BigDecimal("1268.250301319697"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
    }

    @Test
    void quarterlyProjectionUsesFourCompoundingPeriods() {
        givenAccount(new BigDecimal("1000"), InterestPeriod.QUARTERLY);
        givenRate("4", "0");
        assertThat(
                        service.calculateYear(ACCOUNT_ID, USER_ID, 2026)
                                .calendarYearEstimate()
                                .setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("40.60");
    }

    @Test
    void scheduledRateAppliesOnlyAfterItsEffectiveDate() {
        givenAccount(new BigDecimal("1000"), InterestPeriod.ANNUAL);
        when(variationRepository.findByAccountIdOrderByValidFromDesc(ACCOUNT_ID))
                .thenReturn(
                        List.of(
                                InterestRateVariation.builder()
                                        .id(1L)
                                        .validFrom(LocalDate.of(2026, 7, 1))
                                        .rate(new BigDecimal("4"))
                                        .build()));
        assertThat(
                        service.calculateYear(ACCOUNT_ID, USER_ID, 2026)
                                .calendarYearEstimate()
                                .setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("20.16");
    }

    @Test
    void perRateAccrualUsesChangingCashBalancesAndExcludesFutureDays() {
        givenAccount(new BigDecimal("2000"), InterestPeriod.ANNUAL);
        LocalDate today = LocalDate.now();
        InterestRateVariation active =
                InterestRateVariation.builder()
                        .id(1L)
                        .validFrom(today.minusDays(2))
                        .rate(new BigDecimal("36.5"))
                        .taxRate(new BigDecimal("25"))
                        .build();
        InterestRateVariation future =
                InterestRateVariation.builder()
                        .id(2L)
                        .validFrom(today.plusMonths(6))
                        .rate(new BigDecimal("73"))
                        .build();
        when(variationRepository.findByAccountIdOrderByValidFromDesc(ACCOUNT_ID))
                .thenReturn(List.of(future, active));
        when(accountService.getAccountBalanceHistory(ACCOUNT_ID, USER_ID, "ALL"))
                .thenReturn(
                        List.of(
                                new org.openfinance.dto.BalanceHistoryPoint(
                                        today.minusDays(2), new BigDecimal("1000")),
                                new org.openfinance.dto.BalanceHistoryPoint(
                                        today.minusDays(1), new BigDecimal("2000"))));
        java.util.Map<Long, InterestCalculatorService.Accrual> accruals =
                service.calculateAccrualsByVariation(ACCOUNT_ID, USER_ID);
        assertThat(accruals.get(1L).activeDays()).isEqualTo(3);
        assertThat(accruals.get(1L).interestProduced()).isEqualByComparingTo("3.75");
        assertThat(accruals).doesNotContainKey(2L);
        assertThat(service.calculateHistoricalAccumulated(ACCOUNT_ID, USER_ID, "ALL"))
                .isEqualByComparingTo("3.75");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"Europe/Paris,16", "America/New_York,15"})
    void accruesThroughTheUsersTodayWhenTheServerIsOnAnotherDay(String zone, String expected) {
        givenAccount(new BigDecimal("36500"), InterestPeriod.ANNUAL);
        when(variationRepository.findByAccountIdOrderByValidFromDesc(ACCOUNT_ID))
                .thenReturn(
                        List.of(
                                InterestRateVariation.builder()
                                        .accountId(ACCOUNT_ID)
                                        .rate(BigDecimal.ONE)
                                        .taxRate(BigDecimal.ZERO)
                                        .validFrom(LocalDate.of(2026, 1, 1))
                                        .dayCountBasis(InterestDayCountBasis.ACTUAL_365)
                                        .build()));
        java.time.Clock clock =
                java.time.Clock.fixed(
                        java.time.Instant.parse("2026-11-15T23:51:00Z"), java.time.ZoneId.of(zone));
        try (org.openfinance.util.ReportingClock.Scope ignored =
                org.openfinance.util.ReportingClock.use(clock)) {
            assertThat(
                            service.calculateHistoricalAccumulated(
                                    ACCOUNT_ID,
                                    USER_ID,
                                    LocalDate.of(2026, 11, 1),
                                    LocalDate.of(2026, 11, 16)))
                    .isEqualByComparingTo(expected);
        }
    }
}
