package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when category validation fails.
 *
 * <p>This exception is thrown for various category-related business rule violations:
 *
 * <ul>
 *   <li>Attempting to update/delete system categories
 *   <li>Invalid parent-child relationships (type mismatch, circular reference)
 *   <li>Deleting categories with subcategories
 *   <li>Category type mismatch with transaction type
 * </ul>
 *
 * <p>Requirement REQ-2.4: Category Management - Business rule enforcement
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCategoryException extends RuntimeException {

    /**
     * Constructs a new InvalidCategoryException with a detail message.
     *
     * @param message the detail message
     */
    public InvalidCategoryException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidCategoryException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public InvalidCategoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
