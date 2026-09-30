package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.Account;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ImportedOpeningBalanceServiceImpl implements ImportedOpeningBalanceService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /** Apply an opening position only to an empty ledger, or verify it against existing history. */
    public void reconcile(
            Account account, BigDecimal opening, String currency, LocalDate date, Long userId) {
        if (opening == null) return;
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new InvalidTransactionException(
                    "Cannot import an opening balance to a closed account");
        }
        if (currency != null && !currency.equalsIgnoreCase(account.getCurrency())) {
            throw new InvalidTransactionException(
                    "Statement opening currency must match the account currency");
        }
        List<Transaction> movements =
                transactionRepository.findByUserIdAndAccountId(userId, account.getId());
        BigDecimal existing =
                account.getOpeningBalance() == null ? BigDecimal.ZERO : account.getOpeningBalance();
        if (movements.isEmpty() && existing.signum() == 0 && account.getBalance().signum() == 0) {
            account.setOpeningBalance(opening);
            account.setBalance(opening);
            if (date != null) account.setOpeningDate(date);
            accountRepository.save(account);
            return;
        }
        BigDecimal atOpening = existing;
        for (Transaction movement : movements) {
            if (!Boolean.TRUE.equals(movement.getIsDeleted())
                    && date != null
                    && movement.getDate().isBefore(date)) {
                BigDecimal amount = movement.getBalanceAmount();
                atOpening =
                        atOpening.add(
                                movement.getType() == TransactionType.INCOME
                                        ? amount
                                        : amount.negate());
            }
        }
        if (atOpening.compareTo(opening) != 0) {
            throw new InvalidTransactionException(
                    "Statement opening balance conflicts with the existing account history; reconcile the opening position before importing");
        }
    }
}
