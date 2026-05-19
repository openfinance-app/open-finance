package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a transaction request contains invalid data or violates business rules.
 *
 * <p>This exception is thrown when:
 *
 * <ul>
 *   <li>Transaction type doesn't match category type (e.g., INCOME transaction with EXPENSE
 *       category)
 *   <li>TRANSFER transaction has same source and destination account
 *   <li>TRANSFER transaction has a category (transfers should not be categorized)
 *   <li>Account doesn't belong to the user
 *   <li>Currency doesn't match account currency
 *   <li>Amount is negative or zero
 * </ul>
 *
 * <p>Requirement REQ-2.4.1.1: Transaction validation and business rule enforcement
 *
 * <p>Requirement REQ-2.4.1.4: Transfer transactions must have different source and destination
 *
 * @see org.openfinance.entity.Transaction
 * @since 1.0
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTransactionException extends RuntimeException {

    /**
     * Constructs a new InvalidTransactionException with the specified message.
     *
     * @param message the detail message explaining the validation failure
     */
    public InvalidTransactionException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidTransactionException with a message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public InvalidTransactionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Factory method for account ownership violation.
     *
     * @param accountId the account ID that doesn't belong to the user
     * @param userId the user ID attempting to use the account
     * @return a new InvalidTransactionException
     */
    public static InvalidTransactionException accountNotOwnedByUser(Long accountId, Long userId) {
        return new InvalidTransactionException(
                String.format("Account with ID %d does not belong to user %d", accountId, userId));
    }

    /**
     * Factory method for category type mismatch.
     *
     * @param categoryType the category type (INCOME or EXPENSE)
     * @param transactionType the transaction type
     * @return a new InvalidTransactionException
     */
    public static InvalidTransactionException categoryTypeMismatch(
            String categoryType, String transactionType) {
        return new InvalidTransactionException(
                String.format(
                        "Category type %s does not match transaction type %s",
                        categoryType, transactionType));
    }

    /**
     * Factory method for transfer with same source and destination.
     *
     * @param accountId the account ID used for both source and destination
     * @return a new InvalidTransactionException
     */
    public static InvalidTransactionException sameTransferAccounts(Long accountId) {
        return new InvalidTransactionException(
                String.format(
                        "Transfer source and destination accounts cannot be the same (account ID: %d)",
                        accountId));
    }

    /**
     * Factory method for transfer with category assigned.
     *
     * @return a new InvalidTransactionException
     */
    public static InvalidTransactionException transferWithCategory() {
        return new InvalidTransactionException(
                "TRANSFER transactions should not have a category assigned");
    }

    /**
     * Factory method for transfer missing destination account.
     *
     * @return a new InvalidTransactionException
     */
    public static InvalidTransactionException transferMissingDestination() {
        return new InvalidTransactionException(
                "TRANSFER transactions must have a destination account (toAccountId)");
    }

    /**
     * Factory method for invalid amount (zero or negative).
     *
     * @param amount the invalid amount
     * @return a new InvalidTransactionException
     */
    public static InvalidTransactionException invalidAmount(String amount) {
        return new InvalidTransactionException(
                String.format(
                        "Invalid transaction amount: %s. Amount must be greater than zero",
                        amount));
    }

    /**
     * Factory method for currency mismatch between account and transaction.
     *
     * @param accountCurrency the account currency
     * @param transactionCurrency the transaction currency
     * @return a new InvalidTransactionException
     */
    public static InvalidTransactionException currencyMismatch(
            String accountCurrency, String transactionCurrency) {
        return new InvalidTransactionException(
                String.format(
                        "Transaction currency %s does not match account currency %s",
                        transactionCurrency, accountCurrency));
    }
}
