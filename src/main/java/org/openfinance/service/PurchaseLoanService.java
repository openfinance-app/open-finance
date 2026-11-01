package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.openfinance.dto.PropertyPurchaseRequest.Route;

/** Reuse an actual loan receipt, or record a new draw, as part of an atomic purchase. */
public interface PurchaseLoanService {
    void fund(
            Long userId,
            Long loanId,
            BigDecimal amount,
            String currency,
            LocalDate date,
            Route route,
            Long accountId,
            Long receiptId,
            Long assetId,
            Long propertyId);
}
