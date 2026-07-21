package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.NetWorth;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.NetWorthRepository;

/**
 * Unit tests for NetWorthService.
 *
 * <p>Tests cover: - Net worth calculation (assets - liabilities) - Snapshot creation and updates -
 * Historical data retrieval - Monthly change calculation - Edge cases (no data, negative balances,
 * etc.)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NetWorthService Unit Tests")
class NetWorthServiceTest {

    @Mock private NetWorthRepository netWorthRepository;

    @Mock private AccountRepository accountRepository;

    @Mock private org.openfinance.repository.AssetRepository assetRepository;

    @Mock private org.openfinance.repository.LiabilityRepository liabilityRepository;

    @Mock private org.openfinance.repository.RealEstateRepository realEstateRepository;

    @Mock
    private org.openfinance.repository.RealEstateValueHistoryRepository
            realEstateValueHistoryRepository;

    @Mock private org.openfinance.security.EncryptionService encryptionService;

    @Mock private ExchangeRateService exchangeRateService;

    @Mock private org.openfinance.repository.TransactionRepository transactionRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @Mock private CurrencyRepository currencyRepository;

    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;

    @InjectMocks private NetWorthService netWorthService;

    private Long testUserId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testDate = LocalDate.of(2026, 1, 31);

        // Mock empty lists by default to prevent NullPointerException
        when(assetRepository.findByUserId(any())).thenReturn(java.util.Collections.emptyList());
        when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(any()))
                .thenReturn(java.util.Collections.emptyList());
        when(realEstateRepository.findByUserIdAndIsActive(any(), eq(true)))
                .thenReturn(java.util.Collections.emptyList());
        // Mock exchange rate service to return amount unchanged (identity conversion)
        when(exchangeRateService.convert(any(BigDecimal.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.openfinance.testutil.DefaultCurrencyProviderMocks.stub(defaultCurrencyProvider);
    }

    // ==================== calculateNetWorth Tests ====================

    @Test
    @DisplayName("Should calculate net worth with positive account balances")
    void shouldCalculateNetWorthWithPositiveBalances() {
        // Arrange
        List<Account> accounts =
                List.of(
                        createAccount(100L, AccountType.CHECKING, new BigDecimal("1000.00")),
                        createAccount(101L, AccountType.SAVINGS, new BigDecimal("5000.00")),
                        createAccount(102L, AccountType.INVESTMENT, new BigDecimal("10000.00")));

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);

        // Act
        BigDecimal netWorth = netWorthService.calculateNetWorth(testUserId, testDate);

        // Assert
        assertThat(netWorth).isEqualByComparingTo(new BigDecimal("16000.00"));
        verify(accountRepository, times(2)).findByUserIdAndIsActive(testUserId, true);
    }

    @Test
    @DisplayName("Should calculate net worth with mixed positive and negative balances")
    void shouldCalculateNetWorthWithMixedBalances() {
        // Arrange
        List<Account> accounts =
                List.of(
                        createAccount(100L, AccountType.CHECKING, new BigDecimal("2000.00")),
                        createAccount(101L, AccountType.SAVINGS, new BigDecimal("8000.00")),
                        createAccount(
                                102L, AccountType.CREDIT_CARD, new BigDecimal("-1500.00")), // Debt
                        createAccount(
                                103L, AccountType.CREDIT_CARD, new BigDecimal("-500.00")) // Debt
                        );

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);

        // Act
        BigDecimal netWorth = netWorthService.calculateNetWorth(testUserId, testDate);

        // Assert: (2000 + 8000) - (1500 + 500) = 8000
        assertThat(netWorth).isEqualByComparingTo(new BigDecimal("8000.00"));
    }

    @Test
    @DisplayName("Should calculate net worth as zero when no accounts exist")
    void shouldCalculateZeroNetWorthWhenNoAccounts() {
        // Arrange
        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(List.of());

        // Act
        BigDecimal netWorth = netWorthService.calculateNetWorth(testUserId, testDate);

        // Assert
        assertThat(netWorth).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate negative net worth when liabilities exceed assets")
    void shouldCalculateNegativeNetWorth() {
        // Arrange
        List<Account> accounts =
                List.of(
                        createAccount(100L, AccountType.CHECKING, new BigDecimal("500.00")),
                        createAccount(101L, AccountType.CREDIT_CARD, new BigDecimal("-3000.00")));

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);

        // Act
        BigDecimal netWorth = netWorthService.calculateNetWorth(testUserId, testDate);

        // Assert: 500 - 3000 = -2500
        assertThat(netWorth).isEqualByComparingTo(new BigDecimal("-2500.00"));
    }

    @Test
    @DisplayName("Should throw exception when userId is null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> netWorthService.calculateNetWorth(null, testDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when date is null")
    void shouldThrowExceptionWhenDateIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> netWorthService.calculateNetWorth(testUserId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Date cannot be null");
    }

    // ==================== calculateTotalAssets Tests ====================

    @Test
    @DisplayName("Should calculate total assets from positive balances only")
    void shouldCalculateTotalAssetsFromPositiveBalances() {
        // Arrange
        List<Account> accounts =
                List.of(
                        createAccount(100L, AccountType.CHECKING, new BigDecimal("1500.00")),
                        createAccount(101L, AccountType.SAVINGS, new BigDecimal("3500.00")),
                        createAccount(
                                102L,
                                AccountType.CREDIT_CARD,
                                new BigDecimal("-1000.00")) // Should be excluded
                        );

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);

        // Act
        BigDecimal totalAssets = netWorthService.calculateTotalAssets(testUserId);

        // Assert: Only 1500 + 3500 = 5000 (negative balance excluded)
        assertThat(totalAssets).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    // ==================== calculateTotalLiabilities Tests ====================

    @Test
    @DisplayName("Should calculate total liabilities from negative balances")
    void shouldCalculateTotalLiabilitiesFromNegativeBalances() {
        // Arrange
        List<Account> accounts =
                List.of(
                        createAccount(
                                100L,
                                AccountType.CHECKING,
                                new BigDecimal("2000.00")), // Should be excluded
                        createAccount(101L, AccountType.CREDIT_CARD, new BigDecimal("-1200.00")),
                        createAccount(102L, AccountType.CREDIT_CARD, new BigDecimal("-800.00")));

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);

        // Act
        BigDecimal totalLiabilities = netWorthService.calculateTotalLiabilities(testUserId);

        // Assert: 1200 + 800 = 2000 (converted to positive)
        assertThat(totalLiabilities).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("Should calculate zero liabilities when no negative balances exist")
    void shouldCalculateZeroLiabilitiesWhenNoNegativeBalances() {
        // Arrange
        List<Account> accounts =
                List.of(
                        createAccount(100L, AccountType.CHECKING, new BigDecimal("1000.00")),
                        createAccount(101L, AccountType.SAVINGS, new BigDecimal("5000.00")));

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);

        // Act
        BigDecimal totalLiabilities = netWorthService.calculateTotalLiabilities(testUserId);

        // Assert
        assertThat(totalLiabilities).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ==================== saveNetWorthSnapshot Tests ====================

    @Test
    @DisplayName("Should create new snapshot when none exists for date")
    void shouldCreateNewSnapshotWhenNoneExists() {
        // Arrange
        List<Account> accounts =
                List.of(createAccount(100L, AccountType.CHECKING, new BigDecimal("5000.00")));

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);
        when(netWorthRepository.findByUserIdAndSnapshotDate(testUserId, testDate))
                .thenReturn(Optional.empty());

        NetWorth savedSnapshot =
                createNetWorth(
                        1L,
                        testUserId,
                        testDate,
                        new BigDecimal("5000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.00"));

        when(netWorthRepository.save(any(NetWorth.class))).thenReturn(savedSnapshot);

        // Act
        NetWorth result = netWorthService.saveNetWorthSnapshot(testUserId, testDate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(testUserId);
        assertThat(result.getSnapshotDate()).isEqualTo(testDate);
        assertThat(result.getTotalAssets()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.getTotalLiabilities()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getNetWorth()).isEqualByComparingTo(new BigDecimal("5000.00"));

        verify(netWorthRepository).save(any(NetWorth.class));
    }

    @Test
    @DisplayName("Should update existing snapshot when one exists for date")
    void shouldUpdateExistingSnapshotWhenExists() {
        // Arrange
        List<Account> accounts =
                List.of(createAccount(100L, AccountType.CHECKING, new BigDecimal("7000.00")));

        NetWorth existingSnapshot =
                createNetWorth(
                        1L,
                        testUserId,
                        testDate,
                        new BigDecimal("5000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.00"));

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);
        when(netWorthRepository.findByUserIdAndSnapshotDate(testUserId, testDate))
                .thenReturn(Optional.of(existingSnapshot));

        NetWorth updatedSnapshot =
                createNetWorth(
                        1L,
                        testUserId,
                        testDate,
                        new BigDecimal("7000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("7000.00"));

        when(netWorthRepository.save(any(NetWorth.class))).thenReturn(updatedSnapshot);

        // Act
        NetWorth result = netWorthService.saveNetWorthSnapshot(testUserId, testDate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L); // Same ID (updated, not created)
        assertThat(result.getNetWorth()).isEqualByComparingTo(new BigDecimal("7000.00"));

        verify(netWorthRepository).save(existingSnapshot); // Updated the existing entity
    }

    @Test
    @DisplayName("Should save snapshot for current date when date not specified")
    void shouldSaveSnapshotForCurrentDate() {
        // Arrange
        LocalDate today = LocalDate.now();
        List<Account> accounts =
                List.of(createAccount(100L, AccountType.CHECKING, new BigDecimal("3000.00")));

        when(accountRepository.findByUserIdAndIsActive(testUserId, true)).thenReturn(accounts);
        when(netWorthRepository.findByUserIdAndSnapshotDate(eq(testUserId), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        NetWorth savedSnapshot =
                createNetWorth(
                        1L,
                        testUserId,
                        today,
                        new BigDecimal("3000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("3000.00"));

        when(netWorthRepository.save(any(NetWorth.class))).thenReturn(savedSnapshot);

        // Act
        NetWorth result = netWorthService.saveNetWorthSnapshot(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getSnapshotDate()).isEqualTo(today);
        verify(netWorthRepository).save(any(NetWorth.class));
    }

    // ==================== getNetWorthHistory Tests ====================

    @Test
    @DisplayName("Should retrieve net worth history for date range")
    void shouldRetrieveNetWorthHistoryForDateRange() {
        // Arrange
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);

        List<NetWorth> history =
                List.of(
                        createNetWorth(
                                1L,
                                testUserId,
                                LocalDate.of(2026, 1, 1),
                                new BigDecimal("5000.00"),
                                BigDecimal.ZERO,
                                new BigDecimal("5000.00")),
                        createNetWorth(
                                2L,
                                testUserId,
                                LocalDate.of(2026, 1, 15),
                                new BigDecimal("6000.00"),
                                BigDecimal.ZERO,
                                new BigDecimal("6000.00")),
                        createNetWorth(
                                3L,
                                testUserId,
                                LocalDate.of(2026, 1, 31),
                                new BigDecimal("7000.00"),
                                BigDecimal.ZERO,
                                new BigDecimal("7000.00")));

        when(netWorthRepository.findByUserIdAndDateRange(testUserId, startDate, endDate))
                .thenReturn(history);

        // Act
        List<NetWorth> result = netWorthService.getNetWorthHistory(testUserId, startDate, endDate);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getSnapshotDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(1).getSnapshotDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.get(2).getSnapshotDate()).isEqualTo(LocalDate.of(2026, 1, 31));

        verify(netWorthRepository).findByUserIdAndDateRange(testUserId, startDate, endDate);
    }

    @Test
    @DisplayName("Should throw exception when start date is after end date")
    void shouldThrowExceptionWhenStartDateIsAfterEndDate() {
        // Arrange
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 1);

        // Act & Assert
        assertThatThrownBy(() -> netWorthService.getNetWorthHistory(testUserId, startDate, endDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date must be before or equal to end date");
    }

    // ==================== getLatestNetWorth Tests ====================

    @Test
    @DisplayName("Should retrieve latest net worth snapshot")
    void shouldRetrieveLatestNetWorth() {
        // Arrange
        NetWorth latestSnapshot =
                createNetWorth(
                        5L,
                        testUserId,
                        testDate,
                        new BigDecimal("10000.00"),
                        new BigDecimal("2000.00"),
                        new BigDecimal("8000.00"));

        when(netWorthRepository.findLatestByUserId(testUserId))
                .thenReturn(Optional.of(latestSnapshot));

        // Act
        Optional<NetWorth> result = netWorthService.getLatestNetWorth(testUserId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(5L);
        assertThat(result.get().getNetWorth()).isEqualByComparingTo(new BigDecimal("8000.00"));

        verify(netWorthRepository).findLatestByUserId(testUserId);
    }

    @Test
    @DisplayName("Should return empty when no snapshots exist")
    void shouldReturnEmptyWhenNoSnapshotsExist() {
        // Arrange
        when(netWorthRepository.findLatestByUserId(testUserId)).thenReturn(Optional.empty());

        // Act
        Optional<NetWorth> result = netWorthService.getLatestNetWorth(testUserId);

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== calculateMonthlyChange Tests ====================

    @Test
    @DisplayName("Should calculate monthly change with positive growth")
    void shouldCalculateMonthlyChangeWithPositiveGrowth() {
        // Arrange
        NetWorth currentSnapshot =
                createNetWorth(
                        2L,
                        testUserId,
                        testDate,
                        new BigDecimal("12000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("12000.00"));
        NetWorth previousSnapshot =
                createNetWorth(
                        1L,
                        testUserId,
                        testDate.minusMonths(1),
                        new BigDecimal("10000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("10000.00"));

        when(netWorthRepository.findLatestByUserId(testUserId))
                .thenReturn(Optional.of(currentSnapshot));
        when(netWorthRepository.findClosestToDate(eq(testUserId), any(LocalDate.class)))
                .thenReturn(Optional.of(previousSnapshot));

        // Act
        NetWorthService.NetWorthChange change = netWorthService.calculateMonthlyChange(testUserId);

        // Assert: 12000 - 10000 = 2000 (+20%)
        assertThat(change.amount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(change.percentage()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Should calculate monthly change with negative growth")
    void shouldCalculateMonthlyChangeWithNegativeGrowth() {
        // Arrange
        NetWorth currentSnapshot =
                createNetWorth(
                        2L,
                        testUserId,
                        testDate,
                        new BigDecimal("8000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("8000.00"));
        NetWorth previousSnapshot =
                createNetWorth(
                        1L,
                        testUserId,
                        testDate.minusMonths(1),
                        new BigDecimal("10000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("10000.00"));

        when(netWorthRepository.findLatestByUserId(testUserId))
                .thenReturn(Optional.of(currentSnapshot));
        when(netWorthRepository.findClosestToDate(eq(testUserId), any(LocalDate.class)))
                .thenReturn(Optional.of(previousSnapshot));

        // Act
        NetWorthService.NetWorthChange change = netWorthService.calculateMonthlyChange(testUserId);

        // Assert: 8000 - 10000 = -2000 (-20%)
        assertThat(change.amount()).isEqualByComparingTo(new BigDecimal("-2000.00"));
        assertThat(change.percentage()).isEqualByComparingTo(new BigDecimal("-20.00"));
    }

    @Test
    @DisplayName("Should return zero change when no current snapshot exists")
    void shouldReturnZeroChangeWhenNoCurrentSnapshot() {
        // Arrange
        when(netWorthRepository.findLatestByUserId(testUserId)).thenReturn(Optional.empty());

        // Act
        NetWorthService.NetWorthChange change = netWorthService.calculateMonthlyChange(testUserId);

        // Assert: no current data → no comparison
        assertThat(change.hasComparison()).isFalse();
        assertThat(change.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should return zero change when no previous snapshot exists")
    void shouldReturnZeroChangeWhenNoPreviousSnapshot() {
        // Arrange
        NetWorth currentSnapshot =
                createNetWorth(
                        1L,
                        testUserId,
                        testDate,
                        new BigDecimal("10000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("10000.00"));

        when(netWorthRepository.findLatestByUserId(testUserId))
                .thenReturn(Optional.of(currentSnapshot));
        when(netWorthRepository.findClosestToDate(eq(testUserId), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // Act
        NetWorthService.NetWorthChange change = netWorthService.calculateMonthlyChange(testUserId);

        // Assert: When no previous data, hasComparison=false, percentage is null,
        // amount=current net worth
        assertThat(change.hasComparison()).isFalse();
        assertThat(change.percentage()).isNull();
        assertThat(change.amount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    // ==================== cleanupOldSnapshots Tests ====================

    @Test
    @DisplayName("Should cleanup old snapshots beyond retention period")
    void shouldCleanupOldSnapshotsBeyondRetentionPeriod() {
        // Arrange
        int retentionDays = 730; // 2 years
        when(netWorthRepository.deleteByUserIdAndSnapshotDateBefore(
                        eq(testUserId), any(LocalDate.class)))
                .thenReturn(15);

        // Act
        int deletedCount = netWorthService.cleanupOldSnapshots(testUserId, retentionDays);

        // Assert
        assertThat(deletedCount).isEqualTo(15);
        verify(netWorthRepository)
                .deleteByUserIdAndSnapshotDateBefore(eq(testUserId), any(LocalDate.class));
    }

    @Test
    @DisplayName("Should throw exception when retention days is negative")
    void shouldThrowExceptionWhenRetentionDaysIsNegative() {
        // Act & Assert
        assertThatThrownBy(() -> netWorthService.cleanupOldSnapshots(testUserId, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Retention days must be non-negative");
    }

    // ==================== Helper Methods ====================

    private Account createAccount(Long id, AccountType type, BigDecimal balance) {
        return Account.builder()
                .id(id)
                .userId(testUserId)
                .name("Account " + id)
                .type(type)
                .currency("EUR")
                .balance(balance)
                .isActive(true)
                .build();
    }

    private NetWorth createNetWorth(
            Long id,
            Long userId,
            LocalDate date,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal netWorth) {
        return NetWorth.builder()
                .id(id)
                .userId(userId)
                .snapshotDate(date)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .netWorth(netWorth)
                .currency("EUR")
                .build();
    }
}
