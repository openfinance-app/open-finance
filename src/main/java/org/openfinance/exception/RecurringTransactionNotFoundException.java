package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested recurring transaction cannot be found.
 *
 * <p>This exception is thrown when:
 *
 * <ul>
 *   <li>A recurring transaction ID does not exist in the database
 *   <li>A user attempts to access a recurring transaction they don't own (authorization failure)
 * </ul>
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own recurring transactions
 *
 * <p>Requirement REQ-2.3.6: Recurring transaction management
 *
 * @see org.openfinance.entity.RecurringTransaction
 * @since 1.0
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecurringTransactionNotFoundException extends RuntimeException {

    /**
     * Constructs a new RecurringTransactionNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public RecurringTransactionNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new RecurringTransactionNotFoundException with a message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public RecurringTransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Factory method for creating an exception when recurring transaction is not found by ID.
     *
     * @param recurringTransactionId the recurring transaction ID that was not found
     * @return a new RecurringTransactionNotFoundException
     */
    public static RecurringTransactionNotFoundException byId(Long recurringTransactionId) {
        return new RecurringTransactionNotFoundException(
                String.format(
                        "Recurring transaction with ID %d not found", recurringTransactionId));
    }

    /**
     * Factory method for creating an exception when recurring transaction is not found or not owned
     * by user.
     *
     * @param recurringTransactionId the recurring transaction ID that was not found
     * @param userId the user ID attempting to access the recurring transaction
     * @return a new RecurringTransactionNotFoundException
     */
    public static RecurringTransactionNotFoundException byIdAndUser(
            Long recurringTransactionId, Long userId) {
        return new RecurringTransactionNotFoundException(
                String.format(
                        "Recurring transaction with ID %d not found or not accessible by user %d",
                        recurringTransactionId, userId));
    }
}
