package org.openfinance.exception;

/**
 * Exception thrown when a requested resource is not found.
 *
 * <p>This is a runtime exception that should be mapped to HTTP 404 Not Found by the
 * GlobalExceptionHandler.
 *
 * @see org.openfinance.exception.GlobalExceptionHandler
 */
public class ResourceNotFoundException extends RuntimeException implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    /**
     * Constructs a new resource not found exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ResourceNotFoundException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /**
     * Constructs a new resource not found exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /**
     * Constructs a new resource not found exception for a resource ID.
     *
     * @param resourceId the resource ID that was not found
     */
    public ResourceNotFoundException(Long resourceId) {
        super("Resource not found with id: " + resourceId);
        this.messageKey = "error.resource.not.found";
        this.messageArgs = new Object[] {resourceId};
    }

    @Override
    public String getMessageKey() {
        return messageKey;
    }

    @Override
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
