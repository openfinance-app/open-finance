package org.openfinance.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;

/** The same origination boundary applies to every route that posts a loan movement. */
public final class LoanPostingPolicy {
    private LoanPostingPolicy() {}

    /** Imported opening debt already incorporates every principal movement before this boundary. */
    public static boolean isHistorical(
            Liability liability, LocalDate date, LocalDateTime recordedAt) {
        return liability.getConvertedFromAccountId() != null
                && liability.getBalanceStartDate() != null
                && date != null
                && (date.isBefore(liability.getBalanceStartDate())
                        || (date.equals(liability.getBalanceStartDate())
                                && recordedAt != null
                                && liability.getCreatedAt() != null
                                && !recordedAt.isAfter(liability.getCreatedAt())));
    }

    /** Linking a booked payment is metadata, not a second posting against opening debt. */
    public static void validateLink(
            Liability liability, TransactionRequest request, Transaction existing) {
        if (existing == null
                || !isHistorical(liability, existing.getDate(), existing.getCreatedAt())) {
            validateDate(liability, request.getDate());
            return;
        }
        validateUnchangedPayment(request, existing);
        if (request.getLiabilityId() != null
                && (request.getMovementType() == null
                        || !Set.of(
                                        MovementType.REPAYMENT,
                                        MovementType.INTEREST,
                                        MovementType.INSURANCE,
                                        MovementType.FEE)
                                .contains(request.getMovementType())
                        || request.getTrancheId() != null)) {
            throw InvalidTransactionException.liabilityConversion(
                    "history", "Link an existing payment without changing its cash movement");
        }
    }

    private static void validateUnchangedPayment(TransactionRequest request, Transaction existing) {
        if (existing.getType() != TransactionType.EXPENSE
                || existing.getTransferId() != null
                || request.getType() != existing.getType()
                || !Objects.equals(request.getDate(), existing.getDate())
                || !Objects.equals(request.getAccountId(), existing.getAccountId())
                || !Objects.equals(request.getCurrency(), existing.getCurrency())
                || !Objects.equals(request.getOriginalCurrency(), existing.getOriginalCurrency())
                || !sameAmount(request.getAmount(), existing.getAmount())
                || !sameAmount(request.getOriginalAmount(), existing.getOriginalAmount())
                || !sameAmount(request.getConversionRate(), existing.getConversionRate())) {
            throw InvalidTransactionException.liabilityConversion(
                    "history", "Link an existing payment without changing its cash movement");
        }
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    public static void validateDate(Liability liability, LocalDate date) {
        if (date != null
                && liability.getBalanceStartDate() != null
                && date.isBefore(liability.getBalanceStartDate())) {
            throw InvalidTransactionException.liabilityConversion(
                    "date",
                    "Record managed payments on or after the conversion date; earlier payments are already in the imported history");
        }
        if (date == null
                || date.isAfter(LocalDate.now())
                || (liability.getStartDate() != null && date.isBefore(liability.getStartDate()))) {
            throw new InvalidTransactionException(
                    "Loan movement date must fall between loan origination and today");
        }
    }
}
