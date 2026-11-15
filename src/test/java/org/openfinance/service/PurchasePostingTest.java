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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.DisbursementRequest;
import org.openfinance.dto.PropertyPurchaseRequest.Route;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Purchase posting reuses only matching payments and disburses exactly once")
class PurchasePostingTest {
    @Mock private TransactionRepository transactions;
    @Mock private AssetRepository assets;
    @Mock private RealEstateRepository properties;
    @Mock private LiabilityService liabilities;
    @Mock private AssetFinancingService financing;
    private PurchasePaymentServiceImpl payments;
    private PurchaseLoanServiceImpl loans;
    private static final LocalDate DATE = LocalDate.of(2024, 1, 10);

    @BeforeEach
    void setUp() {
        payments = new PurchasePaymentServiceImpl(transactions, assets, properties);
        loans = new PurchaseLoanServiceImpl(liabilities, transactions, financing);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DELETED",
                "INCOME",
                "TRANSFER",
                "INVESTMENT",
                "LIABILITY",
                "MOVEMENT",
                "OTHER_ASSET",
                "OTHER_PROPERTY",
                "ACCOUNT",
                "CURRENCY",
                "AMOUNT",
                "AFTER_PURCHASE",
                "FUTURE"
            })
    void refusesToRelabelAnUnrelatedOrChangedPurchasePayment(String problem) {
        Transaction tx = payment();
        switch (problem) {
            case "DELETED" -> tx.setIsDeleted(true);
            case "INCOME" -> tx.setType(TransactionType.INCOME);
            case "TRANSFER" -> tx.setTransferId("pair");
            case "INVESTMENT" -> tx.setInvestmentQuantity(BigDecimal.ONE);
            case "LIABILITY" -> tx.setLiabilityId(8L);
            case "MOVEMENT" -> tx.setMovementType(MovementType.INTEREST);
            case "OTHER_ASSET" -> tx.setAssetId(99L);
            case "OTHER_PROPERTY" -> tx.setRealEstateId(99L);
            case "ACCOUNT" -> tx.setAccountId(99L);
            case "CURRENCY" -> tx.setCurrency("EUR");
            case "AMOUNT" -> tx.setAmount(new BigDecimal("100.000000000000000001"));
            case "AFTER_PURCHASE" -> tx.setDate(DATE.plusDays(1));
            case "FUTURE" -> tx.setDate(LocalDate.now().plusDays(1));
            default -> throw new AssertionError(problem);
        }
        when(transactions.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(tx));
        LocalDate purchaseDate = problem.equals("FUTURE") ? tx.getDate() : DATE;
        assertThatThrownBy(
                        () ->
                                payments.link(
                                        1L,
                                        7L,
                                        2L,
                                        "USD",
                                        new BigDecimal("100"),
                                        purchaseDate,
                                        8L,
                                        9L))
                .isInstanceOf(InvalidTransactionException.class);
        verify(transactions, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"8,", ",9", "8,9"})
    void linkingAnAlreadyAssociatedPaymentIsIdempotentAndPreservesAllCashFields(
            Long asset, Long property) {
        Transaction tx = payment();
        tx.setAssetId(asset);
        tx.setRealEstateId(property);
        tx.setDate(DATE.minusDays(1));
        when(transactions.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(tx));
        payments.link(1L, 7L, 2L, "USD", new BigDecimal("100.0000"), DATE, asset, property);
        assertThat(tx.getAmount()).isEqualByComparingTo("100");
        assertThat(tx.getAccountId()).isEqualTo(2);
        assertThat(tx.getDate()).isEqualTo(DATE.minusDays(1));
        assertThat(tx.getAssetId()).isEqualTo(asset);
        assertThat(tx.getRealEstateId()).isEqualTo(property);
        verify(transactions).save(tx);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DIRECT_ROUTE",
                "DELETED",
                "EXPENSE",
                "NOT_DISBURSEMENT",
                "OTHER_LOAN",
                "OTHER_ACCOUNT",
                "CURRENCY",
                "AMOUNT",
                "LATE"
            })
    void cannotReuseAnUnrelatedLoanReceipt(String problem) {
        Transaction receipt = receipt();
        switch (problem) {
            case "DIRECT_ROUTE" -> {}
            case "DELETED" -> receipt.setIsDeleted(true);
            case "EXPENSE" -> receipt.setType(TransactionType.EXPENSE);
            case "NOT_DISBURSEMENT" -> receipt.setMovementType(MovementType.REPAYMENT);
            case "OTHER_LOAN" -> receipt.setLiabilityId(99L);
            case "OTHER_ACCOUNT" -> receipt.setAccountId(99L);
            case "CURRENCY" -> receipt.setCurrency("EUR");
            case "AMOUNT" -> receipt.setAmount(new BigDecimal("99.99"));
            case "LATE" -> receipt.setDate(DATE.plusDays(1));
            default -> throw new AssertionError(problem);
        }
        when(transactions.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(receipt));
        assertThatThrownBy(
                        () ->
                                loans.fund(
                                        1L,
                                        3L,
                                        new BigDecimal("100"),
                                        "USD",
                                        DATE,
                                        problem.equals("DIRECT_ROUTE")
                                                ? Route.DIRECT
                                                : Route.ACCOUNT,
                                        2L,
                                        7L,
                                        8L,
                                        null))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(liabilities, financing);
    }

    @Test
    void anExistingMatchingReceiptOnlyCreatesTheFinancingAssociation() {
        Transaction receipt = receipt();
        when(transactions.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(receipt));
        loans.fund(1L, 3L, new BigDecimal("100.000"), "USD", DATE, Route.ACCOUNT, 2L, 7L, 8L, null);
        verifyNoInteractions(liabilities);
        verify(financing).ensureDirectFinancing(1L, 3L, 8L);
        verify(transactions, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"DIRECT,8,, ,8,", "DIRECT,8,9, ,,9", "ACCOUNT,8,,2,,", "ACCOUNT,8,9,2,,"})
    void routesNewDisbursementsToExactlyOneDestination(
            Route route,
            Long asset,
            Long property,
            Long expectedAccount,
            Long expectedAsset,
            Long expectedProperty) {
        loans.fund(1L, 3L, new BigDecimal("100"), "USD", DATE, route, 2L, null, asset, property);
        ArgumentCaptor<DisbursementRequest> request =
                ArgumentCaptor.forClass(DisbursementRequest.class);
        verify(liabilities).disburse(eq(1L), eq(3L), request.capture());
        assertThat(request.getValue().getToAccountId()).isEqualTo(expectedAccount);
        assertThat(request.getValue().getDirectAssetId()).isEqualTo(expectedAsset);
        assertThat(request.getValue().getDirectRealEstateId()).isEqualTo(expectedProperty);
        assertThat(request.getValue().getAmount()).isEqualByComparingTo("100");
        assertThat(request.getValue().getDate()).isEqualTo(DATE);
        verify(financing).ensureDirectFinancing(1L, 3L, asset);
    }

    private static Transaction payment() {
        return Transaction.builder()
                .id(7L)
                .userId(1L)
                .accountId(2L)
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100"))
                .currency("USD")
                .date(DATE)
                .isDeleted(false)
                .build();
    }

    private static Transaction receipt() {
        Transaction tx = payment();
        tx.setType(TransactionType.INCOME);
        tx.setMovementType(MovementType.DISBURSEMENT);
        tx.setLiabilityId(3L);
        return tx;
    }
}
