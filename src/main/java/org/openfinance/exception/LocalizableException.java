package org.openfinance.exception;

/**
 * Marker interface for exceptions that carry a Spring MessageSource key and optional arguments, so
 * {@link GlobalExceptionHandler} can resolve a locale-aware message instead of using the raw
 * English string.
 */
public interface LocalizableException {

    /**
     * Returns the MessageSource key for this exception's message.
     *
     * @return message key (e.g. {@code "error.institution.not.found"})
     */
    String getMessageKey();

    /**
     * Returns the positional arguments to be interpolated into the message pattern. May be {@code
     * null} or empty when the message has no placeholders.
     *
     * @return argument array or {@code null}
     */
    Object[] getMessageArgs();
}
