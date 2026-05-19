package org.openfinance.exception;

/**
 * Exception thrown when an institution is not found.
 *
 * <p>Requirements: REQ-2.6.1.3 - Predefined Financial Institutions
 */
public class InstitutionNotFoundException extends ResourceNotFoundException
        implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    public InstitutionNotFoundException(Long id) {
        super("Institution not found with id: " + id);
        this.messageKey = "error.institution.not.found";
        this.messageArgs = new Object[] {id};
    }

    public InstitutionNotFoundException(String message) {
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
