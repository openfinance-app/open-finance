package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.LoanPaymentHistoryEntry;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;

@ExtendWith(MockitoExtension.class)
class LoanPaymentHistoryTest {
    @Mock TransactionRepository transactions;
    @Mock TransactionSplitRepository splits;
    @Mock CategoryRepository categories;
    @Mock ExchangeRateService rates;
    @Mock NetWorthService balances;
    private LoanPaymentHistory history;
    private Liability loan;
    private final LocalDate date = LocalDate.now().minusMonths(2);

    @BeforeEach
    void setUp() {
        history =
                new LoanPaymentHistory(
                        transactions, new LoanPaymentTotals(splits, categories, rates), balances);
        loan = new Liability();
        loan.setId(7L);
        loan.setUserId(1L);
        loan.setCurrency("EUR");
        loan.setCurrentBalance("0");
    }

    @Test
    void retainsPaidOffHistoryAndStoredFxWithoutInventingMissingPayments() {
        Transaction payment = payment(1L, MovementType.REPAYMENT, "120");
        payment.setCurrency("USD");
        payment.setOriginalCurrency("EUR");
        payment.setOriginalAmount(new BigDecimal("100"));
        payment.setConversionRate(new BigDecimal("1.2"));
        payment.setPrincipalAmount(new BigDecimal("80"));
        Transaction insurance = payment(2L, MovementType.INSURANCE, "5");
        insurance.setDate(date.minusMonths(3));
        Transaction deleted = payment(3L, MovementType.REPAYMENT, "50");
        deleted.setIsDeleted(true);
        Transaction future = payment(4L, MovementType.REPAYMENT, "50");
        future.setDate(LocalDate.now().plusMonths(1));
        Transaction transfer = payment(5L, MovementType.REPAYMENT, "50");
        transfer.setTransferId("paired-transfer");
        when(transactions.findIncludingConvertedAccount(7L, 1L))
                .thenReturn(List.of(payment, deleted, future, transfer, insurance));
        when(balances.getStandaloneLiabilityBalancesAt(eq(loan), anyList()))
                .thenReturn(
                        Map.of(date, BigDecimal.ZERO, insurance.getDate(), new BigDecimal("80")));

        List<LoanPaymentHistoryEntry> paid = history.entries(loan);

        assertThat(paid).extracting(LoanPaymentHistoryEntry::transactionId).containsExactly(2L, 1L);
        assertThat(paid.get(0).insurancePortion()).isEqualByComparingTo("5");
        assertThat(paid.get(1).paymentAmount()).isEqualByComparingTo("100");
        assertThat(paid.get(1).principalPortion()).isEqualByComparingTo("80");
        assertThat(paid.get(1).otherCharges()).isEqualByComparingTo("20");
        assertThat(paid.get(1).remainingBalance()).isZero();
        verify(splits).findByTransactionIdIn(List.of(2L, 1L));
        verify(balances).getStandaloneLiabilityBalancesAt(loan, List.of(insurance.getDate(), date));
        verifyNoInteractions(rates);
    }

    @Test
    void recognizesRepaymentInImportedDebtAccountWithoutCountingTheCashTransferLeg() {
        loan.setConvertedFromAccountId(9L);
        Transaction draw = payment(1L, null, "1000");
        draw.setAccountId(9L);
        draw.setType(TransactionType.TRANSFER);
        Transaction repaid = payment(2L, null, "-100");
        repaid.setAccountId(9L);
        repaid.setType(TransactionType.TRANSFER);
        repaid.setTransferId("repayment-pair");
        Transaction cashLeg = payment(3L, MovementType.REPAYMENT, "100");
        cashLeg.setTransferId("repayment-pair");
        when(transactions.findIncludingConvertedAccount(7L, 1L))
                .thenReturn(List.of(draw, repaid, cashLeg));
        when(balances.getStandaloneLiabilityBalancesAt(eq(loan), anyList()))
                .thenReturn(Map.of(date, new BigDecimal("900")));
        List<LoanPaymentHistoryEntry> paid = history.entries(loan);
        assertThat(paid).hasSize(1);
        assertThat(paid.get(0).principalPortion()).isEqualByComparingTo("100");
        assertThat(paid.get(0).paymentAmount()).isEqualByComparingTo("100");
        assertThat(paid.get(0).remainingBalance()).isEqualByComparingTo("900");
        assertThat(paid.get(0).interestPortion()).isZero();
    }

    @Test
    void leavesAccountBackedLoansToTheirAccountHistory() {
        loan.setRepresentedByAccountId(9L);
        assertThat(history.entries(loan)).isEmpty();
        verifyNoInteractions(transactions, balances);
    }

    private Transaction payment(long id, MovementType type, String amount) {
        return Transaction.builder()
                .id(id)
                .accountId(5L)
                .userId(1L)
                .liabilityId(7L)
                .date(date)
                .type(TransactionType.EXPENSE)
                .movementType(type)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .build();
    }
}
