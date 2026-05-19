package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestRateVariationResponse {

    private Long id;
    private Long accountId;
    private BigDecimal rate;
    private BigDecimal taxRate;
    private LocalDate validFrom;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
