package org.openfinance.util;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openfinance.entity.Account;

/** Shared ownership boundary for cash and account-linked holdings; closure retains value. */
public final class AccountValuation {
    private AccountValuation() {}

    public static Set<Long> excludedAccountIds(List<Account> accounts, LocalDate date) {
        return accounts.stream()
                .filter(account -> !account.isPresentAt(date))
                .map(Account::getId)
                .collect(Collectors.toSet());
    }
}
