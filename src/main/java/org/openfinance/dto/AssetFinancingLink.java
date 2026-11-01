package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetFinancingLink {
    @NotNull private Long assetId;
    private Long liabilityId;
    private boolean primaryMortgage;

    @NotNull
    @Pattern(regexp = "FINANCING|COLLATERAL")
    private String relationship;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal allocationPercentage;

    private String assetName;
    private String liabilityName;

    /** Allocated outstanding principal in assetCurrency; collateral alone carries no allocation. */
    private BigDecimal allocatedBalance;

    private String assetCurrency;
    private String liabilityCurrency;
}
