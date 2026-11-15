package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Account;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Statement opening balances reconcile cash at the statement boundary")
class ImportedOpeningBalanceBoundaryTest {
    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @InjectMocks private ImportedOpeningBalanceServiceImpl service;
    private final LocalDate date = LocalDate.of(2025, 1, 10);

    private Account account() {
        return Account.builder()
                .id(2L)
                .userId(7L)
                .currency("EUR")
                .isActive(true)
                .openingBalance(BigDecimal.ZERO)
                .balance(BigDecimal.ZERO)
                .openingDate(date.minusYears(1))
                .build();
    }

    @Test
    void anAbsentStatementOpeningDoesNotChangeOrReadTheLedger() {
        Account account = account();
        service.reconcile(account, null, "USD", date, 7L);
        assertThat(account.getBalance()).isZero();
        verifyNoInteractions(accounts, transactions);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"EUR", "eur"})
    void aConfirmedEmptyLedgerAdoptsExactStatementOpeningAmounts(String currency) {
        Account account = account();
        account.setOpeningBalance(null);
        BigDecimal opening = new BigDecimal("9007199254740993.01");
        service.reconcile(account, opening, currency, date, 7L);
        assertThat(account.getOpeningBalance()).isEqualByComparingTo(opening);
        assertThat(account.getBalance()).isEqualByComparingTo(opening);
        assertThat(account.getOpeningDate()).isEqualTo(date);
        verify(accounts).save(account);
    }

    @Test
    void anUndatedStatementDoesNotEraseTheAccountsKnownOpeningDate() {
        Account account = account();
        service.reconcile(account, new BigDecimal("100.01"), "EUR", null, 7L);
        assertThat(account.getOpeningDate()).isEqualTo(date.minusYears(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"closed", "currency"})
    void refusesIncompatibleDestinationAccountsBeforeReadingTheirLedger(String reason) {
        Account account = account();
        if (reason.equals("closed")) account.setIsActive(false);
        assertThatThrownBy(
                        () ->
                                service.reconcile(
                                        account,
                                        BigDecimal.ONE,
                                        reason.equals("currency") ? "USD" : "EUR",
                                        date,
                                        7L))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(accounts, transactions);
        assertThat(account.getBalance()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "INCOME,-1,false,112.34",
        "EXPENSE,-1,false,87.66",
        "TRANSFER,-1,false,87.66",
        "INCOME,0,false,100",
        "INCOME,1,false,100",
        "INCOME,-1,true,100"
    })
    void reconcilesOnlyNondeletedCashBeforeTheOpeningDate(
            TransactionType type, int offset, boolean deleted, String expected) {
        Account account = account();
        account.setOpeningBalance(new BigDecimal("100"));
        account.setBalance(new BigDecimal("112.34"));
        Transaction movement =
                Transaction.builder()
                        .accountId(2L)
                        .date(date.plusDays(offset))
                        .type(type)
                        .amount(new BigDecimal("999"))
                        .accountAmount(new BigDecimal("12.34"))
                        .isDeleted(deleted)
                        .build();
        when(transactions.findByUserIdAndAccountId(7L, 2L)).thenReturn(List.of(movement));
        service.reconcile(account, new BigDecimal(expected), "EUR", date, 7L);
        verify(accounts, never()).save(account);
        assertThat(account.getBalance()).isEqualByComparingTo("112.34");
    }

    @Test
    void aConflictingOpeningNeverOverwritesAnExistingBalance() {
        Account account = account();
        account.setBalance(new BigDecimal("20.01"));
        assertThatThrownBy(() -> service.reconcile(account, BigDecimal.ONE, "EUR", date, 7L))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("conflicts");
        assertThat(account.getOpeningBalance()).isZero();
        assertThat(account.getBalance()).isEqualByComparingTo("20.01");
        verify(accounts, never()).save(account);
    }

    @Test
    void holdingsReceivedWithoutCashDoNotChangeTheStatementOpeningPosition() {
        Account account = account();
        account.setOpeningBalance(new BigDecimal("100.01"));
        account.setBalance(new BigDecimal("100.01"));
        Transaction holding =
                Transaction.builder()
                        .accountId(2L)
                        .date(date.minusDays(1))
                        .type(TransactionType.INCOME)
                        .amount(new BigDecimal("500"))
                        .investmentQuantity(new BigDecimal("5"))
                        .investmentCash(false)
                        .accountAmount(BigDecimal.ZERO)
                        .build();
        when(transactions.findByUserIdAndAccountId(7L, 2L)).thenReturn(List.of(holding));
        service.reconcile(account, new BigDecimal("100.01"), "EUR", date, 7L);
        assertThat(account.getBalance()).isEqualByComparingTo("100.01");
        verify(accounts, never()).save(account);
    }

    @Test
    void aPairedForeignTransferCountsOnlyThisAccountsOwnCashRow() {
        Account account = account();
        account.setOpeningBalance(new BigDecimal("100"));
        account.setBalance(new BigDecimal("350"));
        Transaction source =
                Transaction.builder()
                        .accountId(1L)
                        .toAccountId(2L)
                        .transferId("pair")
                        .date(date.minusDays(1))
                        .currency("USD")
                        .type(TransactionType.EXPENSE)
                        .amount(new BigDecimal("300"))
                        .build();
        Transaction received =
                Transaction.builder()
                        .accountId(2L)
                        .toAccountId(1L)
                        .transferId("pair")
                        .date(date.minusDays(1))
                        .currency("EUR")
                        .type(TransactionType.INCOME)
                        .amount(new BigDecimal("250"))
                        .build();
        when(transactions.findByUserIdAndAccountId(7L, 2L)).thenReturn(List.of(source, received));
        service.reconcile(account, new BigDecimal("350"), "EUR", date, 7L);
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("100");
        assertThat(account.getBalance()).isEqualByComparingTo("350");
        verify(accounts, never()).save(account);
    }
}
