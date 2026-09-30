package org.openfinance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

/** One atomic purchase, identified by a stable client-generated retry key. */
@Data
public class PropertyPurchaseRequest {
    public enum Route {
        DIRECT,
        ACCOUNT
    }

    @NotNull private UUID operationId;
    @Valid @NotNull private RealEstatePropertyRequest property;
    @Valid private LiabilityRequest newMortgage;
    private Long existingMortgageId;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 26, fraction = 18)
    private BigDecimal loanAmount;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 26, fraction = 18)
    private BigDecimal downPayment;

    @NotNull private Route route;
    private Long accountId;

    @Size(max = 500)
    private String paymentDescription;
}
