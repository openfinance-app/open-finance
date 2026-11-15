package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AssetFinancingLink;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityAssetLink;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.LiabilityAssetLinkRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Financing allocation boundaries preserve existing ownership and principal")
class AssetFinancingBoundaryTest {
    @Mock private LiabilityAssetLinkRepository links;
    @Mock private LiabilityRepository liabilities;
    @Mock private AccountRepository accounts;
    @Mock private AssetRepository assets;
    @Mock private RealEstateRepository properties;
    @Mock private ExchangeRateService rates;
    @InjectMocks private AssetFinancingServiceImpl service;

    private Asset asset(Long id) {
        return Asset.builder()
                .id(id)
                .userId(7L)
                .type(AssetType.VEHICLE)
                .currency("EUR")
                .acquisitionType(AcquisitionType.PURCHASE)
                .build();
    }

    private Liability loan() {
        Liability loan = new Liability();
        loan.setId(3L);
        loan.setUserId(7L);
        loan.setCurrency("EUR");
        loan.setCurrentBalance("1000.01");
        return loan;
    }

    private void ownedLoan() {
        when(liabilities.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(loan()));
    }

    private void ownedAsset(Asset asset) {
        when(assets.findByIdAndUserId(asset.getId(), 7L)).thenReturn(Optional.of(asset));
    }

    private AssetFinancingLink requested(Long id, String relationship, String percent) {
        return AssetFinancingLink.builder()
                .assetId(id)
                .relationship(relationship)
                .allocationPercentage(percent == null ? null : new BigDecimal(percent))
                .build();
    }

    private LiabilityAssetLink link(Long id, String relationship, String percent) {
        return LiabilityAssetLink.builder()
                .userId(7L)
                .liabilityId(3L)
                .assetId(id)
                .relationship(relationship)
                .allocationPercentage(new BigDecimal(percent))
                .build();
    }

    private RealEstateProperty property(Long id, Long assetId, boolean active) {
        return RealEstateProperty.builder()
                .id(id)
                .userId(7L)
                .assetId(assetId)
                .mortgageId(3L)
                .isActive(active)
                .build();
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "FINANCING,NULL",
                "FINANCING,-0.01",
                "FINANCING,100.01",
                "FINANCING,0",
                "COLLATERAL,0.01",
                "OTHER,0"
            },
            nullValues = "NULL")
    void rejectsInvalidAllocationsBeforeReplacingAnyExistingRelationship(
            String relationship, String percentage) {
        ownedLoan();
        ownedAsset(asset(1L));
        assertThatThrownBy(
                        () ->
                                service.replace(
                                        7L, 3L, List.of(requested(1L, relationship, percentage))))
                .isInstanceOf(InvalidTransactionException.class);
        verify(links, never()).deleteByLiabilityIdAndUserId(any(), any());
        verify(links, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "total-over-100"})
    void rejectsDuplicateOrOverallocatedSharesWithoutDestroyingExistingAllocations(String invalid) {
        ownedLoan();
        ownedAsset(asset(1L));
        Long second = invalid.equals("duplicate") ? 1L : 2L;
        if (second == 2L) ownedAsset(asset(2L));
        assertThatThrownBy(
                        () ->
                                service.replace(
                                        7L,
                                        3L,
                                        List.of(
                                                requested(1L, "FINANCING", "60"),
                                                requested(second, "FINANCING", "50"))))
                .isInstanceOf(InvalidTransactionException.class);
        verify(links, never()).deleteByLiabilityIdAndUserId(any(), any());
        verify(links, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"financial", "planned", "sold", "missing-property", "inactive-property"})
    void newlyLinkedCollateralMustBeAnOwnedPhysicalPossession(String invalid) {
        Asset target = asset(1L);
        switch (invalid) {
            case "financial" -> target.setType(AssetType.STOCK);
            case "planned" -> target.setAcquisitionType(AcquisitionType.PLANNED);
            case "sold" -> target.setSoldDate(LocalDate.now());
            default -> {
                target.setType(AssetType.REAL_ESTATE);
                when(properties.findByAssetIdAndUserId(1L, 7L))
                        .thenReturn(
                                invalid.equals("missing-property")
                                        ? Optional.empty()
                                        : Optional.of(property(8L, 1L, false)));
            }
        }
        ownedLoan();
        ownedAsset(target);
        assertThatThrownBy(() -> service.replace(7L, 3L, List.of(requested(1L, "COLLATERAL", "0"))))
                .isInstanceOf(InvalidTransactionException.class);
        verify(links, never()).deleteByLiabilityIdAndUserId(any(), any());
    }

    @Test
    void keepingHistoricalFinancingOnASoldAssetDoesNotRequireRestoringTheAsset() {
        Asset target = asset(1L);
        target.setSoldDate(LocalDate.now());
        ownedLoan();
        ownedAsset(target);
        when(links.findByLiabilityIdAndUserId(3L, 7L))
                .thenReturn(List.of(link(1L, "FINANCING", "100")));
        assertThat(service.replace(7L, 3L, List.of(requested(1L, "FINANCING", "50")))).hasSize(1);
        verify(links)
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                saved ->
                                        saved.getAllocationPercentage()
                                                        .compareTo(new BigDecimal("50"))
                                                == 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-financing", "other-asset", "collateral-only", "empty"})
    void removingAPrimaryAllocationDoesNotSilentlyRecreateItsImplicitFullShare(String replacement) {
        ownedLoan();
        RealEstateProperty primary = property(8L, 1L, true);
        RealEstateProperty foreign = property(9L, 3L, true);
        foreign.setUserId(8L);
        when(properties.findByMortgageId(3L)).thenReturn(List.of(primary, foreign));
        if (!replacement.equals("other-asset")) ownedAsset(asset(1L));
        Long target = replacement.equals("other-asset") ? 2L : 1L;
        if (target == 2L) ownedAsset(asset(2L));
        List<AssetFinancingLink> request =
                replacement.equals("empty")
                        ? List.of()
                        : List.of(
                                requested(
                                        target,
                                        replacement.equals("collateral-only")
                                                ? "COLLATERAL"
                                                : "FINANCING",
                                        replacement.equals("collateral-only") ? "0" : "100"));
        // Return the rows that would remain after replacement, so an implicit primary can be
        // observed.
        when(links.findByLiabilityIdAndUserId(3L, 7L))
                .thenReturn(
                        request.stream()
                                .map(
                                        r ->
                                                link(
                                                        r.getAssetId(),
                                                        r.getRelationship(),
                                                        r.getAllocationPercentage()
                                                                .toPlainString()))
                                .toList());
        service.replace(7L, 3L, request);
        assertThat(primary.getMortgageId())
                .isEqualTo(replacement.equals("same-financing") ? 3L : null);
        assertThat(foreign.getMortgageId()).isEqualTo(3L);
        verify(links).deleteByLiabilityIdAndUserId(3L, 7L);
        verify(links).flush();
    }

    @Test
    void primaryMortgageFallbackSkipsForeignAndUnlinkedPropertiesAndPrefersAnActiveOne() {
        ownedLoan();
        ownedAsset(asset(2L));
        RealEstateProperty foreign = property(1L, 1L, true);
        foreign.setUserId(8L);
        when(properties.findByMortgageId(3L))
                .thenReturn(
                        List.of(
                                property(2L, null, true),
                                property(3L, 3L, false),
                                foreign,
                                property(4L, 2L, true)));
        assertThat(service.forLiability(7L, 3L))
                .singleElement()
                .satisfies(
                        result -> {
                            assertThat(result.getAssetId()).isEqualTo(2L);
                            assertThat(result.isPrimaryMortgage()).isTrue();
                            assertThat(result.getAllocatedBalance())
                                    .isEqualByComparingTo("1000.01");
                        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "existing-share",
                "other-financing",
                "collateral",
                "other-primary",
                "inactive-primary",
                "same-primary"
            })
    void directFundingRespectsExplicitSharesAndOtherActivePrimaryProperties(String state) {
        ownedAsset(asset(1L));
        ownedLoan();
        if (state.equals("existing-share"))
            when(links.findByLiabilityIdAndUserId(3L, 7L))
                    .thenReturn(List.of(link(1L, "FINANCING", "40")));
        if (state.equals("other-financing"))
            when(links.findByLiabilityIdAndUserId(3L, 7L))
                    .thenReturn(List.of(link(2L, "FINANCING", "40")));
        if (state.equals("collateral"))
            when(links.findByLiabilityIdAndUserId(3L, 7L))
                    .thenReturn(List.of(link(1L, "COLLATERAL", "0")));
        if (state.contains("primary"))
            when(properties.findByMortgageId(3L))
                    .thenReturn(
                            List.of(
                                    property(
                                            8L,
                                            state.equals("same-primary") ? 1L : 2L,
                                            !state.equals("inactive-primary"))));
        if (state.equals("other-financing") || state.equals("other-primary")) {
            assertThatThrownBy(() -> service.ensureDirectFinancing(7L, 3L, 1L))
                    .isInstanceOf(InvalidTransactionException.class);
            verify(links, never()).save(any());
        } else {
            service.ensureDirectFinancing(7L, 3L, 1L);
            if (state.equals("existing-share")) verify(links, never()).save(any());
            else
                verify(links)
                        .save(
                                org.mockito.ArgumentMatchers.argThat(
                                        saved ->
                                                saved.getAssetId().equals(1L)
                                                        && saved.getAllocationPercentage()
                                                                        .compareTo(
                                                                                new BigDecimal(
                                                                                        "100"))
                                                                == 0));
        }
    }

    @Test
    void removingAPrimaryLoanDeletesOnlyThisAssetsFinancingLink() {
        LiabilityAssetLink removed = link(1L, "FINANCING", "50");
        LiabilityAssetLink collateral = link(1L, "COLLATERAL", "0");
        LiabilityAssetLink otherAsset = link(2L, "FINANCING", "50");
        when(links.findByLiabilityIdAndUserId(3L, 7L))
                .thenReturn(List.of(removed, collateral, otherAsset));
        service.synchronizePrimary(7L, 1L, 3L, null);
        verify(links).delete(removed);
        verify(links, never()).delete(collateral);
        verify(links, never()).delete(otherAsset);
        verify(links).flush();
        verifyNoInteractions(assets, liabilities);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"1,3,3", "NULL,3,4", "1,NULL,NULL"},
            nullValues = "NULL")
    void unchangedOrAbsentPrimaryAssetHasNoFinancingSideEffects(
            Long assetId, Long previous, Long next) {
        service.synchronizePrimary(7L, assetId, previous, next);
        verifyNoInteractions(links, assets, liabilities, properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-mortgage", "explicit-financing", "collateral-only"})
    void legacyPropertiesOnlyUseImplicitDebtWhenNoExplicitFinancingExists(String state) {
        Long mortgage = state.equals("no-mortgage") ? null : 3L;
        if (mortgage != null)
            when(links.findByLiabilityIdAndUserId(3L, 7L))
                    .thenReturn(
                            List.of(
                                    link(
                                            1L,
                                            state.equals("explicit-financing")
                                                    ? "FINANCING"
                                                    : "COLLATERAL",
                                            state.equals("explicit-financing") ? "100" : "0")));
        if (state.equals("collateral-only")) ownedLoan();
        assertThat(service.propertyDebt(7L, null, mortgage, "eur"))
                .isEqualByComparingTo(state.equals("collateral-only") ? "1000.01" : "0");
        verifyNoInteractions(rates);
    }
}
