package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for data export operations.
 *
 * <p>Requirement: REQ-3.4 - Data Export and Backup
 *
 * @author Open Finance Development Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportRequest {

    /** Export format: JSON or CSV */
    @NotBlank(message = "{export.format.required}")
    @Pattern(regexp = "^(JSON|CSV)$", message = "{export.format.invalid}")
    private String format;

    /** Include accounts in export */
    @Builder.Default private boolean includeAccounts = true;

    /** Include transactions in export */
    @Builder.Default private boolean includeTransactions = true;

    /** Include assets in export */
    @Builder.Default private boolean includeAssets = true;

    /** Include liabilities in export */
    @Builder.Default private boolean includeLiabilities = true;

    /** Include budgets in export */
    @Builder.Default private boolean includeBudgets = true;

    /** Include categories in export */
    @Builder.Default private boolean includeCategories = true;

    /** Include real estate properties in export */
    @Builder.Default private boolean includeRealEstate = true;

    /** Optional start date for filtering transactions and time-based data */
    private LocalDate startDate;

    /** Optional end date for filtering transactions and time-based data */
    private LocalDate endDate;

    /** Include deleted/soft-deleted records */
    @Builder.Default private boolean includeDeleted = false;
}
