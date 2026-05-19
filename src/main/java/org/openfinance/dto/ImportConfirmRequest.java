package org.openfinance.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for confirming transaction import after user review.
 *
 * <p>This DTO is used to finalize the import process after the user has reviewed parsed
 * transactions, mapped categories, and decided how to handle duplicates.
 *
 * <p>Requirement REQ-2.5.1.5: Category mapping during import
 *
 * <p>Requirement REQ-2.5.1.6: Duplicate detection and handling
 *
 * @see org.openfinance.entity.ImportSession
 * @see org.openfinance.service.ImportService#confirmImport(Long, Long, Long, Map, boolean)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportConfirmRequest {

    /**
     * Target account ID for imported transactions.
     *
     * <p>Optional. If not provided, the backend will auto-create an account using the suggested
     * account name detected during file parsing.
     *
     * <p>Requirement REQ-2.5.1.3: Account selection for import
     */
    private Long accountId;

    /**
     * Mapping of imported category names to existing category IDs.
     *
     * <p>Key: Original category name from imported file (e.g., "Groceries")
     *
     * <p>Value: ID of existing category in the database
     *
     * <p>Example:
     *
     * <pre>{@code
     * {
     *   "Groceries": 15,
     *   "Salary": 3,
     *   "Utilities": 22
     * }
     * }</pre>
     *
     * <p>If a category name is not in this map, the system will attempt to auto-create it or leave
     * it unmapped based on configuration.
     *
     * <p>Requirement REQ-2.5.1.5: Category mapping during import
     */
    @Builder.Default private Map<String, Long> categoryMappings = new HashMap<>();

    /**
     * Whether to skip transactions flagged as potential duplicates.
     *
     * <p>If true, transactions with "DUPLICATE:" validation errors will be skipped. If false,
     * duplicates will be imported (user accepts potential duplicates).
     *
     * <p>Requirement REQ-2.5.1.6: Duplicate detection and handling
     */
    @Builder.Default private boolean skipDuplicates = true;
}
