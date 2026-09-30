package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetHistoryResponse;
import org.openfinance.dto.BudgetSuggestion;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.mapper.BudgetMapper;
import org.openfinance.repository.BudgetAlertRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class BudgetAuditRegressionTest {
    @Mock private BudgetRepository budgets;
    @Mock private BudgetAlertRepository alerts;
    @Mock private CategoryRepository categories;
    @Mock private CurrencyRepository currencies;
    @Mock private TransactionRepository transactions;
    @Mock private TransactionSplitRepository splits;
    @Mock private BudgetMapper mapper;
    @Mock private EncryptionService encryption;
    @Mock private MessageSource messages;
    @Mock private OperationHistoryService history;
    @Mock private SearchTokenService search;
    @Mock private DefaultCurrencyProvider defaultCurrency;
    @Mock private ExchangeRateService exchangeRates;
    @InjectMocks private BudgetService service;

    private Category groceries() {
        return Category.builder()
                .id(4L)
                .userId(9L)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .isSystem(true)
                .build();
    }

    @ParameterizedTest
    @EnumSource(BudgetPeriod.class)
    void calendarBoundariesDoNotCreateAdditionalAllowances(BudgetPeriod period) {
        Category category = groceries();
        LocalDate start = LocalDate.of(2026, 1, 1);
        Budget budget =
                Budget.builder()
                        .id(7L)
                        .userId(9L)
                        .categoryId(4L)
                        .amount("100")
                        .currency("EUR")
                        .period(period)
                        .startDate(start)
                        .endDate(start.plusYears(1))
                        .rollover(true)
                        .build();
        when(budgets.findByIdAndUserId(7L, 9L)).thenReturn(Optional.of(budget));
        when(categories.findByIdAndUserId(4L, 9L)).thenReturn(Optional.of(category));
        // This fixture deliberately crosses boundaries for every period enum.
        if (period == BudgetPeriod.WEEKLY) {
            when(messages.getMessage(eq("budget.period.weekOf"), any(), any(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(2));
        }

        BudgetHistoryResponse detail = service.getBudgetHistory(7L, 9L);

        assertThat(detail.getHistory()).hasSize(1);
        assertThat(detail.getHistory().getFirst().getPeriodStart()).isEqualTo(start);
        assertThat(detail.getHistory().getFirst().getPeriodEnd()).isEqualTo(start.plusYears(1));
        assertThat(detail.getTotalBudgeted()).isEqualByComparingTo("100");
        assertThat(detail.getTotalSpent()).isZero();
        assertThat(detail.getHistory().getFirst().getRemaining()).isEqualByComparingTo("100");
    }

    @Test
    void suggestionsConvertBothTransactionsAndSplitsAtTheirPostingDate() {
        Category category = groceries();
        LocalDate date = LocalDate.now().minusDays(1);
        Transaction euros =
                Transaction.builder()
                        .id(10L)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("100"))
                        .currency("EUR")
                        .date(date)
                        .build();
        Transaction dollars =
                Transaction.builder()
                        .id(11L)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("100"))
                        .currency("USD")
                        .date(date)
                        .build();
        Transaction splitParent =
                Transaction.builder()
                        .id(12L)
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("120"))
                        .currency("USD")
                        .date(date)
                        .build();
        TransactionSplit split =
                TransactionSplit.builder()
                        .id(13L)
                        .transactionId(12L)
                        .transaction(splitParent)
                        .categoryId(4L)
                        .amount(new BigDecimal("50"))
                        .build();
        when(categories.findByUserIdAndType(9L, CategoryType.EXPENSE))
                .thenReturn(List.of(category));
        when(defaultCurrency.resolveForUser(9L)).thenReturn("EUR");
        when(transactions.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(9L)))
                .thenAnswer(
                        call ->
                                inWindow(date, call.getArgument(1), call.getArgument(2))
                                        ? List.of(euros, dollars)
                                        : List.of());
        when(splits.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(9L)))
                .thenAnswer(
                        call ->
                                inWindow(date, call.getArgument(1), call.getArgument(2))
                                        ? List.of(split)
                                        : List.of());
        when(splits.countUniqueTransactionsByCategoryIdInAndDateRange(
                        anyList(), any(), any(), eq(9L)))
                .thenAnswer(
                        call -> inWindow(date, call.getArgument(1), call.getArgument(2)) ? 1L : 0L);
        when(exchangeRates.convert(new BigDecimal("100"), "USD", "EUR", date))
                .thenReturn(new BigDecimal("85"));
        when(exchangeRates.convert(new BigDecimal("50"), "USD", "EUR", date))
                .thenReturn(new BigDecimal("42.50"));

        List<BudgetSuggestion> suggestions =
                service.analyzeCategorySpending(9L, BudgetPeriod.MONTHLY, 1, null);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().getAverageSpent()).isEqualByComparingTo("227.50");
        assertThat(suggestions.getFirst().getSuggestedAmount()).isEqualByComparingTo("228");
        assertThat(suggestions.getFirst().getTransactionCount()).isEqualTo(3);
        assertThat(suggestions.getFirst().getCurrency()).isEqualTo("EUR");
    }

    private boolean inWindow(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
