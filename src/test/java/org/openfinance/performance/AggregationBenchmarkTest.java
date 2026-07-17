package org.openfinance.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.entity.Category;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.service.DashboardService;
import org.openfinance.service.NetWorthService;
import org.openfinance.service.OperationHistoryService;
import org.springframework.util.StopWatch;

/**
 * Benchmarks for in-memory aggregation at scale.
 *
 * <p>Verifies that in-memory aggregation (which replaced SQL SUM after encryption) completes within
 * acceptable time for 10K, 50K, and 100K transactions.
 *
 * <p>Testing Strategy item 5: "Benchmark aggregation on 10K, 50K, 100K transactions"
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("benchmark")
@DisplayName("Aggregation Benchmark — In-Memory Sums at Scale")
class AggregationBenchmarkTest {

    @Mock private NetWorthService netWorthService;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private EncryptionService encryptionService;
    @Mock private AssetRepository assetRepository;
    @Mock private UserRepository userRepository;
    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private DashboardService dashboardService;

    private static final Long USER_ID = 1L;
    private static final String[] CATEGORIES = {
        "Groceries", "Rent", "Utilities", "Transport", "Entertainment",
        "Healthcare", "Insurance", "Dining", "Shopping", "Subscriptions"
    };

    @BeforeEach
    void setUp() {
        User mockUser =
                User.builder().id(USER_ID).username("benchuser").baseCurrency("USD").build();
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
    }

    // ------------------------------------------------------------------
    // Cash flow aggregation benchmarks
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getCashFlow aggregates 10K transactions under 500ms")
    void cashFlow10K() {
        benchmarkCashFlow(10_000, 500);
    }

    @Test
    @DisplayName("getCashFlow aggregates 50K transactions under 1500ms")
    void cashFlow50K() {
        benchmarkCashFlow(50_000, 1500);
    }

    @Test
    @DisplayName("getCashFlow aggregates 100K transactions under 3000ms")
    void cashFlow100K() {
        benchmarkCashFlow(100_000, 3000);
    }

    // ------------------------------------------------------------------
    // Spending-by-category aggregation benchmarks
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getSpendingByCategory aggregates 10K transactions under 500ms")
    void spendingByCategory10K() {
        benchmarkSpendingByCategory(10_000, 500);
    }

    @Test
    @DisplayName("getSpendingByCategory aggregates 50K transactions under 1500ms")
    void spendingByCategory50K() {
        benchmarkSpendingByCategory(50_000, 1500);
    }

    @Test
    @DisplayName("getSpendingByCategory aggregates 100K transactions under 3000ms")
    void spendingByCategory100K() {
        benchmarkSpendingByCategory(100_000, 3000);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void benchmarkCashFlow(int txCount, long maxMillis) {
        List<Transaction> transactions = generateTransactions(txCount);
        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(transactions);

        StopWatch sw = new StopWatch();
        sw.start();
        Map<String, BigDecimal> result = dashboardService.getCashFlow(USER_ID, 30);
        sw.stop();

        assertThat(result).containsKeys("income", "expenses", "netCashFlow");
        assertThat(result.get("income")).isPositive();
        assertThat(result.get("expenses")).isPositive();
        assertThat(sw.getTotalTimeMillis())
                .as(
                        "getCashFlow(%d txns) took %dms, max %dms",
                        txCount, sw.getTotalTimeMillis(), maxMillis)
                .isLessThan(maxMillis);
    }

    private void benchmarkSpendingByCategory(int txCount, long maxMillis) {
        List<Transaction> transactions = generateTransactions(txCount);
        when(transactionRepository.findByUserIdAndDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(transactions);

        StopWatch sw = new StopWatch();
        sw.start();
        Map<String, BigDecimal> result = dashboardService.getSpendingByCategory(USER_ID, 30);
        sw.stop();

        assertThat(result).isNotEmpty();
        assertThat(sw.getTotalTimeMillis())
                .as(
                        "getSpendingByCategory(%d txns) took %dms, max %dms",
                        txCount, sw.getTotalTimeMillis(), maxMillis)
                .isLessThan(maxMillis);
    }

    private List<Transaction> generateTransactions(int count) {
        List<Transaction> transactions = new ArrayList<>(count);
        LocalDate baseDate = LocalDate.now().minusDays(15);

        for (int i = 0; i < count; i++) {
            TransactionType type = (i % 3 == 0) ? TransactionType.INCOME : TransactionType.EXPENSE;
            String categoryName = CATEGORIES[i % CATEGORIES.length];
            Category category =
                    Category.builder()
                            .id((long) (i % CATEGORIES.length) + 1)
                            .name(categoryName)
                            .build();

            transactions.add(
                    Transaction.builder()
                            .id((long) i)
                            .userId(USER_ID)
                            .accountId(1L)
                            .type(type)
                            .amount(new BigDecimal("50.00").add(BigDecimal.valueOf(i % 100)))
                            .currency("USD")
                            .description("Transaction " + i)
                            .date(baseDate.plusDays(i % 30))
                            .category(category)
                            .categoryId(category.getId())
                            .isDeleted(false)
                            .build());
        }
        return transactions;
    }
}
