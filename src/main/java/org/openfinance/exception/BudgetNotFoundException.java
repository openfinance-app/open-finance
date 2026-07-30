package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a budget is not found in the database.
 *
 * <p>This exception is typically thrown when attempting to retrieve, update, or delete a budget by
 * ID that doesn't exist or doesn't belong to the user.
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own budgets
 *
 * <p>Requirement REQ-2.9.1: Budget tracking with proper error handling
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BudgetNotFoundException extends RuntimeException implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    /**
     * Constructs a new BudgetNotFoundException with a detail message.
     *
     * @param message the detail message
     */
    public BudgetNotFoundException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /**
     * Constructs a new BudgetNotFoundException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public BudgetNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.messageKey = null;
        this.messageArgs = null;
    }

    private BudgetNotFoundException(String message, String messageKey, Object[] messageArgs) {
        super(message);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    /**
     * Creates an exception for a budget not found by ID.
     *
     * @param budgetId the budget ID that was not found
     * @return a new BudgetNotFoundException
     */
    public static BudgetNotFoundException byId(Long budgetId) {
        return new BudgetNotFoundException(
                "Budget not found with id: " + budgetId,
                "error.budget.not.found",
                new Object[] {budgetId});
    }

    /**
     * Creates an exception for a budget not found for a specific user.
     *
     * @param budgetId the budget ID
     * @param userId the user ID
     * @return a new BudgetNotFoundException
     */
    public static BudgetNotFoundException byIdAndUser(Long budgetId, Long userId) {
        return new BudgetNotFoundException(
                String.format("Budget not found with id: %d for user: %d", budgetId, userId),
                "error.budget.not.found",
                new Object[] {budgetId});
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
