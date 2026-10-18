package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.openfinance.entity.InterestPeriod;

/** Read-only preview of a new account, using the same calendar-year engine as saved accounts. */
public record InterestPreviewRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 26, fraction = 18) BigDecimal balance,
        @NotNull @DecimalMin("0") BigDecimal rate,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
        @NotNull LocalDate openingDate,
        @NotNull InterestPeriod interestPeriod,
        @NotBlank String currency) {}
