package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class AssetSaleRequest {
    @NotNull private UUID operationId;
    @NotNull private Long accountId;
    @NotNull @PastOrPresent private LocalDate date;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 26, fraction = 18)
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 26, fraction = 18)
    private BigDecimal proceeds;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 26, fraction = 18)
    private BigDecimal fees = BigDecimal.ZERO;

    @Size(max = 500)
    private String description;

    @Size(max = 5000)
    private String notes;
}
