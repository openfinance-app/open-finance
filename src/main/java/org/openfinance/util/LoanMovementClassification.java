package org.openfinance.util;

import java.math.BigDecimal;
import java.util.Objects;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;

/** Contextual labels for the imported debt ledger; this never changes the booked movement. */
public final class LoanMovementClassification {
    private LoanMovementClassification() {}

    public static boolean isSourceAccount(Liability loan, Transaction transaction) {
        return loan.getConvertedFromAccountId() != null
                && Objects.equals(loan.getConvertedFromAccountId(), transaction.getAccountId());
    }

    public static MovementType classify(Liability loan, Transaction transaction) {
        if (transaction.getMovementType() != null) return transaction.getMovementType();
        if (!isSourceAccount(loan, transaction)) return null;
        // Transfers have one signed row per account, as in the account balance calculation.
        BigDecimal delta = transaction.getBalanceAmount();
        if (transaction.getType() != TransactionType.INCOME) delta = delta.negate();
        if (delta.signum() == 0) return null;
        return delta.signum() > 0 ? MovementType.REPAYMENT : MovementType.DISBURSEMENT;
    }

    public static boolean isPayment(MovementType type) {
        return type == MovementType.REPAYMENT
                || type == MovementType.INTEREST
                || type == MovementType.INSURANCE
                || type == MovementType.FEE;
    }
}
