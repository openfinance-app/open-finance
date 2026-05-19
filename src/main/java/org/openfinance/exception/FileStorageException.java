package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when file storage operations fail.
 *
 * <p>This exception is thrown when there are issues with reading, writing, encrypting, or
 * decrypting files on the filesystem.
 *
 * <p>Common scenarios:
 *
 * <ul>
 *   <li>Failed to write file to disk
 *   <li>Failed to read file from disk
 *   <li>Failed to encrypt/decrypt file contents
 *   <li>Failed to delete file from disk
 *   <li>Storage directory not accessible
 * </ul>
 *
 * <p>Requirement REQ-2.12: File Attachment System - File storage operations
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class FileStorageException extends RuntimeException {

    /**
     * Constructs a new FileStorageException with a detail message.
     *
     * @param message the detail message
     */
    public FileStorageException(String message) {
        super(message);
    }

    /**
     * Constructs a new FileStorageException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for failed file write operation.
     *
     * @param fileName the file name
     * @param cause the underlying cause
     * @return a new FileStorageException
     */
    public static FileStorageException writeFailure(String fileName, Throwable cause) {
        return new FileStorageException("Failed to write file: " + fileName, cause);
    }

    /**
     * Creates an exception for failed file read operation.
     *
     * @param fileName the file name
     * @param cause the underlying cause
     * @return a new FileStorageException
     */
    public static FileStorageException readFailure(String fileName, Throwable cause) {
        return new FileStorageException("Failed to read file: " + fileName, cause);
    }

    /**
     * Creates an exception for failed encryption operation.
     *
     * @param cause the underlying cause
     * @return a new FileStorageException
     */
    public static FileStorageException encryptionFailure(Throwable cause) {
        return new FileStorageException("Failed to encrypt file", cause);
    }

    /**
     * Creates an exception for failed decryption operation.
     *
     * @param cause the underlying cause
     * @return a new FileStorageException
     */
    public static FileStorageException decryptionFailure(Throwable cause) {
        return new FileStorageException("Failed to decrypt file", cause);
    }
}
