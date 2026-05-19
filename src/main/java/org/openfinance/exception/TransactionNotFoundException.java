package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested transaction cannot be found.
 *
 * <p>This exception is thrown when:
 *
 * <ul>
 *   <li>A transaction ID does not exist in the database
 *   <li>A user attempts to access a transaction they don't own (authorization failure)
 *   <li>A transaction has been soft-deleted and is no longer accessible
 * </ul>
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own transactions
 *
 * <p>Requirement REQ-2.4.1.3: Soft-deleted transactions are hidden from normal queries
 *
 * @see org.openfinance.entity.Transaction
 * @since 1.0
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TransactionNotFoundException extends RuntimeException {

    /**
     * Constructs a new TransactionNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public TransactionNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new TransactionNotFoundException with a message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public TransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Factory method for creating an exception when transaction is not found by ID.
     *
     * @param transactionId the transaction ID that was not found
     * @return a new TransactionNotFoundException
     */
    public static TransactionNotFoundException byId(Long transactionId) {
        return new TransactionNotFoundException(
                String.format("Transaction with ID %d not found", transactionId));
    }

    /**
     * Factory method for creating an exception when transaction is not found or not owned by user.
     *
     * @param transactionId the transaction ID that was not found
     * @param userId the user ID attempting to access the transaction
     * @return a new TransactionNotFoundException
     */
    public static TransactionNotFoundException byIdAndUser(Long transactionId, Long userId) {
        return new TransactionNotFoundException(
                String.format(
                        "Transaction with ID %d not found or not accessible by user %d",
                        transactionId, userId));
    }
}
