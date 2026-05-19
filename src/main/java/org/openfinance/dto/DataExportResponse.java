package org.openfinance.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for data export operations.
 *
 * <p>Requirement: REQ-3.4 - Data Export and Backup
 *
 * @author Open Finance Development Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportResponse {

    /** Unique export ID for tracking */
    private String exportId;

    /** Export format (JSON or CSV) */
    private String format;

    /** Filename for the exported file */
    private String filename;

    /** File size in bytes */
    private long fileSizeBytes;

    /** Number of accounts exported */
    private int accountCount;

    /** Number of transactions exported */
    private int transactionCount;

    /** Number of assets exported */
    private int assetCount;

    /** Number of liabilities exported */
    private int liabilityCount;

    /** Number of budgets exported */
    private int budgetCount;

    /** Number of categories exported */
    private int categoryCount;

    /** Number of real estate properties exported */
    private int realEstateCount;

    /** Export generation timestamp */
    private LocalDateTime generatedAt;

    /** Export expiration timestamp (downloadable for 24 hours) */
    private LocalDateTime expiresAt;

    /** Message providing additional context */
    private String message;
}
