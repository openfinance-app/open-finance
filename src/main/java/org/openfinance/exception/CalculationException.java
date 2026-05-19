package org.openfinance.exception;

/**
 * Base exception for financial calculation errors.
 *
 * <p>Extends RuntimeException to allow unchecked exception handling while providing structured
 * error information.
 *
 * <p>Requirement NFR 1: Performance - Error handling should not impact calculation performance
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
public class CalculationException extends RuntimeException {

    /**
     * Creates a new CalculationException with the specified message.
     *
     * @param message Human-readable error message
     */
    public CalculationException(String message) {
        super(message);
    }

    /**
     * Creates a new CalculationException with message and cause.
     *
     * @param message Human-readable error message
     * @param cause The underlying exception
     */
    public CalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
