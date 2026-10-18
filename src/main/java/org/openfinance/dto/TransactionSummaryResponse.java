package org.openfinance.dto;

import java.math.BigDecimal;

/** All matching rows, before pagination, valued in the user's reporting currency. */
public record TransactionSummaryResponse(
        long transactionCount,
        String currency,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        BigDecimal transferNetAmount,
        BigDecimal netAmount) {}
