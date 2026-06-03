package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.dto.AccountSummary;
import org.openfinance.dto.DashboardSummary;
import org.openfinance.dto.NetWorthSummary;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;

/**
 * Unit tests for DashboardService.
 *
 * <p>
 * Tests cover: - Dashboard summary aggregation - Net worth summary retrieval -
 * Account summaries
 * with decryption and sorting - Recent transactions retrieval - Cash flow
 * calculation (income vs
 * expenses) - Spending by category analysis - Edge cases and error handling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DashboardService Unit Tests")
class DashboardServiceTest {

        @Mock
        private NetWorthService netWorthService;

        @Mock
        private AccountRepository accountRepository;

        @Mock
        private TransactionRepository transactionRepository;

        @Mock
        private TransactionMapper transactionMapper;

        @Mock
        private EncryptionService encryptionService;

        @Mock
        private AssetRepository assetRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private OperationHistoryService operationHistoryService;

        @InjectMocks
        private DashboardService dashboardService;

        private Long userId;

        @BeforeEach
        void setUp() {
                userId = 1L;

                // Mock user repository to return a user with EUR base currency
                // Using lenient() because not all tests call methods that need the user
                User mockUser = User.builder().id(userId).username("testuser").baseCurrency("EUR").build();
                lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

                // Mock NetWorthService to return a default NetWorth object
                // Using lenient() because not all tests call these methods
                lenient()
                                .when(
                                                netWorthService.saveNetWorthSnapshot(
                                                                anyLong(), any(LocalDate.class), anyString()))
                                .thenReturn(createNetWorth());
                lenient()
                                .when(netWorthService.calculateMonthlyChange(anyLong()))
                                .thenReturn(
                                                new NetWorthService.NetWorthChange(
                                                                BigDecimal.ZERO, BigDecimal.ZERO, false));
        }

        // ==================== getDashboardSummary Tests ====================

        @Test
        @DisplayName("Should get dashboard summary successfully")
        void shouldGetDashboardSummarySuccessfully() {
                // Arrange
                NetWorthSummary netWorthSummary = NetWorthSummary.builder()
                                .date(LocalDate.now())
                                .totalAssets(new BigDecimal("10000"))
                                .totalLiabilities(new BigDecimal("2000"))
                                .netWorth(new BigDecimal("8000"))
                                .monthlyChangeAmount(new BigDecimal("500"))
                                .monthlyChangePercentage(new BigDecimal("6.67"))
                                .currency("EUR")
                                .build();

                List<Account> accounts = List.of(
                                createAccount(100L, "Checking", new BigDecimal("5000")),
                                createAccount(101L, "Savings", new BigDecimal("3000")));

                when(netWorthService.saveNetWorthSnapshot(
                                eq(userId), any(LocalDate.class), eq("EUR")))
                                .thenReturn(createNetWorth());
                when(netWorthService.calculateMonthlyChange(userId))
                                .thenReturn(
                                                new NetWorthService.NetWorthChange(
                                                                new BigDecimal("500"), new BigDecimal("6.67"), true));
                when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(accounts);
                when(transactionRepository.findByUserId(
                                eq(userId), any(org.springframework.data.domain.Pageable.class)))
                                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
                when(transactionRepository.countByUserId(userId)).thenReturn(0L);

                // Act
                DashboardSummary result = dashboardService.getDashboardSummary(userId);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getNetWorth()).isNotNull();
                assertThat(result.getAccounts()).hasSize(2);
                assertThat(result.getRecentTransactions()).isEmpty();
                assertThat(result.getTotalAccounts()).isEqualTo(2);
                assertThat(result.getTotalTransactions()).isZero();
                assertThat(result.getBaseCurrency()).isEqualTo("EUR");

                verify(netWorthService)
                                .saveNetWorthSnapshot(
                                                eq(userId), any(LocalDate.class), eq("EUR"));
                verify(accountRepository).findByUserIdAndIsActive(userId, true);
                verify(transactionRepository).countByUserId(userId);
        }

        @Test
        @DisplayName("Should throw exception when user ID is null for dashboard summary")
        void shouldThrowExceptionWhenUserIdIsNullForDashboard() {
                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getDashboardSummary(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User ID cannot be null");

                verifyNoInteractions(netWorthService, accountRepository, transactionRepository);
        }

        @Test
        @DisplayName("Should handle no net worth snapshot scenario")
        void shouldHandleNoNetWorthSnapshotScenario() {
                // Arrange
                when(netWorthService.saveNetWorthSnapshot(
                                eq(userId), any(LocalDate.class), eq("EUR")))
                                .thenReturn(createNetWorth());
                when(netWorthService.calculateMonthlyChange(userId))
                                .thenReturn(
                                                new NetWorthService.NetWorthChange(
                                                                BigDecimal.ZERO, BigDecimal.ZERO, false));
                when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(List.of());
                when(transactionRepository.findByUserId(
                                eq(userId), any(org.springframework.data.domain.Pageable.class)))
                                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
                when(transactionRepository.countByUserId(userId)).thenReturn(0L);

                // Act
                DashboardSummary result = dashboardService.getDashboardSummary(userId);

                // Assert
                assertThat(result).isNotNull();
                verify(netWorthService)
                                .saveNetWorthSnapshot(
                                                eq(userId), any(LocalDate.class), eq("EUR"));
        }

        // ==================== getNetWorthSummary Tests ====================

        @Test
        @DisplayName("Should get net worth summary with existing snapshot")
        void shouldGetNetWorthSummaryWithExistingSnapshot() {
                // Arrange
                NetWorth netWorth = createNetWorth();
                when(netWorthService.saveNetWorthSnapshot(
                                eq(userId), any(LocalDate.class), eq("EUR")))
                                .thenReturn(netWorth);
                when(netWorthService.calculateMonthlyChange(userId))
                                .thenReturn(
                                                new NetWorthService.NetWorthChange(
                                                                new BigDecimal("500"), new BigDecimal("6.67"), true));

                // Act
                NetWorthSummary result = dashboardService.getNetWorthSummary(userId);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getDate()).isEqualTo(netWorth.getSnapshotDate());
                assertThat(result.getTotalAssets()).isEqualByComparingTo(netWorth.getTotalAssets());
                assertThat(result.getTotalLiabilities())
                                .isEqualByComparingTo(netWorth.getTotalLiabilities());
                assertThat(result.getNetWorth()).isEqualByComparingTo(netWorth.getNetWorth());
                assertThat(result.getMonthlyChangeAmount()).isEqualByComparingTo(new BigDecimal("500"));
                assertThat(result.getMonthlyChangePercentage())
                                .isEqualByComparingTo(new BigDecimal("6.67"));
                assertThat(result.getCurrency()).isEqualTo("EUR");

                verify(netWorthService)
                                .saveNetWorthSnapshot(
                                                eq(userId), any(LocalDate.class), eq("EUR"));
                verify(netWorthService).calculateMonthlyChange(userId);
        }

        @Test
        @DisplayName("Should create snapshot when none exists for net worth summary")
        void shouldCreateSnapshotWhenNoneExistsForNetWorth() {
                // Arrange
                when(netWorthService.saveNetWorthSnapshot(
                                eq(userId), any(LocalDate.class), eq("EUR")))
                                .thenReturn(createNetWorth());
                when(netWorthService.calculateMonthlyChange(userId))
                                .thenReturn(
                                                new NetWorthService.NetWorthChange(
                                                                BigDecimal.ZERO, BigDecimal.ZERO, false));

                // Act
                NetWorthSummary result = dashboardService.getNetWorthSummary(userId);

                // Assert
                assertThat(result).isNotNull();
                verify(netWorthService)
                                .saveNetWorthSnapshot(
                                                eq(userId), any(LocalDate.class), eq("EUR"));
        }

        @Test
        @DisplayName("Should throw exception when user ID is null for net worth summary")
        void shouldThrowExceptionWhenUserIdIsNullForNetWorth() {
                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getNetWorthSummary(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User ID cannot be null");

                verifyNoInteractions(netWorthService);
        }

        // ==================== getAccountSummaries Tests ====================

        @Test
        @DisplayName("Should get account summaries decrypted")
        void shouldGetAccountSummariesDecrypted() {
                // Arrange
                List<Account> accounts = List.of(
                                createAccount(100L, "Checking Account", new BigDecimal("5000")),
                                createAccount(101L, "Savings Account", new BigDecimal("3000")));

                when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(accounts);

                // Act
                List<AccountSummary> result = dashboardService.getAccountSummaries(userId);

                // Assert
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getName()).isEqualTo("Checking Account");
                assertThat(result.get(1).getName()).isEqualTo("Savings Account");

                verify(accountRepository).findByUserIdAndIsActive(userId, true);
        }

        @Test
        @DisplayName("Should sort account summaries by balance descending")
        void shouldSortAccountSummariesByBalanceDescending() {
                // Arrange
                List<Account> accounts = List.of(
                                createAccount(100L, "low_balance", new BigDecimal("1000")),
                                createAccount(101L, "high_balance", new BigDecimal("9000")),
                                createAccount(102L, "medium_balance", new BigDecimal("5000")));

                when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(accounts);

                // Act
                List<AccountSummary> result = dashboardService.getAccountSummaries(userId);

                // Assert
                assertThat(result).hasSize(3);
                assertThat(result.get(0).getBalance())
                                .isEqualByComparingTo(new BigDecimal("9000")); // Highest first
                assertThat(result.get(1).getBalance()).isEqualByComparingTo(new BigDecimal("5000"));
                assertThat(result.get(2).getBalance())
                                .isEqualByComparingTo(new BigDecimal("1000")); // Lowest last
        }

        @Test
        @DisplayName("Should return empty list when no accounts exist")
        void shouldReturnEmptyListWhenNoAccounts() {
                // Arrange
                when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(List.of());

                // Act
                List<AccountSummary> result = dashboardService.getAccountSummaries(userId);

                // Assert
                assertThat(result).isEmpty();
                verify(accountRepository).findByUserIdAndIsActive(userId, true);
        }

        // ==================== getRecentTransactions Tests ====================

        @Test
        @DisplayName("Should get recent transactions decrypted")
        void shouldGetRecentTransactionsDecrypted() {
                // Arrange
                int limit = 5;
                List<Transaction> transactions = List.of(
                                createTransaction(1L, "Description 1"),
                                createTransaction(2L, "Description 2"));

                TransactionResponse response1 = new TransactionResponse();
                response1.setId(1L);
                TransactionResponse response2 = new TransactionResponse();
                response2.setId(2L);

                org.springframework.data.domain.Page<Transaction> page = new org.springframework.data.domain.PageImpl<>(
                                transactions);
                when(transactionRepository.findByUserId(
                                eq(userId), any(org.springframework.data.domain.Pageable.class)))
                                .thenReturn(page);
                when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(response1, response2);

                // Act
                List<TransactionResponse> result = dashboardService.getRecentTransactions(userId, limit);

                // Assert
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getDescription()).isEqualTo("Description 1");
                assertThat(result.get(1).getDescription()).isEqualTo("Description 2");

                verify(transactionRepository)
                                .findByUserId(eq(userId), any(org.springframework.data.domain.Pageable.class));
                verify(transactionMapper, times(2)).toResponse(any(Transaction.class));
        }

        @Test
        @DisplayName("Should limit transactions to specified number")
        void shouldLimitTransactionsToSpecifiedNumber() {
                // Arrange
                int limit = 2;
                List<Transaction> transactions = List.of(
                                createTransaction(1L, "desc1"),
                                createTransaction(2L, "desc2"),
                                createTransaction(3L, "desc3"),
                                createTransaction(4L, "desc4"));

                org.springframework.data.domain.Page<Transaction> page = new org.springframework.data.domain.PageImpl<>(
                                transactions.subList(0, limit));
                when(transactionRepository.findByUserId(
                                eq(userId), any(org.springframework.data.domain.Pageable.class)))
                                .thenReturn(page);
                when(transactionMapper.toResponse(any(Transaction.class)))
                                .thenAnswer(
                                                inv -> {
                                                        TransactionResponse r = new TransactionResponse();
                                                        r.setId(((Transaction) inv.getArgument(0)).getId());
                                                        return r;
                                                });

                // Act
                List<TransactionResponse> result = dashboardService.getRecentTransactions(userId, limit);

                // Assert
                assertThat(result).hasSize(2); // Should be limited to 2
        }

        @Test
        @DisplayName("Should throw exception when limit is zero or negative")
        void shouldThrowExceptionWhenLimitIsZeroOrNegative() {
                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getRecentTransactions(userId, 0))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Limit must be positive");

                assertThatThrownBy(() -> dashboardService.getRecentTransactions(userId, -1))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Limit must be positive");
        }

        // ==================== getCashFlow Tests ====================

        @Test
        @DisplayName("Should calculate cash flow with income and expenses")
        void shouldCalculateCashFlowWithIncomeAndExpenses() {
                // Arrange
                int period = 30;
                LocalDate startDate = LocalDate.now().minusDays(period);
                LocalDate endDate = LocalDate.now();

                List<Transaction> transactions = List.of(
                                createTransaction(
                                                1L, TransactionType.INCOME, new BigDecimal("3000"), false),
                                createTransaction(2L, TransactionType.INCOME, new BigDecimal("500"), false),
                                createTransaction(
                                                3L, TransactionType.EXPENSE, new BigDecimal("1000"), false),
                                createTransaction(
                                                4L, TransactionType.EXPENSE, new BigDecimal("500"), false));

                when(transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate))
                                .thenReturn(transactions);

                // Act
                Map<String, BigDecimal> result = dashboardService.getCashFlow(userId, period);

                // Assert
                assertThat(result).containsKeys("income", "expenses", "netCashFlow");
                assertThat(result.get("income")).isEqualByComparingTo(new BigDecimal("3500"));
                assertThat(result.get("expenses")).isEqualByComparingTo(new BigDecimal("1500"));
                assertThat(result.get("netCashFlow")).isEqualByComparingTo(new BigDecimal("2000"));

                verify(transactionRepository).findByUserIdAndDateBetween(userId, startDate, endDate);
        }

        @Test
        @DisplayName("Should return zero when no transactions exist for cash flow")
        void shouldReturnZeroWhenNoTransactionsForCashFlow() {
                // Arrange
                int period = 30;
                LocalDate startDate = LocalDate.now().minusDays(period);
                LocalDate endDate = LocalDate.now();

                when(transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate))
                                .thenReturn(List.of());

                // Act
                Map<String, BigDecimal> result = dashboardService.getCashFlow(userId, period);

                // Assert
                assertThat(result.get("income")).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(result.get("expenses")).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(result.get("netCashFlow")).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should exclude deleted transactions from cash flow")
        void shouldExcludeDeletedTransactionsFromCashFlow() {
                // Arrange
                int period = 30;
                LocalDate startDate = LocalDate.now().minusDays(period);
                LocalDate endDate = LocalDate.now();

                List<Transaction> transactions = List.of(
                                createTransaction(
                                                1L,
                                                TransactionType.INCOME,
                                                new BigDecimal("1000"),
                                                false), // Active
                                createTransaction(
                                                2L,
                                                TransactionType.INCOME,
                                                new BigDecimal("500"),
                                                true), // Deleted -
                                // should be
                                // excluded
                                createTransaction(
                                                3L, TransactionType.EXPENSE, new BigDecimal("300"), false) // Active
                );

                when(transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate))
                                .thenReturn(transactions);

                // Act
                Map<String, BigDecimal> result = dashboardService.getCashFlow(userId, period);

                // Assert
                assertThat(result.get("income"))
                                .isEqualByComparingTo(new BigDecimal("1000")); // Only active income
                assertThat(result.get("expenses")).isEqualByComparingTo(new BigDecimal("300"));
                assertThat(result.get("netCashFlow")).isEqualByComparingTo(new BigDecimal("700"));
        }

        @Test
        @DisplayName("Should throw exception when period is zero or negative for cash flow")
        void shouldThrowExceptionWhenPeriodIsZeroOrNegativeForCashFlow() {
                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getCashFlow(userId, 0))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Period must be positive");

                assertThatThrownBy(() -> dashboardService.getCashFlow(userId, -10))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Period must be positive");
        }

        // ==================== getSpendingByCategory Tests ====================

        @Test
        @DisplayName("Should group spending by category and sort")
        void shouldGroupSpendingByCategoryAndSort() {
                // Arrange
                int period = 30;
                LocalDate startDate = LocalDate.now().minusDays(period);
                LocalDate endDate = LocalDate.now();

                List<Transaction> transactions = List.of(
                                createTransaction(
                                                1L,
                                                TransactionType.EXPENSE,
                                                new BigDecimal("500"),
                                                false,
                                                10L), // Category
                                // 10
                                createTransaction(
                                                2L,
                                                TransactionType.EXPENSE,
                                                new BigDecimal("300"),
                                                false,
                                                10L), // Category
                                // 10
                                createTransaction(
                                                3L,
                                                TransactionType.EXPENSE,
                                                new BigDecimal("1000"),
                                                false,
                                                20L), // Category
                                // 20
                                // (highest)
                                createTransaction(
                                                4L,
                                                TransactionType.EXPENSE,
                                                new BigDecimal("200"),
                                                false,
                                                30L), // Category
                                // 30
                                createTransaction(
                                                5L,
                                                TransactionType.INCOME,
                                                new BigDecimal("2000"),
                                                false,
                                                40L) // Income
                // -
                // excluded
                );

                when(transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate))
                                .thenReturn(transactions);

                // Act
                Map<String, BigDecimal> result = dashboardService.getSpendingByCategory(userId, period);

                // Assert
                assertThat(result).hasSize(3);
                assertThat(result).containsKeys("Category_20", "Category_10", "Category_30");
                assertThat(result.get("Category_20"))
                                .isEqualByComparingTo(new BigDecimal("1000")); // Highest
                assertThat(result.get("Category_10"))
                                .isEqualByComparingTo(new BigDecimal("800")); // 500 + 300
                assertThat(result.get("Category_30")).isEqualByComparingTo(new BigDecimal("200")); // Lowest

                // Verify sorting (highest first)
                List<String> keys = List.copyOf(result.keySet());
                assertThat(keys.get(0)).isEqualTo("Category_20"); // Highest spending first
        }

        @Test
        @DisplayName("Should handle uncategorized transactions")
        void shouldHandleUncategorizedTransactions() {
                // Arrange
                int period = 30;
                LocalDate startDate = LocalDate.now().minusDays(period);
                LocalDate endDate = LocalDate.now();

                List<Transaction> transactions = List.of(
                                createTransaction(
                                                1L,
                                                TransactionType.EXPENSE,
                                                new BigDecimal("500"),
                                                false,
                                                null), // Uncategorized
                                createTransaction(
                                                2L,
                                                TransactionType.EXPENSE,
                                                new BigDecimal("300"),
                                                false,
                                                null), // Uncategorized
                                createTransaction(
                                                3L,
                                                TransactionType.EXPENSE,
                                                new BigDecimal("200"),
                                                false,
                                                10L) // Categorized
                );

                when(transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate))
                                .thenReturn(transactions);

                // Act
                Map<String, BigDecimal> result = dashboardService.getSpendingByCategory(userId, period);

                // Assert
                assertThat(result).containsKeys("Uncategorized", "Category_10");
                assertThat(result.get("Uncategorized"))
                                .isEqualByComparingTo(new BigDecimal("800")); // 500 + 300
                assertThat(result.get("Category_10")).isEqualByComparingTo(new BigDecimal("200"));
        }

        @Test
        @DisplayName("Should return empty map when no expenses exist")
        void shouldReturnEmptyMapWhenNoExpenses() {
                // Arrange
                int period = 30;
                LocalDate startDate = LocalDate.now().minusDays(period);
                LocalDate endDate = LocalDate.now();

                when(transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate))
                                .thenReturn(List.of());

                // Act
                Map<String, BigDecimal> result = dashboardService.getSpendingByCategory(userId, period);

                // Assert
                assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when period is zero or negative for spending")
        void shouldThrowExceptionWhenPeriodIsZeroOrNegativeForSpending() {
                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getSpendingByCategory(userId, 0))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Period must be positive");

                assertThatThrownBy(() -> dashboardService.getSpendingByCategory(userId, -5))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Period must be positive");
        }

        // ==================== getAssetAllocation Tests ====================

        @Test
        @DisplayName("Should calculate asset allocation with multiple asset types")
        void shouldCalculateAssetAllocationWithMultipleTypes() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("EUR").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("100"),
                                                new BigDecimal("10"),
                                                new BigDecimal("15")), // Total: 1500
                                createAsset(
                                                2L,
                                                AssetType.STOCK,
                                                new BigDecimal("50"),
                                                new BigDecimal("20"),
                                                new BigDecimal("30")), // Total: 1500
                                createAsset(
                                                3L,
                                                AssetType.CRYPTO,
                                                new BigDecimal("2"),
                                                new BigDecimal("5000"),
                                                new BigDecimal("10000")), // Total: 20000
                                createAsset(
                                                4L,
                                                AssetType.BOND,
                                                new BigDecimal("10"),
                                                new BigDecimal("100"),
                                                new BigDecimal("105")) // Total: 1050
                );
                // Total portfolio value: 24,050

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                // Act
                List<org.openfinance.dto.AssetAllocation> result = dashboardService.getAssetAllocation(userId);

                // Assert
                assertThat(result).hasSize(3); // STOCK, CRYPTO, BOND

                // Verify CRYPTO allocation (highest)
                org.openfinance.dto.AssetAllocation cryptoAllocation = result.get(0);
                assertThat(cryptoAllocation.getType()).isEqualTo(AssetType.CRYPTO);
                assertThat(cryptoAllocation.getTypeName()).isEqualTo("Cryptocurrency");
                assertThat(cryptoAllocation.getTotalValue()).isEqualByComparingTo(new BigDecimal("20000"));
                assertThat(cryptoAllocation.getPercentage())
                                .isEqualByComparingTo(new BigDecimal("83.16")); // 20000/24050
                // * 100
                assertThat(cryptoAllocation.getAssetCount()).isEqualTo(1);
                assertThat(cryptoAllocation.getCurrency()).isEqualTo("EUR");

                // Verify STOCK allocation (second)
                org.openfinance.dto.AssetAllocation stockAllocation = result.get(1);
                assertThat(stockAllocation.getType()).isEqualTo(AssetType.STOCK);
                assertThat(stockAllocation.getTotalValue()).isEqualByComparingTo(new BigDecimal("3000"));
                assertThat(stockAllocation.getPercentage())
                                .isEqualByComparingTo(new BigDecimal("12.47")); // 3000/24050
                // * 100
                assertThat(stockAllocation.getAssetCount()).isEqualTo(2);

                // Verify BOND allocation (third)
                org.openfinance.dto.AssetAllocation bondAllocation = result.get(2);
                assertThat(bondAllocation.getType()).isEqualTo(AssetType.BOND);
                assertThat(bondAllocation.getTotalValue()).isEqualByComparingTo(new BigDecimal("1050"));
                assertThat(bondAllocation.getAssetCount()).isEqualTo(1);

                verify(assetRepository).findByUserId(userId);
                verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("Should return empty list when no assets exist for allocation")
        void shouldReturnEmptyListWhenNoAssetsForAllocation() {
                // Arrange
                when(assetRepository.findByUserId(userId)).thenReturn(List.of());

                // Act
                List<org.openfinance.dto.AssetAllocation> result = dashboardService.getAssetAllocation(userId);

                // Assert
                assertThat(result).isEmpty();
                verify(assetRepository).findByUserId(userId);
                verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("Should return empty list when total portfolio value is zero")
        void shouldReturnEmptyListWhenTotalValueIsZero() {
                // Arrange
                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO),
                                createAsset(
                                                2L,
                                                AssetType.CRYPTO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);

                // Act
                List<org.openfinance.dto.AssetAllocation> result = dashboardService.getAssetAllocation(userId);

                // Assert
                assertThat(result).isEmpty();
                verify(assetRepository).findByUserId(userId);
        }

        @Test
        @DisplayName("Should handle single asset type allocation")
        void shouldHandleSingleAssetTypeAllocation() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("USD").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("100"),
                                                new BigDecimal("50"),
                                                new BigDecimal("55")),
                                createAsset(
                                                2L,
                                                AssetType.STOCK,
                                                new BigDecimal("50"),
                                                new BigDecimal("100"),
                                                new BigDecimal("110")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                // Act
                List<org.openfinance.dto.AssetAllocation> result = dashboardService.getAssetAllocation(userId);

                // Assert
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getType()).isEqualTo(AssetType.STOCK);
                assertThat(result.get(0).getTotalValue())
                                .isEqualByComparingTo(new BigDecimal("11000")); // 100*55 +
                // 50*110
                assertThat(result.get(0).getPercentage()).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(result.get(0).getAssetCount()).isEqualTo(2);
                assertThat(result.get(0).getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("Should use default currency when user base currency is null")
        void shouldUseDefaultCurrencyWhenBaseCurrencyIsNull() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency(null).build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("10"),
                                                new BigDecimal("50"),
                                                new BigDecimal("60")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                // Act
                List<org.openfinance.dto.AssetAllocation> result = dashboardService.getAssetAllocation(userId);

                // Assert
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getCurrency()).isEqualTo("EUR"); // Default fallback
        }

        @Test
        @DisplayName("Should sort asset allocation by total value descending")
        void shouldSortAssetAllocationByValueDescending() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("EUR").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.BOND,
                                                new BigDecimal("10"),
                                                new BigDecimal("100"),
                                                new BigDecimal("100")), // 1000 (third)
                                createAsset(
                                                2L,
                                                AssetType.CRYPTO,
                                                new BigDecimal("5"),
                                                new BigDecimal("1000"),
                                                new BigDecimal("2000")), // 10000 (first)
                                createAsset(
                                                3L,
                                                AssetType.STOCK,
                                                new BigDecimal("50"),
                                                new BigDecimal("50"),
                                                new BigDecimal("100")) // 5000 (second)
                );

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                // Act
                List<org.openfinance.dto.AssetAllocation> result = dashboardService.getAssetAllocation(userId);

                // Assert
                assertThat(result).hasSize(3);
                assertThat(result.get(0).getType()).isEqualTo(AssetType.CRYPTO); // Highest
                assertThat(result.get(0).getTotalValue()).isEqualByComparingTo(new BigDecimal("10000"));
                assertThat(result.get(1).getType()).isEqualTo(AssetType.STOCK);
                assertThat(result.get(1).getTotalValue()).isEqualByComparingTo(new BigDecimal("5000"));
                assertThat(result.get(2).getType()).isEqualTo(AssetType.BOND); // Lowest
                assertThat(result.get(2).getTotalValue()).isEqualByComparingTo(new BigDecimal("1000"));
        }

        @Test
        @DisplayName("Should throw exception when user ID is null for asset allocation")
        void shouldThrowExceptionWhenUserIdIsNullForAllocation() {
                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getAssetAllocation(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User ID cannot be null");

                verifyNoInteractions(assetRepository, userRepository);
        }

        @Test
        @DisplayName("Should throw exception when user not found for asset allocation")
        void shouldThrowExceptionWhenUserNotFoundForAllocation() {
                // Arrange
                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("10"),
                                                new BigDecimal("50"),
                                                new BigDecimal("60")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getAssetAllocation(userId))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User not found with ID: " + userId);

                verify(assetRepository).findByUserId(userId);
                verify(userRepository).findById(userId);
        }

        // ==================== getPortfolioPerformance Tests ====================

        @Test
        @DisplayName("Should calculate portfolio performance with historical data")
        void shouldCalculatePortfolioPerformanceWithHistoricalData() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("EUR").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("100"),
                                                new BigDecimal("50"),
                                                new BigDecimal("60")), // Cost: 5000, Value: 6000
                                createAsset(
                                                2L,
                                                AssetType.CRYPTO,
                                                new BigDecimal("2"),
                                                new BigDecimal("5000"),
                                                new BigDecimal("7000")) // Cost: 10000, Value: 14000
                );
                // Total cost: 15000, Total value: 20000, Gain: 5000 (33.33%)

                List<NetWorth> netWorthHistory = List.of(
                                createNetWorthWithDate(
                                                LocalDate.now().minusDays(30), new BigDecimal("18000")),
                                createNetWorthWithDate(
                                                LocalDate.now().minusDays(15), new BigDecimal("19000")),
                                createNetWorthWithDate(LocalDate.now(), new BigDecimal("20000")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(netWorthService.getNetWorthHistory(
                                eq(userId), any(LocalDate.class), any(LocalDate.class)))
                                .thenReturn(netWorthHistory);

                // Act
                List<org.openfinance.dto.PortfolioPerformance> result = dashboardService.getPortfolioPerformance(userId,
                                365);

                // Assert
                assertThat(result).hasSize(3);

                // Verify Total Value metric
                org.openfinance.dto.PortfolioPerformance totalValue = result.get(0);
                assertThat(totalValue.getLabel()).isEqualTo("Total Value");
                assertThat(totalValue.getCurrentValue()).isEqualByComparingTo(new BigDecimal("20000"));
                assertThat(totalValue.getChangeAmount())
                                .isEqualByComparingTo(new BigDecimal("2000")); // 20000 - 18000
                assertThat(totalValue.getChangePercentage())
                                .isEqualByComparingTo(new BigDecimal("11.11")); // 2000/18000
                // * 100
                assertThat(totalValue.getCurrency()).isEqualTo("EUR");
                assertThat(totalValue.getSparklineData()).hasSize(3);

                // Verify Unrealized Gain metric
                org.openfinance.dto.PortfolioPerformance unrealizedGain = result.get(1);
                assertThat(unrealizedGain.getLabel()).isEqualTo("Unrealized Gain");
                assertThat(unrealizedGain.getCurrentValue()).isEqualByComparingTo(new BigDecimal("5000"));
                assertThat(unrealizedGain.getChangePercentage())
                                .isEqualByComparingTo(new BigDecimal("33.33")); // 5000/15000
                // * 100

                // Verify Cost Basis metric
                org.openfinance.dto.PortfolioPerformance costBasis = result.get(2);
                assertThat(costBasis.getLabel()).isEqualTo("Cost Basis");
                assertThat(costBasis.getCurrentValue()).isEqualByComparingTo(new BigDecimal("15000"));
                assertThat(costBasis.getChangeAmount()).isEqualByComparingTo(BigDecimal.ZERO);

                verify(assetRepository).findByUserId(userId);
                verify(userRepository).findById(userId);
                verify(netWorthService)
                                .getNetWorthHistory(eq(userId), any(LocalDate.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("Should return empty list when no assets exist for performance")
        void shouldReturnEmptyListWhenNoAssetsForPerformance() {
                // Arrange
                when(assetRepository.findByUserId(userId)).thenReturn(List.of());

                // Act
                List<org.openfinance.dto.PortfolioPerformance> result = dashboardService.getPortfolioPerformance(userId,
                                365);

                // Assert
                assertThat(result).isEmpty();
                verify(assetRepository).findByUserId(userId);
                verifyNoInteractions(userRepository, netWorthService);
        }

        @Test
        @DisplayName("Should handle zero cost basis for performance")
        void shouldHandleZeroCostBasisForPerformance() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("EUR").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                new BigDecimal("100")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(netWorthService.getNetWorthHistory(
                                eq(userId), any(LocalDate.class), any(LocalDate.class)))
                                .thenReturn(List.of());

                // Act
                List<org.openfinance.dto.PortfolioPerformance> result = dashboardService.getPortfolioPerformance(userId,
                                365);

                // Assert
                assertThat(result).hasSize(3);
                org.openfinance.dto.PortfolioPerformance unrealizedGain = result.get(1);
                assertThat(unrealizedGain.getChangePercentage())
                                .isEqualByComparingTo(BigDecimal.ZERO); // Avoid
                // division by
                // zero
        }

        @Test
        @DisplayName("Should handle no historical data for sparklines")
        void shouldHandleNoHistoricalDataForSparklines() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("USD").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("10"),
                                                new BigDecimal("50"),
                                                new BigDecimal("60")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(netWorthService.getNetWorthHistory(
                                eq(userId), any(LocalDate.class), any(LocalDate.class)))
                                .thenReturn(List.of());

                // Act
                List<org.openfinance.dto.PortfolioPerformance> result = dashboardService.getPortfolioPerformance(userId,
                                365);

                // Assert
                assertThat(result).hasSize(3);
                org.openfinance.dto.PortfolioPerformance totalValue = result.get(0);
                assertThat(totalValue.getSparklineData()).isEmpty();
                assertThat(totalValue.getChangeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(totalValue.getChangePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle single data point in historical data")
        void shouldHandleSingleDataPointInHistory() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("EUR").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("10"),
                                                new BigDecimal("50"),
                                                new BigDecimal("60")));

                List<NetWorth> netWorthHistory = List
                                .of(createNetWorthWithDate(LocalDate.now(), new BigDecimal("600")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(netWorthService.getNetWorthHistory(
                                eq(userId), any(LocalDate.class), any(LocalDate.class)))
                                .thenReturn(netWorthHistory);

                // Act
                List<org.openfinance.dto.PortfolioPerformance> result = dashboardService.getPortfolioPerformance(userId,
                                365);

                // Assert
                assertThat(result).hasSize(3);
                org.openfinance.dto.PortfolioPerformance totalValue = result.get(0);
                assertThat(totalValue.getSparklineData()).hasSize(1);
                assertThat(totalValue.getChangeAmount())
                                .isEqualByComparingTo(BigDecimal.ZERO); // Need at least 2
                // points
                assertThat(totalValue.getChangePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should calculate negative performance when value decreased")
        void shouldCalculateNegativePerformanceWhenValueDecreased() {
                // Arrange
                User user = User.builder().id(userId).username("testuser").baseCurrency("EUR").build();

                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("100"),
                                                new BigDecimal("100"),
                                                new BigDecimal("80")) // Cost: 10000, Value: 8000, Loss: -2000
                );

                List<NetWorth> netWorthHistory = List.of(
                                createNetWorthWithDate(
                                                LocalDate.now().minusDays(30), new BigDecimal("10000")),
                                createNetWorthWithDate(LocalDate.now(), new BigDecimal("8000")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(netWorthService.getNetWorthHistory(
                                eq(userId), any(LocalDate.class), any(LocalDate.class)))
                                .thenReturn(netWorthHistory);

                // Act
                List<org.openfinance.dto.PortfolioPerformance> result = dashboardService.getPortfolioPerformance(userId,
                                365);

                // Assert
                org.openfinance.dto.PortfolioPerformance totalValue = result.get(0);
                assertThat(totalValue.getChangeAmount()).isEqualByComparingTo(new BigDecimal("-2000"));
                assertThat(totalValue.getChangePercentage())
                                .isEqualByComparingTo(new BigDecimal("-20.00")); // -2000/10000
                // * 100

                org.openfinance.dto.PortfolioPerformance unrealizedGain = result.get(1);
                assertThat(unrealizedGain.getCurrentValue()).isEqualByComparingTo(new BigDecimal("-2000"));
                assertThat(unrealizedGain.getChangePercentage())
                                .isEqualByComparingTo(new BigDecimal("-20.00"));
        }

        @Test
        @DisplayName("Should throw exception when user ID is null for portfolio performance")
        void shouldThrowExceptionWhenUserIdIsNullForPerformance() {
                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getPortfolioPerformance(null, 365))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User ID cannot be null");

                verifyNoInteractions(assetRepository, userRepository, netWorthService);
        }

        @Test
        @DisplayName("Should throw exception when user not found for portfolio performance")
        void shouldThrowExceptionWhenUserNotFoundForPerformance() {
                // Arrange
                List<Asset> assets = List.of(
                                createAsset(
                                                1L,
                                                AssetType.STOCK,
                                                new BigDecimal("10"),
                                                new BigDecimal("50"),
                                                new BigDecimal("60")));

                when(assetRepository.findByUserId(userId)).thenReturn(assets);
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                // Act & Assert
                assertThatThrownBy(() -> dashboardService.getPortfolioPerformance(userId, 365))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("User not found with ID: " + userId);

                verify(assetRepository).findByUserId(userId);
                verify(userRepository).findById(userId);
        }

        // ==================== Helper Methods ====================

        private Account createAccount(Long id, String name, BigDecimal balance) {
                return Account.builder()
                                .id(id)
                                .userId(userId)
                                .name(name)
                                .type(AccountType.CHECKING)
                                .balance(balance)
                                .currency("EUR")
                                .isActive(true)
                                .build();
        }

        private NetWorth createNetWorth() {
                return NetWorth.builder()
                                .id(1L)
                                .userId(userId)
                                .snapshotDate(LocalDate.now())
                                .totalAssets(new BigDecimal("10000"))
                                .totalLiabilities(new BigDecimal("2000"))
                                .netWorth(new BigDecimal("8000"))
                                .currency("EUR")
                                .build();
        }

        private Transaction createTransaction(Long id, String description) {
                return Transaction.builder()
                                .id(id)
                                .userId(userId)
                                .accountId(100L)
                                .type(TransactionType.EXPENSE)
                                .amount(new BigDecimal("100"))
                                .date(LocalDate.now())
                                .description(description)
                                .isDeleted(false)
                                .build();
        }

        private Transaction createTransaction(
                        Long id, TransactionType type, BigDecimal amount, boolean isDeleted) {
                return Transaction.builder()
                                .id(id)
                                .userId(userId)
                                .accountId(100L)
                                .type(type)
                                .amount(amount)
                                .date(LocalDate.now())
                                .isDeleted(isDeleted)
                                .build();
        }

        private Transaction createTransaction(
                        Long id, TransactionType type, BigDecimal amount, boolean isDeleted, Long categoryId) {
                return Transaction.builder()
                                .id(id)
                                .userId(userId)
                                .accountId(100L)
                                .type(type)
                                .amount(amount)
                                .date(LocalDate.now())
                                .categoryId(categoryId)
                                .isDeleted(isDeleted)
                                .build();
        }

        private Asset createAsset(
                        Long id,
                        AssetType type,
                        BigDecimal quantity,
                        BigDecimal purchasePrice,
                        BigDecimal currentPrice) {
                return Asset.builder()
                                .id(id)
                                .userId(userId)
                                .type(type)
                                .name("Asset_" + id)
                                .symbol("SYM" + id)
                                .quantity(quantity)
                                .purchasePrice(purchasePrice)
                                .currentPrice(currentPrice)
                                .currency("EUR")
                                .build();
        }

        private NetWorth createNetWorthWithDate(LocalDate date, BigDecimal totalAssets) {
                return NetWorth.builder()
                                .id(1L)
                                .userId(userId)
                                .snapshotDate(date)
                                .totalAssets(totalAssets)
                                .totalLiabilities(BigDecimal.ZERO)
                                .netWorth(totalAssets)
                                .currency("EUR")
                                .build();
        }
}
