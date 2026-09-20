package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Category;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionSplitRepository;

@ExtendWith(MockitoExtension.class)
class LoanPaymentTotalsTest {
    @Mock TransactionSplitRepository splits;
    @Mock CategoryRepository categories;
    @Mock ExchangeRateService rates;
    @InjectMocks LoanPaymentTotals totals;

    @Test
    void convertedRepaymentChargesReconcileWithTheStoredPrincipalAndOriginalTotal() {
        Liability loan = new Liability();
        loan.setCurrency("EUR");
        Transaction repayment =
                Transaction.builder()
                        .id(1L)
                        .type(TransactionType.EXPENSE)
                        .movementType(MovementType.REPAYMENT)
                        .currency("USD")
                        .amount(new BigDecimal("3"))
                        .originalCurrency("EUR")
                        .originalAmount(BigDecimal.ONE)
                        .conversionRate(new BigDecimal("3"))
                        .principalAmount(new BigDecimal("0.33"))
                        .build();
        when(splits.findByTransactionIdIn(List.of(1L)))
                .thenReturn(
                        List.of(
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .categoryId(10L)
                                        .amount(BigDecimal.ONE)
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .categoryId(20L)
                                        .amount(BigDecimal.ONE)
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .amount(BigDecimal.ONE)
                                        .build()));
        when(categories.findById(10L))
                .thenReturn(
                        Optional.of(
                                Category.builder().nameKey("category.interest.expense").build()));
        when(categories.findById(20L))
                .thenReturn(Optional.of(Category.builder().nameKey("category.insurance").build()));
        LoanPaymentTotals.Paid paid = totals.calculate(loan, List.of(repayment));
        assertThat(paid.total().add(repayment.getPrincipalAmount())).isEqualByComparingTo("1");
        assertThat(paid.interest()).isEqualByComparingTo("0.335");
        assertThat(paid.insurance()).isEqualByComparingTo("0.335");
    }

    @Test
    void categorizedPrincipalDoesNotDiluteInterestAcrossThreeMonthlyRepayments() {
        Liability loan = new Liability();
        loan.setCurrency("EUR");
        List<Transaction> repayments =
                java.util.stream.IntStream.range(0, 3)
                        .mapToObj(
                                i ->
                                        Transaction.builder()
                                                .id((long) i + 1)
                                                .type(TransactionType.EXPENSE)
                                                .movementType(MovementType.REPAYMENT)
                                                .currency("EUR")
                                                .amount(new BigDecimal("250"))
                                                .principalAmount(
                                                        new BigDecimal(
                                                                List.of("222", "222.74", "223.48")
                                                                        .get(i)))
                                                .build())
                        .toList();
        when(splits.findByTransactionIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(
                        repayments.stream()
                                .flatMap(
                                        tx ->
                                                List.of(
                                                        TransactionSplit.builder()
                                                                .transactionId(tx.getId())
                                                                .categoryId(30L)
                                                                .amount(tx.getPrincipalAmount())
                                                                .build(),
                                                        TransactionSplit.builder()
                                                                .transactionId(tx.getId())
                                                                .categoryId(10L)
                                                                .amount(
                                                                        tx.getAmount()
                                                                                .subtract(
                                                                                        tx
                                                                                                .getPrincipalAmount()))
                                                                .build())
                                                        .stream())
                                .toList());
        when(categories.findById(30L))
                .thenReturn(
                        Optional.of(Category.builder().nameKey("category.loan.repayment").build()));
        when(categories.findById(10L))
                .thenReturn(
                        Optional.of(
                                Category.builder().nameKey("category.interest.expense").build()));

        LoanPaymentTotals.Paid paid = totals.calculate(loan, repayments);

        assertThat(paid.interest()).isEqualByComparingTo("81.78");
        assertThat(paid.other()).isZero();
        assertThat(paid.total()).isEqualByComparingTo("81.78");
    }

    @Test
    void manualRepaymentWithoutChargeSplitsRetainsUnclassifiedCharges() {
        Liability loan = new Liability();
        loan.setCurrency("EUR");
        Transaction repayment =
                Transaction.builder()
                        .id(1L)
                        .type(TransactionType.EXPENSE)
                        .movementType(MovementType.REPAYMENT)
                        .currency("EUR")
                        .amount(new BigDecimal("250"))
                        .principalAmount(new BigDecimal("222"))
                        .build();
        assertThat(totals.calculate(loan, List.of(repayment)).other()).isEqualByComparingTo("28");
    }

    @Test
    void configuredFeesAndRatesAreNotEvidenceOfPayments() {
        Liability loan = new Liability();
        loan.setCurrency("EUR");
        loan.setInterestRate("12");
        loan.setInsurancePercentage("2");
        loan.setAdditionalFees("100");
        assertThat(totals.calculate(loan, List.of()).total()).isZero();
    }
}
