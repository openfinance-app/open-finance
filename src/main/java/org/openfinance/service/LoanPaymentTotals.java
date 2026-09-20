package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.springframework.stereotype.Component;

/** Actual posted loan charges, including charge allocations within repayments. */
@Component
@RequiredArgsConstructor
public class LoanPaymentTotals {
    private final TransactionSplitRepository splits;
    private final CategoryRepository categories;
    private final ExchangeRateService rates;

    public record Paid(
            BigDecimal interest, BigDecimal insurance, BigDecimal fees, BigDecimal other) {
        public BigDecimal total() {
            return interest.add(insurance).add(fees).add(other);
        }
    }

    public Paid calculate(Liability loan, List<Transaction> transactions) {
        Map<Long, List<TransactionSplit>> byTransaction = splitAllocations(transactions);
        Map<String, BigDecimal> paid = new HashMap<>();
        for (Transaction transaction : transactions) {
            if (Boolean.TRUE.equals(transaction.getIsDeleted())
                    || transaction.getTransferId() != null
                    || transaction.getType() != TransactionType.EXPENSE) continue;
            MovementType type = transaction.getMovementType();
            if (type == MovementType.INTEREST
                    || type == MovementType.INSURANCE
                    || type == MovementType.FEE) {
                paid.merge(
                        type.name(),
                        amount(loan, transaction, transaction.getAmount()),
                        BigDecimal::add);
            } else if (type == MovementType.REPAYMENT) {
                allocateRepayment(
                        loan,
                        transaction,
                        byTransaction.getOrDefault(transaction.getId(), List.of()),
                        paid);
            }
        }
        return new Paid(
                paid.getOrDefault("INTEREST", BigDecimal.ZERO),
                paid.getOrDefault("INSURANCE", BigDecimal.ZERO),
                paid.getOrDefault("FEE", BigDecimal.ZERO),
                paid.getOrDefault("OTHER", BigDecimal.ZERO));
    }

    private Map<Long, List<TransactionSplit>> splitAllocations(List<Transaction> transactions) {
        if (transactions.isEmpty()) return Map.of();
        return splits
                .findByTransactionIdIn(transactions.stream().map(Transaction::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(TransactionSplit::getTransactionId));
    }

    private void allocateRepayment(
            Liability loan,
            Transaction transaction,
            List<TransactionSplit> allocations,
            Map<String, BigDecimal> paid) {
        List<TransactionSplit> charges =
                allocations.stream().filter(split -> split.getCategoryId() != null).toList();
        BigDecimal splitTotal =
                charges.stream()
                        .map(TransactionSplit::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal chargeTotal =
                transaction.getPrincipalAmount() == null
                        ? amount(loan, transaction, splitTotal)
                        : amount(loan, transaction, transaction.getAmount())
                                .subtract(transaction.getPrincipalAmount());
        BigDecimal remaining = chargeTotal;
        for (int i = 0; i < charges.size(); i++) {
            TransactionSplit split = charges.get(i);
            BigDecimal value =
                    i == charges.size() - 1
                            ? remaining
                            : chargeTotal
                                    .multiply(split.getAmount())
                                    .divide(splitTotal, 18, RoundingMode.HALF_UP);
            remaining = remaining.subtract(value);
            paid.merge(chargeBucket(split), value, BigDecimal::add);
        }
    }

    private String chargeBucket(TransactionSplit split) {
        String key =
                categories
                        .findById(split.getCategoryId())
                        .map(category -> category.getNameKey() == null ? "" : category.getNameKey())
                        .orElse("");
        return switch (key) {
            case "category.interest.expense" -> "INTEREST";
            case "category.insurance" -> "INSURANCE";
            case "category.bank.fees" -> "FEE";
            default -> "OTHER";
        };
    }

    public BigDecimal amount(Liability loan, Transaction transaction, BigDecimal amount) {
        if (loan.getCurrency().equalsIgnoreCase(transaction.getCurrency())) return amount;
        if (loan.getCurrency().equalsIgnoreCase(transaction.getOriginalCurrency())
                && transaction.getOriginalAmount() != null
                && transaction.getConversionRate() != null) {
            // Use the recorded conversion, including its rounding, to preserve the posted total.
            return amount.compareTo(transaction.getAmount()) == 0
                    ? transaction.getOriginalAmount()
                    : transaction
                            .getOriginalAmount()
                            .multiply(amount)
                            .divide(transaction.getAmount(), 18, RoundingMode.HALF_UP);
        }
        return rates.convert(
                amount, transaction.getCurrency(), loan.getCurrency(), transaction.getDate());
    }
}
