package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.openfinance.dto.InvestmentMovementRequest;
import org.openfinance.dto.InvestmentQuote;
import org.openfinance.entity.Transaction;

public interface InvestmentMovementService {
    static Transaction snapshot(Transaction source) {
        return Transaction.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .accountId(source.getAccountId())
                .assetId(source.getAssetId())
                .date(source.getDate())
                .type(source.getType())
                .amount(source.getAmount())
                .originalAmount(source.getOriginalAmount())
                .originalCurrency(source.getOriginalCurrency())
                .description(source.getDescription())
                .externalReference(source.getExternalReference())
                .currency(source.getCurrency())
                .investmentQuantity(source.getInvestmentQuantity())
                .investmentCash(source.isInvestmentCash())
                .investmentValue(source.getInvestmentValue())
                .investmentPrice(source.getInvestmentPrice())
                .investmentPriceDate(source.getInvestmentPriceDate())
                .investmentPriceSource(source.getInvestmentPriceSource())
                .build();
    }

    InvestmentQuote quote(
            Long assetId,
            Long userId,
            BigDecimal amount,
            String currency,
            LocalDate date,
            Long transactionId);

    /** Apply before booking cash. The previous snapshot is a detached copy of the old movement. */
    void apply(
            Transaction transaction,
            InvestmentMovementRequest request,
            boolean present,
            Transaction previous);

    /** Rebuild both the old and new positions after saving, in the caller's transaction. */
    void recalculate(Transaction previous, Transaction current);
}
