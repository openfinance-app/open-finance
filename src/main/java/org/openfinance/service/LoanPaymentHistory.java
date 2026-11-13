package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.LoanPaymentHistoryEntry;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.util.LoanMovementClassification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanPaymentHistory {
    private final TransactionRepository transactions;
    private final LoanPaymentTotals totals;
    private final NetWorthService balances;

    public List<LoanPaymentHistoryEntry> entries(Liability loan) {
        if (loan.getRepresentedByAccountId() != null) return List.of();
        List<Transaction> payments =
                transactions.findIncludingConvertedAccount(loan.getId(), loan.getUserId()).stream()
                        .filter(tx -> !Boolean.TRUE.equals(tx.getIsDeleted()))
                        .filter(tx -> !tx.getDate().isAfter(LocalDate.now()))
                        .filter(tx -> isPayment(loan, tx))
                        .sorted(
                                Comparator.comparing(Transaction::getDate)
                                        .thenComparing(Transaction::getId))
                        .toList();
        if (payments.isEmpty()) return List.of();
        Map<Long, LoanPaymentTotals.Paid> costs = totals.byTransaction(loan, payments);
        Map<LocalDate, BigDecimal> dailyBalances =
                balances.getStandaloneLiabilityBalancesAt(
                        loan, payments.stream().map(Transaction::getDate).distinct().toList());
        return payments.stream()
                .map(tx -> entry(loan, tx, costs.get(tx.getId()), dailyBalances.get(tx.getDate())))
                .toList();
    }

    private boolean isPayment(Liability loan, Transaction tx) {
        MovementType type = LoanMovementClassification.classify(loan, tx);
        if (LoanMovementClassification.isSourceAccount(loan, tx))
            return type == MovementType.REPAYMENT;
        return tx.getType() == TransactionType.EXPENSE
                && tx.getTransferId() == null
                && LoanMovementClassification.isPayment(type);
    }

    private LoanPaymentHistoryEntry entry(
            Liability loan, Transaction tx, LoanPaymentTotals.Paid paid, BigDecimal balance) {
        if (LoanMovementClassification.isSourceAccount(loan, tx)) {
            BigDecimal amount = tx.getBalanceAmount().abs();
            return new LoanPaymentHistoryEntry(
                    tx.getId(),
                    tx.getDate(),
                    amount,
                    amount,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    balance);
        }
        BigDecimal amount = totals.amount(loan, tx, tx.getAmount());
        BigDecimal principal =
                tx.getMovementType() == MovementType.REPAYMENT
                        ? (tx.getPrincipalAmount() != null
                                ? tx.getPrincipalAmount()
                                : amount.subtract(paid.total()).max(BigDecimal.ZERO))
                        : BigDecimal.ZERO;
        return new LoanPaymentHistoryEntry(
                tx.getId(),
                tx.getDate(),
                amount,
                principal,
                paid.interest(),
                paid.insurance(),
                paid.fees(),
                paid.other(),
                balance);
    }
}
