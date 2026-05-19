package org.openfinance.exception;

/**
 * Exception thrown when attempting to register a user with a username or email that already exists
 * in the system.
 *
 * <p>This is a business logic exception indicating a uniqueness constraint violation.
 *
 * <p>Requirement REQ-2.1.1: User registration with uniqueness validation
 *
 * @see
 *     org.openfinance.service.UserService#registerUser(org.openfinance.dto.UserRegistrationRequest)
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
public class DuplicateUserException extends RuntimeException {

    /**
     * Constructs a new DuplicateUserException with the specified detail message.
     *
     * @param message the detail message explaining which field is duplicated
     */
    public DuplicateUserException(String message) {
        super(message);
    }

    /**
     * Constructs a new DuplicateUserException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public DuplicateUserException(String message, Throwable cause) {
        super(message, cause);
    }
}
