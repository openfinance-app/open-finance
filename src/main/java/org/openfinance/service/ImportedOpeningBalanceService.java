package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.openfinance.entity.Account;

/** Reconciles statement opening positions with the existing account ledger. */
public interface ImportedOpeningBalanceService {
    void reconcile(
            Account account, BigDecimal opening, String currency, LocalDate date, Long userId);
}
