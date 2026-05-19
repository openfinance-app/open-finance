package org.openfinance.exception;

/**
 * Exception thrown when a duplicate simulation name is detected.
 *
 * <p>This exception is thrown when attempting to create or update a simulation with a name that
 * already exists for the current user.
 *
 * <p>HTTP Status: 409 Conflict
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
public class DuplicateSimulationException extends RuntimeException {

    /**
     * Creates a new DuplicateSimulationException with the specified message.
     *
     * @param message the detail message
     */
    public DuplicateSimulationException(String message) {
        super(message);
    }

    /**
     * Creates a new DuplicateSimulationException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public DuplicateSimulationException(String message, Throwable cause) {
        super(message, cause);
    }
}
