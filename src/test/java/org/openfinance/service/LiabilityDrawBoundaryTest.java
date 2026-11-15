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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.DisbursementRequest;
import org.openfinance.dto.LiabilityTrancheResponse;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Direct loan funding rejects unavailable assets and protects already repaid draws")
class LiabilityDrawBoundaryTest {
    @Mock private LiabilityRepository loans;
    @Mock private LiabilityTrancheRepository tranches;
    @Mock private RealEstateRepository properties;
    @Mock private AssetRepository backingAssetRepository;
    @Mock private LiabilityTrancheService trancheService;
    @Mock private AssetFinancingService financing;
    @Mock private TransactionRepository transactions;
    @Mock private TransactionService transactionService;
    @Mock private NetWorthRepository netWorthRepository;
    @InjectMocks private LiabilityService service;
    private Liability loan;
    private LiabilityTranche tranche;
    private static final LocalDate DATE = LocalDate.of(2024, 1, 10);

    @BeforeEach
    void prepare() {
        loan = new Liability();
        loan.setId(3L);
        loan.setUserId(1L);
        loan.setCurrency("EUR");
        loan.setStartDate(DATE.minusDays(1));
        loan.setCurrentBalance("100");
        tranche =
                LiabilityTranche.builder()
                        .id(7L)
                        .userId(1L)
                        .liabilityId(3L)
                        .trancheNo(1)
                        .plannedAmount(new BigDecimal("100"))
                        .status(TrancheStatus.PLANNED)
                        .currency("EUR")
                        .build();
        lenient().when(loans.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(loan));
        lenient().when(tranches.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(tranche));
    }

    private DisbursementRequest request() {
        return DisbursementRequest.builder()
                .trancheId(7L)
                .directAssetId(9L)
                .amount(new BigDecimal("20"))
                .date(DATE)
                .build();
    }

    private Asset asset() {
        return Asset.builder()
                .id(9L)
                .userId(1L)
                .type(AssetType.VEHICLE)
                .currency("EUR")
                .quantity(BigDecimal.ONE)
                .purchasePrice(new BigDecimal("100"))
                .currentPrice(new BigDecimal("100"))
                .purchaseDate(DATE.minusDays(1))
                .acquisitionType(AcquisitionType.PURCHASE)
                .build();
    }

    private void assertUnchanged() {
        assertThat(loan.getCurrentBalance()).isEqualTo("100");
        verify(loans, never()).save(any());
        verify(transactionService, never()).createTransaction(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"represented", "wrong-loan", "drawn", "cancelled"})
    void cannotDrawFromAnUnavailableLoanOrTranche(String invalid) {
        switch (invalid) {
            case "represented" -> loan.setRepresentedByAccountId(12L);
            case "wrong-loan" -> tranche.setLiabilityId(99L);
            case "drawn" -> tranche.setStatus(TrancheStatus.DRAWN);
            case "cancelled" -> tranche.setStatus(TrancheStatus.CANCELLED);
        }
        assertThatThrownBy(() -> service.disburse(1L, 3L, request()))
                .isInstanceOf(InvalidTransactionException.class);
        assertUnchanged();
        verify(backingAssetRepository, never()).findByIdAndUserId(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-property", "bank-and-property", "increase-without-cost"})
    void propertyCapitalizationMustUseTheDirectPropertyRoute(String invalid) {
        DisbursementRequest request = request();
        if (invalid.equals("increase-without-cost")) request.setIncreasePropertyValue(true);
        else {
            request.setCapitalizePropertyCost(true);
            if (invalid.equals("bank-and-property")) {
                request.setDirectRealEstateId(5L);
                request.setToAccountId(12L);
            }
        }
        assertThatThrownBy(() -> service.disburse(1L, 3L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verify(loans, never()).findByIdAndUserId(any(), any());
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "security",
                "planned",
                "sold",
                "before-purchase",
                "foreign-currency"
            })
    void cannotFundAnUnownedOrIneligibleAsset(String invalid) {
        Asset asset = asset();
        switch (invalid) {
            case "security" -> asset.setType(AssetType.STOCK);
            case "planned" -> asset.setAcquisitionType(AcquisitionType.PLANNED);
            case "sold" -> asset.setSoldDate(DATE);
            case "before-purchase" -> asset.setPurchaseDate(DATE.plusDays(1));
            case "foreign-currency" -> asset.setCurrency("USD");
        }
        when(backingAssetRepository.findByIdAndUserId(9L, 1L))
                .thenReturn(invalid.equals("missing") ? Optional.empty() : Optional.of(asset));
        assertThatThrownBy(() -> service.disburse(1L, 3L, request()))
                .isInstanceOf(InvalidTransactionException.class);
        assertUnchanged();
        verify(financing, never()).ensureDirectFinancing(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "inactive"})
    void directPropertyMustBelongToTheUserAndBeActive(String invalid) {
        DisbursementRequest request = request();
        request.setDirectAssetId(null);
        request.setDirectRealEstateId(5L);
        when(properties.findByIdAndUserId(5L, 1L))
                .thenReturn(
                        invalid.equals("missing")
                                ? Optional.empty()
                                : Optional.of(
                                        RealEstateProperty.builder()
                                                .id(5L)
                                                .userId(1L)
                                                .assetId(9L)
                                                .isActive(false)
                                                .build()));
        assertThatThrownBy(() -> service.disburse(1L, 3L, request))
                .isInstanceOf(InvalidTransactionException.class);
        assertUnchanged();
    }

    private void drawn() {
        tranche.setDirectDisbursement(true);
        tranche.setStatus(TrancheStatus.DRAWN);
        tranche.setDrawnDate(DATE);
        tranche.setDrawnAmount(new BigDecimal("100"));
        lenient().when(trancheService.allocatedPrincipal(tranche)).thenReturn(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "bank-receipt",
                "planned",
                "cancelled",
                "already-reversed",
                "no-date",
                "before-draw",
                "future",
                "repaid",
                "inconsistent-debt"
            })
    void reversalDoesNotEraseRepaymentsOrRewriteInvalidHistoricalDates(String invalid) {
        drawn();
        LocalDate date = DATE;
        switch (invalid) {
            case "bank-receipt" -> tranche.setDirectDisbursement(false);
            case "planned" -> tranche.setStatus(TrancheStatus.PLANNED);
            case "cancelled" -> tranche.setStatus(TrancheStatus.CANCELLED);
            case "already-reversed" -> tranche.setReversedDate(DATE);
            case "no-date" -> date = null;
            case "before-draw" -> date = DATE.minusDays(1);
            case "future" -> date = LocalDate.now().plusDays(1);
            case "repaid" -> when(trancheService.allocatedPrincipal(tranche))
                    .thenReturn(new BigDecimal("0.01"));
            case "inconsistent-debt" -> tranche.setDrawnAmount(new BigDecimal("100.01"));
        }
        LocalDate reversalDate = date;
        assertThatThrownBy(() -> service.reverseDirectDraw(1L, 7L, reversalDate))
                .isInstanceOf(InvalidTransactionException.class);
        assertUnchanged();
        verify(tranches, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-day", "today"})
    void reversalAtEitherInclusiveDateBoundaryCancelsOnlyTheDraw(String boundary) {
        drawn();
        LocalDate date = boundary.equals("same-day") ? DATE : LocalDate.now();
        LiabilityTrancheResponse result = service.reverseDirectDraw(1L, 7L, date);
        assertThat(loan.getCurrentBalance()).isEqualTo("0");
        assertThat(result.getStatus()).isEqualTo(TrancheStatus.CANCELLED);
        assertThat(result.getReversedDate()).isEqualTo(date);
        assertThat(result.getDrawnDate()).isEqualTo(DATE);
        assertThat(result.getDrawnAmount()).isEqualByComparingTo("100");
        verify(netWorthRepository)
                .deleteByUserIdAndSnapshotDateBetween(
                        1L, loan.getStartDate().withDayOfMonth(1), LocalDate.now());
    }

    @Test
    void propertyDrawdownsExcludeOtherPropertiesAndUndrawnPlansAndSortStably() {
        when(properties.findByIdAndUserId(5L, 1L))
                .thenReturn(Optional.of(RealEstateProperty.builder().id(5L).userId(1L).build()));
        LiabilityTranche first = draw(1L, 5L, DATE, "10");
        LiabilityTranche sameDay = draw(2L, 5L, DATE, "20");
        LiabilityTranche latest = draw(3L, 5L, DATE.plusDays(1), "30");
        when(tranches.findByUserId(1L))
                .thenReturn(
                        List.of(
                                latest,
                                draw(4L, 6L, DATE, "40"),
                                sameDay,
                                draw(5L, 5L, DATE, null),
                                draw(6L, null, DATE, "60"),
                                first));
        assertThat(service.getPropertyDrawdowns(1L, 5L))
                .extracting(LiabilityTrancheResponse::getId)
                .containsExactly(1L, 2L, 3L);
    }

    private LiabilityTranche draw(long id, Long property, LocalDate date, String amount) {
        return LiabilityTranche.builder()
                .id(id)
                .userId(1L)
                .liabilityId(3L)
                .realEstateId(property)
                .status(amount == null ? TrancheStatus.PLANNED : TrancheStatus.DRAWN)
                .drawnDate(date)
                .drawnAmount(amount == null ? null : new BigDecimal(amount))
                .currency("EUR")
                .build();
    }
}
