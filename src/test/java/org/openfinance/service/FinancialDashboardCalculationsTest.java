package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.BusinessRulesProperties;
import org.openfinance.dto.BorrowingCapacity;
import org.openfinance.dto.EstimatedInterestSummary;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard inclusive periods and dated debt interest")
class FinancialDashboardCalculationsTest {
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private NetWorthService netWorthService;

    @Mock private AccountRepository accountRepository;
    @Mock private LiabilityRepository liabilityRepository;
    @Mock private LiabilityTrancheRepository liabilityTrancheRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private LiabilityService liabilityService;
    @Mock private InterestCalculatorService interestCalculatorService;
    @Spy private BusinessRulesProperties businessRules = new BusinessRulesProperties();
    @InjectMocks private DashboardService dashboardService;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setup() {
        lenient()
                .when(userRepository.findById(1L))
                .thenReturn(Optional.of(User.builder().id(1L).baseCurrency("EUR").build()));
        lenient().when(defaultCurrencyProvider.resolve("EUR")).thenReturn("EUR");
        ReflectionTestUtils.setField(
                netWorthService, "transactionRepository", transactionRepository);
        ReflectionTestUtils.setField(
                netWorthService, "liabilityTrancheRepository", liabilityTrancheRepository);
    }

    @ParameterizedTest
    @CsvSource({
        "1,30,900",
        "2,30,450",
        "30,3926.94,3926.94",
        "31,3100.31,3000.30",
        "60,6400.30,3200.15"
    })
    void borrowingNormalizesInclusiveDaysWithoutRoundingTheMonthFraction(
            int days, BigDecimal income, BigDecimal expectedMonthly) {
        LocalDate start = today.minusDays(days - 1L);
        when(transactionRepository.findByUserIdAndDateBetween(1L, start, today))
                .thenReturn(
                        List.of(
                                transaction(TransactionType.INCOME, income),
                                transaction(
                                        TransactionType.EXPENSE, income.divide(BigDecimal.TEN))));

        BorrowingCapacity result = dashboardService.getBorrowingCapacity(1L, start, today);

        assertThat(result.getAnalysisPeriod()).isEqualTo(days);
        assertThat(result.getMonthlyIncome()).isEqualByComparingTo(expectedMonthly);
        assertThat(result.getMonthlyExpenses())
                .isEqualByComparingTo(
                        expectedMonthly
                                .divide(BigDecimal.TEN)
                                .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void borrowingPresetQueriesExactlyItsNumberOfInclusiveDays() {
        dashboardService.getBorrowingCapacity(1L, 30);
        verify(transactionRepository).findByUserIdAndDateBetween(1L, today.minusDays(29), today);
    }

    @Test
    void borrowingRejectsReversedDates() {
        assertThatThrownBy(
                        () -> dashboardService.getBorrowingCapacity(1L, today, today.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newlyOpenedDebtAccruesOnlyTodayWhileProjectionRemainsAnnual() {
        loan(today, "1000", "12");
        EstimatedInterestSummary result = dashboardService.getEstimatedInterestSummary(1L, "1Y");
        assertThat(result.getTotalEarned()).isEqualByComparingTo("-0.33");
        assertThat(result.getTotalProjected()).isEqualByComparingTo("-120");
    }

    @Test
    void drawsAndPrincipalRepaymentsChangeHistoricalAccrualOnTheirDates() {
        loan(today.minusDays(9), "500", "36.5");
        Transaction draw = transaction(TransactionType.INCOME, new BigDecimal("1000"));
        draw.setMovementType(MovementType.DISBURSEMENT);
        draw.setDate(today.minusDays(7));
        Transaction repayment = transaction(TransactionType.EXPENSE, new BigDecimal("510"));
        repayment.setPrincipalAmount(new BigDecimal("500"));
        repayment.setDate(today.minusDays(3));
        lenient()
                .when(transactionRepository.findByLiabilityIdAndUserId(10L, 1L))
                .thenReturn(List.of(draw, repayment));

        EstimatedInterestSummary result = dashboardService.getEstimatedInterestSummary(1L, "1Y");

        // Four days at 1000 and four at 500; charges never change principal.
        assertThat(result.getTotalEarned()).isEqualByComparingTo("-6");
        assertThat(result.getTotalProjected()).isEqualByComparingTo("-182.50");
    }

    @Test
    void reversedDirectDrawAccruesOnlyWhileOutstanding() {
        loan(today.minusDays(9), "0", "36.5");
        LiabilityTranche tranche = LiabilityTranche.builder().build();
        tranche.setDirectDisbursement(true);
        tranche.setDrawnAmount(new BigDecimal("1000"));
        tranche.setDrawnDate(today.minusDays(7));
        tranche.setReversedDate(today.minusDays(3));
        lenient()
                .when(liabilityTrancheRepository.findByLiabilityIdAndUserId(10L, 1L))
                .thenReturn(List.of(tranche));

        EstimatedInterestSummary result = dashboardService.getEstimatedInterestSummary(1L, "1Y");

        assertThat(result.getTotalEarned()).isEqualByComparingTo("-4");
        assertThat(result.getTotalProjected()).isZero();
    }

    @Test
    void repaidDebtRetainsPastInterestWithZeroProjection() {
        loan(today.minusDays(2), "0", "36.5");
        Transaction repayment = transaction(TransactionType.EXPENSE, new BigDecimal("1002"));
        repayment.setPrincipalAmount(new BigDecimal("1000"));
        lenient()
                .when(transactionRepository.findByLiabilityIdAndUserId(10L, 1L))
                .thenReturn(List.of(repayment));

        EstimatedInterestSummary result = dashboardService.getEstimatedInterestSummary(1L, "ALL");

        assertThat(result.getTotalEarned()).isEqualByComparingTo("-2");
        assertThat(result.getTotalProjected()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"1D,1", "7D,7", "30,30", "ALL,401"})
    void debtAccrualHonorsSelectedInclusivePeriod(String period, String days) {
        loan(today.minusDays(400), "1000", "36.5");
        assertThat(dashboardService.getEstimatedInterestSummary(1L, period).getTotalEarned())
                .isEqualByComparingTo(new BigDecimal(days).negate());
    }

    @Test
    void debtAccrualHonorsCalendarYearAndYearToDate() {
        loan(today.minusYears(2), "1000", "36.5");
        assertThat(dashboardService.getEstimatedInterestSummary(1L, "1Y").getTotalEarned())
                .isEqualByComparingTo(
                        BigDecimal.valueOf(ChronoUnit.DAYS.between(today.minusYears(1), today) + 1)
                                .negate());
        assertThat(dashboardService.getEstimatedInterestSummary(1L, "YTD").getTotalEarned())
                .isEqualByComparingTo(BigDecimal.valueOf(today.getDayOfYear()).negate());
    }

    @Test
    void futureDebtHasNoHistoricalInterest() {
        loan(today.plusDays(1), "1000", "36.5");
        assertThat(dashboardService.getEstimatedInterestSummary(1L, "1Y").getTotalEarned())
                .isZero();
    }

    @Test
    void missingHistoricalExchangeRateCannotHideALoanFromTheTotal() {
        Liability loan = loan(today, "1000", "36.5");
        loan.setCurrency("USD");
        when(exchangeRateService.convert(new BigDecimal("365.000"), "USD", "EUR"))
                .thenReturn(new BigDecimal("292"));
        when(exchangeRateService.convert(any(BigDecimal.class), eq("USD"), eq("EUR"), eq(today)))
                .thenThrow(new IllegalStateException("Rate unavailable"));

        assertThatThrownBy(() -> dashboardService.getEstimatedInterestSummary(1L, "1Y"))
                .isInstanceOf(org.openfinance.exception.ExchangeRateUnavailableException.class);
    }

    @Test
    void historicalDebtUsesEachDaysExchangeRateAndLoadsTheLedgerOnce() {
        Liability loan = loan(today.minusDays(1), "1000", "36.5");
        loan.setCurrency("USD");
        when(exchangeRateService.convert(new BigDecimal("365.000"), "USD", "EUR"))
                .thenReturn(new BigDecimal("292"));
        when(exchangeRateService.convert(new BigDecimal("1000"), "USD", "EUR", today.minusDays(1)))
                .thenReturn(new BigDecimal("900"));
        when(exchangeRateService.convert(new BigDecimal("1000"), "USD", "EUR", today))
                .thenReturn(new BigDecimal("800"));

        EstimatedInterestSummary result = dashboardService.getEstimatedInterestSummary(1L, "1Y");

        assertThat(result.getTotalEarned()).isEqualByComparingTo("-1.70");
        assertThat(result.getTotalProjected()).isEqualByComparingTo("-292");
        verify(transactionRepository).findByLiabilityIdAndUserId(10L, 1L);
        verify(liabilityTrancheRepository).findByLiabilityIdAndUserId(10L, 1L);
    }

    private Liability loan(LocalDate start, String balance, String rate) {
        Liability loan = new Liability();
        loan.setId(10L);
        loan.setUserId(1L);
        loan.setName("Test loan");
        loan.setCurrency("EUR");
        loan.setStartDate(start);
        loan.setCurrentBalance(balance);
        loan.setInterestRate(rate);
        when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(loan));
        return loan;
    }

    private Transaction transaction(TransactionType type, BigDecimal amount) {
        Transaction transaction = Transaction.builder().build();
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCurrency("EUR");
        transaction.setDate(today);
        transaction.setIsDeleted(false);
        return transaction;
    }
}
