package org.openfinance.service;

import static org.openfinance.service.ImportedAccountRouting.resolveImportedAccountId;
import static org.openfinance.service.ImportedAccountRouting.resolveImportedDestinationAccountId;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openfinance.dto.ImportedTransaction;

/** Matches complementary statement sides without collapsing independent reverse transfers. */
final class ImportedTransferMatcher {
    private final List<Side> unmatched = new ArrayList<>();
    private int nextKey;

    static Groups group(
            List<ImportedTransaction> rows, Map<String, Long> accountIds, Long fallback) {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        Groups groups = new Groups();
        for (ImportedTransaction row : rows) {
            if (!row.isTransfer() || row.getToAccountName() == null) continue;
            Long source = resolveImportedAccountId(row, accountIds, fallback);
            Long target = resolveImportedDestinationAccountId(row, accountIds, fallback);
            if (source == null || target == null) continue;
            String key = matcher.keyFor(row, source, target);
            groups.keys.put(row, key);
            groups.rows.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    static final class Groups {
        private final Map<ImportedTransaction, String> keys = new IdentityHashMap<>();
        private final Map<String, List<ImportedTransaction>> rows = new LinkedHashMap<>();

        String keyFor(ImportedTransaction row) {
            return keys.get(row);
        }

        List<ImportedTransaction> rowsFor(String key) {
            return List.copyOf(rows.get(key));
        }
    }

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
        if (first.getAmount().signum() == second.getAmount().signum()) {
            return false;
        }
        if (first.isOfxTransfer()
                && second.isOfxTransfer()
                && first.getOfxTransferReference() != null
                && second.getOfxTransferReference() != null) {
            return first.getOfxTransferReference().equals(second.getOfxTransferReference());
        }
        if (Math.abs(
                        ChronoUnit.DAYS.between(
                                first.getTransactionDate(), second.getTransactionDate()))
                > 1) return false;
        boolean differentCurrencies =
                first.getCurrency() != null
                        && second.getCurrency() != null
                        && !first.getCurrency().equalsIgnoreCase(second.getCurrency());
        if (first.isHoldingTransfer() || second.isHoldingTransfer()) {
            return first.isHoldingTransfer()
                    && second.isHoldingTransfer()
                    && java.util.Objects.equals(
                            first.getInvestmentSecurity(), second.getInvestmentSecurity())
                    && first.getInvestmentQuantity() != null
                    && second.getInvestmentQuantity() != null
                    && first.getInvestmentQuantity().add(second.getInvestmentQuantity()).signum()
                            == 0;
        }
        if (!differentCurrencies) {
            return matchingAmount(first).compareTo(matchingAmount(second)) == 0;
        }
        // Native currency amounts need not match. Require a shared transaction description
        // as well as complementary accounts, signs, and nearby dates in this case.
        return first.getPayee() != null
                && !first.getPayee().isBlank()
                && first.getPayee().equalsIgnoreCase(second.getPayee());
    }

    private java.math.BigDecimal matchingAmount(ImportedTransaction row) {
        return row.getTransferAmount() == null
                ? row.getAmount().abs()
                : row.getTransferAmount().abs();
    }

    private record Side(
            ImportedTransaction transaction, Long accountId, Long otherAccountId, String key) {}
}
