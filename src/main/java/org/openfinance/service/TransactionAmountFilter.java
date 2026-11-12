package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.openfinance.dto.AmountCurrencyMode;
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
        if (minimum == null && maximum == null && exact == null) return transaction -> true;
        if (mode == AmountCurrencyMode.NATIVE) {
            return transaction ->
                    AmountComparison.matches(
                            transaction.getAmount() == null ? null : transaction.getAmount().abs(),
                            minimum,
                            maximum,
                            exact);
        }
        // Keep both legs available even when another filter selects only one account or type.
        Map<String, List<Transaction>> transfers =
                transactions.stream()
                        .filter(transaction -> transaction.getTransferId() != null)
                        .collect(Collectors.groupingBy(Transaction::getTransferId));
        return transaction -> {
            if (transaction.getAmount() == null) return false;
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
            if (!result.converted()
                    && !result.baseCurrency().equalsIgnoreCase(transaction.getCurrency())) {
                return false;
            }
            BigDecimal amount = result.amountInBaseCurrency();
            return AmountComparison.matches(
                    amount == null ? null : amount.abs(), minimum, maximum, exact);
        };
    }
}
