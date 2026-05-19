package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for institution create/update requests.
 *
 * <p>Requirements: REQ-2.6.1.3 - Predefined Financial Institutions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionRequest {

    /** Name of the financial institution. Required for creation. */
    @NotBlank(message = "{institution.name.required}")
    @Size(max = 200, message = "{institution.name.max}")
    private String name;

    /** BIC (Bank Identifier Code) in ISO 9362 format. 4 to 15 character code. */
    @Size(min = 4, max = 15, message = "{institution.bic.size}")
    private String bic;

    /** Country code in ISO 3166-1 alpha-2 format. */
    @Size(min = 2, max = 2, message = "{institution.country.size}")
    private String country;

    /** Institution logo as base64-encoded string. Supports PNG, JPG, SVG formats. Max size: 2MB */
    private String logo;
}
