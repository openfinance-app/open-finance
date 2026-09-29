package org.openfinance.exception;

/** A reversal is unavailable or conflicts with a later action; its transaction must roll back. */
public class HistoryConflictException extends RuntimeException {
    private final String reason;

    public HistoryConflictException(String reason) {
        super("history.unavailable." + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
