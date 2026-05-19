package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for payee create/update requests.
 *
 * <p>Requirements: Payee Management Feature
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayeeRequest {

    /** Name of the payee. Required for creation. */
    @NotBlank(message = "{payee.name.required}")
    @Size(max = 100, message = "{payee.name.max}")
    private String name;

    /**
     * Payee logo as base64-encoded string or file path reference. Supports PNG, JPG, SVG formats.
     * Max size: 500KB
     */
    private String logo;

    /**
     * Default category ID to associate with this payee.
     *
     * <p>When a transaction is created with this payee, the category will be auto-filled from this
     * field (REQ-CAT-5.1).
     *
     * <p>Requirements: REQ-CAT-5.1 - Payee-to-Category Auto-Fill
     */
    private Long categoryId;
}
