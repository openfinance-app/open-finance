package org.openfinance.service;

import static org.openfinance.service.ImportedAccountRouting.hasImportedDestinationReference;
import static org.openfinance.service.ImportedAccountRouting.resolveImportedAccountId;
import static org.openfinance.service.ImportedAccountRouting.resolveImportedDestinationAccountId;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.openfinance.dto.ImportedTransaction;

/** Matches complementary statement sides without collapsing independent reverse transfers. */
final class ImportedTransferMatcher {
    private static final String REVIEW_PAIR_PREFIX = "review:statement-pair:";
    private final List<Side> unmatched = new ArrayList<>();
    private int nextKey;

    /** Remember statement pairs before a reviewer edits the dates or amounts used to match them. */
    static void identifyReviewPairs(List<ImportedTransaction> rows) {
        Map<String, Long> accounts = new LinkedHashMap<>();
        List<ImportedTransaction> transfers =
                rows.stream()
                        .filter(
                                row ->
                                        row.isTransfer()
                                                && !row.hasErrors()
                                                && row.getAmount() != null
                                                && row.getTransactionDate() != null)
                        .toList();
        for (ImportedTransaction row : transfers) {
            String account =
                    ImportStatementPositions.accountKey(
                            row.getAccountName(), row.getAccountNumber());
            if (account != null)
                accounts.computeIfAbsent(account, ignored -> (long) accounts.size() + 1);
        }
        Set<String> used = new HashSet<>();
        for (ImportedTransaction row : rows) used.add(row.getTransferGroupKey());
        int index = 0;
        for (List<ImportedTransaction> pair : group(transfers, accounts, null).rows.values()) {
            if (pair.size() != 2 || pair.getFirst().getTransferGroupKey() != null) continue;
            while (used.contains(REVIEW_PAIR_PREFIX + index)) index++;
            String key = REVIEW_PAIR_PREFIX + index++;
            pair.forEach(row -> row.setTransferGroupKey(key));
        }
    }

    static boolean isReviewPair(ImportedTransaction row) {
        return row.getTransferGroupKey() != null
                && row.getTransferGroupKey().startsWith(REVIEW_PAIR_PREFIX);
    }

    static boolean hasDistinctReviewedLegs(List<ImportedTransaction> group) {
        if (group.size() != 2) return false;
        ImportedTransaction first = group.getFirst();
        ImportedTransaction last = group.getLast();
        return isReviewPair(first)
                && (!first.getTransactionDate().equals(last.getTransactionDate())
                        || !Objects.equals(first.getCurrency(), last.getCurrency())
                        || first.getAmount().add(last.getAmount()).signum() != 0);
    }

    static Groups group(
            List<ImportedTransaction> rows, Map<String, Long> accountIds, Long fallback) {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        Groups groups = new Groups();
        for (ImportedTransaction row : rows) {
            if (!row.isTransfer() || !hasImportedDestinationReference(row)) continue;
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
