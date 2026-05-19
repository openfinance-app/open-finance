package org.openfinance.exception;

/**
 * Exception thrown when a requested resource is not found.
 *
 * <p>This is a runtime exception that should be mapped to HTTP 404 Not Found by the
 * GlobalExceptionHandler.
 *
 * @see org.openfinance.exception.GlobalExceptionHandler
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Constructs a new resource not found exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new resource not found exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
