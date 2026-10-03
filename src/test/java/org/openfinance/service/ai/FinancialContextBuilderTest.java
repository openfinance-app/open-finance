package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Budget;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.service.BudgetService;
import org.openfinance.service.DefaultCurrencyProvider;
import org.openfinance.service.ExchangeRateService;
import org.openfinance.service.NetWorthService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
class FinancialContextBuilderTest {
    @Mock AccountRepository accountRepository;
    @Mock org.openfinance.repository.CategoryRepository categoryRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock AssetRepository assetRepository;
    @Mock LiabilityRepository liabilityRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock NetWorthService netWorthService;
    @Mock ExchangeRateService exchangeRateService;
    @Mock DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock BudgetService budgetService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @Spy ResourceBundleMessageSource messageSource = messages();
    @InjectMocks FinancialContextBuilder builder;

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @BeforeEach
    void setup() {
        lenient().when(defaultCurrencyProvider.resolveForUser(1L)).thenReturn("EUR");
        lenient()
                .when(defaultCurrencyProvider.resolve(anyString()))
                .thenAnswer(i -> i.getArgument(0));
        lenient()
                .when(netWorthService.calculateTotalAssets(1L, "EUR"))
                .thenReturn(new BigDecimal("3600"));
        lenient()
                .when(netWorthService.calculateTotalLiabilities(1L, "EUR"))
                .thenReturn(BigDecimal.ZERO);
        lenient()
                .when(exchangeRateService.convert(any(), anyString(), eq("EUR")))
                .thenAnswer(
                        i ->
                                ((BigDecimal) i.getArgument(0))
                                        .multiply(
                                                "USD".equals(i.getArgument(1))
                                                        ? new BigDecimal("0.8")
                                                        : BigDecimal.ONE));
        lenient()
                .when(
                        exchangeRateService.convert(
                                any(), anyString(), eq("EUR"), any(LocalDate.class)))
                .thenAnswer(
                        i ->
                                ((BigDecimal) i.getArgument(0))
                                        .multiply(
                                                "USD".equals(i.getArgument(1))
                                                        ? new BigDecimal("0.5")
                                                        : BigDecimal.ONE));
    }

    @Test
    void usesDatedCashFlowButCurrentBalanceConversionAndExcludesTransfersAndDeletedTransactions() {
        when(accountRepository.findByUserIdAndIsActive(1L, true))
                .thenReturn(
                        List.of(
                                Account.builder()
                                        .id(1L)
                                        .name("Euro")
                                        .balance(new BigDecimal("2880"))
                                        .currency("EUR")
                                        .build(),
                                Account.builder()
                                        .id(2L)
                                        .name("Dollar")
                                        .balance(new BigDecimal("900"))
                                        .currency("USD")
                                        .build()));
        Transaction transfer = tx("500", "EUR", TransactionType.EXPENSE);
        transfer.setTransferId("internal");
        Transaction deleted = tx("99", "EUR", TransactionType.EXPENSE);
        deleted.setIsDeleted(true);
        when(transactionRepository.findByUserIdAndDateBetween(
                        1L, LocalDate.now().withDayOfMonth(1), LocalDate.now()))
                .thenReturn(
                        List.of(
                                tx("2000", "EUR", TransactionType.INCOME),
                                tx("100", "USD", TransactionType.EXPENSE),
                                tx("120", "EUR", TransactionType.EXPENSE),
                                transfer,
                                deleted));
        Map<String, FinancialFact> facts = facts(builder.buildMinimalContext(1L, Locale.ENGLISH));
        assertThat(facts.get("accounts.total").amount()).isEqualTo("3600.00");
        assertThat(facts.get("cashflow.expenses").amount()).isEqualTo("170.00");
        assertThat(facts.get("cashflow.surplus").amount()).isEqualTo("1830.00");
        verify(exchangeRateService).convert(new BigDecimal("100"), "USD", "EUR", LocalDate.now());
        verifyNoInteractions(budgetService);
    }

    @Test
    void includesCanonicalBudgetProgressWithCurrencyAndPeriod() {
        when(budgetRepository.findActiveByUserIdAndDate(1L, LocalDate.now()))
                .thenReturn(List.of(Budget.builder().id(8L).build()));
        when(budgetService.calculateBudgetProgress(8L, 1L))
                .thenReturn(
                        BudgetProgressResponse.builder()
                                .categoryName("Food")
                                .budgeted(new BigDecimal("100"))
                                .spent(new BigDecimal("170"))
                                .remaining(new BigDecimal("-70"))
                                .currency("EUR")
                                .startDate(LocalDate.now().withDayOfMonth(1))
                                .endDate(LocalDate.now())
                                .build());
        Map<String, FinancialFact> facts = facts(builder.buildContext(1L, Locale.ENGLISH));
        assertThat(facts.get("budget.8.limit").amount()).isEqualTo("100.00");
        assertThat(facts.get("budget.8.spent").amount()).isEqualTo("170.00");
        assertThat(facts.get("budget.8.remaining").amount()).isEqualTo("-70.00");
        assertThat(facts.get("budget.8.limit").currency()).isEqualTo("EUR");
        assertThat(facts.get("budget.8.limit").period()).contains(LocalDate.now().toString());
    }

    @Test
    void missingFxCannotBecomeAnUnconvertedCashFlowFact() {
        when(transactionRepository.findByUserIdAndDateBetween(
                        1L, LocalDate.now().withDayOfMonth(1), LocalDate.now()))
                .thenReturn(List.of(tx("100", "USD", TransactionType.EXPENSE)));
        when(exchangeRateService.convert(any(), eq("USD"), eq("EUR"), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("missing FX"));
        String context = builder.buildMinimalContext(1L, Locale.ENGLISH);
        assertThat(facts(context)).doesNotContainKey("cashflow.expenses");
        assertThat(context).contains("cash flow unavailable");
    }

    @Test
    void honoursLocaleAndExcludesPlannedHoldings() {
        when(assetRepository.findByUserId(1L))
                .thenReturn(
                        List.of(
                                Asset.builder()
                                        .id(9L)
                                        .acquisitionType(AcquisitionType.PLANNED)
                                        .build()));
        LocaleContextHolder.setLocale(Locale.FRENCH);
        try {
            Map<String, FinancialFact> facts = facts(builder.buildContext(1L));
            assertThat(facts.get("net_worth").label()).isEqualTo("Patrimoine net");
            assertThat(facts).doesNotContainKey("asset.9");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void retrievesTheRequestedHistoricalCategoryIncludingChildrenAndDatedFx() {
        org.openfinance.entity.Category parent =
                org.openfinance.entity.Category.builder().id(40L).name("Food").build();
        org.openfinance.entity.Category child =
                org.openfinance.entity.Category.builder()
                        .id(41L)
                        .parentId(40L)
                        .name("Groceries")
                        .build();
        when(categoryRepository.findByUserId(1L)).thenReturn(List.of(parent, child));
        Transaction groceries = tx("100", "EUR", TransactionType.EXPENSE);
        groceries.setCategoryId(41L);
        Transaction converted = tx("70", "USD", TransactionType.EXPENSE);
        converted.setCategoryId(41L);
        Transaction unrelated = tx("500", "EUR", TransactionType.EXPENSE);
        unrelated.setCategoryId(42L);
        Transaction removed = tx("1000", "EUR", TransactionType.EXPENSE);
        removed.setCategoryId(41L);
        removed.setIsDeleted(true);
        when(transactionRepository.findByUserIdAndDateBetween(
                        1L, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)))
                .thenReturn(List.of(groceries, converted, unrelated, removed));
        Map<String, FinancialFact> selected =
                facts(
                        builder.forQuestion(
                                1L,
                                Locale.ENGLISH,
                                "How much did I spend on Food in September 2025?",
                                "[VERIFIED_FINANCIAL_DATA]"));
        assertThat(selected).containsOnlyKeys("requested.category.40");
        assertThat(selected.get("requested.category.40").amount()).isEqualTo("135.00");
        assertThat(selected.get("requested.category.40").period())
                .isEqualTo("2025-09-01 / 2025-09-30");
    }

    @Test
    void categoryIncomeDoesNotUseExpenseTotals() {
        org.openfinance.entity.Category salary =
                org.openfinance.entity.Category.builder()
                        .id(51L)
                        .name("Salary")
                        .type(org.openfinance.entity.CategoryType.INCOME)
                        .build();
        when(categoryRepository.findByUserId(1L)).thenReturn(List.of(salary));
        Transaction income = tx("2500", "EUR", TransactionType.INCOME);
        income.setCategoryId(51L);
        Transaction other = tx("500", "EUR", TransactionType.INCOME);
        other.setCategoryId(52L);
        when(transactionRepository.findByUserIdAndDateBetween(
                        1L, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)))
                .thenReturn(List.of(income, other));
        FinancialFact fact =
                facts(
                                builder.forQuestion(
                                        1L,
                                        Locale.ENGLISH,
                                        "What was my Salary income in September 2025?",
                                        "[VERIFIED_FINANCIAL_DATA]"))
                        .get("requested.category.51");
        assertThat(fact.amount()).isEqualTo("2500.00");
        assertThat(fact.label()).isEqualTo("Category income");
    }

    @Test
    void unrelatedQuestionsDoNotExposeFinancialFactsOrInviteIrrelevantNumbers() {
        String context =
                "[VERIFIED_FINANCIAL_DATA]\n[FACT] {\"id\":\"net_worth\",\"label\":\"Net worth\",\"amount\":\"100.00\",\"currency\":\"EUR\",\"period\":\"today\",\"entity\":\"\"}\n";
        assertThat(
                        builder.forQuestion(
                                1L, Locale.ENGLISH, "What is the capital of Italy?", context))
                .doesNotContain("[FACT]");
        assertThat(
                        builder.forQuestion(
                                1L, Locale.ENGLISH, "What is my mortgage interest rate?", context))
                .doesNotContain("[FACT]");
        assertThat(facts(builder.forQuestion(1L, Locale.ENGLISH, "What is my net worth?", context)))
                .containsOnlyKeys("net_worth");
    }

    private Transaction tx(String amount, String currency, TransactionType type) {
        return Transaction.builder()
                .amount(new BigDecimal(amount))
                .currency(currency)
                .type(type)
                .date(LocalDate.now())
                .isDeleted(false)
                .build();
    }

    private Map<String, FinancialFact> facts(String context) {
        return context.lines()
                .filter(s -> s.startsWith("[FACT] "))
                .map(
                        s -> {
                            try {
                                return objectMapper.readValue(s.substring(7), FinancialFact.class);
                            } catch (Exception ex) {
                                throw new AssertionError(ex);
                            }
                        })
                .collect(Collectors.toMap(FinancialFact::id, Function.identity()));
    }
}
