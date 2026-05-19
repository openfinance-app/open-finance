package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when attempting to delete an account that has active transactions.
 *
 * <p>This exception is thrown when a user tries to soft-delete an account that still has associated
 * transactions. To maintain data integrity and prevent orphaned transactions, accounts with active
 * transactions cannot be deleted.
 *
 * <p>The user should either:
 *
 * <ul>
 *   <li>Delete all transactions associated with the account first, or
 *   <li>Reassign the transactions to another account before deletion
 * </ul>
 *
 * <p>Requirement REQ-2.2.4: Prevent soft-delete of accounts with active transactions
 *
 * <p>Requirement REQ-2.5: Data integrity - maintain referential integrity for transactions
 *
 * @since 1.0
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AccountHasTransactionsException extends RuntimeException {

    /**
     * Constructs a new AccountHasTransactionsException with a detail message.
     *
     * @param message the detail message
     */
    public AccountHasTransactionsException(String message) {
        super(message);
    }

    /**
     * Constructs a new AccountHasTransactionsException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public AccountHasTransactionsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for an account with active transactions.
     *
     * @param accountName the name of the account (decrypted)
     * @param transactionCount the number of active transactions
     * @return a new AccountHasTransactionsException with a user-friendly message
     */
    public static AccountHasTransactionsException forAccount(
            String accountName, long transactionCount) {
        return new AccountHasTransactionsException(
                String.format(
                        "Cannot delete account '%s' because it has %d active transaction%s. "
                                + "Delete or reassign the transaction%s first.",
                        accountName,
                        transactionCount,
                        transactionCount == 1 ? "" : "s",
                        transactionCount == 1 ? "" : "s"));
    }
}
