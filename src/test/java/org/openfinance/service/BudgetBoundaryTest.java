package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetRequest;
import org.openfinance.dto.BudgetSuggestion;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetAlert;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.mapper.BudgetMapper;
import org.openfinance.mapper.BudgetMapperImpl;
import org.openfinance.repository.BudgetAlertRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("Budget editing, alerts and spending windows")
class BudgetBoundaryTest {
    @Mock BudgetRepository budgets;
    @Mock BudgetAlertRepository alerts;
    @Mock CategoryRepository categories;
    @Mock CurrencyRepository currencies;
    @Mock TransactionRepository transactions;
    @Mock TransactionSplitRepository splits;
    @Mock DefaultCurrencyProvider defaultCurrency;
    @Mock ExchangeRateService exchange;
    @Mock MessageSource messages;
    @Mock OperationHistoryService history;
    @Mock ApplicationEventPublisher events;
    @Spy BudgetMapper mapper = new BudgetMapperImpl();
    @InjectMocks BudgetService service;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCalls")
    void refusesMissingIdentityAndRequiredInputBeforeQuerying(
            String name, Consumer<BudgetService> operation) {
        assertThatThrownBy(() -> operation.accept(service))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
        verifyNoInteractions(budgets, categories, transactions, alerts);
    }

    static Stream<Arguments> invalidCalls() {
        return Stream.of(
                invalid("create user", s -> s.createBudget(new BudgetRequest(), null)),
                invalid("create request", s -> s.createBudget(null, 1L)),
                invalid("update id", s -> s.updateBudget(null, new BudgetRequest(), 1L)),
                invalid("update user", s -> s.updateBudget(5L, new BudgetRequest(), null)),
                invalid("update request", s -> s.updateBudget(5L, null, 1L)),
                invalid("delete id", s -> s.deleteBudget(null, 1L)),
                invalid("delete user", s -> s.deleteBudget(5L, null)),
                invalid("lookup id", s -> s.getBudgetById(null, 1L)),
                invalid("lookup user", s -> s.getBudgetById(5L, null)),
                invalid("list user", s -> s.getBudgetsByUser(null)),
                invalid("period user", s -> s.getBudgetsByPeriod(null, BudgetPeriod.MONTHLY)),
                invalid("period", s -> s.getBudgetsByPeriod(1L, null)),
                invalid("progress id", s -> s.calculateBudgetProgress(null, 1L)),
                invalid("progress user", s -> s.calculateBudgetProgress(5L, null)),
                invalid("summary user", s -> s.getBudgetSummary(null, BudgetPeriod.MONTHLY)),
                invalid("summary period", s -> s.getBudgetSummary(1L, null)),
                invalid("all summaries user", s -> s.getAllBudgetsSummary(null)),
                invalid(
                        "analysis user",
                        s -> s.analyzeCategorySpending(null, BudgetPeriod.MONTHLY, 6, null)),
                invalid("analysis period", s -> s.analyzeCategorySpending(1L, null, 6, null)));
    }

    private static Arguments invalid(String name, Consumer<BudgetService> call) {
        return Arguments.of(name, call);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "start",
                "end",
                "category",
                "amount",
                "rollover",
                "recurring",
                "overspending",
                "period",
                "unchanged",
                "notes"
            })
    void editsResetThresholdAlertsOnlyWhenTheBudgetCalculationChanges(String change) {
        Budget budget = budget();
        BudgetRequest request = request();
        switch (change) {
            case "start" -> request.setStartDate(request.getStartDate().plusDays(1));
            case "end" -> request.setEndDate(request.getEndDate().minusDays(1));
            case "category" -> request.setCategoryId(6L);
            case "amount" -> request.setAmount(new BigDecimal("100.01"));
            case "rollover" -> request.setRollover(true);
            case "recurring" -> request.setRecurring(true);
            case "overspending" -> request.setRolloverOverspending(true);
            case "period" -> request.setPeriod(BudgetPeriod.WEEKLY);
            case "notes" -> request.setNotes("Display-only change");
            default -> request.setAmount(new BigDecimal("100.000"));
        }
        BudgetAlert alert =
                BudgetAlert.builder()
                        .id(java.util.UUID.fromString("00000000-0000-0000-0000-000000000009"))
                        .budget(budget)
                        .threshold(new BigDecimal("75"))
                        .lastTriggered(LocalDateTime.of(2024, 1, 8, 12, 0))
                        .isRead(false)
                        .build();
        when(budgets.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(budget));
        when(categories.findByIdAndUserId(request.getCategoryId(), 1L))
                .thenReturn(Optional.of(category(request.getCategoryId())));
        when(budgets.findByUserIdAndCategoryId(1L, request.getCategoryId()))
                .thenReturn(List.of(budget));
        when(budgets.save(budget)).thenReturn(budget);
        lenient().when(alerts.findByBudgetId(5L)).thenReturn(List.of(alert));
        service.updateBudget(5L, request, 1L);
        if (change.equals("unchanged") || change.equals("notes")) {
            assertThat(alert.getLastTriggered()).isNotNull();
            assertThat(alert.isRead()).isFalse();
            verify(alerts, never()).save(any());
        } else {
            assertThat(alert.getLastTriggered()).isNull();
            assertThat(alert.isRead()).isTrue();
            verify(alerts).save(alert);
        }
        assertThat(budget.getPeriod()).isEqualTo(request.getPeriod());
        assertThat(new BigDecimal(budget.getAmount())).isEqualByComparingTo(request.getAmount());
    }

    @ParameterizedTest
    @EnumSource(BudgetPeriod.class)
    void analysisWindowsCoverTheRequestedRangeExactlyOnce(BudgetPeriod period) {
        when(categories.findByUserId(1L)).thenReturn(List.of(category(5L)));
        List<LocalDate[]> windows = new ArrayList<>();
        when(transactions.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenAnswer(
                        call -> {
                            LocalDate start = call.getArgument(1), end = call.getArgument(2);
                            windows.add(new LocalDate[] {start, end});
                            return List.of(
                                    Transaction.builder()
                                            .type(TransactionType.EXPENSE)
                                            .date(start)
                                            .currency("EUR")
                                            .amount(new BigDecimal("10.01"))
                                            .build());
                        });
        when(splits.countUniqueTransactionsByCategoryIdInAndDateRange(
                        anyList(), any(), any(), eq(1L)))
                .thenReturn(null);
        List<BudgetSuggestion> result =
                service.analyzeCategorySpending(1L, period, 14, List.of(), " eur ");
        assertThat(result)
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getAverageSpent()).isEqualByComparingTo("10.01");
                            assertThat(row.getSuggestedAmount()).isEqualByComparingTo("11");
                            assertThat(row.getTransactionCount()).isEqualTo(windows.size());
                            assertThat(row.getCurrency()).isEqualTo("EUR");
                        });
        assertThat(windows.getFirst()[0]).isEqualTo(LocalDate.now().minusMonths(14));
        assertThat(windows.getLast()[1]).isEqualTo(LocalDate.now());
        for (int i = 0; i < windows.size(); i++) {
            assertThat(windows.get(i)[1])
                    .isAfterOrEqualTo(windows.get(i)[0])
                    .isBeforeOrEqualTo(LocalDate.now());
            if (i > 0) assertThat(windows.get(i)[0]).isEqualTo(windows.get(i - 1)[1].plusDays(1));
        }
        verifyNoInteractions(exchange);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "transfer", "paired", "holdings", "refund", "zero"})
    void nonSpendingActivityDoesNotProduceABudgetSuggestion(String kind) {
        when(categories.findByUserId(1L)).thenReturn(List.of(category(5L)));
        Transaction row =
                Transaction.builder()
                        .id(3L)
                        .type(TransactionType.EXPENSE)
                        .currency("EUR")
                        .date(LocalDate.now())
                        .amount(new BigDecimal("10"))
                        .build();
        switch (kind) {
            case "deleted" -> row.setIsDeleted(true);
            case "transfer" -> row.setType(TransactionType.TRANSFER);
            case "paired" -> row.setTransferId("paired");
            case "holdings" -> {
                row.setInvestmentQuantity(BigDecimal.ONE);
                row.setInvestmentCash(false);
            }
            case "refund" -> row.setType(TransactionType.INCOME);
            default -> row.setAmount(BigDecimal.ZERO);
        }
        when(transactions.findByCategoryIdInAndDateRange(anyList(), any(), any(), eq(1L)))
                .thenReturn(List.of(row));
        assertThat(service.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 0, null, "EUR"))
                .isEmpty();
    }

    @Test
    void childCategoriesAndSplitRowsDoNotDoubleCountDeletedOrMissingAmounts() {
        Category parent = category(5L), child = category(6L);
        parent.setSubcategories(List.of(child, child));
        child.setSubcategories(null);
        when(categories.findByUserId(1L)).thenReturn(List.of(parent));
        Transaction expense =
                Transaction.builder()
                        .id(10L)
                        .type(TransactionType.EXPENSE)
                        .currency("EUR")
                        .date(LocalDate.now())
                        .amount(new BigDecimal("40"))
                        .build();
        Transaction deleted =
                Transaction.builder()
                        .id(11L)
                        .type(TransactionType.EXPENSE)
                        .currency("EUR")
                        .date(LocalDate.now())
                        .isDeleted(true)
                        .amount(new BigDecimal("900"))
                        .build();
        when(transactions.findByCategoryIdInAndDateRange(eq(List.of(5L, 6L)), any(), any(), eq(1L)))
                .thenReturn(List.of(expense));
        when(splits.findByCategoryIdInAndDateRange(eq(List.of(5L, 6L)), any(), any(), eq(1L)))
                .thenReturn(
                        List.of(
                                TransactionSplit.builder()
                                        .transaction(deleted)
                                        .amount(new BigDecimal("900"))
                                        .build(),
                                TransactionSplit.builder()
                                        .transaction(expense)
                                        .amount(null)
                                        .build()));
        assertThat(service.analyzeCategorySpending(1L, BudgetPeriod.MONTHLY, 0, null, "EUR"))
                .singleElement()
                .satisfies(row -> assertThat(row.getSuggestedAmount()).isEqualByComparingTo("40"));
    }

    private Category category(Long id) {
        return Category.builder().id(id).userId(1L).name("Groceries").isSystem(false).build();
    }

    private Budget budget() {
        return Budget.builder()
                .id(5L)
                .userId(1L)
                .categoryId(5L)
                .amount("100.00")
                .currency("EUR")
                .period(BudgetPeriod.MONTHLY)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .rollover(false)
                .recurring(false)
                .rolloverOverspending(false)
                .build();
    }

    private BudgetRequest request() {
        return BudgetRequest.builder()
                .categoryId(5L)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .period(BudgetPeriod.MONTHLY)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .rollover(false)
                .recurring(false)
                .rolloverOverspending(false)
                .build();
    }
}
