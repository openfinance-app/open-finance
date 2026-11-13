package org.openfinance.util;

import java.math.BigDecimal;
import java.util.List;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;

/** Investment contributions come from the active ledger, never from changes in market value. */
public final class CapitalizedCosts {
    private CapitalizedCosts() {}

    /** Direct payments are investment contributions even though no bank-account cash moved. */
    public static BigDecimal directFunding(List<LiabilityTranche> draws) {
        return draws.stream()
                .filter(LiabilityTranche::isDirectDisbursement)
                .filter(t -> t.getStatus() == TrancheStatus.DRAWN && t.getReversedDate() == null)
                .map(LiabilityTranche::getCapitalizedAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal total(List<Transaction> movements) {
        return movements.stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .filter(
                        t ->
                                t.getType() == TransactionType.EXPENSE
                                        && t.getMovementType() == MovementType.CAPITAL_IMPROVEMENT)
                .map(
                        t ->
                                t.getOriginalCurrency() != null && t.getConversionRate() != null
                                        ? t.getOriginalAmount()
                                        : t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
