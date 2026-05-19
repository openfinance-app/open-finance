package org.openfinance.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO for file upload operations. Contains upload metadata and validation status.
 *
 * <p>Requirement REQ-2.5.1.1: File Format Support
 *
 * <p>Example response:
 *
 * <pre>
 * {
 *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
 *   "fileName": "transactions.qif",
 *   "fileSize": 12345,
 *   "fileType": "qif",
 *   "status": "VALIDATED",
 *   "message": "File uploaded successfully",
 *   "uploadedAt": "2024-01-15T10:30:00"
 * }
 * </pre>
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-01-15
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    /**
     * Unique identifier for the upload session. Used to track the file through the import process.
     */
    private String uploadId;

    /** Original name of the uploaded file. */
    private String fileName;

    /** Size of the uploaded file in bytes. */
    private Long fileSize;

    /** Detected file type/format (qif, ofx, qfx, csv). */
    private String fileType;

    /** Validation status of the upload. Possible values: VALIDATED, INVALID, ERROR */
    private String status;

    /**
     * Human-readable message about the upload result. Contains validation errors if status is not
     * VALIDATED.
     */
    private String message;

    /** Timestamp when the file was uploaded. */
    private LocalDateTime uploadedAt;

    /**
     * Number of records detected in the file (if applicable). Null if file hasn't been parsed yet.
     */
    private Integer recordCount;
}
