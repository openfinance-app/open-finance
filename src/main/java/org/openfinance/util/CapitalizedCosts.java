package org.openfinance.util;

import java.math.BigDecimal;
import java.util.List;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;

/** Investment contributions come from the active ledger, never from changes in market value. */
public final class CapitalizedCosts {
    private CapitalizedCosts() {}

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
