package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    void explicitComponentsPreserveCustomCategoriesAndRecognizeCategorizedPrincipal() {
        Liability loan = new Liability();
        loan.setCurrency("EUR");
        Transaction payment =
                Transaction.builder()
                        .id(1L)
                        .currency("EUR")
                        .type(TransactionType.EXPENSE)
                        .movementType(MovementType.REPAYMENT)
                        .amount(new BigDecimal("100"))
                        .principalAmount(new BigDecimal("80"))
                        .build();
        when(splits.findByTransactionIdIn(List.of(1L)))
                .thenReturn(
                        List.of(
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .categoryId(42L)
                                        .loanComponent(
                                                org.openfinance.entity.LoanComponent.PRINCIPAL)
                                        .amount(new BigDecimal("80"))
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .categoryId(43L)
                                        .loanComponent(
                                                org.openfinance.entity.LoanComponent.INTEREST)
                                        .amount(new BigDecimal("12"))
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .categoryId(44L)
                                        .loanComponent(
                                                org.openfinance.entity.LoanComponent.INSURANCE)
                                        .amount(new BigDecimal("6"))
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .categoryId(45L)
                                        .loanComponent(org.openfinance.entity.LoanComponent.FEE)
                                        .amount(new BigDecimal("2"))
                                        .build()));
        LoanPaymentTotals.Paid paid = totals.calculate(loan, List.of(payment));
        assertThat(paid.interest()).isEqualByComparingTo("12");
        assertThat(paid.insurance()).isEqualByComparingTo("6");
        assertThat(paid.fees()).isEqualByComparingTo("2");
        assertThat(paid.total()).isEqualByComparingTo("20");
    }

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

    private Liability nativeLoan() {
        Liability loan = new Liability();
        loan.setCurrency("EUR");
        return loan;
    }

    private Transaction payment() {
        return Transaction.builder()
                .id(1L)
                .currency("EUR")
                .type(TransactionType.EXPENSE)
                .movementType(MovementType.REPAYMENT)
                .amount(new BigDecimal("100.01"))
                .principalAmount(new BigDecimal("80"))
                .date(java.time.LocalDate.of(2025, 1, 1))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "transfer", "income", "unlinked", "disbursement"})
    void nonChargesNeverInflateRecordedBorrowingCosts(String kind) {
        Transaction payment = payment();
        switch (kind) {
            case "deleted" -> payment.setIsDeleted(true);
            case "transfer" -> payment.setTransferId("linked-transfer");
            case "income" -> payment.setType(TransactionType.INCOME);
            case "unlinked" -> payment.setMovementType(null);
            case "disbursement" -> payment.setMovementType(MovementType.DISBURSEMENT);
            default -> throw new AssertionError(kind);
        }
        assertThat(totals.calculate(nativeLoan(), List.of(payment)).total()).isZero();
        assertThat(totals.byTransaction(nativeLoan(), List.of(payment))).containsOnlyKeys(1L);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "category.interest.expense,INTEREST",
                "category.insurance,INSURANCE",
                "category.bank.fees,FEE",
                "category.loan.repayment,PRINCIPAL",
                "custom,OTHER",
                "NULL,OTHER",
                "MISSING,OTHER"
            },
            nullValues = "NULL")
    void classifiesLegacyRepaymentSplitsByTheirCanonicalCategoryWithoutLosingResidualCosts(
            String key, String bucket) {
        Transaction payment = payment();
        TransactionSplit split =
                TransactionSplit.builder()
                        .transactionId(1L)
                        .categoryId(42L)
                        .amount(new BigDecimal("20.01"))
                        .build();
        when(splits.findByTransactionIdIn(List.of(1L))).thenReturn(List.of(split));
        when(categories.findById(42L))
                .thenReturn(
                        "MISSING".equals(key)
                                ? Optional.empty()
                                : Optional.of(Category.builder().id(42L).nameKey(key).build()));
        LoanPaymentTotals.Paid paid = totals.calculate(nativeLoan(), List.of(payment));
        assertThat(paid.total()).isEqualByComparingTo("20.01");
        assertThat(
                        switch (bucket) {
                            case "INTEREST" -> paid.interest();
                            case "INSURANCE" -> paid.insurance();
                            case "FEE" -> paid.fees();
                            default -> paid.other();
                        })
                .isEqualByComparingTo("20.01");
    }

    @Test
    void unallocatedZeroAndNegativeSplitsDoNotDilutePositiveChargeAllocations() {
        Transaction payment = payment();
        when(splits.findByTransactionIdIn(List.of(1L)))
                .thenReturn(
                        List.of(
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .amount(new BigDecimal("80"))
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .loanComponent(org.openfinance.entity.LoanComponent.FEE)
                                        .amount(BigDecimal.ZERO)
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .loanComponent(
                                                org.openfinance.entity.LoanComponent.INSURANCE)
                                        .amount(new BigDecimal("-10"))
                                        .build(),
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .loanComponent(
                                                org.openfinance.entity.LoanComponent.INTEREST)
                                        .amount(new BigDecimal("20.01"))
                                        .build()));
        LoanPaymentTotals.Paid paid = totals.calculate(nativeLoan(), List.of(payment));
        assertThat(paid.interest()).isEqualByComparingTo("20.01");
        assertThat(paid.total()).isEqualByComparingTo("20.01");
    }

    @Test
    void explicitSplitChargesRemainKnownWhenARepaymentHasNoRecordedPrincipal() {
        Transaction payment = payment();
        payment.setPrincipalAmount(null);
        when(splits.findByTransactionIdIn(List.of(1L)))
                .thenReturn(
                        List.of(
                                TransactionSplit.builder()
                                        .transactionId(1L)
                                        .loanComponent(
                                                org.openfinance.entity.LoanComponent.INTEREST)
                                        .amount(new BigDecimal("12.34"))
                                        .build()));
        assertThat(totals.calculate(nativeLoan(), List.of(payment)).interest())
                .isEqualByComparingTo("12.34");
    }

    @ParameterizedTest
    @ValueSource(strings = {"currency", "amount", "rate"})
    void incompleteBookedConversionFallsBackToTheHistoricalRate(String missing) {
        Transaction payment = payment();
        payment.setCurrency("USD");
        payment.setOriginalCurrency("EUR");
        payment.setOriginalAmount(new BigDecimal("90.01"));
        payment.setConversionRate(new BigDecimal("0.9"));
        switch (missing) {
            case "currency" -> payment.setOriginalCurrency("GBP");
            case "amount" -> payment.setOriginalAmount(null);
            case "rate" -> payment.setConversionRate(null);
            default -> throw new AssertionError(missing);
        }
        when(rates.convert(new BigDecimal("10.01"), "USD", "EUR", payment.getDate()))
                .thenReturn(new BigDecimal("9.09"));
        assertThat(totals.amount(nativeLoan(), payment, new BigDecimal("10.01")))
                .isEqualByComparingTo("9.09");
    }

    @Test
    void emptyPaymentHistoryNeedsNoCategoryOrExchangeRateQueries() {
        assertThat(totals.calculate(nativeLoan(), List.of()).total()).isZero();
        assertThat(totals.byTransaction(nativeLoan(), List.of())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(splits, categories, rates);
    }
}
