package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.dto.InsightResponse;
import org.openfinance.entity.Budget;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.InsightType;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.InsightRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.ai.AIProvider;
import org.openfinance.service.ai.AIProviderException;
import org.openfinance.service.ai.AIRequestLimits;
import org.springframework.context.support.ResourceBundleMessageSource;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {
    @Mock InsightRepository insightRepository;
    @Mock InsightWriter insightWriter;
    @Mock TransactionRepository transactionRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock AccountRepository accountRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock UserRepository userRepository;
    @Mock UserSettingsRepository userSettingsRepository;
    @Mock RecurringTransactionRepository recurringTransactionRepository;
    @Mock DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock NetWorthService netWorthService;
    @Mock BudgetService budgetService;
    @Mock ExchangeRateService exchangeRateService;
    @Mock AIProvider aiProvider;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @Spy AIRequestLimits requestLimits = new AIRequestLimits();

    @Spy
    org.openfinance.config.BusinessRulesProperties businessRules =
            new org.openfinance.config.BusinessRulesProperties();

    @Spy ResourceBundleMessageSource messageSource = messages();
    @InjectMocks InsightService service;

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    private String provenance() {
        return ",\"currency\":\"EUR\",\"country\":\"FR\",\"asOf\":\""
                + LocalDate.now()
                + "\",\"sourceUrl\":\"https://example.test/statistics\"}";
    }

    @BeforeEach
    void setup() {
        lenient()
                .when(userRepository.findById(1L))
                .thenReturn(Optional.of(User.builder().id(1L).build()));
        lenient().when(defaultCurrencyProvider.resolveForUser(1L)).thenReturn("EUR");
        lenient()
                .when(netWorthService.calculateTotalAssets(1L, "EUR"))
                .thenReturn(new BigDecimal("3600"));
        lenient()
                .when(netWorthService.calculateTotalLiabilities(1L, "EUR"))
                .thenReturn(BigDecimal.ZERO);
        lenient()
                .when(insightWriter.replaceGenerated(eq(1L), anyList()))
                .thenAnswer(i -> i.getArgument(1));
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
                .when(exchangeRateService.convert(any(), anyString(), eq("EUR"), any()))
                .thenAnswer(
                        i ->
                                ((BigDecimal) i.getArgument(0))
                                        .multiply(
                                                "USD".equals(i.getArgument(1))
                                                        ? new BigDecimal("0.5")
                                                        : BigDecimal.ONE));
        lenient()
                .when(transactionRepository.findByUserIdAndType(1L, TransactionType.INCOME))
                .thenReturn(List.of(tx("2000", "EUR", TransactionType.INCOME)));
        lenient()
                .when(aiProvider.sendPrompt(anyString(), anyString()))
                .thenAnswer(
                        i -> {
                            String prompt = i.getArgument(0);
                            return Mono.just(
                                    prompt.startsWith("You are a tax")
                                            ? "{\"baseRate\":20,\"topRate\":40,\"standardDeduction\":1000"
                                                    + provenance()
                                            : prompt.startsWith("Review these")
                                                    ? "[]"
                                                    : "{\"medianIncome\":2500,\"medianNetWorth\":150000"
                                                            + provenance());
                        });
    }

    @Test
    void usesCanonicalNetWorthAndBudgetProgress() {
        Category category =
                Category.builder().id(3L).name("Food").type(CategoryType.EXPENSE).build();
        when(budgetRepository.findActiveByUserIdAndDate(1L, LocalDate.now()))
                .thenReturn(List.of(Budget.builder().id(5L).category(category).build()));
        when(budgetService.calculateBudgetProgress(5L, 1L))
                .thenReturn(
                        BudgetProgressResponse.builder()
                                .budgeted(new BigDecimal("100"))
                                .spent(new BigDecimal("170"))
                                .percentageSpent(new BigDecimal("170"))
                                .currency("EUR")
                                .build());
        List<InsightResponse> results = service.generateInsights(1L);
        assertThat(results)
                .filteredOn(i -> i.getType() == InsightType.BUDGET_WARNING)
                .singleElement()
                .satisfies(i -> assertThat(i.getDescription()).contains("70", "70%"));
        assertThat(results)
                .filteredOn(i -> i.getTitle().equals("Net Worth Assessment"))
                .singleElement()
                .satisfies(
                        i ->
                                assertThat(i.getDescription())
                                        .contains("3600 EUR", "Unverified AI estimate"));
        verify(netWorthService).calculateTotalAssets(1L, "EUR");
        verify(insightRepository, never()).deleteByUser_Id(anyLong());
    }

    @Test
    void convertsRecurringCostsAndHistoricalIncomeBeforeSumming() {
        when(recurringTransactionRepository.findByUserIdAndIsActive(1L))
                .thenReturn(List.of(recurring(1L, "EUR"), recurring(2L, "USD")));
        when(transactionRepository.findByUserIdAndType(1L, TransactionType.INCOME))
                .thenReturn(List.of(tx("2000", "USD", TransactionType.INCOME)));
        List<InsightResponse> results = service.generateInsights(1L);
        assertThat(results)
                .filteredOn(i -> i.getTitle().equals("Recurring Expenses Summary"))
                .singleElement()
                .satisfies(i -> assertThat(i.getDescription()).contains("180 EUR/month", "18%"));
        verify(exchangeRateService, atLeastOnce())
                .convert(new BigDecimal("2000"), "USD", "EUR", LocalDate.now());
    }

    @Test
    void readsPlaintextCategoriesForSubscriptionAndDeductionRules() {
        Category subscriptions =
                Category.builder()
                        .id(1L)
                        .name("Subscriptions")
                        .nameKey("category.subscriptions")
                        .type(CategoryType.EXPENSE)
                        .build();
        Category donations =
                Category.builder()
                        .id(2L)
                        .name("Donations")
                        .nameKey("category.donations")
                        .type(CategoryType.EXPENSE)
                        .build();
        when(categoryRepository.findByUserIdAndType(1L, CategoryType.EXPENSE))
                .thenReturn(List.of(subscriptions, donations));
        when(transactionRepository.findByCategoryIdAndDateRange(eq(1L), any(), any(), eq(1L)))
                .thenReturn(List.of(tx("80", "EUR", TransactionType.EXPENSE)));
        when(transactionRepository.findByCategoryIdAndDateRange(eq(2L), any(), any(), eq(1L)))
                .thenReturn(List.of(tx("50", "EUR", TransactionType.EXPENSE)));
        List<InsightResponse> results = service.generateInsights(1L);
        assertThat(results)
                .extracting(InsightResponse::getTitle)
                .contains("Subscription Review Opportunity", "Potential Tax Deductions");
    }

    @Test
    void failureCannotPublishOrDeletePreviousResults() {
        when(aiProvider.sendPrompt(anyString(), anyString()))
                .thenReturn(Mono.error(new AIProviderException("fixture", "unavailable")));
        assertThatThrownBy(() -> service.generateInsights(1L))
                .isInstanceOf(AIProviderException.class);
        verifyNoInteractions(insightWriter, insightRepository);
    }

    @Test
    void rejectsNegativeTaxParametersWithoutSavingThem() {
        when(aiProvider.sendPrompt(startsWith("You are a tax"), anyString()))
                .thenReturn(
                        Mono.just(
                                "{\"baseRate\":-25,\"topRate\":40,\"standardDeduction\":-1000"
                                        + provenance()));
        assertThatThrownBy(() -> service.generateInsights(1L))
                .isInstanceOf(AIProviderException.class);
        verifyNoInteractions(insightWriter);
    }

    @Test
    void rejectsUnownedCompetitorSuggestions() {
        when(recurringTransactionRepository.findByUserIdAndIsActive(1L))
                .thenReturn(List.of(recurring(1L, "EUR")));
        when(aiProvider.sendPrompt(startsWith("Review these"), anyString()))
                .thenReturn(Mono.just("[{\"originalServiceId\":999,\"potentialSavings\":999999}]"));
        assertThatThrownBy(() -> service.generateInsights(1L))
                .isInstanceOf(AIProviderException.class);
        verifyNoInteractions(insightWriter);
    }

    @Test
    void missingFxNeverFallsBackToRawCurrencyAmounts() {
        when(transactionRepository.findByUserIdAndType(1L, TransactionType.INCOME))
                .thenReturn(List.of(tx("100", "USD", TransactionType.INCOME)));
        when(exchangeRateService.convert(any(), eq("USD"), eq("EUR"), any()))
                .thenThrow(new IllegalStateException("No rate"));
        assertThatThrownBy(() -> service.generateInsights(1L))
                .isInstanceOf(AIProviderException.class);
        verifyNoInteractions(insightWriter);
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

    private RecurringTransaction recurring(Long id, String currency) {
        return RecurringTransaction.builder()
                .id(id)
                .type(TransactionType.EXPENSE)
                .description("Service " + id)
                .amount(new BigDecimal("100"))
                .currency(currency)
                .frequency(RecurringFrequency.MONTHLY)
                .build();
    }
}
