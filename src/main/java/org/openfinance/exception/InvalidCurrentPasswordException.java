package org.openfinance.exception;

/** Localized validation error for a password change. */
public class InvalidCurrentPasswordException extends IllegalArgumentException
        implements LocalizableException {
    public InvalidCurrentPasswordException() {
        super("Current password is incorrect");
    }

    @Override
    public String getMessageKey() {
        return "error.password.current.incorrect";
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[0];
    }
}
