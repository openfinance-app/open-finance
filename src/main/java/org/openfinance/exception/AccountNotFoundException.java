package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an account is not found in the database.
 *
 * <p>This exception is typically thrown when attempting to retrieve, update, or delete an account
 * by ID that doesn't exist or doesn't belong to the user.
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own accounts
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {

    /**
     * Constructs a new AccountNotFoundException with a detail message.
     *
     * @param message the detail message
     */
    public AccountNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new AccountNotFoundException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for an account not found by ID.
     *
     * @param accountId the account ID that was not found
     * @return a new AccountNotFoundException
     */
    public static AccountNotFoundException byId(Long accountId) {
        return new AccountNotFoundException("Account not found with id: " + accountId);
    }

    /**
     * Creates an exception for an account not found for a specific user.
     *
     * @param accountId the account ID
     * @param userId the user ID
     * @return a new AccountNotFoundException
     */
    public static AccountNotFoundException byIdAndUser(Long accountId, Long userId) {
        return new AccountNotFoundException(
                String.format("Account not found with id: %d for user: %d", accountId, userId));
    }
}
