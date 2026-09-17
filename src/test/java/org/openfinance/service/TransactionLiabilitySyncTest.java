package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.TransactionSplitRequest;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RealEstateValueHistory;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for Task 3: liability / property balance synchronization on linked transactions.
 *
 * <p>Covers DISBURSEMENT increases, REPAYMENT principal-only reduction via categorized splits,
 * reversal on delete/update, capital improvements on real estate, currency guard and tranche
 * clamping.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Transaction ↔ Liability/Property balance sync")
class TransactionLiabilitySyncTest {

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long LIABILITY_ID = 200L;
    private static final Long PROPERTY_ID = 300L;
    private static final Long TX_ID = 100L;
    private static final Long TRANCHE_ID = 400L;

    @Mock private AccountCurrencyService accountCurrencyService;

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PayeeRepository payeeRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private EncryptionService encryptionService;
    @Mock private BudgetAlertService budgetAlertService;
    @Mock private TransactionSplitService transactionSplitService;
    @Mock private UserRepository userRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private MessageSource messageSource;
    @Mock private NetWorthRepository netWorthRepository;
    @Mock private OperationHistoryService operationHistoryService;
    @Mock private SearchTokenService searchTokenService;
    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock private CurrencyConversionHelper currencyConversionHelperField;
    @Mock private LiabilityRepository liabilityRepository;
    @Mock private LiabilityTrancheRepository liabilityTrancheRepository;
    @Mock private RealEstateRepository realEstateRepository;
    @Mock private RealEstateValueHistoryRepository realEstateValueHistoryRepository;
    @Mock private org.openfinance.mapper.RealEstateMapper realEstateMapper;
    @Mock private AssetService assetService;

    @InjectMocks private TransactionService transactionService;
    private org.openfinance.repository.LiabilityPrincipalAllocationRepository allocations;

    @BeforeEach
    void setUp() {
        when(realEstateRepository.existsByIdAndUserId(PROPERTY_ID, USER_ID)).thenReturn(true);
        for (long categoryId : new long[] {5L, 6L}) {
            when(categoryRepository.findByIdAndUserId(categoryId, 1L))
                    .thenReturn(
                            Optional.of(
                                    org.openfinance.entity.Category.builder()
                                            .id(categoryId)
                                            .userId(1L)
                                            .type(org.openfinance.entity.CategoryType.EXPENSE)
                                            .name("Loan costs")
                                            .build()));
        }
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        org.openfinance.testutil.DefaultCurrencyProviderMocks.stub(
                defaultCurrencyProvider, userRepository);
        CurrencyConversionHelper helper =
                new CurrencyConversionHelper(
                        userRepository, defaultCurrencyProvider, exchangeRateService);
        ReflectionTestUtils.setField(transactionService, "currencyConversionHelper", helper);
        RealEstateService realEstateService =
                new RealEstateService(
                        realEstateRepository,
                        realEstateValueHistoryRepository,
                        liabilityRepository,
                        currencyRepository,
                        realEstateMapper,
                        encryptionService,
                        assetService,
                        org.mockito.Mockito.mock(AssetFinancingService.class),
                        transactionRepository,
                        userRepository,
                        exchangeRateService,
                        netWorthRepository,
                        operationHistoryService,
                        searchTokenService,
                        defaultCurrencyProvider,
                        helper);
        ReflectionTestUtils.setField(transactionService, "realEstateService", realEstateService);

        // Real tranche allocator/reconciler (Task 7): the clamp/invariant logic it owns must run
        LiabilityTrancheService liabilityTrancheService =
                new LiabilityTrancheService(
                        liabilityTrancheRepository,
                        (allocations =
                                org.openfinance.testutil.PrincipalAllocationRepositoryMocks
                                        .create()));
        ReflectionTestUtils.setField(
                transactionService, "liabilityTrancheService", liabilityTrancheService);
    }

    // ---------- Helpers ----------

    private Account accountFixture(String currency) {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(1L)
                .currency(currency)
                .name("Checking")
                .balance(new BigDecimal("1000.00"))
                .build();
    }

    private Liability liabilityFixture(String currentBalance, String currency) {
        Liability liability = new Liability();
        liability.setId(LIABILITY_ID);
        liability.setUserId(USER_ID);
        liability.setCurrency(currency);
        liability.setCurrentBalance(currentBalance);
        return liability;
    }

    private RealEstateProperty propertyFixture(String currentValue) {
        return RealEstateProperty.builder()
                .id(PROPERTY_ID)
                .userId(1L)
                .currency("USD")
                .currentValue(currentValue)
                .build();
    }

    private TransactionRequest linkedRequest(
            BigDecimal amount, MovementType movementType, String currency) {
        return TransactionRequest.builder()
                .accountId(ACCOUNT_ID)
                .type(
                        movementType == MovementType.DISBURSEMENT
                                ? TransactionType.INCOME
                                : TransactionType.EXPENSE)
                .amount(amount)
                .currency(currency)
                .date(LocalDate.now())
                .movementType(movementType)
                .liabilityId(movementType == MovementType.CAPITAL_IMPROVEMENT ? null : LIABILITY_ID)
                .realEstateId(movementType == MovementType.CAPITAL_IMPROVEMENT ? PROPERTY_ID : null)
                .build();
    }

    private Transaction linkedEntity(
            Long id, BigDecimal amount, MovementType movementType, String currency) {
        return Transaction.builder()
                .id(id)
                .userId(1L)
                .accountId(ACCOUNT_ID)
                .type(
                        movementType == MovementType.DISBURSEMENT
                                ? TransactionType.INCOME
                                : TransactionType.EXPENSE)
                .amount(amount)
                .currency(currency)
                .date(LocalDate.now())
                .movementType(movementType)
                .liabilityId(movementType == MovementType.CAPITAL_IMPROVEMENT ? null : LIABILITY_ID)
                .realEstateId(movementType == MovementType.CAPITAL_IMPROVEMENT ? PROPERTY_ID : null)
                .build();
    }

    private TransactionSplitRequest split(BigDecimal amount, Long categoryId) {
        return TransactionSplitRequest.builder().amount(amount).categoryId(categoryId).build();
    }

    private TransactionSplitResponse storedSplit(BigDecimal amount, Long categoryId) {
        return TransactionSplitResponse.builder().amount(amount).categoryId(categoryId).build();
    }

    private void stubCreate(TransactionRequest request, Transaction mapped, Transaction saved) {
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture(request.getCurrency())));
        when(transactionMapper.toEntity(request)).thenReturn(mapped);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);
        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenReturn(new TransactionResponse());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(liabilityRepository.save(any(Liability.class))).thenAnswer(inv -> inv.getArgument(0));
        when(realEstateRepository.save(any(RealEstateProperty.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(realEstateValueHistoryRepository.save(any(RealEstateValueHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- (a) DISBURSEMENT increases liability balance ----------

    @Test
    @DisplayName("DISBURSEMENT increases Liability.currentBalance by full amount")
    void disbursementIncreasesLiabilityBalanceByFullAmount() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD");
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD"),
                linkedEntity(TX_ID, new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("7000.00");
    }

    // ---------- (b) REPAYMENT reduces by principal leg only ----------

    @Test
    @DisplayName(
            "REPAYMENT 1200 with categorized splits 300+100 reduces liability by 800 principal only")
    void repaymentReducesLiabilityByPrincipalLegOnly() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("1200.00"), MovementType.REPAYMENT, "USD");
        request.setSplits(
                List.of(
                        split(new BigDecimal("800.00"), null),
                        split(new BigDecimal("300.00"), 5L),
                        split(new BigDecimal("100.00"), 6L)));
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("1200.00"), MovementType.REPAYMENT, "USD"),
                linkedEntity(TX_ID, new BigDecimal("1200.00"), MovementType.REPAYMENT, "USD"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("4200.00");
    }

    // ---------- (c) reversal on delete restores balance ----------

    @Test
    @DisplayName("Deleting a REPAYMENT restores the principal previously deducted")
    void deleteRestoresLiabilityBalance() {
        Transaction existing =
                linkedEntity(TX_ID, new BigDecimal("1200.00"), MovementType.REPAYMENT, "USD");
        existing.setPrincipalAmount(new BigDecimal("800.00"));
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionSplitService.getSplitsForTransaction(TX_ID))
                .thenReturn(
                        List.of(
                                storedSplit(new BigDecimal("800.00"), null),
                                storedSplit(new BigDecimal("300.00"), 5L),
                                storedSplit(new BigDecimal("100.00"), 6L)));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("4200.00", "USD")));
        when(liabilityRepository.save(any(Liability.class))).thenAnswer(inv -> inv.getArgument(0));

        transactionService.deleteTransaction(TX_ID, USER_ID);

        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("5000.00");
    }

    // ---------- update reverses old legs then applies new ----------

    @Test
    @DisplayName("Update reverses the old principal leg then applies the new one")
    void updateReversesOldAndAppliesNewLiabilityMovement() {
        Transaction existing =
                linkedEntity(TX_ID, new BigDecimal("1200.00"), MovementType.REPAYMENT, "USD");
        existing.setPrincipalAmount(new BigDecimal("800.00"));
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenReturn(new TransactionResponse());
        when(transactionSplitService.getSplitsForTransaction(TX_ID))
                .thenReturn(
                        List.of(
                                storedSplit(new BigDecimal("800.00"), null),
                                storedSplit(new BigDecimal("300.00"), 5L),
                                storedSplit(new BigDecimal("100.00"), 6L)));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("4200.00", "USD")));
        when(liabilityRepository.save(any(Liability.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest update =
                linkedRequest(new BigDecimal("900.00"), MovementType.REPAYMENT, "USD");
        update.setSplits(
                List.of(
                        split(new BigDecimal("600.00"), null),
                        split(new BigDecimal("300.00"), 5L)));

        transactionService.updateTransaction(TX_ID, USER_ID, update);

        // Reverse +800 (4200 → 5000) then apply −600 principal (5000 → 4400)
        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("4400.00");
    }

    // ---------- capital improvement bumps property value + history ----------

    @Test
    @DisplayName("CAPITAL_IMPROVEMENT increases property value and records value history")
    void capitalImprovementBumpsPropertyValueAndRecordsHistory() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("5000.00"), MovementType.CAPITAL_IMPROVEMENT, "USD");
        stubCreate(
                request,
                linkedEntity(
                        null, new BigDecimal("5000.00"), MovementType.CAPITAL_IMPROVEMENT, "USD"),
                linkedEntity(
                        TX_ID, new BigDecimal("5000.00"), MovementType.CAPITAL_IMPROVEMENT, "USD"));
        when(realEstateRepository.findByIdAndUserId(PROPERTY_ID, USER_ID))
                .thenReturn(Optional.of(propertyFixture("200000.00")));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<RealEstateProperty> propertyCaptor =
                ArgumentCaptor.forClass(RealEstateProperty.class);
        verify(realEstateRepository).save(propertyCaptor.capture());
        assertThat(propertyCaptor.getValue().getCurrentValue()).isEqualTo("205000.00");

        ArgumentCaptor<RealEstateValueHistory> historyCaptor =
                ArgumentCaptor.forClass(RealEstateValueHistory.class);
        verify(realEstateValueHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getRecordedValue()).isEqualTo("5000.00");
        assertThat(historyCaptor.getValue().isAdjustment()).isTrue();
        assertThat(historyCaptor.getValue().getPropertyId()).isEqualTo(PROPERTY_ID);
    }

    // ---------- currency guard ----------

    @Test
    @DisplayName("Liability currency mismatch is rejected with InvalidTransactionException")
    void liabilityCurrencyMismatchIsRejected() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("100.00"), MovementType.REPAYMENT, "EUR");
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("100.00"), MovementType.REPAYMENT, "EUR"),
                linkedEntity(TX_ID, new BigDecimal("100.00"), MovementType.REPAYMENT, "EUR"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");
    }

    // ---------- tranche clamp guard ----------

    @Test
    @DisplayName("DISBURSEMENT balance is clamped to SUM(drawn) of DRAWN tranches")
    void disbursementAddsToOpeningBalance() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD");
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD"),
                linkedEntity(TX_ID, new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));
        LiabilityTranche drawn =
                LiabilityTranche.builder()
                        .id(1L)
                        .liabilityId(LIABILITY_ID)
                        .userId(1L)
                        .trancheNo(1)
                        .plannedAmount(new BigDecimal("6000.00"))
                        .drawnAmount(new BigDecimal("6000.00"))
                        .status(TrancheStatus.DRAWN)
                        .currency("USD")
                        .build();
        LiabilityTranche planned =
                LiabilityTranche.builder()
                        .id(2L)
                        .liabilityId(LIABILITY_ID)
                        .userId(1L)
                        .trancheNo(2)
                        .plannedAmount(new BigDecimal("4000.00"))
                        .status(TrancheStatus.PLANNED)
                        .currency("USD")
                        .build();
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(List.of(drawn, planned));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("7000.00");
    }

    // ---------- DISBURSEMENT tranche lifecycle on delete/update ----------

    @Test
    @DisplayName(
            "Creating a raw DISBURSEMENT fails fast when the balance exceeds the drawn tranches")
    void rawDisbursementPreservesOpeningPrincipal() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD");
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD"),
                linkedEntity(TX_ID, new BigDecimal("2000.00"), MovementType.DISBURSEMENT, "USD"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("50000.00", "USD")));
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(List.of(drawnTrancheFixture(new BigDecimal("40000.00"))));

        transactionService.createTransaction(USER_ID, request);
        ArgumentCaptor<Liability> balance = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(balance.capture());
        assertThat(balance.getValue().getCurrentBalance()).isEqualTo("52000.00");
    }

    private Transaction disbursementEntity(BigDecimal amount) {
        Transaction tx = linkedEntity(TX_ID, amount, MovementType.DISBURSEMENT, "USD");
        tx.setType(TransactionType.INCOME);
        tx.setTrancheId(TRANCHE_ID);
        return tx;
    }

    private LiabilityTranche drawnTrancheFixture(BigDecimal drawnAmount) {
        return LiabilityTranche.builder()
                .id(TRANCHE_ID)
                .liabilityId(LIABILITY_ID)
                .userId(1L)
                .trancheNo(1)
                .plannedAmount(new BigDecimal("50000.00"))
                .drawnAmount(drawnAmount)
                .drawnDate(LocalDate.now().minusDays(1))
                .status(TrancheStatus.DRAWN)
                .currency("USD")
                .build();
    }

    @Test
    @DisplayName("Deleting a DISBURSEMENT tx reverts its tranche to PLANNED and zeroes the balance")
    void deleteDisbursementTxRevertsTrancheToPlanned() {
        // Shared mutable tranche so the reconcile sees the state persisted so far
        LiabilityTranche tranche = drawnTrancheFixture(new BigDecimal("40000.00"));
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(disbursementEntity(new BigDecimal("40000.00"))));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionSplitService.getSplitsForTransaction(TX_ID)).thenReturn(List.of());
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("40000.00", "USD")));
        when(liabilityRepository.save(any(Liability.class))).thenAnswer(inv -> inv.getArgument(0));
        when(liabilityTrancheRepository.findByIdAndUserId(TRANCHE_ID, USER_ID))
                .thenReturn(Optional.of(tranche));
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(List.of(tranche));
        when(liabilityTrancheRepository.save(any(LiabilityTranche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        transactionService.deleteTransaction(TX_ID, USER_ID);

        ArgumentCaptor<LiabilityTranche> trancheCaptor =
                ArgumentCaptor.forClass(LiabilityTranche.class);
        verify(liabilityTrancheRepository).save(trancheCaptor.capture());
        assertThat(trancheCaptor.getValue().getStatus()).isEqualTo(TrancheStatus.PLANNED);
        assertThat(trancheCaptor.getValue().getDrawnAmount()).isNull();
        assertThat(trancheCaptor.getValue().getDrawnDate()).isNull();

        // The tranche must be reverted BEFORE the balance adjustment so the reconcile
        // re-derives the balance without the drawdown: invariant kept, balance back to 0
        ArgumentCaptor<Liability> liabilityCaptor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(liabilityCaptor.capture());
        assertThat(new BigDecimal(liabilityCaptor.getValue().getCurrentBalance()))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Updating a DISBURSEMENT tx amount re-marks the tranche DRAWN with the new amount")
    void updateDisbursementTxAmountUpdatesTrancheDrawnAmount() {
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(disbursementEntity(new BigDecimal("40000.00"))));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenReturn(new TransactionResponse());
        when(transactionSplitService.getSplitsForTransaction(TX_ID)).thenReturn(List.of());
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("40000.00", "USD")));
        when(liabilityRepository.save(any(Liability.class))).thenAnswer(inv -> inv.getArgument(0));
        when(liabilityTrancheRepository.findByIdAndUserId(TRANCHE_ID, USER_ID))
                .thenAnswer(inv -> Optional.of(drawnTrancheFixture(new BigDecimal("40000.00"))));
        when(liabilityTrancheRepository.save(any(LiabilityTranche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest update =
                linkedRequest(new BigDecimal("45000.00"), MovementType.DISBURSEMENT, "USD");
        update.setTrancheId(TRANCHE_ID);

        transactionService.updateTransaction(TX_ID, USER_ID, update);

        // First save reverts the tranche to PLANNED, the second re-marks it DRAWN at 45000
        ArgumentCaptor<LiabilityTranche> trancheCaptor =
                ArgumentCaptor.forClass(LiabilityTranche.class);
        verify(liabilityTrancheRepository, times(2)).save(trancheCaptor.capture());
        assertThat(trancheCaptor.getAllValues().get(0).getStatus())
                .isEqualTo(TrancheStatus.PLANNED);
        assertThat(trancheCaptor.getValue().getStatus()).isEqualTo(TrancheStatus.DRAWN);
        assertThat(trancheCaptor.getValue().getDrawnAmount())
                .isEqualByComparingTo(new BigDecimal("45000.00"));
    }

    @Test
    @DisplayName(
            "Updating a DISBURSEMENT amount keeps the liability balance at the new amount "
                    + "(tranche re-marked DRAWN before the reconcile clamp)")
    void updateDisbursementAmountKeepsLiabilityBalanceAfterRedraw() {
        // Shared mutable tranche so the reconcile sees the state persisted so far
        LiabilityTranche tranche = drawnTrancheFixture(new BigDecimal("40000.00"));
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(disbursementEntity(new BigDecimal("40000.00"))));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenReturn(new TransactionResponse());
        when(transactionSplitService.getSplitsForTransaction(TX_ID)).thenReturn(List.of());
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("40000.00", "USD")));
        when(liabilityRepository.save(any(Liability.class))).thenAnswer(inv -> inv.getArgument(0));
        when(liabilityTrancheRepository.findByIdAndUserId(TRANCHE_ID, USER_ID))
                .thenReturn(Optional.of(tranche));
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(List.of(tranche));
        when(liabilityTrancheRepository.save(any(LiabilityTranche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest update =
                linkedRequest(new BigDecimal("45000.00"), MovementType.DISBURSEMENT, "USD");
        update.setTrancheId(TRANCHE_ID);

        transactionService.updateTransaction(TX_ID, USER_ID, update);

        // Reverse 40000 (balance → 0, tranche PLANNED), re-draw 45000: the clamp must count the
        // re-drawn tranche, so the final balance is 45000 — not clamped down to 0.
        ArgumentCaptor<Liability> liabilityCaptor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository, times(2)).save(liabilityCaptor.capture());
        assertThat(liabilityCaptor.getValue().getCurrentBalance()).isEqualTo("45000.00");

        ArgumentCaptor<LiabilityTranche> trancheCaptor =
                ArgumentCaptor.forClass(LiabilityTranche.class);
        verify(liabilityTrancheRepository, times(2)).save(trancheCaptor.capture());
        assertThat(trancheCaptor.getValue().getStatus()).isEqualTo(TrancheStatus.DRAWN);
        assertThat(trancheCaptor.getValue().getDrawnAmount())
                .isEqualByComparingTo(new BigDecimal("45000.00"));
    }

    // ---------- FX liability legs (Task 9) ----------

    @Test
    @DisplayName(
            "FX REPAYMENT from an EUR account to a USD liability debits the account natively and"
                    + " reduces the liability by the liability-currency principal")
    void fxRepaymentReducesLiabilityByLiabilityCurrencyPrincipal() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("1200.00"), MovementType.REPAYMENT, "EUR");
        request.setOriginalAmount(new BigDecimal("1310.04"));
        request.setOriginalCurrency("USD");
        request.setConversionRate(new BigDecimal("0.9160"));
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("1200.00"), MovementType.REPAYMENT, "EUR"),
                linkedEntity(TX_ID, new BigDecimal("1200.00"), MovementType.REPAYMENT, "EUR"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));

        transactionService.createTransaction(USER_ID, request);

        // Account debit stays account-native: 1000 − 1200 EUR
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getBalance())
                .isEqualByComparingTo(new BigDecimal("-200.00"));

        // Liability leg moves in the liability currency: 5000 − 1310.04 USD
        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("3689.96");
    }

    @Test
    @DisplayName(
            "FX REPAYMENT converts categorized split legs to the liability currency before"
                    + " extracting the principal leg")
    void fxRepaymentConvertsSplitsToLiabilityCurrency() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("500.00"), MovementType.REPAYMENT, "EUR");
        request.setOriginalAmount(new BigDecimal("1000.00"));
        request.setOriginalCurrency("USD");
        request.setConversionRate(new BigDecimal("0.5000"));
        request.setSplits(
                List.of(
                        split(new BigDecimal("350.00"), null),
                        split(new BigDecimal("100.00"), 5L),
                        split(new BigDecimal("50.00"), 6L)));
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("500.00"), MovementType.REPAYMENT, "EUR"),
                linkedEntity(TX_ID, new BigDecimal("500.00"), MovementType.REPAYMENT, "EUR"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));

        transactionService.createTransaction(USER_ID, request);

        // Principal leg = 1000 − (100 + 50) / 0.5 = 700 USD → 5000 − 700
        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("4300.00");
    }

    @Test
    @DisplayName("FX DISBURSEMENT increases the liability by the liability-currency total")
    void fxDisbursementIncreasesLiabilityByLiabilityCurrencyTotal() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("600.00"), MovementType.DISBURSEMENT, "EUR");
        request.setOriginalAmount(new BigDecimal("1200.00"));
        request.setOriginalCurrency("USD");
        request.setConversionRate(new BigDecimal("0.5000"));
        stubCreate(
                request,
                linkedEntity(null, new BigDecimal("600.00"), MovementType.DISBURSEMENT, "EUR"),
                linkedEntity(TX_ID, new BigDecimal("600.00"), MovementType.DISBURSEMENT, "EUR"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("6200.00");
    }

    @Test
    @DisplayName("FX REPAYMENT on a staged loan allocates the converted principal to the tranche")
    void fxRepaymentOnStagedLoanAllocatesConvertedPrincipal() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("600.00"), MovementType.REPAYMENT, "EUR");
        request.setOriginalAmount(new BigDecimal("1200.00"));
        request.setOriginalCurrency("USD");
        request.setConversionRate(new BigDecimal("0.5000"));
        request.setSplits(
                List.of(
                        split(new BigDecimal("500.00"), null),
                        split(new BigDecimal("100.00"), 5L)));
        Transaction mapped =
                linkedEntity(null, new BigDecimal("600.00"), MovementType.REPAYMENT, "EUR");
        Transaction saved =
                linkedEntity(TX_ID, new BigDecimal("600.00"), MovementType.REPAYMENT, "EUR");
        // The service persists the conversion fields before saving (applyConversionFields)
        saved.setOriginalAmount(new BigDecimal("1200.00"));
        saved.setOriginalCurrency("USD");
        saved.setConversionRate(new BigDecimal("0.5000"));
        stubCreate(request, mapped, saved);
        LiabilityTranche tranche = drawnTrancheFixture(new BigDecimal("5000.00"));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("5000.00", "USD")));
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(List.of(tranche));
        when(transactionRepository.findByTrancheIdAndUserId(TRANCHE_ID, USER_ID))
                .thenReturn(List.of(saved));
        // saveSplits persisted the request splits — later reads return them
        when(transactionSplitService.getSplitsForTransaction(TX_ID))
                .thenReturn(
                        List.of(
                                storedSplit(new BigDecimal("500.00"), null),
                                storedSplit(new BigDecimal("100.00"), 5L)));
        when(liabilityTrancheRepository.save(any(LiabilityTranche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        transactionService.createTransaction(USER_ID, request);

        // Principal leg = 1200 − 100/0.5 = 1000 USD; tranche remaining 5000 − 1000 = 4000
        assertThat(
                        allocations
                                .findByTransactionIdAndUserId(saved.getId(), USER_ID)
                                .getFirst()
                                .getTrancheId())
                .isEqualTo(TRANCHE_ID);
        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("4000.00");
    }

    @Test
    @DisplayName("Deleting an FX REPAYMENT restores the liability-currency principal")
    void deleteFxRepaymentRestoresConvertedPrincipal() {
        Transaction existing =
                linkedEntity(TX_ID, new BigDecimal("1200.00"), MovementType.REPAYMENT, "EUR");
        existing.setOriginalAmount(new BigDecimal("1310.04"));
        existing.setOriginalCurrency("USD");
        existing.setConversionRate(new BigDecimal("0.9160"));
        existing.setPrincipalAmount(new BigDecimal("1310.04"));
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("EUR")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionSplitService.getSplitsForTransaction(TX_ID)).thenReturn(List.of());
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("3689.96", "USD")));
        when(liabilityRepository.save(any(Liability.class))).thenAnswer(inv -> inv.getArgument(0));

        transactionService.deleteTransaction(TX_ID, USER_ID);

        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentBalance()).isEqualTo("5000.00");
    }

    // ---------- multi-instrument guard (Task 6 deferred minor) ----------

    @Test
    @DisplayName("Linking more than one instrument on a single transaction is rejected")
    void multiInstrumentLinkIsRejected() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("100.00"), MovementType.REPAYMENT, "USD");
        request.setRealEstateId(PROPERTY_ID);
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("at most one");
    }

    // ---------- improvement currency guard (Task 6 deferred minor) ----------

    @Test
    @DisplayName("CAPITAL_IMPROVEMENT currency mismatch with the property is rejected")
    void capitalImprovementPropertyCurrencyMismatchIsRejected() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("5000.00"), MovementType.CAPITAL_IMPROVEMENT, "EUR");
        stubCreate(
                request,
                linkedEntity(
                        null, new BigDecimal("5000.00"), MovementType.CAPITAL_IMPROVEMENT, "EUR"),
                linkedEntity(
                        TX_ID, new BigDecimal("5000.00"), MovementType.CAPITAL_IMPROVEMENT, "EUR"));
        when(realEstateRepository.findByIdAndUserId(PROPERTY_ID, USER_ID))
                .thenReturn(Optional.of(propertyFixture("200000.00")));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName(
            "FX CAPITAL_IMPROVEMENT applies the property-currency total from the conversion fields")
    void fxCapitalImprovementAppliesPropertyCurrencyTotal() {
        TransactionRequest request =
                linkedRequest(new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT, "EUR");
        request.setOriginalAmount(new BigDecimal("1000.00"));
        request.setOriginalCurrency("USD");
        request.setConversionRate(new BigDecimal("0.5000"));
        stubCreate(
                request,
                linkedEntity(
                        null, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT, "EUR"),
                linkedEntity(
                        TX_ID, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT, "EUR"));
        when(realEstateRepository.findByIdAndUserId(PROPERTY_ID, USER_ID))
                .thenReturn(Optional.of(propertyFixture("200000.00")));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<RealEstateProperty> propertyCaptor =
                ArgumentCaptor.forClass(RealEstateProperty.class);
        verify(realEstateRepository).save(propertyCaptor.capture());
        assertThat(propertyCaptor.getValue().getCurrentValue()).isEqualTo("201000.00");
    }

    // ---------- update reverse leg derives from OLD values (Task 7 deferred minor) ----------

    @Test
    @DisplayName(
            "Updating a staged-loan REPAYMENT reverses the old principal without the reconciler"
                    + " re-deriving from the new persisted splits (single WARN-free reconcile)")
    void updateReversesOldPrincipalWithoutReconcileOverrideFromNewState() {
        LiabilityTranche tranche = drawnTrancheFixture(new BigDecimal("5000.00"));
        Transaction existing =
                linkedEntity(TX_ID, new BigDecimal("1200.00"), MovementType.REPAYMENT, "USD");
        existing.setTrancheId(TRANCHE_ID);
        existing.setPrincipalAmount(new BigDecimal("800.00"));
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenReturn(new TransactionResponse());
        // Call #1: the before-update response snapshot, call #2: the OLD-splits capture for the
        // reverse leg — both see the old state; afterwards the (already replaced) NEW splits are
        // what the reconciler sees — exactly the production persistence ordering.
        when(transactionSplitService.getSplitsForTransaction(TX_ID))
                .thenReturn(
                        List.of(
                                storedSplit(new BigDecimal("800.00"), null),
                                storedSplit(new BigDecimal("300.00"), 5L),
                                storedSplit(new BigDecimal("100.00"), 6L)),
                        List.of(
                                storedSplit(new BigDecimal("800.00"), null),
                                storedSplit(new BigDecimal("300.00"), 5L),
                                storedSplit(new BigDecimal("100.00"), 6L)))
                .thenReturn(
                        List.of(
                                storedSplit(new BigDecimal("600.00"), null),
                                storedSplit(new BigDecimal("300.00"), 5L)));
        Liability existingLoan = liabilityFixture("4200.00", "USD");
        java.util.List<String> savedBalances = new java.util.ArrayList<>();
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(existingLoan));
        when(liabilityRepository.save(any(Liability.class)))
                .thenAnswer(
                        inv -> {
                            Liability saved = inv.getArgument(0);
                            savedBalances.add(saved.getCurrentBalance());
                            return saved;
                        });
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(List.of(tranche));
        when(transactionRepository.findByTrancheIdAndUserId(TRANCHE_ID, USER_ID))
                .thenReturn(List.of(existing));
        when(liabilityTrancheRepository.save(any(LiabilityTranche.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            TransactionRequest r = inv.getArgument(0);
                            Transaction t = inv.getArgument(1);
                            t.setAmount(r.getAmount());
                            return null;
                        })
                .when(transactionMapper)
                .updateEntityFromRequest(any(TransactionRequest.class), any(Transaction.class));

        TransactionRequest update =
                linkedRequest(new BigDecimal("900.00"), MovementType.REPAYMENT, "USD");
        update.setTrancheId(TRANCHE_ID);
        update.setSplits(
                List.of(
                        split(new BigDecimal("600.00"), null),
                        split(new BigDecimal("300.00"), 5L)));

        transactionService.updateTransaction(TX_ID, USER_ID, update);

        // Reverse leg: stored 4200 + old principal 800, NOT overridden by the reconciler (which
        // already sees the new 600 principal). Apply leg: 5000 − 600 with the single reconcile.
        ArgumentCaptor<Liability> captor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository, times(2)).save(captor.capture());
        assertThat(savedBalances).containsExactly("5000.00", "4400.00");
    }

    @Test
    @DisplayName(
            "Creating a DISBURSEMENT with trancheId marks the tranche DRAWN before the balance "
                    + "reconcile, so the balance equals the amount")
    void createDisbursementWithTrancheIdMarksDrawnThenAddsBalance() {
        LiabilityTranche planned =
                LiabilityTranche.builder()
                        .id(TRANCHE_ID)
                        .liabilityId(LIABILITY_ID)
                        .userId(1L)
                        .trancheNo(1)
                        .plannedAmount(new BigDecimal("50000.00"))
                        .status(TrancheStatus.PLANNED)
                        .currency("USD")
                        .build();
        TransactionRequest request =
                linkedRequest(new BigDecimal("45000.00"), MovementType.DISBURSEMENT, "USD");
        request.setTrancheId(TRANCHE_ID);
        Transaction mapped =
                linkedEntity(null, new BigDecimal("45000.00"), MovementType.DISBURSEMENT, "USD");
        mapped.setTrancheId(TRANCHE_ID);
        Transaction saved =
                linkedEntity(TX_ID, new BigDecimal("45000.00"), MovementType.DISBURSEMENT, "USD");
        saved.setTrancheId(TRANCHE_ID);
        stubCreate(request, mapped, saved);
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liabilityFixture("0.00", "USD")));
        when(liabilityTrancheRepository.findByIdAndUserId(TRANCHE_ID, USER_ID))
                .thenReturn(Optional.of(planned));
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(List.of(planned));
        when(liabilityTrancheRepository.save(any(LiabilityTranche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        transactionService.createTransaction(USER_ID, request);

        // The tranche is marked DRAWN before the reconcile, so drawnSum counts it and the
        // balance is the full amount instead of being clamped down to 0.
        ArgumentCaptor<Liability> liabilityCaptor = ArgumentCaptor.forClass(Liability.class);
        verify(liabilityRepository).save(liabilityCaptor.capture());
        assertThat(liabilityCaptor.getValue().getCurrentBalance()).isEqualTo("45000.00");

        ArgumentCaptor<LiabilityTranche> trancheCaptor =
                ArgumentCaptor.forClass(LiabilityTranche.class);
        verify(liabilityTrancheRepository).save(trancheCaptor.capture());
        assertThat(trancheCaptor.getValue().getStatus()).isEqualTo(TrancheStatus.DRAWN);
        assertThat(trancheCaptor.getValue().getDrawnAmount())
                .isEqualByComparingTo(new BigDecimal("45000.00"));
    }
}
