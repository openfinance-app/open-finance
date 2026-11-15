package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.PropertyConversionCandidate;
import org.openfinance.dto.PropertyConversionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetPriceHistoryRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.PropertyConversionReceiptRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName(
        "Property conversion only accepts reviewed, owned holdings and preserves shared accounts")
class PropertyConversionBoundaryTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    @Mock private AssetRepository assets;
    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private AssetPriceHistoryRepository prices;
    @Mock private RealEstateRepository properties;
    @Mock private RealEstateValueHistoryRepository valuations;
    @Mock private PropertyConversionReceiptRepository receipts;
    @Mock private RealEstateService realEstate;
    @Mock private AccountService accountService;
    @Spy private ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @InjectMocks private PropertyConversionServiceImpl service;

    private Asset asset(Long id) {
        return Asset.builder()
                .id(id)
                .userId(7L)
                .accountId(3L)
                .name("Imported holding " + id)
                .type(AssetType.STOCK)
                .currency("EUR")
                .quantity(BigDecimal.ONE)
                .purchasePrice(BigDecimal.TEN)
                .currentPrice(new BigDecimal("20"))
                .purchaseDate(DATE)
                .importOpeningQuantity(BigDecimal.ONE)
                .importOpeningCost(BigDecimal.TEN)
                .acquisitionType(AcquisitionType.PURCHASE)
                .importSource("skrooge:unit:" + id)
                .build();
    }

    private Account account() {
        return Account.builder()
                .id(3L)
                .userId(7L)
                .name("Source account")
                .currency("EUR")
                .balance(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    private void candidateData(Asset asset, Account account) {
        when(assets.findByUserId(7L)).thenReturn(List.of(asset));
        when(accounts.findByUserId(7L)).thenReturn(account == null ? List.of() : List.of(account));
    }

    private PropertyConversionRequest request(String revision) {
        return new PropertyConversionRequest(
                UUID.randomUUID(),
                1L,
                revision,
                " Home ",
                " Address ",
                PropertyType.RESIDENTIAL,
                null,
                null);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-imported",
                "property",
                "physical",
                "sold",
                "planned",
                "zero-units",
                "negative-units",
                "missing-purchase-date",
                "future-purchase",
                "missing-account",
                "deleted-account",
                "converted-account"
            })
    void hidesIneligibleHoldingsWithoutReadingTheirMovementsOrPrices(String reason) {
        Asset asset = asset(1L);
        Account account = account();
        switch (reason) {
            case "not-imported" -> asset.setImportSource(null);
            case "property" -> asset.setType(AssetType.REAL_ESTATE);
            case "physical" -> asset.setType(AssetType.VEHICLE);
            case "sold" -> asset.setSoldDate(LocalDate.now());
            case "planned" -> asset.setAcquisitionType(AcquisitionType.PLANNED);
            case "zero-units" -> asset.setQuantity(BigDecimal.ZERO);
            case "negative-units" -> asset.setQuantity(BigDecimal.ONE.negate());
            case "missing-purchase-date" -> asset.setPurchaseDate(null);
            case "future-purchase" -> asset.setPurchaseDate(LocalDate.now().plusDays(1));
            case "missing-account" -> account = null;
            case "deleted-account" -> account.setDeletedDate(LocalDate.now());
            case "converted-account" -> account.setLiabilityConvertedAt(LocalDate.now());
            default -> throw new AssertionError(reason);
        }
        candidateData(asset, account);
        assertThat(service.candidates(7L)).isEmpty();
        verifyNoInteractions(transactions, prices, realEstate, valuations, accountService);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "cash",
                "loan-balance",
                "other-investment",
                "planned-investment",
                "sold-investment",
                "empty-investment",
                "physical",
                "property",
                "only-source",
                "inactive-account"
            })
    void closesOnlyAnAccountThatWillNoLongerHoldCashOrOtherFinancialPositions(String state) {
        Asset source = asset(1L);
        Account account = account();
        if (state.equals("cash")) account.setBalance(new BigDecimal("0.01"));
        if (state.equals("inactive-account")) account.setIsActive(false);
        candidateData(source, account);
        if (!state.equals("cash") && !state.equals("inactive-account"))
            when(accounts.isLiabilityBalanceSource(3L, 7L))
                    .thenReturn(state.equals("loan-balance"));
        if (!List.of("cash", "inactive-account", "loan-balance").contains(state)) {
            Asset other = asset(2L);
            if (state.equals("planned-investment")) {
                other.setAcquisitionType(AcquisitionType.PLANNED);
                other.setQuantity(BigDecimal.ZERO);
            }
            if (state.equals("sold-investment")) other.setSoldDate(DATE);
            if (state.equals("empty-investment")) other.setQuantity(BigDecimal.ZERO);
            if (state.equals("physical")) other.setType(AssetType.VEHICLE);
            if (state.equals("property")) other.setType(AssetType.REAL_ESTATE);
            when(assets.findByAccountId(3L))
                    .thenReturn(
                            state.equals("only-source") ? List.of(source) : List.of(source, other));
        }
        assertThat(service.candidates(7L))
                .singleElement()
                .satisfies(
                        candidate -> {
                            assertThat(candidate.closesSourceAccount())
                                    .isEqualTo(
                                            !List.of(
                                                            "cash",
                                                            "loan-balance",
                                                            "other-investment",
                                                            "planned-investment")
                                                    .contains(state));
                            assertThat(candidate.purchasePrice()).isEqualByComparingTo("10");
                            assertThat(candidate.currentValue()).isEqualByComparingTo("20");
                        });
        verifyNoInteractions(accountService, realEstate, receipts);
    }

    @Test
    void deletedMovementsNeitherAffectTheReviewCountNorItsRevision() {
        Asset source = asset(1L);
        candidateData(source, account());
        Transaction deleted =
                Transaction.builder()
                        .id(3L)
                        .assetId(1L)
                        .userId(7L)
                        .date(DATE)
                        .type(TransactionType.EXPENSE)
                        .amount(BigDecimal.TEN)
                        .investmentQuantity(BigDecimal.ONE)
                        .isDeleted(true)
                        .build();
        when(transactions.findByAssetIdAndUserId(1L, 7L)).thenReturn(List.of(deleted), List.of());
        PropertyConversionCandidate before = service.candidates(7L).getFirst();
        PropertyConversionCandidate after = service.candidates(7L).getFirst();
        assertThat(before.transactionCount()).isZero();
        assertThat(before.sourceRevision()).isEqualTo(after.sourceRevision());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unlinked", "already-converted", "revision", "future-movement"})
    void invalidOrOutdatedConversionRequestsDoNotCreatePropertiesOrCloseAccounts(String invalid) {
        Asset source = asset(1L);
        Account account = account();
        if (invalid.equals("unlinked")) source.setAccountId(null);
        when(assets.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(source));
        if (!invalid.equals("unlinked"))
            when(accounts.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(account));
        if (invalid.equals("already-converted"))
            when(properties.findByAssetIdAndUserId(1L, 7L))
                    .thenReturn(Optional.of(RealEstateProperty.builder().id(9L).build()));
        String revision = "outdated";
        if (invalid.equals("future-movement")) {
            candidateData(source, account);
            when(transactions.findByAssetIdAndUserId(1L, 7L))
                    .thenReturn(
                            List.of(
                                    Transaction.builder()
                                            .id(4L)
                                            .assetId(1L)
                                            .userId(7L)
                                            .date(LocalDate.now().plusDays(1))
                                            .type(TransactionType.EXPENSE)
                                            .amount(BigDecimal.TEN)
                                            .currency("EUR")
                                            .investmentQuantity(BigDecimal.ONE)
                                            .build()));
            revision = service.candidates(7L).getFirst().sourceRevision();
        }
        PropertyConversionRequest request = request(revision);
        assertThatThrownBy(() -> service.convert(7L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(realEstate, accountService, valuations);
        verify(receipts, never()).save(any());
    }
}
