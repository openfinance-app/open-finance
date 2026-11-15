package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetHistoryResponse;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName(
        "Budget carryover follows contiguous periods, currency boundaries and overspending policy")
class BudgetRolloverTest {
    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionSplitRepository transactionSplitRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @InjectMocks private BudgetService service;

    private Budget month(long id, int month, String currency) {
        LocalDate start = LocalDate.of(2024, month, 1);
        return Budget.builder()
                .id(id)
                .userId(1L)
                .categoryId(5L)
                .period(BudgetPeriod.MONTHLY)
                .startDate(start)
                .endDate(start.withDayOfMonth(start.lengthOfMonth()))
                .currency(currency)
                .amount("100")
                .rollover(true)
                .recurring(false)
                .build();
    }

    private void repository(Budget current, List<Budget> periods) {
        when(budgetRepository.findByIdAndUserId(current.getId(), 1L))
                .thenReturn(Optional.of(current));
        when(budgetRepository.findByUserIdAndCategoryId(1L, 5L)).thenReturn(periods);
        when(categoryRepository.findByIdAndUserId(5L, 1L))
                .thenReturn(
                        Optional.of(
                                Category.builder()
                                        .id(5L)
                                        .userId(1L)
                                        .name("Food")
                                        .isSystem(true)
                                        .build()));
    }

    @Test
    void convertsCarryAtEachPreviousPeriodEndAndDoesNotCountItAsNewAllowance() {
        Budget january = month(1, 1, "USD");
        Budget february = month(2, 2, "EUR");
        Budget current = month(3, 3, "CAD");
        repository(current, List.of(current, february, january));
        when(exchangeRateService.convert(new BigDecimal("100"), "USD", "EUR", january.getEndDate()))
                .thenReturn(new BigDecimal("90"));
        when(exchangeRateService.convert(
                        new BigDecimal("190"), "EUR", "CAD", february.getEndDate()))
                .thenReturn(new BigDecimal("285"));

        BudgetHistoryResponse history = service.getBudgetHistory(3L, 1L);

        assertThat(history.getInitialCarryover()).isEqualByComparingTo("285");
        assertThat(history.getTotalBudgeted()).isEqualByComparingTo("100");
        assertThat(history.getHistory())
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.getAvailable()).isEqualByComparingTo("385");
                            assertThat(entry.getRemaining()).isEqualByComparingTo("385");
                        });
        verify(exchangeRateService)
                .convert(new BigDecimal("100"), "USD", "EUR", january.getEndDate());
        verify(exchangeRateService)
                .convert(new BigDecimal("190"), "EUR", "CAD", february.getEndDate());
    }

    @Test
    void aGapBeforeTheCurrentPeriodBreaksCarryover() {
        Budget january = month(1, 1, "EUR");
        Budget current = month(3, 3, "USD");
        repository(current, List.of(january));
        BudgetHistoryResponse history = service.getBudgetHistory(3L, 1L);
        assertThat(history.getInitialCarryover()).isEqualByComparingTo("0");
        assertThat(history.getHistory().getFirst().getAvailable()).isEqualByComparingTo("100");
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void aGapInsideHistoryStartsANewCarryChain() {
        Budget january = month(1, 1, "EUR");
        Budget march = month(3, 3, "USD");
        Budget current = month(4, 4, "USD");
        repository(current, List.of(january, march));
        assertThat(service.getBudgetHistory(4L, 1L).getInitialCarryover())
                .isEqualByComparingTo("100");
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void ignoresOtherBudgetFrequenciesAndFutureRows() {
        Budget previous = month(1, 1, "EUR");
        Budget otherFrequency = month(2, 1, "USD");
        otherFrequency.setPeriod(BudgetPeriod.YEARLY);
        Budget current = month(3, 2, "EUR");
        repository(current, List.of(month(4, 3, "USD"), otherFrequency, current, previous));
        assertThat(service.getBudgetHistory(3L, 1L).getInitialCarryover())
                .isEqualByComparingTo("100");
        verifyNoInteractions(exchangeRateService);
    }

    @ParameterizedTest(name = "rollover={0}, overspending={1}, carry={2}")
    @CsvSource({"true, true, -20", "true, false, 0", "false, true, 0", "false, false, 0"})
    void carriesDebtOnlyWhenBothRolloverPoliciesPermitIt(
            boolean rollover, boolean overspending, String expected) {
        Budget previous = month(1, 1, "EUR");
        previous.setRollover(rollover);
        previous.setRolloverOverspending(overspending);
        Budget current = month(2, 2, "EUR");
        repository(current, List.of(previous));
        when(transactionRepository.findByCategoryIdInAndDateRange(
                        anyList(), eq(previous.getStartDate()), eq(previous.getEndDate()), eq(1L)))
                .thenReturn(
                        List.of(
                                Transaction.builder()
                                        .id(1L)
                                        .userId(1L)
                                        .type(TransactionType.EXPENSE)
                                        .date(previous.getStartDate())
                                        .currency("EUR")
                                        .amount(new BigDecimal("120"))
                                        .build()));
        BudgetHistoryResponse history = service.getBudgetHistory(2L, 1L);
        assertThat(history.getInitialCarryover()).isEqualByComparingTo(expected);
        assertThat(history.getHistory().getFirst().getAvailable())
                .isEqualByComparingTo(new BigDecimal("100").add(new BigDecimal(expected)));
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void disablingRolloverBreaksAnEarlierNonzeroCarryChain() {
        Budget january = month(1, 1, "EUR");
        january.setRollover(false);
        Budget february = month(2, 2, "USD");
        Budget current = month(3, 3, "USD");
        repository(current, List.of(january, february));
        assertThat(service.getBudgetHistory(3L, 1L).getInitialCarryover())
                .isEqualByComparingTo("100");
        verifyNoInteractions(exchangeRateService);
    }
}
