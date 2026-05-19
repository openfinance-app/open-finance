package org.openfinance.exception;

/**
 * Exception thrown when a payee is not found.
 *
 * <p>Requirements: Payee Management Feature
 */
public class PayeeNotFoundException extends ResourceNotFoundException
        implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    public PayeeNotFoundException(Long id) {
        super("Payee not found with id: " + id);
        this.messageKey = "error.payee.not.found";
        this.messageArgs = new Object[] {id};
    }

    public PayeeNotFoundException(String message) {
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
