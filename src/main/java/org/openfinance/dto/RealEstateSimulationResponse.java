package org.openfinance.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for a real estate simulation.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealEstateSimulationResponse {

    private Long id;
    private String name;
    private String simulationType;
    private String data;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
