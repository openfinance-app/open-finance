package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.openfinance.dto.AmountCurrencyMode;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.entity.Transaction;
import org.openfinance.util.AmountComparison;

/**
 * Inclusive bounds and exact amounts in the selected currency, applied before pagination or search
 * result limits.
 */
final class TransactionAmountFilter {
    private TransactionAmountFilter() {}

    static Predicate<Transaction> matching(
            List<Transaction> transactions,
            BigDecimal minimum,
            BigDecimal maximum,
            CurrencyConversionHelper conversion) {
        return matching(transactions, minimum, maximum, null, AmountCurrencyMode.BASE, conversion);
    }

    static Predicate<Transaction> matching(
            List<Transaction> transactions,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal exact,
            AmountCurrencyMode mode,
            CurrencyConversionHelper conversion) {
        return matching(transactions, Map.of(), minimum, maximum, exact, mode, conversion);
    }

    static Predicate<Transaction> matching(
            List<Transaction> transactions,
            Map<Long, List<TransactionSplitResponse>> splits,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal exact,
            AmountCurrencyMode mode,
            CurrencyConversionHelper conversion) {
        if (minimum == null && maximum == null && exact == null) return transaction -> true;
        if (mode == AmountCurrencyMode.NATIVE) {
            return transaction ->
                    matchesAmount(transaction.getAmount(), minimum, maximum, exact)
                            || splits.getOrDefault(transaction.getId(), List.of()).stream()
                                    .anyMatch(
                                            split ->
                                                    matchesAmount(
                                                            split.getAmount(),
                                                            minimum,
                                                            maximum,
                                                            exact));
        }
        // Keep both legs available even when another filter selects only one account or type.
        Map<String, List<Transaction>> transfers =
                transactions.stream()
                        .filter(transaction -> transaction.getTransferId() != null)
                        .collect(Collectors.groupingBy(Transaction::getTransferId));
        return transaction -> {
            BigDecimal amount = convertedAmount(transaction, transfers, conversion);
            if (matchesAmount(amount, minimum, maximum, exact)) return true;
            if (amount == null || transaction.getAmount().signum() == 0) return false;
            // Compare each split at the parent's booked/dated value. Cross multiplication
            // avoids rounding a derived exchange rate before an exact comparison.
            BigDecimal total = transaction.getAmount().abs();
            return splits.getOrDefault(transaction.getId(), List.of()).stream()
                    .filter(split -> split.getAmount() != null)
                    .anyMatch(
                            split ->
                                    matchesAmount(
                                            amount.multiply(split.getAmount()),
                                            minimum == null ? null : minimum.multiply(total),
                                            maximum == null ? null : maximum.multiply(total),
                                            exact == null ? null : exact.multiply(total)));
        };
    }

    private static BigDecimal convertedAmount(
            Transaction transaction,
            Map<String, List<Transaction>> transfers,
            CurrencyConversionHelper conversion) {
        if (transaction.getAmount() == null) return null;
        Transaction opposite =
                transfers.getOrDefault(transaction.getTransferId(), List.of()).stream()
                        .filter(leg -> Objects.equals(leg.getUserId(), transaction.getUserId()))
                        .filter(leg -> !Objects.equals(leg.getId(), transaction.getId()))
                        .filter(leg -> leg.getType() != transaction.getType())
                        .findFirst()
                        .orElse(null);
        CurrencyConversionHelper.ConversionResult result =
                conversion.convertTransaction(
                        transaction,
                        opposite == null ? null : opposite.getAmount(),
                        opposite == null ? null : opposite.getCurrency());
        // A missing FX quote falls back to native amounts for display, not for comparisons.
        return result.converted()
                        || result.baseCurrency().equalsIgnoreCase(transaction.getCurrency())
                ? result.amountInBaseCurrency()
                : null;
    }

    private static boolean matchesAmount(
            BigDecimal amount, BigDecimal minimum, BigDecimal maximum, BigDecimal exact) {
        return AmountComparison.matches(
                amount == null ? null : amount.abs(), minimum, maximum, exact);
    }
}
