package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidAssetStateException;
import org.openfinance.mapper.AssetMapper;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for Task 6: physical asset cost-basis synchronization on linked transactions.
 *
 * <p>Covers CAPITAL_IMPROVEMENT bumps of Asset.currentPrice (per-unit when quantity > 1),
 * MAINTENANCE being account-only, and reversal of the price bump when the movement is deleted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Transaction ↔ physical asset cost sync")
class AssetImprovementSyncTest {

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long ASSET_ID = 500L;
    private static final Long TX_ID = 100L;

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
    @Mock private LiabilityRepository liabilityRepository;
    @Mock private LiabilityTrancheRepository liabilityTrancheRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetMapper assetMapper;
    @Mock private EncryptionProperties encryptionProperties;

    @InjectMocks private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        when(assetRepository.existsByIdAndUserId(ASSET_ID, USER_ID)).thenReturn(true);
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        org.openfinance.testutil.DefaultCurrencyProviderMocks.stub(
                defaultCurrencyProvider, userRepository);
        CurrencyConversionHelper helper =
                new CurrencyConversionHelper(
                        userRepository, defaultCurrencyProvider, exchangeRateService);
        ReflectionTestUtils.setField(transactionService, "currencyConversionHelper", helper);
        AssetService assetService =
                new AssetService(
                        assetRepository,
                        transactionRepository,
                        accountRepository,
                        currencyRepository,
                        assetMapper,
                        encryptionService,
                        userRepository,
                        exchangeRateService,
                        netWorthRepository,
                        operationHistoryService,
                        searchTokenService,
                        org.mockito.Mockito.mock(AttachmentService.class),
                        defaultCurrencyProvider,
                        encryptionProperties,
                        helper);
        ReflectionTestUtils.setField(transactionService, "assetService", assetService);
    }

    // ---------- Helpers ----------

    private Account accountFixture(String currency) {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .currency(currency)
                .name("Checking")
                .balance(new BigDecimal("1000.00"))
                .build();
    }

    private Asset assetFixture(AssetType type, BigDecimal quantity, BigDecimal currentPrice) {
        return Asset.builder()
                .id(ASSET_ID)
                .userId(USER_ID)
                .type(type)
                .name("Linked asset")
                .quantity(quantity)
                .purchasePrice(currentPrice)
                .currentPrice(currentPrice)
                .currency("USD")
                .purchaseDate(LocalDate.now().minusYears(1))
                .build();
    }

    private TransactionRequest assetRequest(BigDecimal amount, MovementType movementType) {
        return TransactionRequest.builder()
                .accountId(ACCOUNT_ID)
                .type(TransactionType.EXPENSE)
                .amount(amount)
                .currency("USD")
                .date(LocalDate.now())
                .movementType(movementType)
                .assetId(ASSET_ID)
                .build();
    }

    private Transaction assetEntity(Long id, BigDecimal amount, MovementType movementType) {
        return Transaction.builder()
                .id(id)
                .userId(USER_ID)
                .accountId(ACCOUNT_ID)
                .type(TransactionType.EXPENSE)
                .amount(amount)
                .currency("USD")
                .date(LocalDate.now())
                .movementType(movementType)
                .assetId(ASSET_ID)
                .build();
    }

    private void stubCreate(TransactionRequest request, Transaction mapped, Transaction saved) {
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture(request.getCurrency())));
        when(transactionMapper.toEntity(request)).thenReturn(mapped);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);
        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenReturn(new TransactionResponse());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- (a) CAPITAL_IMPROVEMENT bumps currentPrice on quantity-1 asset ----------

    @Test
    @DisplayName("CAPITAL_IMPROVEMENT on physical asset (quantity 1) increases currentPrice")
    void capitalImprovementBumpsPhysicalAssetPrice() {
        TransactionRequest request =
                assetRequest(new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT);
        stubCreate(
                request,
                assetEntity(null, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT),
                assetEntity(TX_ID, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT));
        when(assetRepository.findByIdAndUserId(ASSET_ID, USER_ID))
                .thenReturn(
                        Optional.of(
                                assetFixture(
                                        AssetType.VEHICLE,
                                        BigDecimal.ONE,
                                        new BigDecimal("20000.00"))));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPrice())
                .isEqualByComparingTo(new BigDecimal("20500.00"));
        assertThat(captor.getValue().getLastUpdated()).isNotNull();
    }

    // ---------- (b) MAINTENANCE never touches the asset ----------

    @Test
    @DisplayName("MAINTENANCE with assetId does not change the asset price")
    void maintenanceDoesNotChangeAssetPrice() {
        TransactionRequest request =
                assetRequest(new BigDecimal("80.00"), MovementType.MAINTENANCE);
        stubCreate(
                request,
                assetEntity(null, new BigDecimal("80.00"), MovementType.MAINTENANCE),
                assetEntity(TX_ID, new BigDecimal("80.00"), MovementType.MAINTENANCE));
        when(assetRepository.findByIdAndUserId(ASSET_ID, USER_ID))
                .thenReturn(
                        Optional.of(
                                assetFixture(
                                        AssetType.VEHICLE,
                                        BigDecimal.ONE,
                                        new BigDecimal("20000.00"))));

        transactionService.createTransaction(USER_ID, request);

        verify(assetRepository, never()).save(any(Asset.class));
    }

    // ---------- (c) reversal on delete restores the price ----------

    @Test
    @DisplayName("Deleting a CAPITAL_IMPROVEMENT tx restores the previous asset price")
    void deleteRestoresAssetPrice() {
        Transaction existing =
                assetEntity(TX_ID, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT);
        when(transactionRepository.findByIdAndUserId(TX_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(accountFixture("USD")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.findByIdAndUserId(ASSET_ID, USER_ID))
                .thenReturn(
                        Optional.of(
                                assetFixture(
                                        AssetType.VEHICLE,
                                        BigDecimal.ONE,
                                        new BigDecimal("20500.00"))));

        transactionService.deleteTransaction(TX_ID, USER_ID);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPrice())
                .isEqualByComparingTo(new BigDecimal("20000.00"));
    }

    // ---------- (d) quantity > 1 divides the improvement per unit ----------

    @Test
    @DisplayName("CAPITAL_IMPROVEMENT 200 on a quantity-4 asset bumps currentPrice by 50 per unit")
    void capitalImprovementDividedAcrossQuantity() {
        TransactionRequest request =
                assetRequest(new BigDecimal("200.00"), MovementType.CAPITAL_IMPROVEMENT);
        stubCreate(
                request,
                assetEntity(null, new BigDecimal("200.00"), MovementType.CAPITAL_IMPROVEMENT),
                assetEntity(TX_ID, new BigDecimal("200.00"), MovementType.CAPITAL_IMPROVEMENT));
        when(assetRepository.findByIdAndUserId(ASSET_ID, USER_ID))
                .thenReturn(
                        Optional.of(
                                assetFixture(
                                        AssetType.COLLECTIBLE,
                                        new BigDecimal("4"),
                                        new BigDecimal("100.00"))));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPrice())
                .isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // ---------- (e) non-physical assets are rejected ----------

    @Test
    @DisplayName("CAPITAL_IMPROVEMENT on a non-physical asset is rejected")
    void capitalImprovementOnNonPhysicalAssetRejected() {
        TransactionRequest request =
                assetRequest(new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT);
        stubCreate(
                request,
                assetEntity(null, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT),
                assetEntity(TX_ID, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT));
        when(assetRepository.findByIdAndUserId(ASSET_ID, USER_ID))
                .thenReturn(
                        Optional.of(
                                assetFixture(
                                        AssetType.STOCK,
                                        BigDecimal.ONE,
                                        new BigDecimal("20000.00"))));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, request))
                .isInstanceOf(InvalidAssetStateException.class);
    }

    // ---------- (f) currency guard mirrors the liability guard (Task 6 deferred minor) ----------

    @Test
    @DisplayName("CAPITAL_IMPROVEMENT currency mismatch with the asset is rejected")
    void capitalImprovementAssetCurrencyMismatchIsRejected() {
        TransactionRequest request =
                assetRequest(new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT);
        request.setCurrency("EUR");
        stubCreate(
                request,
                assetEntity(null, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT),
                assetEntity(TX_ID, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT));
        when(assetRepository.findByIdAndUserId(ASSET_ID, USER_ID))
                .thenReturn(
                        Optional.of(
                                assetFixture(
                                        AssetType.VEHICLE,
                                        BigDecimal.ONE,
                                        new BigDecimal("20000.00"))));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, request))
                .isInstanceOf(org.openfinance.exception.InvalidTransactionException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName(
            "FX CAPITAL_IMPROVEMENT applies the asset-currency total from the conversion fields")
    void fxCapitalImprovementAppliesAssetCurrencyTotal() {
        TransactionRequest request =
                assetRequest(new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT);
        request.setCurrency("EUR");
        request.setOriginalAmount(new BigDecimal("1000.00"));
        request.setOriginalCurrency("USD");
        request.setConversionRate(new BigDecimal("0.5000"));
        stubCreate(
                request,
                assetEntity(null, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT),
                assetEntity(TX_ID, new BigDecimal("500.00"), MovementType.CAPITAL_IMPROVEMENT));
        when(assetRepository.findByIdAndUserId(ASSET_ID, USER_ID))
                .thenReturn(
                        Optional.of(
                                assetFixture(
                                        AssetType.VEHICLE,
                                        BigDecimal.ONE,
                                        new BigDecimal("20000.00"))));

        transactionService.createTransaction(USER_ID, request);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPrice())
                .isEqualByComparingTo(new BigDecimal("21000.00"));
    }
}
