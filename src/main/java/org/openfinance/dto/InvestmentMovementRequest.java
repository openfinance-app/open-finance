package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** A holding movement. Quantity is positive; the operation determines its sign and cash effect. */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class InvestmentMovementRequest {
    public enum Operation {
        ADD,
        REMOVE,
        BUY,
        SELL
    }

    public enum Pricing {
        QUANTITY,
        HISTORICAL
    }

    @NotNull private Long assetId;
    @NotNull private Operation operation;
    @Builder.Default private Pricing pricing = Pricing.QUANTITY;

    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 26, fraction = 18)
    private BigDecimal quantity;
}
