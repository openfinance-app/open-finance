package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Partial alert update: omitted fields retain their current values. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAlertUpdateRequest {
    @DecimalMin(value = "1.00", message = "{budget.alert.threshold.min}")
    @DecimalMax(value = "150.00", message = "{budget.alert.threshold.max}")
    private BigDecimal threshold;

    private Boolean isEnabled;
}
