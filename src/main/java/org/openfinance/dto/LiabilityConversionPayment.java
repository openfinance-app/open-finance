package org.openfinance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.openfinance.entity.LoanComponent;

/** Only association metadata is accepted; the booked amounts and dates remain authoritative. */
public record LiabilityConversionPayment(
        @NotNull Long transactionId,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String sourceRevision,
        LoanComponent component,
        @Size(max = 500) List<@NotNull @Valid Split> splits) {
    public record Split(@NotNull Long splitId, @NotNull LoanComponent component) {}
}
