package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for payee responses.
 *
 * <p>This DTO is returned to clients when retrieving payee information. It contains all payee
 * details including logo for display.
 *
 * <p>Requirements: Payee Management Feature
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayeeResponse {

    /** Unique identifier of the payee. */
    private Long id;

    /** Name of the payee. Examples: "Amazon", "Netflix", "EDF", "Carrefour" */
    private String name;

    /** Payee logo as base64-encoded string or file path reference. */
    private String logo;

    /**
     * Default category ID associated with this payee.
     *
     * <p>Requirements: REQ-CAT-5.1 - Payee-to-Category Auto-Fill
     */
    private Long categoryId;

    /** Default category name associated with this payee. */
    private String categoryName;

    /**
     * Flag indicating if this is a system-provided payee. System payees cannot be deleted by users.
     */
    private Boolean isSystem;

    /** Flag indicating if this payee is active/visible. */
    private Boolean isActive;

    /** Timestamp when the payee was created. */
    private LocalDateTime createdAt;

    /** Timestamp when the payee was last updated. */
    private LocalDateTime updatedAt;

    /** Total number of transactions associated with this payee. */
    private Long transactionCount;

    /** Total amount of transactions associated with this payee. */
    private BigDecimal totalAmount;
}
