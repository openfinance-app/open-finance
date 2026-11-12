package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Amount bounds retain booked values and dated conversion precision")
class TransactionAmountFilterTest {
    private static final LocalDate DATE = LocalDate.of(2024, 6, 15);
    @Mock private UserRepository users;
    @Mock private DefaultCurrencyProvider defaults;
    @Mock private ExchangeRateService rates;
    private CurrencyConversionHelper conversion;

    @BeforeEach
    void configureEuroBase() {
        lenient().when(defaults.resolveForUser(1L)).thenReturn("EUR");
        lenient().when(defaults.resolve("EUR")).thenReturn("EUR");
        lenient()
                .when(users.findById(1L))
                .thenReturn(Optional.of(User.builder().id(1L).baseCurrency("EUR").build()));
        conversion = new CurrencyConversionHelper(users, defaults, rates);
    }

    @Test
    void usesTheTransactionDateWithoutRoundingBeforeComparison() {
        Transaction transaction = foreignExpense();
        when(rates.getExchangeRate("USD", "EUR", DATE)).thenReturn(new BigDecimal("2.5"));
        when(rates.convert(transaction.getAmount(), "USD", "EUR", DATE))
                .thenReturn(new BigDecimal("2499.999999999999999999"));

        assertThat(matches(transaction, "2500", null)).isFalse();
        assertThat(matches(transaction, null, "2500")).isTrue();
        when(rates.convert(transaction.getAmount(), "USD", "EUR", DATE))
                .thenReturn(new BigDecimal("2500"));
        assertThat(matches(transaction, "2500", "2500")).isTrue();
        verify(rates, org.mockito.Mockito.times(3))
                .convert(transaction.getAmount(), "USD", "EUR", DATE);
    }

    @Test
    void missingConversionDoesNotCompareTheNativeFallbackAgainstEuroBounds() {
        Transaction transaction = foreignExpense();
        when(rates.getExchangeRate("USD", "EUR", DATE))
                .thenThrow(new IllegalStateException("No historical quote"));

        assertThat(matches(transaction, "500", null)).isFalse();
        assertThat(matches(transaction, null, "2500")).isFalse();
        assertThat(matches(transaction, null, null)).isTrue();
    }

    @Test
    void recordedAccountAmountTakesPrecedenceOverOriginalAndTransferAmounts() {
        Transaction transaction = foreignExpense();
        transaction.setAccountCurrency("EUR");
        transaction.setAccountAmount(new BigDecimal("500"));
        transaction.setOriginalCurrency("EUR");
        transaction.setOriginalAmount(new BigDecimal("600"));

        assertThat(matches(transaction, "500", "500")).isTrue();
        assertThat(
                        conversion
                                .convertTransaction(transaction, new BigDecimal("700"), "EUR")
                                .amountInBaseCurrency())
                .isEqualByComparingTo("500");
        verifyNoInteractions(rates);
    }

    @Test
    void otherTransferLegSuppliesBookedAmountWithoutLeakingAnotherUsersLeg() {
        Transaction transaction = foreignExpense();
        transaction.setTransferId("paired");
        Transaction opposite =
                Transaction.builder()
                        .id(2L)
                        .userId(1L)
                        .type(TransactionType.INCOME)
                        .transferId("paired")
                        .currency("EUR")
                        .amount(new BigDecimal("500"))
                        .build();
        Transaction otherUser =
                Transaction.builder()
                        .id(3L)
                        .userId(2L)
                        .type(TransactionType.INCOME)
                        .transferId("paired")
                        .currency("EUR")
                        .amount(new BigDecimal("9000"))
                        .build();

        assertThat(
                        TransactionAmountFilter.matching(
                                        List.of(otherUser, transaction, opposite),
                                        new BigDecimal("500"),
                                        new BigDecimal("500"),
                                        conversion)
                                .test(transaction))
                .isTrue();
        verifyNoInteractions(rates);
    }

    private boolean matches(Transaction transaction, String minimum, String maximum) {
        return TransactionAmountFilter.matching(
                        List.of(transaction),
                        minimum == null ? null : new BigDecimal(minimum),
                        maximum == null ? null : new BigDecimal(maximum),
                        conversion)
                .test(transaction);
    }

    private Transaction foreignExpense() {
        return Transaction.builder()
                .id(1L)
                .userId(1L)
                .type(TransactionType.EXPENSE)
                .date(DATE)
                .currency("USD")
                .amount(new BigDecimal("1000"))
                .build();
    }
}
