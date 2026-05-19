package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a liability is not found in the database.
 *
 * <p>This exception is typically thrown when attempting to retrieve, update, or delete a liability
 * by ID that doesn't exist or doesn't belong to the user.
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own liabilities
 *
 * <p>Requirement REQ-6.1: Liability Management
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class LiabilityNotFoundException extends RuntimeException {

    /**
     * Constructs a new LiabilityNotFoundException with a detail message.
     *
     * @param message the detail message
     */
    public LiabilityNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new LiabilityNotFoundException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public LiabilityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for a liability not found by ID.
     *
     * @param liabilityId the liability ID that was not found
     * @return a new LiabilityNotFoundException
     */
    public static LiabilityNotFoundException byId(Long liabilityId) {
        return new LiabilityNotFoundException("Liability not found with id: " + liabilityId);
    }

    /**
     * Creates an exception for a liability not found for a specific user.
     *
     * @param liabilityId the liability ID
     * @param userId the user ID
     * @return a new LiabilityNotFoundException
     */
    public static LiabilityNotFoundException byIdAndUser(Long liabilityId, Long userId) {
        return new LiabilityNotFoundException(
                String.format("Liability not found with id: %d for user: %d", liabilityId, userId));
    }
}
