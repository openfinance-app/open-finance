package org.openfinance.service;

import java.util.List;
import java.util.Objects;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Account;

/** XFER describes a bank operation, not necessarily a transfer between this user's accounts. */
final class OfxTransferResolver {
    private OfxTransferResolver() {}

    static void resolve(List<ImportedTransaction> rows, List<Account> accounts) {
        for (ImportedTransaction row : rows) {
            if (!row.isOfxTransfer()) continue;
            row.setTransfer(false);
            row.setTransferGroupKey(null);
            row.setToAccountName(null);
            row.setToAccountNumber(null);
            String other = row.getOfxCounterpartyAccount();
            if (other == null && row.getOfxTransferReference() != null) {
                List<ImportedTransaction> referenced =
                        rows.stream()
                                .filter(
                                        candidate ->
                                                Objects.equals(
                                                        row.getOfxTransferReference(),
                                                        candidate.getOfxTransferReference()))
                                .toList();
                if (referenced.size() == 2) {
                    ImportedTransaction candidate =
                            referenced.get(0) == row ? referenced.get(1) : referenced.get(0);
                    if (opposite(row, candidate)) other = candidate.getAccountNumber();
                }
            }
            if (other == null || same(other, row.getAccountNumber())) continue;
            String number = other;
            List<Account> owned =
                    accounts.stream()
                            .filter(
                                    account ->
                                            account.getDeletedDate() == null
                                                    && same(number, account.getAccountNumber()))
                            .toList();
            if (owned.size() > 1) continue;
            ImportedTransaction declared =
                    rows.stream()
                            .filter(candidate -> same(number, candidate.getAccountNumber()))
                            .findFirst()
                            .orElse(null);
            if (declared == null && owned.isEmpty()) continue;
            row.setTransfer(true);
            row.setToAccountNumber(number);
            row.setToAccountName(
                    declared != null ? declared.getAccountName() : owned.getFirst().getName());
            row.setCategory(null);
        }
    }

    private static boolean opposite(ImportedTransaction first, ImportedTransaction second) {
        return second.isOfxTransfer()
                && !first.hasErrors()
                && !second.hasErrors()
                && first.getAmount() != null
                && second.getAmount() != null
                && first.getAmount().signum() * second.getAmount().signum() == -1
                && first.getAccountNumber() != null
                && second.getAccountNumber() != null
                && !same(first.getAccountNumber(), second.getAccountNumber())
                && (second.getOfxCounterpartyAccount() == null
                        || same(second.getOfxCounterpartyAccount(), first.getAccountNumber()));
    }

    private static boolean same(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }
}
