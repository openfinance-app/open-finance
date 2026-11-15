package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.TransferUpdateRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.PaymentMethod;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.exception.TransactionNotFoundException;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName(
        "Transfer edits distinguish metadata from money and reject invalid changes before writes")
class TransferUpdateBoundaryTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    @Mock private TransactionRepository transactions;
    @Mock private AccountRepository accounts;
    @Mock private TransactionMapper mapper;
    @Mock private CurrencyConversionHelper conversion;
    @Mock private AccountCurrencyService booking;
    @Mock private InvestmentMovementService investments;
    @Mock private ExchangeRateService rates;
    @Mock private CurrencyRepository currencies;
    @Mock private TransactionSplitService splits;
    @InjectMocks private TransactionService service;

    private Transaction leg(Long id, Long accountId, TransactionType type) {
        return Transaction.builder()
                .id(id)
                .userId(7L)
                .accountId(accountId)
                .toAccountId(accountId == 1L ? 2L : 1L)
                .transferId("pair")
                .type(type)
                .date(DATE)
                .amount(new BigDecimal("100"))
                .currency("EUR")
                .description("Original description")
                .notes("Original notes")
                .paymentMethod(PaymentMethod.CASH)
                .build();
    }

    private TransferUpdateRequest request() {
        return TransferUpdateRequest.builder()
                .fromAccountId(1L)
                .toAccountId(2L)
                .date(DATE)
                .amount(new BigDecimal("100.000"))
                .currency("eur")
                .tags("reviewed")
                .isReconciled(true)
                .build();
    }

    private void paired(Transaction source, Transaction destination) {
        when(transactions.findByTransferId("pair")).thenReturn(List.of(source, destination));
    }

    private void noFinancialWrites() {
        verify(accounts, never()).save(any());
        verifyNoInteractions(booking, investments, rates);
    }

    private Account account(Long id, String currency) {
        return Account.builder()
                .id(id)
                .userId(7L)
                .currency(currency)
                .balance(new BigDecimal("500"))
                .build();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingTransferIdentityWithoutQueryingTransactions(String transferId) {
        assertThatThrownBy(() -> service.updateTransfer(transferId, 7L, request()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions, accounts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "request"})
    void requiresTheOwnerAndUpdatePayload(String missing) {
        assertThatThrownBy(
                        () ->
                                service.updateTransfer(
                                        "pair",
                                        missing.equals("user") ? null : 7L,
                                        missing.equals("request") ? null : request()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions, accounts);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "one-leg",
                "three-legs",
                "foreign-debit",
                "foreign-credit",
                "no-debit",
                "no-credit"
            })
    void refusesIncompleteOrForeignTransfersBeforeUpdatingEitherLeg(String corruption) {
        Transaction debit = leg(11L, 1L, TransactionType.EXPENSE),
                credit = leg(12L, 2L, TransactionType.INCOME);
        if (corruption.equals("foreign-debit")) debit.setUserId(8L);
        if (corruption.equals("foreign-credit")) credit.setUserId(8L);
        if (corruption.equals("no-debit")) debit.setType(TransactionType.INCOME);
        if (corruption.equals("no-credit")) credit.setType(TransactionType.EXPENSE);
        when(transactions.findByTransferId("pair"))
                .thenReturn(
                        switch (corruption) {
                            case "missing" -> List.of();
                            case "one-leg" -> List.of(debit);
                            case "three-legs" -> List.of(
                                    debit, credit, leg(13L, 3L, TransactionType.INCOME));
                            default -> List.of(debit, credit);
                        });
        assertThatThrownBy(() -> service.updateTransfer("pair", 7L, request()))
                .isInstanceOfAny(
                        TransactionNotFoundException.class, InvalidTransactionException.class);
        verify(transactions, never()).save(any());
        noFinancialWrites();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "source-account",
                "destination-account",
                "amount",
                "currency",
                "date",
                "missing-original",
                "original-amount",
                "original-currency",
                "missing-rate",
                "rate"
            })
    void receivingInvestmentUnitsPreventsAnyChangeToTheBookedFinancialInstruction(String change) {
        Transaction debit = leg(11L, 1L, TransactionType.EXPENSE),
                credit = leg(12L, 2L, TransactionType.INCOME);
        credit.setInvestmentQuantity(BigDecimal.ONE);
        TransferUpdateRequest request = request();
        switch (change) {
            case "source-account" -> request.setFromAccountId(3L);
            case "destination-account" -> request.setToAccountId(3L);
            case "amount" -> request.setAmount(new BigDecimal("100.01"));
            case "currency" -> request.setCurrency("USD");
            case "date" -> request.setDate(DATE.plusDays(1));
            default -> {
                request.setOriginalAmount(new BigDecimal("200"));
                request.setOriginalCurrency("USD");
                request.setConversionRate(new BigDecimal("0.5"));
                if (!change.equals("missing-original"))
                    debit.setOriginalAmount(
                            new BigDecimal(change.equals("original-amount") ? "199" : "200"));
                debit.setOriginalCurrency(change.equals("original-currency") ? "GBP" : "USD");
                debit.setConversionRate(
                        change.equals("missing-rate")
                                ? null
                                : new BigDecimal(change.equals("rate") ? "0.49" : "0.5"));
            }
        }
        paired(debit, credit);
        assertThatThrownBy(() -> service.updateTransfer("pair", 7L, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("destination movement must remain unchanged");
        assertThat(debit.getAmount()).isEqualByComparingTo("100");
        assertThat(credit.getInvestmentQuantity()).isEqualByComparingTo("1");
        verify(transactions, never()).save(any());
        noFinancialWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "blank", "text", "matching-conversion"})
    void metadataOnlyChangesPreserveBothBookedLegsIncludingTheirIndependentCurrency(String edit) {
        Transaction debit = leg(11L, 1L, TransactionType.EXPENSE),
                credit = leg(12L, 2L, TransactionType.INCOME);
        credit.setCurrency("USD");
        credit.setAmount(new BigDecimal("123.456789123456789"));
        credit.setInvestmentQuantity(BigDecimal.ONE);
        TransferUpdateRequest request = request();
        if (!edit.equals("omitted")) {
            request.setDescription(edit.equals("blank") ? "  " : "Edited description");
            request.setNotes(edit.equals("blank") ? "" : "Edited notes");
            request.setPaymentMethod(null);
        }
        if (edit.equals("matching-conversion")) {
            debit.setOriginalAmount(new BigDecimal("200"));
            debit.setOriginalCurrency("USD");
            debit.setConversionRate(new BigDecimal("0.5"));
            request.setOriginalAmount(new BigDecimal("200.00"));
            request.setOriginalCurrency("usd");
            request.setConversionRate(new BigDecimal("0.500"));
        }
        paired(debit, credit);
        when(mapper.toResponse(debit)).thenReturn(new TransactionResponse());
        when(conversion.convertTransaction(any(), any(), any()))
                .thenReturn(
                        new CurrencyConversionHelper.ConversionResult(
                                "EUR",
                                new BigDecimal("100"),
                                BigDecimal.ONE,
                                false,
                                null,
                                null,
                                null));
        service.updateTransfer("pair", 7L, request);
        assertThat(debit.getAmount()).isEqualByComparingTo("100");
        assertThat(credit.getAmount()).isEqualByComparingTo("123.456789123456789");
        assertThat(credit.getCurrency()).isEqualTo("USD");
        assertThat(credit.getInvestmentQuantity()).isEqualByComparingTo("1");
        for (Transaction transaction : List.of(debit, credit)) {
            assertThat(transaction.getDescription())
                    .isEqualTo(
                            edit.equals("omitted")
                                    ? "Original description"
                                    : edit.equals("blank") ? null : "Edited description");
            assertThat(transaction.getNotes())
                    .isEqualTo(
                            edit.equals("omitted")
                                    ? "Original notes"
                                    : edit.equals("blank") ? null : "Edited notes");
            assertThat(transaction.getPaymentMethod())
                    .isEqualTo(edit.equals("omitted") ? PaymentMethod.CASH : null);
            assertThat(transaction.getIsReconciled()).isTrue();
            assertThat(transaction.getTags()).isEqualTo("reviewed");
            verify(transactions).save(transaction);
        }
        noFinancialWrites();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "same-account",
                "foreign-source",
                "foreign-destination",
                "currency",
                "null-amount",
                "zero-amount",
                "negative-amount"
            })
    void invalidMoneyOrOwnershipCannotReverseTheOldBalances(String invalid) {
        Transaction debit = leg(11L, 1L, TransactionType.EXPENSE),
                credit = leg(12L, 2L, TransactionType.INCOME);
        paired(debit, credit);
        TransferUpdateRequest request = request();
        request.setAmount(new BigDecimal("200"));
        if (invalid.equals("same-account")) request.setToAccountId(1L);
        if (invalid.equals("currency")) request.setCurrency("USD");
        if (invalid.equals("null-amount")) request.setAmount(null);
        if (invalid.equals("zero-amount")) request.setAmount(BigDecimal.ZERO);
        if (invalid.equals("negative-amount")) request.setAmount(BigDecimal.ONE.negate());
        if (!invalid.equals("same-account") && !invalid.equals("foreign-source"))
            when(accounts.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(account(1L, "EUR")));
        if (!List.of("same-account", "foreign-source", "foreign-destination").contains(invalid))
            when(accounts.findByIdAndUserId(2L, 7L)).thenReturn(Optional.of(account(2L, "EUR")));
        assertThatThrownBy(() -> service.updateTransfer("pair", 7L, request))
                .isInstanceOfAny(AccountNotFoundException.class, InvalidTransactionException.class);
        assertThat(debit.getAmount()).isEqualByComparingTo("100");
        assertThat(credit.getAmount()).isEqualByComparingTo("100");
        verify(transactions, never()).save(any());
        noFinancialWrites();
    }
}
