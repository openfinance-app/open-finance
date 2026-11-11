package org.openfinance.service;

import java.util.List;
import java.util.Map;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Account;

/** Resolves statement account keys using the caller's already authorized account mapping. */
final class ImportedAccountRouting {
    private ImportedAccountRouting() {}

    static Long resolveImportedAccountId(
            ImportedTransaction importedTx,
            Map<String, Long> accountIdsByKey,
            Long fallbackAccountId) {
        String descriptorKey =
                ImportStatementPositions.accountKey(
                        importedTx.getAccountName(), importedTx.getAccountNumber());
        if (descriptorKey != null && accountIdsByKey.containsKey(descriptorKey)) {
            return accountIdsByKey.get(descriptorKey);
        }
        return fallbackAccountId;
    }

    static Long resolveImportedDestinationAccountId(
            ImportedTransaction importedTx,
            Map<String, Long> accountIdsByKey,
            Long fallbackAccountId) {
        String descriptorKey =
                ImportStatementPositions.accountKey(
                        importedTx.getToAccountName(), importedTx.getToAccountNumber());
        if (descriptorKey != null && accountIdsByKey.containsKey(descriptorKey)) {
            return accountIdsByKey.get(descriptorKey);
        }
        return fallbackAccountId;
    }

    /** Reject closed accounts before import routing changes their balances or active state. */
    static void requireActiveImportAccount(Account account) {
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new org.openfinance.exception.InvalidTransactionException(
                    "Cannot import to inactive account: " + account.getId());
        }
    }

    static boolean shouldUseImportedAccountRouting(List<ImportedTransaction> transactions) {
        return transactions.stream().anyMatch(ImportedAccountRouting::hasImportedAccountReference);
    }

    private static boolean hasImportedAccountReference(ImportedTransaction transaction) {
        return (transaction.getAccountName() != null && !transaction.getAccountName().isBlank())
                || (transaction.getAccountNumber() != null
                        && !transaction.getAccountNumber().isBlank())
                || (transaction.getToAccountName() != null
                        && !transaction.getToAccountName().isBlank());
    }

    static boolean matchesImportedAccount(
            Account existingAccount, String importedName, String importedAccountNumber) {
        if (existingAccount.getDeletedDate() != null) return false;
        String existingName = existingAccount.getName();
        String existingAccountNumber = existingAccount.getAccountNumber();
        boolean sameName =
                existingName != null
                        && importedName != null
                        && existingName.equalsIgnoreCase(importedName);
        boolean sameNumber =
                importedAccountNumber != null
                        && !importedAccountNumber.isBlank()
                        && existingAccountNumber != null
                        && existingAccountNumber.equalsIgnoreCase(importedAccountNumber);
        return sameName || sameNumber;
    }

    static boolean requiresFallbackAccount(List<ImportedTransaction> transactions) {
        return transactions.stream()
                .anyMatch(
                        transaction ->
                                !transaction.isTransfer()
                                        && (transaction.getAccountName() == null
                                                || transaction.getAccountName().isBlank())
                                        && (transaction.getAccountNumber() == null
                                                || transaction.getAccountNumber().isBlank()));
    }
}
