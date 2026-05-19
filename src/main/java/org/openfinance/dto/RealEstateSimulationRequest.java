package org.openfinance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a real estate simulation.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealEstateSimulationRequest {

    @NotNull(message = "{simulation.name.required}")
    @Size(min = 1, max = 200, message = "{simulation.name.between}")
    private String name;

    @NotNull(message = "{simulation.type.required}")
    @Pattern(regexp = "buy_rent|rental_investment", message = "{simulation.type.invalid}")
    private String simulationType;

    @NotNull(message = "{simulation.data.required}")
    @Size(max = 10000, message = "{simulation.data.max}")
    private String data;
}
