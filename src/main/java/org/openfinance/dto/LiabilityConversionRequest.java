package org.openfinance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Reviewed source plus contract terms. The outstanding balance and currency come from the source.
 */
public record LiabilityConversionRequest(
        @NotNull UUID operationId,
        @NotNull Long accountId,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String sourceRevision,
        @NotNull @DecimalMin("0") @Digits(integer = 26, fraction = 18) BigDecimal fundedPrincipal,
        @NotNull @Valid LiabilityRequest liability,
        @Size(max = 500) List<@NotNull @Valid LiabilityConversionPayment> payments) {}
