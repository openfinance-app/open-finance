package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.entity.Account;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.testutil.DefaultCurrencyProviderMocks;

/**
 * Unit tests for the liability-reversal logic of {@link NetWorthService#backfillNetWorthHistory}.
 *
 * <p>Historical liability balances are reconstructed by reversing liability-linked movements made
 * after each target date. REPAYMENT movements add back their <em>principal leg</em> (total −
 * categorized splits — only that leg reduced the outstanding balance); DISBURSEMENT movements are
 * subtracted in full (the drawdown was not yet in the balance at the target date).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NetWorthService backfill — disbursement-aware liability reversal")
class NetWorthServiceBackfillTest {
    @Mock private org.openfinance.repository.LiabilityTrancheRepository liabilityTrancheRepository;

    @Mock private AccountCurrencyService accountCurrencyService;

    @Mock private NetWorthRepository netWorthRepository;
    @Mock private AccountRepository accountRepository;

    @Mock
    private org.openfinance.repository.AccountStatusHistoryRepository
            accountStatusHistoryRepository;

    @Mock private AssetRepository assetRepository;
    @Mock private LiabilityRepository liabilityRepository;
    @Mock private RealEstateRepository realEstateRepository;
    @Mock private RealEstateValueHistoryRepository realEstateValueHistoryRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock private NetWorthSnapshotWriter snapshotWriter;

    @InjectMocks private NetWorthService netWorthService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(accountCurrencyService.historicalPosition(any(), any(), any(), any()))
                .thenAnswer(
                        i ->
                                new AccountCurrencyService.Position(
                                        i.getArgument(1),
                                        ((org.openfinance.entity.Account) i.getArgument(0))
                                                .getCurrency()));
        org.mockito.Mockito.lenient()
                .when(exchangeRateService.convert(any(BigDecimal.class), any(), any(), any()))
                .thenAnswer(i -> i.getArgument(0));

        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(assetRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(realEstateRepository.findByUserIdAndIsActive(USER_ID, true)).thenReturn(List.of());
        when(realEstateValueHistoryRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(netWorthRepository.findByUserIdAndSnapshotDate(any(), any()))
                .thenReturn(Optional.empty());
        when(exchangeRateService.convert(any(BigDecimal.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DefaultCurrencyProviderMocks.stub(defaultCurrencyProvider);
    }

    private Liability mortgage() {
        Liability liability = new Liability();
        liability.setId(10L);
        liability.setUserId(USER_ID);
        liability.setPrincipal("200000");
        liability.setCurrentBalance("99200");
        liability.setCurrency("USD");
        liability.setStartDate(LocalDate.of(2025, 6, 1));
        return liability;
    }

    /**
     * Overrides the empty-account default so zero-liability snapshots are still persisted (the
     * backfill skips snapshots whose assets AND liabilities are both zero).
     */
    private void stubAccountWithBalance(BigDecimal balance) {
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(USER_ID)
                        .balance(balance)
                        .currency("USD")
                        .openingDate(LocalDate.of(2025, 1, 1))
                        .build();
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(account));
    }

    /** Persisted repayments carry their exact applied principal. */
    private Transaction repayment(Long id, BigDecimal amount, LocalDate date) {
        return Transaction.builder()
                .id(id)
                .userId(USER_ID)
                .accountId(1L)
                .type(TransactionType.EXPENSE)
                .amount(amount)
                .principalAmount(amount)
                .movementType(MovementType.REPAYMENT)
                .currency("USD")
                .date(date)
                .liabilityId(10L)
                .build();
    }

    private Transaction disbursement(Long id, BigDecimal amount, LocalDate date) {
        return Transaction.builder()
                .id(id)
                .userId(USER_ID)
                .accountId(1L)
                .type(TransactionType.EXPENSE)
                .amount(amount)
                .currency("USD")
                .date(date)
                .liabilityId(10L)
                .movementType(MovementType.DISBURSEMENT)
                .build();
    }

    private List<NetWorth> runBackfill(List<Transaction> transactions) {
        return runBackfill(transactions, mortgage());
    }

    private List<NetWorth> runBackfill(List<Transaction> transactions, Liability liability) {
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(transactions);
        when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(liability));

        netWorthService.backfillNetWorthHistory(
                USER_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 10), "USD", false);

        ArgumentCaptor<NetWorth> captor = ArgumentCaptor.forClass(NetWorth.class);
        verify(netWorthRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName(
            "repayment with categorized splits adds back only the principal leg (800, not 1200)")
    void backfillReversesOnlyPrincipalLegOfSplitRepayment() {
        Transaction payment = repayment(100L, new BigDecimal("1200"), LocalDate.of(2026, 2, 15));
        payment.setPrincipalAmount(new BigDecimal("800"));
        List<NetWorth> snapshots = runBackfill(List.of(payment));

        assertThat(snapshots).hasSize(3);
        NetWorth january = snapshots.get(0);
        NetWorth february = snapshots.get(1);
        assertThat(snapshots.get(2).getSnapshotDate()).isEqualTo(LocalDate.of(2026, 3, 1));

        // Payment (2026-02-15) is after the Jan and Feb targets: the principal leg 1200 − 400
        // = 800 is added back. The interest/insurance legs (300 + 100) must NOT inflate debt.
        assertThat(january.getTotalLiabilities()).isEqualByComparingTo("100000");
        assertThat(february.getTotalLiabilities()).isEqualByComparingTo("100000");
        // After the payment date the balance is simply the current balance.
        assertThat(snapshots.get(2).getTotalLiabilities()).isEqualByComparingTo("99200");
    }

    @Test
    @DisplayName("plain repayment without splits adds back the full amount")
    void backfillReversesFullPlainRepayment() {

        List<NetWorth> snapshots =
                runBackfill(
                        List.of(
                                repayment(
                                        100L, new BigDecimal("1200"), LocalDate.of(2026, 2, 15))));

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(0).getTotalLiabilities()).isEqualByComparingTo("100400");
        assertThat(snapshots.get(1).getTotalLiabilities()).isEqualByComparingTo("100400");
        assertThat(snapshots.get(2).getTotalLiabilities()).isEqualByComparingTo("99200");
    }

    @Test
    @DisplayName("fully categorized (interest-only) repayment adds back zero")
    void backfillReversesNothingForInterestOnlyRepayment() {
        Transaction payment = repayment(100L, new BigDecimal("300"), LocalDate.of(2026, 2, 15));
        payment.setPrincipalAmount(BigDecimal.ZERO);
        List<NetWorth> snapshots = runBackfill(List.of(payment));

        assertThat(snapshots).hasSize(3);
        // 300 − 300 categorized = 0 principal leg → historical balance equals current balance.
        assertThat(snapshots.get(0).getTotalLiabilities()).isEqualByComparingTo("99200");
        assertThat(snapshots.get(1).getTotalLiabilities()).isEqualByComparingTo("99200");
    }

    @Test
    @DisplayName("disbursement after target is subtracted in full (staged loan: 0 before drawdown)")
    void backfillSubtractsPostTargetDisbursement() {
        // Staged loan: balance was 0 at T0, then tranche T1 (50k) was drawn on 2026-02-15.
        // A positive-balance account keeps zero-liability snapshots from being skipped, so the
        // staged history (0 before the drawdown) stays observable.
        stubAccountWithBalance(new BigDecimal("1000"));
        Liability staged = mortgage();
        staged.setCurrentBalance("50000");

        List<NetWorth> snapshots =
                runBackfill(
                        List.of(
                                disbursement(
                                        200L, new BigDecimal("50000"), LocalDate.of(2026, 2, 15))),
                        staged);

        assertThat(snapshots).hasSize(3);
        // Before the drawdown the outstanding balance was 0 — NOT the current 50k.
        assertThat(snapshots.get(0).getTotalLiabilities()).isEqualByComparingTo("0");
        assertThat(snapshots.get(1).getTotalLiabilities()).isEqualByComparingTo("0");
        // After the drawdown the balance is simply the current balance.
        assertThat(snapshots.get(2).getTotalLiabilities()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("disbursement and repayment both after target net out correctly")
    void backfillNetsPostTargetDisbursementAndRepayment() {
        // Drawdown 50k on 2026-02-15, then a plain repayment 1200 on 2026-02-20.
        stubAccountWithBalance(new BigDecimal("1000"));
        Liability staged = mortgage();
        staged.setCurrentBalance("48800");

        List<NetWorth> snapshots =
                runBackfill(
                        List.of(
                                disbursement(
                                        200L, new BigDecimal("50000"), LocalDate.of(2026, 2, 15)),
                                repayment(100L, new BigDecimal("1200"), LocalDate.of(2026, 2, 20))),
                        staged);

        assertThat(snapshots).hasSize(3);
        // At T0: 48800 − 50000 drawdown + 1200 repayment principal = 0.
        assertThat(snapshots.get(0).getTotalLiabilities()).isEqualByComparingTo("0");
        assertThat(snapshots.get(1).getTotalLiabilities()).isEqualByComparingTo("0");
        assertThat(snapshots.get(2).getTotalLiabilities()).isEqualByComparingTo("48800");
    }

    @Test
    @DisplayName("disbursement before target is kept; only the later repayment is reversed")
    void backfillKeepsPreTargetDisbursementAndReversesPostTargetRepayment() {
        // Drawdown 100k on 2025-12-01 (before all targets), plain repayment 1200 on 2026-02-15.

        List<NetWorth> snapshots =
                runBackfill(
                        List.of(
                                disbursement(
                                        200L, new BigDecimal("100000"), LocalDate.of(2025, 12, 1)),
                                repayment(
                                        100L, new BigDecimal("1200"), LocalDate.of(2026, 2, 15))));

        assertThat(snapshots).hasSize(3);
        // The drawdown predates the targets → must stay in the balance; the repayment after the
        // targets adds back its full principal.
        assertThat(snapshots.get(0).getTotalLiabilities()).isEqualByComparingTo("100400");
        assertThat(snapshots.get(1).getTotalLiabilities()).isEqualByComparingTo("100400");
        assertThat(snapshots.get(2).getTotalLiabilities()).isEqualByComparingTo("99200");
    }
}
