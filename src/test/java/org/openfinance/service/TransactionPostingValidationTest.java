package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction posting rejects inconsistent money and ownership before writing")
class TransactionPostingValidationTest {
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private RealEstateRepository realEstateRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private LiabilityTrancheRepository liabilityTrancheRepository;
    @Mock private TransactionSplitService transactionSplitService;
    @Mock private AccountCurrencyService accountCurrencyService;
    @Mock private InvestmentMovementService investmentMovementService;
    @Mock private NetWorthRepository netWorthRepository;
    @Mock private BudgetAlertService budgetAlertService;
    @Mock private OperationHistoryService operationHistoryService;
    @Mock private SearchTokenService searchTokenService;
    @Mock private CurrencyConversionHelper currencyConversionHelper;
    @Mock private JdbcTemplate jdbcTemplate;
    @Spy private TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);
    @InjectMocks private TransactionService service;
    private Account account;

    @BeforeEach
    void account() {
        account =
                Account.builder()
                        .id(10L)
                        .userId(1L)
                        .name("Cash")
                        .type(AccountType.CHECKING)
                        .currency("EUR")
                        .balance(new BigDecimal("1000"))
                        .openingDate(LocalDate.of(2020, 1, 1))
                        .build();
        lenient()
                .when(accountRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(account));
    }

    private TransactionRequest request() {
        return TransactionRequest.builder()
                .accountId(10L)
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100"))
                .currency("EUR")
                .date(LocalDate.of(2025, 6, 1))
                .build();
    }

    private void rejected(TransactionRequest request, String message) {
        assertThatThrownBy(() -> service.createTransaction(1L, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining(message);
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo("1000");
    }

    @ParameterizedTest
    @CsvSource({"-1,CAPITAL_IMPROVEMENT", "-1,MAINTENANCE", "1,MAINTENANCE", "1,REVALUATION"})
    void valuationCanOnlyIncreaseThroughAnImprovement(String value, MovementType movement) {
        TransactionRequest request = request();
        request.setMovementType(movement);
        request.setValuationChange(new BigDecimal(value));
        rejected(request, "valuation increase");
    }

    @ParameterizedTest
    @CsvSource({
        "CAPITAL_IMPROVEMENT,INCOME",
        "CAPITAL_IMPROVEMENT,TRANSFER",
        "MAINTENANCE,INCOME",
        "MAINTENANCE,TRANSFER"
    })
    void upkeepCannotCreditAnAccount(MovementType movement, TransactionType type) {
        TransactionRequest request = request();
        request.setMovementType(movement);
        request.setType(type);
        rejected(request, "must be expenses");
    }

    @ParameterizedTest
    @CsvSource({
        "DISBURSEMENT,EXPENSE",
        "DISBURSEMENT,TRANSFER",
        "REPAYMENT,INCOME",
        "REPAYMENT,TRANSFER",
        "INTEREST,INCOME",
        "INSURANCE,INCOME",
        "FEE,INCOME",
        "REVALUATION,EXPENSE"
    })
    void loanClassificationMustMatchCashDirection(MovementType movement, TransactionType type) {
        TransactionRequest request = request();
        request.setLiabilityId(20L);
        request.setMovementType(movement);
        request.setType(type);
        rejected(request, "direction or classification");
    }

    @ParameterizedTest
    @EnumSource(
            value = MovementType.class,
            names = {"REPAYMENT", "INTEREST", "INSURANCE", "FEE"})
    void debtPaymentsCannotBePostedWithoutADebt(MovementType movement) {
        TransactionRequest request = request();
        request.setMovementType(movement);
        rejected(request, "requires a liability");
    }

    @Test
    void aPrincipalComponentRequiresALiabilityEvenWhenZero() {
        TransactionRequest request = request();
        request.setPrincipalAmount(BigDecimal.ZERO);
        rejected(request, "requires a liability");
    }

    @ParameterizedTest
    @ValueSource(strings = {"asset", "property", "tranche"})
    void savingsInterestCannotDisguiseAnInstrumentCharge(String linked) {
        TransactionRequest request = request();
        request.setType(TransactionType.INCOME);
        request.setMovementType(MovementType.INTEREST);
        switch (linked) {
            case "asset" -> request.setAssetId(2L);
            case "property" -> request.setRealEstateId(2L);
            case "tranche" -> request.setTrancheId(2L);
            default -> throw new AssertionError(linked);
        }
        rejected(request, "requires a liability");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.000000000000000001"})
    void amountMustBeStrictlyPositive(String amount) {
        TransactionRequest request = request();
        request.setAmount(amount == null ? null : new BigDecimal(amount));
        assertThatThrownBy(() -> service.createTransaction(1L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verify(transactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void conversionMetadataMustBeAnAtomicTriple(int present) {
        TransactionRequest request = request();
        if ((present & 1) != 0) request.setOriginalAmount(new BigDecimal("80"));
        if ((present & 2) != 0) request.setOriginalCurrency("GBP");
        if ((present & 4) != 0) request.setConversionRate(new BigDecimal("1.25"));
        assertThatThrownBy(() -> service.createTransaction(1L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verify(transactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "0|GBP|1.25",
                "-80|GBP|1.25",
                "80|GBP|0",
                "80|GBP|-1.25",
                "80|G2P|1.25",
                "80|GB|1.25",
                "80|ABCDEFGHIJK|1.25"
            },
            delimiter = '|')
    void conversionRequiresPositiveValuesAndACurrencyCode(
            String original, String currency, String rate) {
        TransactionRequest request = request();
        request.setOriginalAmount(new BigDecimal(original));
        request.setOriginalCurrency(currency);
        request.setConversionRate(new BigDecimal(rate));
        assertThatThrownBy(() -> service.createTransaction(1L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verify(transactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"100,1.1", "99,1"})
    void sameCurrencyMetadataCannotInventAConversion(String original, String rate) {
        TransactionRequest request = request();
        request.setOriginalAmount(new BigDecimal(original));
        request.setOriginalCurrency("eur");
        request.setConversionRate(new BigDecimal(rate));
        rejected(request, "Same-currency");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 21L})
    void aTrancheCannotBeDrawnWithoutItsOwningLiability(long liabilityId) {
        TransactionRequest request = request();
        request.setType(TransactionType.INCOME);
        request.setMovementType(MovementType.DISBURSEMENT);
        request.setTrancheId(5L);
        request.setLiabilityId(liabilityId == 0 ? null : liabilityId);
        when(liabilityTrancheRepository.findByIdAndUserId(5L, 1L))
                .thenReturn(
                        Optional.of(
                                LiabilityTranche.builder()
                                        .id(5L)
                                        .liabilityId(20L)
                                        .userId(1L)
                                        .build()));
        rejected(request, "does not belong");
    }

    @ParameterizedTest
    @MethodSource("costDates")
    void upkeepMustFallWithinPropertyOwnership(
            AcquisitionType acquisition, LocalDate acquired, LocalDate sold, LocalDate date) {
        TransactionRequest request = request();
        request.setRealEstateId(3L);
        request.setMovementType(MovementType.MAINTENANCE);
        request.setDate(date);
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(3L)
                        .userId(1L)
                        .isActive(true)
                        .acquisitionType(acquisition)
                        .purchaseDate(acquired)
                        .soldDate(sold)
                        .build();
        when(realEstateRepository.existsByIdAndUserId(3L, 1L)).thenReturn(true);
        when(realEstateRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(property));
        rejected(request, "during ownership");
    }

    static Stream<Arguments> costDates() {
        LocalDate today = LocalDate.now();
        return Stream.of(
                Arguments.of(AcquisitionType.PLANNED, today.minusYears(1), null, today),
                Arguments.of(
                        AcquisitionType.PURCHASE, today.minusDays(3), null, today.minusDays(4)),
                Arguments.of(
                        AcquisitionType.PURCHASE, null, today.minusDays(3), today.minusDays(2)),
                Arguments.of(AcquisitionType.GIFT, today.minusYears(1), null, today.plusDays(1)));
    }

    @Test
    void archivedPropertiesCannotReceiveExpenses() {
        TransactionRequest request = request();
        request.setRealEstateId(3L);
        request.setMovementType(MovementType.MAINTENANCE);
        when(realEstateRepository.existsByIdAndUserId(3L, 1L)).thenReturn(true);
        when(realEstateRepository.findByIdAndUserId(3L, 1L))
                .thenReturn(
                        Optional.of(
                                RealEstateProperty.builder()
                                        .id(3L)
                                        .userId(1L)
                                        .isActive(false)
                                        .build()));
        rejected(request, "Restore the property");
    }

    @ParameterizedTest
    @CsvSource({
        "false,type", "false,asset", "false,property", "false,liability", "false,movement",
                "false,investment",
        "true,type", "true,asset", "true,property", "true,liability", "true,movement",
                "true,investment",
        "true,currency", "true,before", "true,future"
    })
    void saleUpdatesCannotChangeTheOwnedItemOrTheMeaningOfThePayment(
            boolean propertySale, String change) {
        LocalDate purchase = LocalDate.now().minusYears(1);
        Transaction old =
                Transaction.builder()
                        .id(9L)
                        .userId(1L)
                        .accountId(10L)
                        .assetId(propertySale ? null : 2L)
                        .realEstateId(propertySale ? 3L : null)
                        .type(TransactionType.INCOME)
                        .currency("EUR")
                        .amount(new BigDecimal("100"))
                        .date(LocalDate.now().minusDays(1))
                        .build();
        when(transactionRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(old));
        when(currencyConversionHelper.convertTransaction(old, null, null))
                .thenReturn(
                        new CurrencyConversionHelper.ConversionResult(
                                "EUR", old.getAmount(), null, false, null, null, null));
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(3L)
                        .userId(1L)
                        .currency("EUR")
                        .purchaseDate(purchase)
                        .build();
        if (propertySale)
            when(realEstateRepository.findBySaleTransactionIdAndUserId(9L, 1L))
                    .thenReturn(Optional.of(property));
        else
            when(assetRepository.findBySaleTransactionIdAndUserId(9L, 1L))
                    .thenReturn(
                            Optional.of(
                                    Asset.builder()
                                            .id(2L)
                                            .userId(1L)
                                            .type(AssetType.VEHICLE)
                                            .currency("EUR")
                                            .purchaseDate(purchase)
                                            .build()));
        TransactionRequest request = request();
        request.setType(TransactionType.INCOME);
        request.setAssetId(old.getAssetId());
        request.setRealEstateId(old.getRealEstateId());
        request.setDate(LocalDate.now());
        switch (change) {
            case "type" -> request.setType(TransactionType.EXPENSE);
            case "asset" -> request.setAssetId(99L);
            case "property" -> request.setRealEstateId(99L);
            case "liability" -> request.setLiabilityId(99L);
            case "movement" -> request.setMovementType(MovementType.FEE);
            case "investment" -> request.setInvestment(
                    org.openfinance.dto.InvestmentMovementRequest.builder()
                            .assetId(2L)
                            .operation(org.openfinance.dto.InvestmentMovementRequest.Operation.BUY)
                            .quantity(BigDecimal.ONE)
                            .build());
            case "currency" -> request.setCurrency("USD");
            case "before" -> request.setDate(purchase.minusDays(1));
            case "future" -> request.setDate(LocalDate.now().plusDays(1));
            default -> throw new AssertionError(change);
        }
        assertThatThrownBy(() -> service.updateTransaction(9L, 1L, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Keep the sale linked");
        verify(transactionRepository, never()).save(any());
        assertThat(property.getSoldDate()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void saleEditsKeepTheirDateWithinOwnership(int offset) {
        LocalDate purchase = LocalDate.now().minusYears(1);
        Transaction old =
                Transaction.builder()
                        .id(9L)
                        .userId(1L)
                        .accountId(10L)
                        .assetId(2L)
                        .type(TransactionType.INCOME)
                        .currency("EUR")
                        .amount(new BigDecimal("100"))
                        .date(LocalDate.now().minusDays(1))
                        .build();
        when(transactionRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(old));
        when(currencyConversionHelper.convertTransaction(old, null, null))
                .thenReturn(
                        new CurrencyConversionHelper.ConversionResult(
                                "EUR", old.getAmount(), null, false, null, null, null));
        Asset asset =
                Asset.builder()
                        .id(2L)
                        .userId(1L)
                        .type(AssetType.VEHICLE)
                        .currency("EUR")
                        .purchaseDate(purchase)
                        .build();
        when(assetRepository.findBySaleTransactionIdAndUserId(9L, 1L))
                .thenReturn(Optional.of(asset));
        TransactionRequest request = request();
        request.setAssetId(2L);
        request.setType(TransactionType.INCOME);
        request.setDate(offset < 0 ? purchase.minusDays(1) : LocalDate.now().plusDays(1));
        if (offset == 0) request.setCurrency("USD");
        assertThatThrownBy(() -> service.updateTransaction(9L, 1L, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Keep the sale linked");
        verify(transactionRepository, never()).save(any());
        assertThat(asset.getSoldDate()).isNull();
    }
}
