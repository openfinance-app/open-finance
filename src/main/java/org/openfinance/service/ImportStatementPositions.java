package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.openfinance.dto.ImportedTransaction;

/** Original statement boundaries, kept independent of duplicate filtering and review edits. */
final class ImportStatementPositions {
    private ImportStatementPositions() {}

    static Map<String, BigDecimal> netAmounts(List<ImportedTransaction> transactions) {
        Map<String, BigDecimal> amounts = new HashMap<>();
        for (ImportedTransaction tx : transactions) {
            String key =
                    Objects.toString(accountKey(tx.getAccountName(), tx.getAccountNumber()), "");
            if (tx.getAmount() == null) {
                amounts.put(
                        key, null); // An incomplete statement cannot establish an opening balance.
            } else if (!amounts.containsKey(key) || amounts.get(key) != null) {
                amounts.merge(key, tx.getAmount(), BigDecimal::add);
            }
        }
        return amounts;
    }

    static Map<String, LocalDate> openingDates(List<ImportedTransaction> transactions) {
        Map<String, LocalDate> dates = new HashMap<>();
        for (ImportedTransaction tx : transactions) {
            if (tx.getTransactionDate() == null) continue;
            String key =
                    Objects.toString(accountKey(tx.getAccountName(), tx.getAccountNumber()), "");
            dates.merge(
                    key,
                    tx.getTransactionDate(),
                    (left, right) -> left.isBefore(right) ? left : right);
        }
        return dates;
    }

    static String accountKey(String accountName, String accountNumber) {
        if (accountNumber != null && !accountNumber.isBlank()) {
            return "number:"
                    + accountNumber.trim().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        }
        if (accountName != null && !accountName.isBlank()) {
            return "name:"
                    + accountName.trim().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
