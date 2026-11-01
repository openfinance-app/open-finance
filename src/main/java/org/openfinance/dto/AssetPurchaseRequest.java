package org.openfinance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class AssetPurchaseRequest {
    @NotNull private UUID operationId;
    @Valid @NotNull private AssetRequest asset;
    private Long accountId;
    private Long plannedAssetId;
    private Long existingPaymentId;
    @Valid private LiabilityRequest newLoan;
    private Long existingLoanId;
    private Long existingLoanReceiptId;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 26, fraction = 18)
    private BigDecimal loanAmount = BigDecimal.ZERO;

    @NotNull private PropertyPurchaseRequest.Route route = PropertyPurchaseRequest.Route.DIRECT;

    @Size(max = 500)
    private String paymentDescription;
}
