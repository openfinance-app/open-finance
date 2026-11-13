package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LiabilityConversionCandidate(
        Long accountId,
        String name,
        String currency,
        BigDecimal outstandingBalance,
        LocalDate startDate,
        int transactionCount,
        String sourceRevision) {}
