package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Attach an existing purchase expense without posting its cash movement again. */
public interface PurchasePaymentService {
    void linkExisting(Long userId, Long assetId, Long transactionId);

    void link(
            Long userId,
            Long transactionId,
            Long accountId,
            String currency,
            BigDecimal amount,
            LocalDate date,
            Long assetId,
            Long propertyId);
}
