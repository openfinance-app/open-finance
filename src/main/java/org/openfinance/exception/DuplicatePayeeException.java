package org.openfinance.exception;

/**
 * Exception thrown when attempting to create a payee whose name already exists for the user.
 *
 * <p>Implements {@link LocalizableException} so the pre-existing {@code error.payee.duplicate} i18n
 * key is resolved (EN/FR) by {@code GlobalExceptionHandler}, with the payee name supplied as a
 * message argument ({@code {0}}). The {@code super(...)} message remains a raw English fallback for
 * logs and non-localized contexts.
 */
public class DuplicatePayeeException extends RuntimeException implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    public DuplicatePayeeException(String name) {
        super("A payee with this name already exists: " + name);
        this.messageKey = "error.payee.duplicate";
        this.messageArgs = new Object[] {name};
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
