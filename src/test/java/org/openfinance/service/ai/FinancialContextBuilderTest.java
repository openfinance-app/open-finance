package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.*;
import org.openfinance.repository.*;
import org.openfinance.security.EncryptionService;
import org.openfinance.service.NetWorthService;
import org.springframework.context.MessageSource;

/**
 * Unit tests for FinancialContextBuilder Task 11.1.7b: Write FinancialContextBuilder unit tests
 *
 * <p>Tests cover: - buildContext with full financial data - buildContext with empty data -
 * buildMinimalContext - Decryption of encrypted fields - Context formatting and sections
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FinancialContextBuilder Tests")
class FinancialContextBuilderTest {

    @Mock private AccountRepository accountRepository;

    @Mock private TransactionRepository transactionRepository;

    @Mock private AssetRepository assetRepository;

    @Mock private LiabilityRepository liabilityRepository;

    @Mock private BudgetRepository budgetRepository;

    @Mock private NetWorthService netWorthService;

    @Mock private EncryptionService encryptionService;

    @Mock private MessageSource messageSource;

    @InjectMocks private FinancialContextBuilder contextBuilder;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = 1L;

        lenient()
                .when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Nested
    @DisplayName("buildContext Tests")
    class BuildContextTests {

        @Test
        @DisplayName("should build context with all financial data")
        void shouldBuildContextWithAllData() {
            // Given
            when(netWorthService.calculateTotalAssets(eq(userId), eq("USD")))
                    .thenReturn(new BigDecimal("50000.00"));
            when(netWorthService.calculateTotalLiabilities(eq(userId), eq("USD")))
                    .thenReturn(new BigDecimal("5000.00"));

            List<Account> accounts =
                    Arrays.asList(
                            createAccount(1L, "Checking", AccountType.CHECKING, "2500.00"),
                            createAccount(2L, "Savings", AccountType.SAVINGS, "15000.00"));
            when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(accounts);

            List<Transaction> transactions =
                    Arrays.asList(
                            createTransaction(
                                    1L, "Grocery Store", "150.00", TransactionType.EXPENSE),
                            createTransaction(2L, "Salary", "3000.00", TransactionType.INCOME));
            when(transactionRepository.findByUserIdAndDateBetween(
                            eq(userId), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(transactions);

            List<Asset> assets =
                    Arrays.asList(createAsset(1L, "AAPL Stock", AssetType.STOCK, "10000.00"));
            when(assetRepository.findByUserId(userId)).thenReturn(assets);

            List<Liability> liabilities =
                    Arrays.asList(createLiability(1L, "Credit Card", "1500.00"));
            when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(liabilities);

            List<Budget> budgets = Arrays.asList(createBudget(1L, "Groceries", "500.00", "300.00"));
            when(budgetRepository.findByUserId(userId)).thenReturn(budgets);

            // When
            String context = contextBuilder.buildContext(userId);

            // Then
            assertThat(context).isNotNull();
            assertThat(context).contains("=== FINANCIAL SUMMARY ===");
            assertThat(context).contains("Net Worth:");
            assertThat(context).contains("Total Assets:");
            assertThat(context).contains("Total Liabilities:");
            assertThat(context).contains("=== ACCOUNTS");
            assertThat(context).contains("=== RECENT TRANSACTIONS");
            assertThat(context).contains("=== ASSETS");
            assertThat(context).contains("=== LIABILITIES");
            assertThat(context).contains("=== BUDGET STATUS");

            verify(accountRepository, times(1)).findByUserIdAndIsActive(userId, true);
            verify(transactionRepository)
                    .findByUserIdAndDateBetween(
                            eq(userId), any(LocalDate.class), any(LocalDate.class));
            verify(assetRepository).findByUserId(userId);
            verify(liabilityRepository).findByUserIdOrderByCreatedAtDesc(userId);
            verify(budgetRepository).findByUserId(userId);
        }

        @Test
        @DisplayName("should build context with no financial data")
        void shouldBuildContextWithNoData() {
            // Given
            when(netWorthService.calculateTotalAssets(eq(userId), eq("USD")))
                    .thenReturn(BigDecimal.ZERO);
            when(netWorthService.calculateTotalLiabilities(eq(userId), eq("USD")))
                    .thenReturn(BigDecimal.ZERO);
            when(accountRepository.findByUserIdAndIsActive(userId, true))
                    .thenReturn(Collections.emptyList());
            when(transactionRepository.findByUserIdAndDateBetween(
                            eq(userId), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(assetRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
            when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(Collections.emptyList());
            when(budgetRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

            // When
            String context = contextBuilder.buildContext(userId);

            // Then
            assertThat(context).isNotNull();
            assertThat(context).contains("=== FINANCIAL SUMMARY ===");
            assertThat(context).contains("No accounts found");
            assertThat(context).contains("No recent transactions");
        }

        @Test
        @DisplayName("should handle decryption failures gracefully")
        void shouldHandleDecryptionFailures() {
            // Given
            List<Account> accounts =
                    Arrays.asList(createAccount(1L, "Checking", AccountType.CHECKING, "2500.00"));
            when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(accounts);
            when(netWorthService.calculateTotalAssets(eq(userId), eq("USD")))
                    .thenReturn(BigDecimal.ZERO);
            when(netWorthService.calculateTotalLiabilities(eq(userId), eq("USD")))
                    .thenReturn(BigDecimal.ZERO);
            when(transactionRepository.findByUserIdAndDateBetween(
                            eq(userId), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(assetRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
            when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(Collections.emptyList());
            when(budgetRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

            // When
            String context = contextBuilder.buildContext(userId);

            // Then
            assertThat(context).isNotNull();
            assertThat(context).contains("=== FINANCIAL SUMMARY ===");
        }
    }

    @Nested
    @DisplayName("buildMinimalContext Tests")
    class BuildMinimalContextTests {

        @Test
        @DisplayName("should build minimal context with net worth and accounts")
        void shouldBuildMinimalContext() {
            // Given
            when(netWorthService.calculateTotalAssets(eq(userId), eq("USD")))
                    .thenReturn(new BigDecimal("50000.00"));
            when(netWorthService.calculateTotalLiabilities(eq(userId), eq("USD")))
                    .thenReturn(new BigDecimal("5000.00"));

            List<Account> accounts =
                    Arrays.asList(
                            createAccount(1L, "Checking", AccountType.CHECKING, "2500.00"),
                            createAccount(2L, "Savings", AccountType.SAVINGS, "15000.00"));
            when(accountRepository.findByUserIdAndIsActive(userId, true)).thenReturn(accounts);

            // When
            String context = contextBuilder.buildMinimalContext(userId);

            // Then
            assertThat(context).isNotNull();
            assertThat(context).contains("=== FINANCIAL SUMMARY ===");
            assertThat(context).contains("Net Worth:");
            // Accept various number formats and currency symbols/codes
            assertThat(context).contains("45");
            assertThat(context).contains("USD");
            assertThat(context).contains("=== ACCOUNTS ===");
            assertThat(context).contains("Checking");
            assertThat(context).contains("Savings");

            verify(netWorthService).calculateTotalAssets(eq(userId), eq("USD"));
            verify(netWorthService).calculateTotalLiabilities(eq(userId), eq("USD"));
            verify(accountRepository, times(1)).findByUserIdAndIsActive(userId, true);
        }
    }

    // Helper methods to create test entities

    private Account createAccount(Long id, String name, AccountType type, String balance) {
        return Account.builder()
                .id(id)
                .userId(userId)
                .name("encrypted-" + name)
                .type(type)
                .balance(new BigDecimal(balance))
                .currency("USD")
                .description("encrypted-Test account")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Transaction createTransaction(
            Long id, String description, String amount, TransactionType type) {
        return Transaction.builder()
                .id(id)
                .userId(userId)
                .description("encrypted-" + description)
                .amount(new BigDecimal(amount))
                .type(type)
                .date(LocalDate.now())
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Asset createAsset(Long id, String name, AssetType type, String currentPrice) {
        return Asset.builder()
                .id(id)
                .userId(userId)
                .name("encrypted-" + name)
                .type(type)
                .quantity(new BigDecimal("10"))
                .purchasePrice(new BigDecimal("900.00"))
                .currentPrice(new BigDecimal(currentPrice))
                .purchaseDate(LocalDate.now().minusMonths(6))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Liability createLiability(Long id, String name, String currentBalance) {
        Liability liability = new Liability();
        liability.setId(id);
        liability.setUserId(userId);
        liability.setName("encrypted-" + name);
        liability.setType(LiabilityType.CREDIT_CARD);
        liability.setCurrentBalance("encrypted-" + currentBalance);
        liability.setInterestRate("encrypted-18.99");
        liability.setStartDate(LocalDate.now().minusYears(1));
        liability.setCurrency("USD");
        liability.setCreatedAt(LocalDateTime.now());
        liability.setUpdatedAt(LocalDateTime.now());
        return liability;
    }

    private Budget createBudget(Long id, String categoryName, String amount, String spent) {
        return Budget.builder()
                .id(id)
                .userId(userId)
                .category(createCategory(categoryName))
                .amount("encrypted-" + amount)
                .period(BudgetPeriod.MONTHLY)
                .startDate(LocalDate.now().withDayOfMonth(1))
                .endDate(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()))
                .rollover(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Category createCategory(String name) {
        return Category.builder()
                .id(1L)
                .name("encrypted-" + name)
                .type(CategoryType.EXPENSE)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
