package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for initiating transaction import from uploaded file.
 *
 * <p>This DTO is used to start the import process after a file has been uploaded. The uploadId
 * references a previously uploaded file, and accountId optionally specifies the target account (can
 * be selected during review if not provided).
 *
 * <p>Requirement REQ-2.5.1: Transaction import from files
 *
 * <p>Requirement REQ-2.5.1.2: Import process initiation
 *
 * @see org.openfinance.entity.ImportSession
 * @see org.openfinance.service.ImportService#startImport(String, Long, Long)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportProcessRequest {

    /**
     * UUID of the previously uploaded file.
     *
     * <p>This is returned by the /api/v1/import/upload endpoint after successful file upload.
     *
     * <p>Requirement REQ-2.5.1.1: File upload and validation
     */
    @NotBlank(message = "{import.upload.id.required}")
    private String uploadId;

    /**
     * Optional target account ID for imported transactions.
     *
     * <p>If provided, all imported transactions will be associated with this account. If null, the
     * user can select the account during the review step.
     *
     * <p>Requirement REQ-2.5.1.3: Account selection for import
     */
    private Long accountId;

    /**
     * Original filename of the uploaded file as shown to the user.
     *
     * <p>Used to derive a friendly account name when no account is selected and the file does not
     * contain account information. If null, the import service falls back to the UUID-based storage
     * filename.
     */
    private String originalFileName;
}
