package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when calculation input validation fails.
 *
 * <p>Thrown when user-provided input values are invalid or outside acceptable ranges. This includes
 * negative savings, excessive return rates, or missing required fields.
 *
 * <p>Maps to HTTP 400 Bad Request response.
 *
 * <p>Requirement 3.2: Input Validation
 *
 * <p>Edge Case EC 1: Zero or Negative Inputs
 *
 * <p>Edge Case EC 4: Large Numbers
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CalculationValidationException extends RuntimeException {

    /** Field name that caused the validation error. */
    private final String fieldName;

    /**
     * Creates a new CalculationValidationException.
     *
     * @param message Human-readable error message
     */
    public CalculationValidationException(String message) {
        super(message);
        this.fieldName = null;
    }

    /**
     * Creates a new CalculationValidationException with field context.
     *
     * @param message Human-readable error message
     * @param fieldName The field that failed validation
     */
    public CalculationValidationException(String message, String fieldName) {
        super(message);
        this.fieldName = fieldName;
    }

    /**
     * Creates a new CalculationValidationException with cause.
     *
     * @param message Human-readable error message
     * @param fieldName The field that failed validation
     * @param cause The underlying exception
     */
    public CalculationValidationException(String message, String fieldName, Throwable cause) {
        super(message, cause);
        this.fieldName = fieldName;
    }

    /**
     * Factory method for negative savings.
     *
     * @param fieldName the field name
     * @param value the invalid value
     * @return a new CalculationValidationException
     */
    public static CalculationValidationException negativeValue(String fieldName, String value) {
        return new CalculationValidationException(
                String.format("%s must be non-negative: %s", fieldName, value), fieldName);
    }

    /**
     * Factory method for value out of range.
     *
     * @param fieldName the field name
     * @param value the invalid value
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return a new CalculationValidationException
     */
    public static CalculationValidationException outOfRange(
            String fieldName, String value, String min, String max) {
        return new CalculationValidationException(
                String.format("%s must be between %s and %s: %s", fieldName, min, max, value),
                fieldName);
    }

    /**
     * Factory method for required field missing.
     *
     * @param fieldName the field name
     * @return a new CalculationValidationException
     */
    public static CalculationValidationException required(String fieldName) {
        return new CalculationValidationException(
                String.format("%s is required", fieldName), fieldName);
    }

    /**
     * Returns the field name that caused the validation error.
     *
     * @return Field name or null if not set
     */
    public String getFieldName() {
        return fieldName;
    }
}
