package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.BusinessRulesProperties;
import org.openfinance.dto.BorrowingCapacity;
import org.openfinance.dto.NetWorthAllocation;
import org.openfinance.dto.PortfolioPerformance;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard allocation and borrowing decisions preserve their accounting meaning")
class DashboardDecisionTest {
    private static final LocalDate START = LocalDate.of(2024, 6, 1);
    private static final LocalDate END = START.plusDays(29);
    @Mock private UserRepository users;
    @Mock private DefaultCurrencyProvider currency;
    @Mock private AccountRepository accounts;
    @Mock private AssetRepository assets;
    @Mock private AssetService assetService;
    @Mock private LiabilityRepository liabilities;
    @Mock private LiabilityService liabilityService;
    @Mock private TransactionRepository transactions;
    @Spy private BusinessRulesProperties rules = new BusinessRulesProperties();
    @InjectMocks private DashboardService service;

    @BeforeEach
    void userCurrency() {
        lenient()
                .when(users.findById(1L))
                .thenReturn(Optional.of(User.builder().id(1L).baseCurrency("EUR").build()));
        lenient().when(currency.resolve("EUR")).thenReturn("EUR");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "accounts",
                "recent",
                "cash",
                "spending",
                "sankey",
                "allocation",
                "interest",
                "history-owner",
                "history-granularity",
                "sankey-period"
            })
    void undatedDashboardQueriesRequireAnOwnerAndAUsableWindow(String operation) {
        assertThatThrownBy(
                        () -> {
                            switch (operation) {
                                case "accounts" -> service.getAccountSummaries(null);
                                case "recent" -> service.getRecentTransactions(null, 10);
                                case "cash" -> service.getCashFlow(null, 30);
                                case "spending" -> service.getSpendingByCategory(null, 30);
                                case "sankey" -> service.getCashflowSankey(null, 30);
                                case "allocation" -> service.getNetWorthAllocation(null);
                                case "interest" -> service.getEstimatedInterestSummary(null, "1M");
                                case "history-owner" -> service.getCashFlowHistory(
                                        null,
                                        org.openfinance.dto.CashFlowGranularity.MONTH,
                                        2024,
                                        1);
                                case "history-granularity" -> service.getCashFlowHistory(
                                        1L, null, 2024, 1);
                                case "sankey-period" -> service.getCashflowSankey(1L, 0);
                                default -> throw new AssertionError(operation);
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users, accounts, assets, liabilities, transactions);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of("cash flow", "spending", "sankey", "portfolio", "borrowing")
                .flatMap(
                        operation ->
                                Stream.of("user", "start", "end")
                                        .map(field -> Arguments.of(operation, field)));
    }

    @ParameterizedTest(name = "{0} rejects missing {1}")
    @MethodSource("invalidRequests")
    void datedRequestsRequireTheirOwnerAndBothBoundaries(String operation, String missing) {
        Long owner = missing.equals("user") ? null : 1L;
        LocalDate start = missing.equals("start") ? null : START;
        LocalDate end = missing.equals("end") ? null : END;
        assertThatThrownBy(
                        () -> {
                            switch (operation) {
                                case "cash flow" -> service.getCashFlow(owner, start, end);
                                case "spending" -> service.getSpendingByCategory(owner, start, end);
                                case "sankey" -> service.getCashflowSankey(owner, start, end);
                                case "portfolio" -> service.getPortfolioPerformance(
                                        owner, start, end);
                                case "borrowing" -> service.getBorrowingCapacity(owner, start, end);
                                default -> throw new AssertionError(operation);
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users, accounts, assets, liabilities, transactions);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void borrowingNeedsAPositiveAnalysisWindow(int days) {
        assertThatThrownBy(() -> service.getBorrowingCapacity(1L, days))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users, transactions, liabilities);
    }

    @ParameterizedTest
    @CsvSource({
        "0,EXCELLENT",
        "200,EXCELLENT",
        "200.10,GOOD",
        "350,GOOD",
        "350.10,FAIR",
        "500,FAIR",
        "500.10,POOR"
    })
    void borrowingHealthUsesInclusiveConfiguredThresholds(BigDecimal payment, String status) {
        Liability loan = loan(LiabilityType.LOAN);
        when(liabilities.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(loan));
        when(liabilityService.effectiveMonthlyPayment(loan)).thenReturn(payment);
        when(transactions.findByUserIdAndDateBetween(1L, START, END))
                .thenReturn(List.of(transaction(TransactionType.INCOME, "1000")));
        BorrowingCapacity result = service.getBorrowingCapacity(1L, START, END);
        assertThat(result.getFinancialHealthStatus()).isEqualTo(status);
        assertThat(result.getDebtToIncomeRatio())
                .isEqualByComparingTo(payment.divide(BigDecimal.TEN));
        assertThat(result.getRecommendedMaxBorrowing())
                .isEqualByComparingTo(new BigDecimal("400").subtract(payment).max(BigDecimal.ZERO));
        assertThat(result.isDebtPaymentsComplete()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noIncomeOrUnknownDebtPaymentsCannotProduceAvailableBorrowing(boolean unknownPayment) {
        Liability loan = loan(LiabilityType.LOAN);
        when(liabilities.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(loan));
        when(liabilityService.effectiveMonthlyPayment(loan))
                .thenReturn(unknownPayment ? null : BigDecimal.ONE);
        when(transactions.findByUserIdAndDateBetween(1L, START, END))
                .thenReturn(
                        unknownPayment
                                ? List.of(transaction(TransactionType.INCOME, "1000"))
                                : List.of());
        BorrowingCapacity result = service.getBorrowingCapacity(1L, START, END);
        assertThat(result.getFinancialHealthStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.getAvailableBorrowingCapacity()).isZero();
        assertThat(result.getRecommendedMaxBorrowing()).isZero();
        assertThat(result.isDebtPaymentsComplete()).isEqualTo(!unknownPayment);
    }

    @Test
    void borrowingExcludesTransfersLoanDrawsAndNonCashSecurityMovements() {
        Transaction transferIn = transaction(TransactionType.INCOME, "500");
        transferIn.setTransferId("pair");
        Transaction transferOut = transaction(TransactionType.EXPENSE, "500");
        transferOut.setTransferId("pair");
        Transaction draw = transaction(TransactionType.INCOME, "10000");
        draw.setMovementType(MovementType.DISBURSEMENT);
        Transaction unitsIn = transaction(TransactionType.INCOME, "900");
        unitsIn.setInvestmentQuantity(BigDecimal.ONE);
        Transaction unitsOut = transaction(TransactionType.EXPENSE, "900");
        unitsOut.setInvestmentQuantity(BigDecimal.ONE.negate());
        when(transactions.findByUserIdAndDateBetween(1L, START, END))
                .thenReturn(
                        List.of(
                                transferIn,
                                transferOut,
                                draw,
                                unitsIn,
                                unitsOut,
                                transaction(TransactionType.INCOME, "1000"),
                                transaction(TransactionType.EXPENSE, "200")));
        BorrowingCapacity result = service.getBorrowingCapacity(1L, START, END);
        assertThat(result.getMonthlyIncome()).isEqualByComparingTo("1000");
        assertThat(result.getMonthlyExpenses()).isEqualByComparingTo("200");
    }

    @ParameterizedTest
    @CsvSource({
        "CHECKING,Cash & Savings",
        "SAVINGS,Cash & Savings",
        "CASH,Cash & Savings",
        "INVESTMENT,Investments",
        "CREDIT_CARD,Credit Cards",
        "OTHER,Other Assets"
    })
    void cashAllocationClassifiesTheAccountAndSeparatesOverdrafts(
            AccountType type, String category) {
        Account positive =
                Account.builder()
                        .id(1L)
                        .type(type)
                        .currency("EUR")
                        .balance(new BigDecimal("100"))
                        .build();
        Account negative =
                Account.builder()
                        .id(2L)
                        .type(type)
                        .currency("EUR")
                        .balance(new BigDecimal("-50"))
                        .build();
        when(accounts.findByUserId(1L)).thenReturn(List.of(positive, negative));
        List<NetWorthAllocation> result = service.getNetWorthAllocation(1L);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(NetWorthAllocation::getCategory).containsOnly(category);
        assertThat(result.get(0).getValue()).isEqualByComparingTo("100");
        assertThat(result.get(0).getIsLiability()).isFalse();
        assertThat(result.get(1).getValue()).isEqualByComparingTo("-50");
        assertThat(result.get(1).getIsLiability()).isTrue();
        assertThat(result.get(0).getPercentage().add(result.get(1).getPercentage()))
                .isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @CsvSource({
        "STOCK,Investments",
        "BOND,Investments",
        "CRYPTO,Investments",
        "ETF,Investments",
        "MUTUAL_FUND,Investments",
        "REAL_ESTATE,Real Estate",
        "VEHICLE,Vehicles",
        "JEWELRY,Personal Assets",
        "COLLECTIBLE,Personal Assets",
        "ELECTRONICS,Personal Assets",
        "FURNITURE,Personal Assets",
        "COMMODITY,Other Assets",
        "OTHER,Other Assets"
    })
    void allocationIncludesEverySupportedAssetKind(AssetType type, String category) {
        Asset asset = asset();
        asset.setType(type);
        when(assets.findCurrentHoldingsByUserId(1L)).thenReturn(List.of(asset));
        NetWorthAllocation allocation = service.getNetWorthAllocation(1L).getFirst();
        assertThat(allocation.getCategory()).isEqualTo(category);
        assertThat(allocation.getValue()).isEqualByComparingTo("12.34");
        assertThat(allocation.getPercentage()).isEqualByComparingTo("100");
        assertThat(allocation.getIsLiability()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "MORTGAGE,Mortgages",
        "LOAN,Loans",
        "PERSONAL_LOAN,Loans",
        "STUDENT_LOAN,Loans",
        "AUTO_LOAN,Loans",
        "CREDIT_CARD,Credit Cards",
        "OTHER,Other Liabilities"
    })
    void allocationIncludesEachLoanKindInTheDocumentedGroup(LiabilityType type, String category) {
        when(liabilities.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(loan(type)));
        NetWorthAllocation allocation = service.getNetWorthAllocation(1L).getFirst();
        assertThat(allocation.getCategory()).isEqualTo(category);
        assertThat(allocation.getValue()).isEqualByComparingTo("-100");
        assertThat(allocation.getIsLiability()).isTrue();
    }

    @Test
    void representedCardDebtIsNotCountedAgainAsAStandaloneLiability() {
        Liability loan = loan(LiabilityType.CREDIT_CARD);
        loan.setRepresentedByAccountId(9L);
        when(liabilities.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(loan));
        when(accounts.findByUserId(1L))
                .thenReturn(
                        List.of(
                                Account.builder()
                                        .id(9L)
                                        .type(AccountType.CREDIT_CARD)
                                        .currency("EUR")
                                        .balance(new BigDecimal("-100"))
                                        .build()));
        List<NetWorthAllocation> result = service.getNetWorthAllocation(1L);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getValue()).isEqualByComparingTo("-100");
        assertThat(result.getFirst().getItemCount()).isEqualTo(1);
    }

    @Test
    void aFutureAcquisitionDoesNotIncreaseTodaysAllocation() {
        Asset asset = asset();
        asset.setPurchaseDate(LocalDate.now().plusDays(1));
        when(assets.findCurrentHoldingsByUserId(1L)).thenReturn(List.of(asset));
        assertThat(service.getNetWorthAllocation(1L)).isEmpty();
        assertThat(service.getAssetAllocation(1L)).isEmpty();
        assertThat(service.getPortfolioPerformance(1L, START, END)).isEmpty();
    }

    @Test
    void anAcquisitionTodayIsIncludedOnEveryAllocationSurface() {
        Asset asset = asset();
        asset.setPurchaseDate(LocalDate.now());
        when(assets.findCurrentHoldingsByUserId(1L)).thenReturn(List.of(asset));
        when(assetService.getCostBasis(asset)).thenReturn(BigDecimal.TEN);
        assertThat(service.getNetWorthAllocation(1L).getFirst().getValue())
                .isEqualByComparingTo("12.34");
        assertThat(service.getAssetAllocation(1L).getFirst().getTotalValue())
                .isEqualByComparingTo("12.34");
        assertThat(service.getPortfolioPerformance(1L, START, END).getFirst().getCurrentValue())
                .isEqualByComparingTo("12.34");
    }

    @Test
    void customPortfolioRangesKeepCurrentValueSeparateFromUnavailableHistoricalReturns() {
        Asset asset = asset();
        when(assets.findCurrentHoldingsByUserId(1L)).thenReturn(List.of(asset));
        when(assetService.getCostBasis(asset)).thenReturn(new BigDecimal("10"));
        List<PortfolioPerformance> result = service.getPortfolioPerformance(1L, START, END);
        assertThat(result.get(0).getCurrentValue()).isEqualByComparingTo("12.34");
        assertThat(result.get(0).getChangeAmount()).isNull();
        assertThat(result.get(0).getSparklineData()).isEmpty();
        assertThat(result.get(1).getCurrentValue()).isEqualByComparingTo("2.34");
        assertThat(result.get(1).getChangePercentage()).isEqualByComparingTo("23.40");
    }

    private Liability loan(LiabilityType type) {
        Liability loan = new Liability();
        loan.setId(10L);
        loan.setUserId(1L);
        loan.setType(type);
        loan.setCurrentBalance("100");
        loan.setCurrency("EUR");
        return loan;
    }

    private Asset asset() {
        return Asset.builder()
                .id(2L)
                .userId(1L)
                .type(AssetType.STOCK)
                .currency("EUR")
                .quantity(BigDecimal.ONE)
                .purchasePrice(BigDecimal.TEN)
                .currentPrice(new BigDecimal("12.34"))
                .build();
    }

    private Transaction transaction(TransactionType type, String amount) {
        return Transaction.builder()
                .id(1L)
                .userId(1L)
                .type(type)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .date(START)
                .isDeleted(false)
                .build();
    }
}
