package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user attempts to access a resource they are not authorized to access.
 *
 * <p>This exception is typically thrown when a user tries to perform an operation on a resource
 * (such as a simulation or property) that belongs to another user.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedException extends RuntimeException implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    /**
     * Constructs a new UnauthorizedException with a detail message.
     *
     * @param message the detail message
     */
    public UnauthorizedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /**
     * Constructs a new UnauthorizedException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /**
     * Creates an exception for accessing another user's simulation.
     *
     * @param simulationId the simulation ID
     * @param userId the user ID attempting access
     * @return a new UnauthorizedException
     */
    public static UnauthorizedException accessingAnotherUsersSimulation(
            Long simulationId, Long userId) {
        return new UnauthorizedException(
                String.format(
                        "User %d is not authorized to access simulation %d", userId, simulationId));
    }

    /**
     * Creates an exception for a generic authorization failure.
     *
     * @param operation the operation that failed authorization
     * @return a new UnauthorizedException
     */
    public static UnauthorizedException operation(String operation) {
        return new UnauthorizedException("Unauthorized to perform operation: " + operation);
    }

    @Override
    public String getMessageKey() {
        return "error.unauthorized";
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[0];
    }
}
