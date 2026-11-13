package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.openfinance.entity.PropertyType;

/** Reclassifies an existing holding; financial values always come from the reviewed source. */
public record PropertyConversionRequest(
        @NotNull UUID operationId,
        @NotNull Long assetId,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String sourceRevision,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String address,
        @NotNull PropertyType propertyType,
        Long mortgageId,
        @DecimalMin("0") @Digits(integer = 26, fraction = 18) BigDecimal rentalIncome) {}
