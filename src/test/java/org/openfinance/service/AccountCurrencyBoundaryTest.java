package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountCurrencyChange;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.AccountCurrencyChangeRepository;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account redenomination preserves ledger units and dated balances")
class AccountCurrencyBoundaryTest {
    private static final LocalDate DATE = LocalDate.of(2024, 6, 15);
    @Mock AccountRepository accounts;
    @Mock TransactionRepository transactions;
    @Mock AccountCurrencyChangeRepository changes;
    @Mock ExchangeRateService exchangeRates;
    @Mock EncryptedUserDataService encryptedData;
    @Mock JdbcTemplate jdbc;
    @InjectMocks AccountCurrencyServiceImpl service;

    @Test
    void sameCurrencyIsACaseInsensitiveNoOp() {
        service.change(account(), "usd", 3L, DATE);
        verifyNoInteractions(transactions, changes, exchangeRates, encryptedData, jdbc);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void missingOrInvalidRatesCannotChangeBalances(String rate) {
        Account account = account();
        when(exchangeRates.getExchangeRate("EUR", "USD", DATE))
                .thenReturn(rate == null ? null : new BigDecimal(rate));
        assertThatThrownBy(() -> service.change(account, "EUR", 3L, DATE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100");
        verifyNoInteractions(transactions, changes, encryptedData, jdbc);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void archivedRowsUseTheirBookedAccountAmountWhenAvailable(boolean hasAccountAmount) {
        Account account = account();
        Transaction live =
                Transaction.builder()
                        .amount(new BigDecimal("999"))
                        .accountAmount(new BigDecimal("10"))
                        .accountCurrency("EUR")
                        .build();
        when(transactions.findAllForAccount(1L, 3L)).thenReturn(List.of(live));
        when(exchangeRates.getExchangeRate("EUR", "USD", DATE)).thenReturn(new BigDecimal("1.25"));
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 7L);
        row.put("amount", "source-cipher");
        row.put("account_amount", hasAccountAmount ? "booked-cipher" : null);
        when(jdbc.queryForList(any(String.class), eq(1L), eq(3L))).thenReturn(List.of(row));
        when(encryptedData.plaintext(
                        eq(hasAccountAmount ? "booked-cipher" : "source-cipher"), any()))
                .thenReturn("12.000000000000000008");
        when(encryptedData.translate(
                        eq("transactions_archive"),
                        eq("account_amount"),
                        eq("15.00000000000000001"),
                        eq(null),
                        any()))
                .thenReturn("converted-cipher");
        service.change(account, "EUR", 3L, DATE);
        assertThat(live.getAccountAmount()).isEqualByComparingTo("12.5");
        assertThat(live.getAmount()).isEqualByComparingTo("999");
        assertThat(account.getBalance()).isEqualByComparingTo("125");
        verify(jdbc).update(any(String.class), eq("converted-cipher"), eq("USD"), eq(7L), eq(3L));
        ArgumentCaptor<AccountCurrencyChange> saved =
                ArgumentCaptor.forClass(AccountCurrencyChange.class);
        verify(changes).save(saved.capture());
        assertThat(saved.getValue().getRate()).isEqualByComparingTo("1.25");
    }

    @Test
    void historicalPositionsUndoLaterConversionsInReverseOrder() {
        Account account = account();
        when(changes.findByAccountIdAndUserIdOrderByEffectiveDateAscIdAsc(1L, 3L))
                .thenReturn(
                        List.of(
                                change(DATE, "GBP", "EUR", "2"),
                                change(DATE.plusDays(1), "EUR", "USD", "4")));
        assertThat(service.historicalPosition(account, new BigDecimal("80"), DATE.minusDays(1), 3L))
                .isEqualTo(
                        new AccountCurrencyService.Position(
                                new BigDecimal("10.000000000000000000"), "GBP"));
        assertThat(service.historicalPosition(account, new BigDecimal("80"), DATE, 3L).amount())
                .isEqualByComparingTo("20");
        assertThat(
                        service.historicalPosition(
                                        account, new BigDecimal("80"), DATE.plusDays(1), 3L)
                                .currency())
                .isEqualTo("USD");
    }

    @Test
    void newForeignBookingsUseTheirTransactionDateThenEachSubsequentRedenomination() {
        when(accounts.findByIdAndUserId(1L, 3L)).thenReturn(Optional.of(account()));
        when(changes.findByAccountIdAndUserIdOrderByEffectiveDateAscIdAsc(1L, 3L))
                .thenReturn(List.of(change(DATE.plusDays(1), "EUR", "USD", "1.25")));
        when(exchangeRates.convert(new BigDecimal("10"), "GBP", "EUR", DATE))
                .thenReturn(new BigDecimal("12"));
        Transaction tx =
                Transaction.builder()
                        .accountId(1L)
                        .date(DATE)
                        .currency("GBP")
                        .amount(new BigDecimal("10"))
                        .build();
        service.book(tx, 3L);
        assertThat(tx.getAccountAmount()).isEqualByComparingTo("15");
        assertThat(tx.getAccountCurrency()).isEqualTo("USD");
        assertThat(tx.getAmount()).isEqualByComparingTo("10");
    }

    private Account account() {
        return Account.builder()
                .id(1L)
                .currency("USD")
                .openingBalance(BigDecimal.TEN)
                .balance(new BigDecimal("100"))
                .build();
    }

    private AccountCurrencyChange change(LocalDate date, String from, String to, String rate) {
        return AccountCurrencyChange.builder()
                .effectiveDate(date)
                .fromCurrency(from)
                .toCurrency(to)
                .rate(new BigDecimal(rate))
                .build();
    }
}
