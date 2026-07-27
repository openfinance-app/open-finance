package org.openfinance.exception;

/**
 * Exception thrown when backup or restore operations fail.
 *
 * <p>Carries an explicit {@link Kind} so {@code GlobalExceptionHandler} can map it to the correct
 * HTTP status deterministically, instead of the previous fragile English-substring matching on the
 * message (which broke as soon as messages were translated).
 *
 * <p><b>Requirements:</b> REQ-2.14.2
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
public class BackupException extends RuntimeException {

    /** Classifies the failure so the HTTP status can be derived without inspecting the message. */
    public enum Kind {
        /** Client-side problem (bad/empty/corrupt input) → 400 Bad Request. */
        VALIDATION,
        /** Requested backup does not exist or is not accessible → 404 Not Found. */
        NOT_FOUND,
        /** Unexpected server-side failure → 500 Internal Server Error. */
        INTERNAL
    }

    private final Kind kind;

    /**
     * Constructs an {@link Kind#INTERNAL} BackupException (back-compat default).
     *
     * @param message the detail message
     */
    public BackupException(String message) {
        this(Kind.INTERNAL, message);
    }

    /**
     * Constructs an {@link Kind#INTERNAL} BackupException with a cause (back-compat default).
     *
     * @param message the detail message
     * @param cause the cause
     */
    public BackupException(String message, Throwable cause) {
        this(Kind.INTERNAL, message, cause);
    }

    /**
     * Constructs a BackupException with an explicit kind.
     *
     * @param kind the failure classification
     * @param message the detail message
     */
    public BackupException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * Constructs a BackupException with an explicit kind and cause.
     *
     * @param kind the failure classification
     * @param message the detail message
     * @param cause the cause
     */
    public BackupException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * @return the failure classification driving the HTTP status
     */
    public Kind getKind() {
        return kind;
    }

    /** Creates a {@link Kind#VALIDATION} (400) BackupException. */
    public static BackupException validation(String message) {
        return new BackupException(Kind.VALIDATION, message);
    }

    /** Creates a {@link Kind#VALIDATION} (400) BackupException with a cause. */
    public static BackupException validation(String message, Throwable cause) {
        return new BackupException(Kind.VALIDATION, message, cause);
    }

    /** Creates a {@link Kind#NOT_FOUND} (404) BackupException. */
    public static BackupException notFound(String message) {
        return new BackupException(Kind.NOT_FOUND, message);
    }

    /** Creates a {@link Kind#INTERNAL} (500) BackupException. */
    public static BackupException internal(String message) {
        return new BackupException(Kind.INTERNAL, message);
    }

    /** Creates a {@link Kind#INTERNAL} (500) BackupException with a cause. */
    public static BackupException internal(String message, Throwable cause) {
        return new BackupException(Kind.INTERNAL, message, cause);
    }
}
