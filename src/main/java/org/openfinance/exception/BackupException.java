package org.openfinance.exception;

/**
 * Exception thrown when backup or restore operations fail.
 *
 * <p><b>Requirements:</b> REQ-2.14.2
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
public class BackupException extends RuntimeException {

    /**
     * Constructs a new BackupException with the specified detail message.
     *
     * @param message the detail message
     */
    public BackupException(String message) {
        super(message);
    }

    /**
     * Constructs a new BackupException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
