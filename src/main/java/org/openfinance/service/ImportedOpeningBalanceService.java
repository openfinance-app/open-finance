package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.openfinance.entity.Account;

/** Reconciles statement opening positions with the existing account ledger. */
public interface ImportedOpeningBalanceService {
    default void reconcile(
            Account account, BigDecimal opening, String currency, LocalDate date, Long userId) {
        reconcile(account, opening, currency, date, userId, null);
    }

    /** A reported closing balance supplies the precision of an OFX/QFX statement. */
    void reconcile(
            Account account,
            BigDecimal opening,
            String currency,
            LocalDate date,
            Long userId,
            BigDecimal reportedClosing);
}
