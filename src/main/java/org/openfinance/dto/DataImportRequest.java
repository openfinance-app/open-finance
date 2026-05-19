package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for data import operations.
 *
 * <p>Requirement: REQ-3.4 - Data Import and Restoration
 *
 * @author Open Finance Development Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataImportRequest {

    /** Import format: JSON or CSV */
    @NotBlank(message = "{import.format.required}")
    @Pattern(regexp = "^(JSON|CSV)$", message = "{import.format.invalid}")
    private String format;

    /**
     * Import mode: MERGE or REPLACE
     *
     * <p>MERGE: Adds new records, updates existing ones by ID
     *
     * <p>REPLACE: Deletes all existing data and imports fresh
     */
    @NotBlank(message = "{import.mode.required}")
    @Pattern(regexp = "^(MERGE|REPLACE)$", message = "{import.mode.invalid}")
    private String mode;

    /** Skip validation errors and continue import */
    @Builder.Default private boolean skipErrors = false;

    /** Dry run mode - validate without importing */
    @Builder.Default private boolean dryRun = false;
}
