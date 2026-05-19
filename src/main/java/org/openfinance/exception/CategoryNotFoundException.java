package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a category is not found in the database.
 *
 * <p>This exception is typically thrown when attempting to retrieve, update, or delete a category
 * by ID that doesn't exist or doesn't belong to the user.
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own categories
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CategoryNotFoundException extends RuntimeException implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    /**
     * Constructs a new CategoryNotFoundException with a detail message.
     *
     * @param message the detail message
     */
    public CategoryNotFoundException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /**
     * Constructs a new CategoryNotFoundException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public CategoryNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /** Private constructor for factory methods that carry a localizable key. */
    private CategoryNotFoundException(String message, String messageKey, Object[] messageArgs) {
        super(message);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    /**
     * Constructs a new CategoryNotFoundException for a missing category ID.
     *
     * @param categoryId the category ID that was not found
     */
    public CategoryNotFoundException(Long categoryId) {
        super("Category not found with id: " + categoryId);
        this.messageKey = "error.category.not.found";
        this.messageArgs = new Object[] {categoryId};
    }

    /**
     * Factory method for creating an exception when category is not found or not owned by user.
     *
     * @param categoryId the category ID that was not found
     * @param userId the user ID attempting to access the category
     * @return a new CategoryNotFoundException
     */
    public static CategoryNotFoundException byIdAndUser(Long categoryId, Long userId) {
        return new CategoryNotFoundException(
                String.format(
                        "Category with ID %d not found or not accessible by user %d",
                        categoryId, userId),
                "error.category.not.found.for.user",
                new Object[] {categoryId, userId});
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
