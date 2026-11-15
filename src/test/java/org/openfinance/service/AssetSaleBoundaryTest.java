package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AssetSaleRequest;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Selling assets validates ownership, net proceeds and settlement before posting")
class AssetSaleBoundaryTest {
    @Mock AssetRepository assets;
    @Mock RealEstateRepository properties;
    @Mock AccountRepository accounts;
    @Mock TransactionService transactions;
    @Mock NetWorthRepository snapshots;
    @Mock OwnershipReceipts receipts;
    @Mock TransactionRepository payments;
    @InjectMocks AssetSaleServiceImpl service;
    private Asset asset;
    private AssetSaleRequest request;
    private Long previousReceipt;

    @BeforeEach
    void setUp() {
        asset =
                Asset.builder()
                        .id(5L)
                        .userId(1L)
                        .name("Car")
                        .type(AssetType.VEHICLE)
                        .acquisitionType(AcquisitionType.PURCHASE)
                        .currency("EUR")
                        .quantity(BigDecimal.ONE)
                        .purchaseDate(LocalDate.of(2020, 1, 1))
                        .build();
        request = new AssetSaleRequest();
        request.setOperationId(UUID.randomUUID());
        request.setAccountId(3L);
        request.setDate(LocalDate.now());
        request.setQuantity(BigDecimal.ONE);
        request.setProceeds(new BigDecimal("100.01"));
        request.setFees(new BigDecimal("0.01"));
        when(receipts.previous(eq(1L), eq(request.getOperationId()), any()))
                .thenAnswer(invocation -> previousReceipt);
    }

    @Test
    void repeatedOperationReturnsItsReceiptWithoutPostingAgain() {
        previousReceipt = 99L;
        assertThat(service.sell(1L, 5L, request)).isEqualTo(99L);
        verifyNoInteractions(assets, properties, accounts, transactions, snapshots, payments);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "planned",
                "already-sold",
                "before-purchase",
                "future",
                "fees-equal-proceeds",
                "fees-above-proceeds",
                "too-many-units",
                "zero-units",
                "negative-units",
                "partial-physical"
            })
    void rejectsInvalidSalesBeforeTouchingCash(String problem) {
        switch (problem) {
            case "planned" -> asset.setAcquisitionType(AcquisitionType.PLANNED);
            case "already-sold" -> asset.setSoldDate(LocalDate.now());
            case "before-purchase" -> request.setDate(asset.getPurchaseDate().minusDays(1));
            case "future" -> request.setDate(LocalDate.now().plusDays(1));
            case "fees-equal-proceeds" -> request.setFees(request.getProceeds());
            case "fees-above-proceeds" -> request.setFees(
                    request.getProceeds().add(BigDecimal.ONE));
            case "too-many-units" -> request.setQuantity(new BigDecimal("2"));
            case "zero-units" -> request.setQuantity(BigDecimal.ZERO);
            case "negative-units" -> request.setQuantity(BigDecimal.ONE.negate());
            case "partial-physical" -> request.setQuantity(new BigDecimal("0.5"));
            case "missing" -> {}
            default -> throw new AssertionError(problem);
        }
        when(assets.findByIdAndUserId(5L, 1L))
                .thenReturn(problem.equals("missing") ? Optional.empty() : Optional.of(asset));
        assertThatThrownBy(() -> service.sell(1L, 5L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(accounts, transactions, payments, snapshots);
        verify(assets, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "closed",
                "unknown-status",
                "foreign-currency",
                "other-investment-account"
            })
    void rejectsAnUnavailableOrIncompatibleReceivingAccount(String problem) {
        Account account =
                Account.builder().id(3L).userId(1L).isActive(true).currency("EUR").build();
        switch (problem) {
            case "closed" -> account.setIsActive(false);
            case "unknown-status" -> account.setIsActive(null);
            case "foreign-currency" -> account.setCurrency("USD");
            case "other-investment-account" -> {
                asset.setType(AssetType.STOCK);
                asset.setAccountId(4L);
            }
            case "missing" -> {}
            default -> throw new AssertionError(problem);
        }
        when(assets.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(asset));
        when(accounts.findByIdAndUserId(3L, 1L))
                .thenReturn(problem.equals("missing") ? Optional.empty() : Optional.of(account));
        assertThatThrownBy(() -> service.sell(1L, 5L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(transactions, payments, snapshots);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "inactive", "sold", "partial"})
    void requiresAnActiveWholeProperty(String problem) {
        asset.setType(AssetType.REAL_ESTATE);
        RealEstateProperty property =
                RealEstateProperty.builder().id(8L).userId(1L).assetId(5L).build();
        if (problem.equals("inactive")) property.setActive(false);
        if (problem.equals("sold")) property.setSoldDate(LocalDate.now());
        if (problem.equals("partial")) request.setQuantity(new BigDecimal("0.5"));
        when(assets.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(asset));
        when(properties.findByAssetIdAndUserId(5L, 1L))
                .thenReturn(problem.equals("missing") ? Optional.empty() : Optional.of(property));
        assertThatThrownBy(() -> service.sell(1L, 5L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(accounts, transactions, payments, snapshots);
    }

    @ParameterizedTest
    @ValueSource(strings = {"physical", "property", "investment"})
    void postsNetCashAndClosesOnlyWholePhysicalOwnership(String kind) {
        RealEstateProperty property =
                RealEstateProperty.builder().id(8L).userId(1L).assetId(5L).build();
        if (kind.equals("property")) {
            asset.setType(AssetType.REAL_ESTATE);
            when(properties.findByAssetIdAndUserId(5L, 1L)).thenReturn(Optional.of(property));
        }
        if (kind.equals("investment")) {
            asset.setType(AssetType.STOCK);
            asset.setAccountId(3L);
            asset.setQuantity(new BigDecimal("2"));
            request.setDescription("Sell one unit");
        }
        when(assets.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(asset));
        when(accounts.findByIdAndUserId(3L, 1L))
                .thenReturn(
                        Optional.of(
                                Account.builder().id(3L).isActive(true).currency("EUR").build()));
        when(transactions.createTransaction(eq(1L), any()))
                .thenReturn(TransactionResponse.builder().id(99L).build());
        Transaction booked = Transaction.builder().id(99L).build();
        when(payments.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(booked));
        assertThat(service.sell(1L, 5L, request)).isEqualTo(99L);
        ArgumentCaptor<TransactionRequest> payment =
                ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactions).createTransaction(eq(1L), payment.capture());
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("100");
        assertThat(payment.getValue().getType()).isEqualTo(TransactionType.INCOME);
        assertThat(booked.getSaleFees()).isEqualByComparingTo("0.01");
        if (kind.equals("investment")) {
            assertThat(payment.getValue().getInvestment().getQuantity()).isEqualByComparingTo("1");
            assertThat(payment.getValue().getDescription()).isEqualTo("Sell one unit");
            assertThat(asset.getSoldDate()).isNull();
            verify(assets, never()).save(any());
        } else {
            assertThat(asset.getSoldDate()).isEqualTo(request.getDate());
            assertThat(asset.getSaleTransactionId()).isEqualTo(99L);
            assertThat(payment.getValue().getDescription()).isEqualTo("Car");
            assertThat(payment.getValue().getInvestment()).isNull();
        }
        if (kind.equals("property")) {
            assertThat(property.getSaleTransactionId()).isEqualTo(99L);
            assertThat(payment.getValue().getRealEstateId()).isEqualTo(8L);
        } else assertThat(payment.getValue().getAssetId()).isEqualTo(5L);
        verify(snapshots)
                .deleteByUserIdAndSnapshotDateBetween(
                        1L, request.getDate().withDayOfMonth(1), LocalDate.now());
        verify(receipts).remember(eq(1L), eq(request.getOperationId()), any(), eq(99L));
    }
}
