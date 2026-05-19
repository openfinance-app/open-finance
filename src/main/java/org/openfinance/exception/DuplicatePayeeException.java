package org.openfinance.exception;

/** Exception thrown when attempting to create a payee with a name that already exists. */
public class DuplicatePayeeException extends RuntimeException {

    public DuplicatePayeeException(String name) {
        super("A payee with this name already exists: " + name);
    }
}
