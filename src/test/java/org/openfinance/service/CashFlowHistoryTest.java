package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.CashFlowGranularity;
import org.openfinance.dto.CashFlowPeriod;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cash flow history aggregation")
class CashFlowHistoryTest {
    @Mock private TransactionRepository transactions;
    @Mock private UserRepository users;
    @Mock private DefaultCurrencyProvider currencies;
    @Mock private ExchangeRateService exchangeRates;
    @InjectMocks private DashboardService dashboard;

    @Test
    @DisplayName("Monthly totals preserve cents, dated FX and transfer/deletion exclusions")
    void aggregatesMonthlyTotals() {
        LocalDate january = LocalDate.of(2024, 1, 1);
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        Transaction foreignIncome = transaction("100", TransactionType.INCOME, january);
        foreignIncome.setCurrency("USD");
        Transaction foreignExpense = transaction("100", TransactionType.EXPENSE, leapDay);
        foreignExpense.setCurrency("USD");
        Transaction deleted = transaction("9000", TransactionType.EXPENSE, leapDay);
        deleted.setIsDeleted(true);
        Transaction transfer = transaction("8000", TransactionType.INCOME, leapDay);
        transfer.setTransferId("internal-transfer");
        when(users.findById(1L))
                .thenReturn(Optional.of(User.builder().baseCurrency("EUR").build()));
        when(currencies.resolve("EUR")).thenReturn("EUR");
        when(exchangeRates.convert(new BigDecimal("100"), "USD", "EUR", january))
                .thenReturn(new BigDecimal("50.00"));
        when(exchangeRates.convert(new BigDecimal("100"), "USD", "EUR", leapDay))
                .thenReturn(new BigDecimal("80.00"));
        when(transactions.findByUserIdAndDateBetween(1L, january, LocalDate.of(2024, 12, 31)))
                .thenReturn(
                        List.of(
                                foreignIncome,
                                foreignExpense,
                                deleted,
                                transfer,
                                transaction("0.10", TransactionType.EXPENSE, leapDay),
                                transaction("0.20", TransactionType.EXPENSE, leapDay),
                                transaction("7000", TransactionType.TRANSFER, leapDay)));

        List<CashFlowPeriod> result =
                dashboard.getCashFlowHistory(1L, CashFlowGranularity.MONTH, 2024, 2);

        assertThat(result).hasSize(12);
        assertThat(result.getFirst().getIncome()).isEqualByComparingTo("50.00");
        assertThat(result.get(1).getExpense()).isEqualByComparingTo("80.30");
        assertThat(result.get(1).getIncome()).isZero();
        assertThat(result.get(2).getIncome()).isZero();
        assertThat(result.get(2).getExpense()).isZero();
        assertThat(result.getLast().getDate()).isEqualTo(LocalDate.of(2024, 12, 1));
        verify(exchangeRates).convert(new BigDecimal("100"), "USD", "EUR", january);
        verify(exchangeRates).convert(new BigDecimal("100"), "USD", "EUR", leapDay);
    }

    @Test
    @DisplayName("Invalid ranges fail before querying transactions")
    void rejectsInvalidPeriods() {
        assertThatThrownBy(
                        () -> dashboard.getCashFlowHistory(1L, CashFlowGranularity.DAY, 2024, 13))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dashboard.getCashFlowHistory(1L, CashFlowGranularity.YEAR, 9, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> dashboard.getCashFlowHistory(1L, CashFlowGranularity.MONTH, 10000, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Transaction transaction(String amount, TransactionType type, LocalDate date) {
        return Transaction.builder()
                .userId(1L)
                .amount(new BigDecimal(amount))
                .type(type)
                .date(date)
                .currency("EUR")
                .isDeleted(false)
                .build();
    }
}
