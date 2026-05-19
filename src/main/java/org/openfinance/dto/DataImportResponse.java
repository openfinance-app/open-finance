package org.openfinance.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for data import operations.
 *
 * <p>Requirement: REQ-3.4 - Data Import and Restoration
 *
 * @author Open Finance Development Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataImportResponse {

    /** Unique import ID for tracking */
    private String importId;

    /** Import status: SUCCESS, PARTIAL_SUCCESS, FAILED */
    private String status;

    /** Number of accounts imported */
    private int accountsImported;

    /** Number of transactions imported */
    private int transactionsImported;

    /** Number of assets imported */
    private int assetsImported;

    /** Number of liabilities imported */
    private int liabilitiesImported;

    /** Number of budgets imported */
    private int budgetsImported;

    /** Number of categories imported */
    private int categoriesImported;

    /** Number of real estate properties imported */
    private int realEstateImported;

    /** Number of records that failed to import */
    private int failedRecords;

    /** List of error messages for failed records */
    @Builder.Default private List<String> errors = new ArrayList<>();

    /** Import timestamp */
    private LocalDateTime importedAt;

    /** Message providing additional context */
    private String message;
}
