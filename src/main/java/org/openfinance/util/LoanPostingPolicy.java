package org.openfinance.util;

import java.time.LocalDate;
import org.openfinance.entity.Liability;
import org.openfinance.exception.InvalidTransactionException;

/** The same origination boundary applies to every route that posts a loan movement. */
public final class LoanPostingPolicy {
    private LoanPostingPolicy() {}

    public static void validateDate(Liability liability, LocalDate date) {
        if (date == null
                || date.isAfter(LocalDate.now())
                || (liability.getStartDate() != null && date.isBefore(liability.getStartDate()))) {
            throw new InvalidTransactionException(
                    "Loan movement date must fall between loan origination and today");
        }
    }
}
