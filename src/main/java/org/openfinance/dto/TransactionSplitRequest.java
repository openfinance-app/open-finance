package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for creating or replacing a single split line within a transaction.
 *
 * <p>A split line represents the allocation of part of the parent transaction's total amount to a
 * specific category. The sum of all split amounts in a request must equal the parent transaction's
 * {@code amount} field (within ±0.01).
 *
 * <p>Requirement REQ-SPL-2.1: Accept splits array on POST /transactions
 *
 * <p>Requirement REQ-SPL-2.2: Accept splits array on PUT /transactions/{id}
 *
 * <p>Requirement REQ-SPL-1.1: categoryId, amount, description per split
 *
 * @see TransactionRequest
 * @see TransactionSplitResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSplitRequest {

    /**
     * ID of the category for this split line (optional).
     *
     * <p>If omitted, the split line is uncategorized.
     *
     * <p>Requirement REQ-SPL-1.1: Optional categoryId per split
     */
    private Long categoryId;

    /**
     * Amount allocated to this split line (required, must be &gt; 0).
     *
     * <p>The sum of all split amounts in the request must equal the parent transaction amount
     * within ±0.01 tolerance.
     *
     * <p>Requirement REQ-SPL-1.1: Amount field; REQ-SPL-1.2: Sum constraint
     */
    @NotNull(message = "{split.amount.required}")
    @DecimalMin(value = "0.01", message = "{split.amount.min}")
    @Digits(integer = 15, fraction = 4, message = "{split.amount.digits}")
    private BigDecimal amount;

    /**
     * Optional description for this split line (max 255 characters).
     *
     * <p>Will be encrypted before storage.
     *
     * <p>Requirement REQ-SPL-1.1: Optional description per split
     */
    @Size(max = 255, message = "{transfer.description.max}")
    private String description;
}
