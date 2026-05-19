package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an attachment is not found in the database.
 *
 * <p>This exception is typically thrown when attempting to retrieve, download, or delete an
 * attachment by ID that doesn't exist or doesn't belong to the user.
 *
 * <p>Requirement REQ-2.12: File Attachment System
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own attachments
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AttachmentNotFoundException extends RuntimeException {

    /**
     * Constructs a new AttachmentNotFoundException with a detail message.
     *
     * @param message the detail message
     */
    public AttachmentNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new AttachmentNotFoundException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public AttachmentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for an attachment not found by ID.
     *
     * @param attachmentId the attachment ID that was not found
     * @return a new AttachmentNotFoundException
     */
    public static AttachmentNotFoundException byId(Long attachmentId) {
        return new AttachmentNotFoundException("Attachment not found with id: " + attachmentId);
    }

    /**
     * Creates an exception for an attachment not found for a specific user.
     *
     * @param attachmentId the attachment ID
     * @param userId the user ID
     * @return a new AttachmentNotFoundException
     */
    public static AttachmentNotFoundException byIdAndUser(Long attachmentId, Long userId) {
        return new AttachmentNotFoundException(
                String.format(
                        "Attachment not found with id: %d for user: %d", attachmentId, userId));
    }
}
