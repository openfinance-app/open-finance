package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.openfinance.entity.LoanComponent;

public record LiabilityConversionPaymentCandidate(
        Long transactionId,
        String sourceRevision,
        LocalDate date,
        String description,
        String notes,
        String accountName,
        String currency,
        BigDecimal amount,
        String categoryName,
        String categoryNameKey,
        LoanComponent suggestedComponent,
        List<Split> splits) {
    public record Split(
            Long splitId,
            BigDecimal amount,
            String categoryName,
            String categoryNameKey,
            LoanComponent suggestedComponent) {}
}
