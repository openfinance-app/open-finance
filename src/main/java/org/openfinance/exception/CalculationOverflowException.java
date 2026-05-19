package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when calculation results exceed representable limits.
 *
 * <p>Thrown when intermediate or final calculation results overflow the maximum representable
 * values. This can occur with very large savings amounts or extremely long projection periods.
 *
 * <p>Maps to HTTP 400 Bad Request with a suggestion to simplify inputs.
 *
 * <p>Edge Case EC 4: Large Numbers - Handling very large values (> €100M)
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CalculationOverflowException extends RuntimeException {

    /** The operation that caused the overflow. */
    private final String operation;

    /**
     * Creates a new CalculationOverflowException.
     *
     * @param message Human-readable error message
     */
    public CalculationOverflowException(String message) {
        super(message);
        this.operation = null;
    }

    /**
     * Creates a new CalculationOverflowException with operation context.
     *
     * @param message Human-readable error message
     * @param operation The calculation operation that overflowed
     */
    public CalculationOverflowException(String message, String operation) {
        super(message);
        this.operation = operation;
    }

    /**
     * Creates a new CalculationOverflowException with cause.
     *
     * @param message Human-readable error message
     * @param cause The underlying arithmetic exception
     */
    public CalculationOverflowException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
    }

    /**
     * Factory method for calculation overflow.
     *
     * @param operation the operation that overflowed
     * @param value the value that exceeded limits
     * @return a new CalculationOverflowException
     */
    public static CalculationOverflowException duringCalculation(String operation, String value) {
        return new CalculationOverflowException(
                String.format(
                        "Calculation overflow during %s: value %s exceeds representable limit",
                        operation, value),
                operation);
    }

    /**
     * Returns the operation that caused the overflow.
     *
     * @return Operation name or null if not set
     */
    public String getOperation() {
        return operation;
    }
}
