package org.openfinance.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for institution responses.
 *
 * <p>This DTO is returned to clients when retrieving institution information. It contains all
 * institution details including logo for display.
 *
 * <p>Requirements: REQ-2.6.1.3 - Predefined Financial Institutions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionResponse {

    /** Unique identifier of the institution. */
    private Long id;

    /** Name of the financial institution. Examples: "BNP Paribas", "Deutsche Bank", "Boursorama" */
    private String name;

    /** BIC (Bank Identifier Code) in ISO 9362 format. 8 or 11 character code. */
    private String bic;

    /** Country code in ISO 3166-1 alpha-2 format. */
    private String country;

    /** Institution logo as base64-encoded string. */
    private String logo;

    /**
     * Flag indicating if this is a system-provided institution. System institutions cannot be
     * deleted by users.
     */
    private Boolean isSystem;

    /** Timestamp when the institution was created. */
    private LocalDateTime createdAt;

    /** Timestamp when the institution was last updated. */
    private LocalDateTime updatedAt;
}
