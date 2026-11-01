package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class AssetBuyRequest {
    @NotNull private UUID operationId;
    @NotNull @PastOrPresent private LocalDate date;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 26, fraction = 18)
    private BigDecimal quantity;

    /** Total paid, including any trading fees, in the holding and account currency. */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 26, fraction = 18)
    private BigDecimal amount;
}
