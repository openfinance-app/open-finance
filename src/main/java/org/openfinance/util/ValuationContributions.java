package org.openfinance.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;

/** An independent valuation supersedes movements already known at its effective date. */
public final class ValuationContributions {
    private ValuationContributions() {}

    public static BigDecimal amount(Transaction transaction) {
        if (transaction.getMovementType() != MovementType.CAPITAL_IMPROVEMENT)
            return BigDecimal.ZERO;
        if (transaction.getValuationChange() != null) return transaction.getValuationChange();
        return transaction.getOriginalCurrency() != null && transaction.getConversionRate() != null
                ? transaction.getOriginalAmount()
                : transaction.getAmount();
    }

    public static boolean applies(
            LocalDate movementDate,
            LocalDateTime movementRecordedAt,
            LocalDate anchorDate,
            LocalDateTime anchorRecordedAt) {
        if (anchorDate == null) return true;
        return movementDate.isAfter(anchorDate)
                || (movementRecordedAt != null
                        && anchorRecordedAt != null
                        && movementRecordedAt.isAfter(anchorRecordedAt));
    }
}
