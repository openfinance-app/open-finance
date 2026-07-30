package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user's account is locked due to multiple failed login attempts.
 *
 * @author Open-Finance Development Team
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountLockedException extends RuntimeException implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    public AccountLockedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    @Override
    public String getMessageKey() {
        return "error.account.locked";
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[0];
    }
}
