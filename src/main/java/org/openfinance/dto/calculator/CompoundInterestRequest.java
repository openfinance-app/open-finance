package org.openfinance.dto.calculator;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for compound interest calculation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompoundInterestRequest {

    /** Initial principal amount. */
    @NotNull(message = "{calc.compound.principal.required}")
    @DecimalMin(value = "0.0", inclusive = false, message = "{calc.compound.principal.min}")
    @Digits(integer = 15, fraction = 2, message = "{calc.compound.principal.digits}")
    private BigDecimal principal;

    /** Annual interest rate as a percentage (e.g. 5 means 5%). */
    @NotNull(message = "{calc.compound.rate.required}")
    @DecimalMin(value = "0.0", inclusive = false, message = "{calc.compound.rate.min}")
    @DecimalMax(value = "100.0", message = "{calc.compound.rate.max}")
    @Digits(integer = 3, fraction = 4, message = "{calc.compound.rate.digits}")
    private BigDecimal annualRate;

    /** Number of compounding periods per year (1, 2, 4, 12, 365). */
    @NotNull(message = "{calc.compound.frequency.required}")
    @Min(value = 1, message = "{calc.compound.frequency.min}")
    @Max(value = 365, message = "{calc.compound.frequency.max}")
    private Integer compoundingFrequency;

    /** Investment duration in years. */
    @NotNull(message = "{calc.compound.years.required}")
    @Min(value = 1, message = "{calc.compound.years.min}")
    @Max(value = 100, message = "{calc.compound.years.max}")
    private Integer years;

    /** Optional regular contribution per period. Defaults to 0. */
    @Builder.Default
    @DecimalMin(value = "0.0", message = "{calc.compound.contribution.min}")
    @Digits(integer = 12, fraction = 2, message = "{calc.compound.contribution.digits}")
    private BigDecimal regularContribution = BigDecimal.ZERO;

    /**
     * Whether contributions are made at the beginning (true) or end (false) of each period.
     * Defaults to false (end of period / ordinary annuity).
     */
    @Builder.Default private boolean contributionAtBeginning = false;
}
