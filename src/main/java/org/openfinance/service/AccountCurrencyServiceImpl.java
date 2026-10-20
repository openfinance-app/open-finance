package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountCurrencyChange;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.AccountCurrencyChangeRepository;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountCurrencyServiceImpl implements AccountCurrencyService {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AccountCurrencyChangeRepository changes;
    private final ExchangeRateService exchangeRates;
    private final EncryptedUserDataService encryptedData;
    private final JdbcTemplate jdbc;

    @Override
    public void change(
            Account account, String previousCurrency, Long userId, LocalDate effectiveDate) {
        if (previousCurrency.equalsIgnoreCase(account.getCurrency())) return;
        BigDecimal rate =
                exchangeRates.getExchangeRate(
                        previousCurrency, account.getCurrency(), effectiveDate);
        if (rate == null || rate.signum() <= 0)
            throw new IllegalStateException("A valid exchange rate is required");
        account.setOpeningBalance(scale(account.getOpeningBalance().multiply(rate)));
        account.setBalance(scale(account.getBalance().multiply(rate)));
        for (Transaction transaction : transactions.findAllForAccount(account.getId(), userId)) {
            transaction.setAccountAmount(scale(transaction.getBalanceAmount().multiply(rate)));
            transaction.setAccountCurrency(account.getCurrency());
        }
        // Archived rows participate in key rotation, portable recovery and later reactivation too.
        for (Map<String, Object> row :
                jdbc.queryForList(
                        "SELECT id, amount, account_amount FROM transactions_archive WHERE account_id = ? AND user_id = ?",
                        account.getId(),
                        userId)) {
            Object stored =
                    row.get("account_amount") == null
                            ? row.get("amount")
                            : row.get("account_amount");
            BigDecimal amount =
                    new BigDecimal(
                            encryptedData.plaintext(stored.toString(), EncryptionContext.getKey()));
            Object converted =
                    encryptedData.translate(
                            "transactions_archive",
                            "account_amount",
                            scale(amount.multiply(rate)).toPlainString(),
                            null,
                            EncryptionContext.getKey());
            jdbc.update(
                    "UPDATE transactions_archive SET account_amount = ?, account_currency = ? WHERE id = ? AND user_id = ?",
                    converted,
                    account.getCurrency(),
                    row.get("id"),
                    userId);
        }
        changes.save(
                AccountCurrencyChange.builder()
                        .accountId(account.getId())
                        .userId(userId)
                        .effectiveDate(effectiveDate)
                        .fromCurrency(previousCurrency)
                        .toCurrency(account.getCurrency())
                        .rate(rate)
                        .build());
    }

    @Override
    public void book(Transaction transaction, Long userId) {
        Account account =
                accounts.findByIdAndUserId(transaction.getAccountId(), userId).orElseThrow();
        if (transaction.getInvestmentQuantity() != null && !transaction.isInvestmentCash()) {
            transaction.setAccountAmount(BigDecimal.ZERO);
            transaction.setAccountCurrency(account.getCurrency());
            return;
        }
        List<AccountCurrencyChange> later = after(account, transaction.getDate(), userId);
        String currencyAtDate =
                later.isEmpty() ? account.getCurrency() : later.getFirst().getFromCurrency();
        BigDecimal amount =
                transaction.getCurrency().equalsIgnoreCase(currencyAtDate)
                        ? transaction.getAmount()
                        : exchangeRates.convert(
                                transaction.getAmount(),
                                transaction.getCurrency(),
                                currencyAtDate,
                                transaction.getDate());
        for (AccountCurrencyChange change : later)
            amount = scale(amount.multiply(change.getRate()));
        transaction.setAccountAmount(amount);
        transaction.setAccountCurrency(account.getCurrency());
    }

    @Override
    @Transactional(readOnly = true)
    public Position historicalPosition(
            Account account, BigDecimal currentUnits, LocalDate date, Long userId) {
        List<AccountCurrencyChange> later = after(account, date, userId);
        BigDecimal amount = currentUnits;
        for (AccountCurrencyChange change : later.reversed())
            amount = amount.divide(change.getRate(), 18, RoundingMode.HALF_UP);
        return new Position(
                amount,
                later.isEmpty() ? account.getCurrency() : later.getFirst().getFromCurrency());
    }

    private List<AccountCurrencyChange> after(Account account, LocalDate date, Long userId) {
        return changes
                .findByAccountIdAndUserIdOrderByEffectiveDateAscIdAsc(account.getId(), userId)
                .stream()
                .filter(change -> change.getEffectiveDate().isAfter(date))
                .toList();
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(18, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
