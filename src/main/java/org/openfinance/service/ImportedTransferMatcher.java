package org.openfinance.service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.openfinance.dto.ImportedTransaction;

/** Matches complementary statement sides without collapsing independent reverse transfers. */
final class ImportedTransferMatcher {
    private final List<Side> unmatched = new ArrayList<>();
    private int nextKey;

    String keyFor(ImportedTransaction transaction, Long accountId, Long otherAccountId) {
        String explicitKey = transaction.getTransferGroupKey();
        if (explicitKey != null && !explicitKey.isBlank()) {
            return "explicit:" + explicitKey;
        }
        for (int index = 0; index < unmatched.size(); index++) {
            Side previous = unmatched.get(index);
            if (accountId.equals(previous.otherAccountId())
                    && otherAccountId.equals(previous.accountId())
                    && areComplementary(previous.transaction(), transaction)) {
                unmatched.remove(index);
                return previous.key();
            }
        }
        String key = "statement-pair:" + nextKey++;
        unmatched.add(new Side(transaction, accountId, otherAccountId, key));
        return key;
    }

    private boolean areComplementary(ImportedTransaction first, ImportedTransaction second) {
        if (first.getAmount().signum() == second.getAmount().signum()
                || Math.abs(
                                ChronoUnit.DAYS.between(
                                        first.getTransactionDate(), second.getTransactionDate()))
                        > 1) {
            return false;
        }
        boolean differentCurrencies =
                first.getCurrency() != null
                        && second.getCurrency() != null
                        && !first.getCurrency().equalsIgnoreCase(second.getCurrency());
        if (!differentCurrencies) {
            return first.getAmount().abs().compareTo(second.getAmount().abs()) == 0;
        }
        // Native currency amounts need not match. Require a shared transaction description
        // as well as complementary accounts, signs, and nearby dates in this case.
        return first.getPayee() != null
                && !first.getPayee().isBlank()
                && first.getPayee().equalsIgnoreCase(second.getPayee());
    }

    private record Side(
            ImportedTransaction transaction, Long accountId, Long otherAccountId, String key) {}
}
