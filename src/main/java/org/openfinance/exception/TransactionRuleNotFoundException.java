package org.openfinance.exception;

/**
 * Exception thrown when a {@link org.openfinance.entity.TransactionRule} is not found or does not
 * belong to the requesting user.
 *
 * <p>Requirement: REQ-TR-NFR-3 — user isolation enforced; returns 404 rather than disclosing the
 * existence of another user's rule.
 */
public class TransactionRuleNotFoundException extends ResourceNotFoundException
        implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    /**
     * Constructs the exception for a missing rule by ID.
     *
     * @param id the rule ID that was not found
     */
    public TransactionRuleNotFoundException(Long id) {
        super("Transaction rule not found with id: " + id);
        this.messageKey = "error.transaction.rule.not.found";
        this.messageArgs = new Object[] {id};
    }

    /**
     * Constructs the exception with a custom message.
     *
     * @param message the detail message
     */
    public TransactionRuleNotFoundException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
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
