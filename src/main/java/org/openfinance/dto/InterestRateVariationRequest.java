package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.InterestDayCountBasis;
import org.openfinance.entity.InterestValueDateMode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestRateVariationRequest {

    @NotNull @Builder.Default
    private InterestValueDateMode creditValueDateMode = InterestValueDateMode.TRANSACTION_DATE;

    @NotNull @Builder.Default
    private InterestValueDateMode debitValueDateMode = InterestValueDateMode.TRANSACTION_DATE;

    @NotNull @Builder.Default
    private InterestDayCountBasis dayCountBasis = InterestDayCountBasis.ACTUAL_365;

    @NotNull(message = "{interest.rate.required}")
    @DecimalMin("0")
    private BigDecimal rate;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal taxRate;

    @NotNull(message = "{interest.valid.from.required}")
    private LocalDate validFrom;
}
