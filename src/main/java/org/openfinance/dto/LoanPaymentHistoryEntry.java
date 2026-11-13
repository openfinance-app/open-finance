package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An actual booked payment, with the outstanding principal at the close of that date. */
public record LoanPaymentHistoryEntry(
        Long transactionId,
        LocalDate paymentDate,
        BigDecimal paymentAmount,
        BigDecimal principalPortion,
        BigDecimal interestPortion,
        BigDecimal insurancePortion,
        BigDecimal feePortion,
        BigDecimal otherCharges,
        BigDecimal remainingBalance) {}
