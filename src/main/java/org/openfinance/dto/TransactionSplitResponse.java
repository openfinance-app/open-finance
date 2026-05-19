package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object returned to clients for a single split line of a transaction.
 *
 * <p>Contains decrypted description and denormalized category metadata (name, icon, color) so that
 * the frontend does not need additional category look-ups.
 *
 * <p>Requirement REQ-SPL-2.3: Include splits in GET /transactions/{id} response
 *
 * <p>Requirement REQ-SPL-2.4: Include splits in list/search responses
 *
 * <p>Requirement REQ-SPL-4.3: Each split line shows category icon/color, name, amount, optional
 * description
 *
 * @see TransactionResponse
 * @see TransactionSplitRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSplitResponse {

    /** Unique identifier of this split line. */
    private Long id;

    /** ID of the parent transaction this split belongs to. */
    private Long transactionId;

    /** ID of the category assigned to this split line (may be null). */
    private Long categoryId;

    /**
     * Display name of the category (denormalized, null if no category).
     *
     * <p>Requirement REQ-SPL-4.3: Show category name in expanded split view
     */
    private String categoryName;

    /**
     * Icon identifier of the category (denormalized, null if no category).
     *
     * <p>Requirement REQ-SPL-4.3: Show category icon in expanded split view
     */
    private String categoryIcon;

    /**
     * Hex color of the category (denormalized, null if no category).
     *
     * <p>Requirement REQ-SPL-4.3: Show category color in expanded split view
     */
    private String categoryColor;

    /** Amount allocated to this split line. */
    private BigDecimal amount;

    /**
     * Optional description for this split line (decrypted).
     *
     * <p>Requirement REQ-SPL-1.1: Optional description per split
     */
    private String description;
}
