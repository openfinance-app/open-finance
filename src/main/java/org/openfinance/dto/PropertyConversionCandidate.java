package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PropertyConversionCandidate(
        Long assetId,
        String name,
        String accountName,
        boolean closesSourceAccount,
        String currency,
        BigDecimal currentValue,
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        int transactionCount,
        int valuationCount,
        String sourceRevision) {}
