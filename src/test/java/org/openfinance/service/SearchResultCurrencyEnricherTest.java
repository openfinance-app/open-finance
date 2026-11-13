package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.openfinance.dto.SearchResultDto;
import org.openfinance.dto.SearchResultDto.SearchResultType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Search display uses booked or dated FX without changing native search amounts")
class SearchResultCurrencyEnricherTest {
    @Mock private TransactionRepository transactions;
    @Mock private UserRepository users;
    @Mock private DefaultCurrencyProvider defaults;
    @Mock private ExchangeRateService rates;
    private SearchResultCurrencyEnricher enricher;
    private static final LocalDate DATE = LocalDate.of(2024, 6, 1);

    @BeforeEach
    void setUp() {
        enricher =
                new SearchResultCurrencyEnricher(
                        transactions, new CurrencyConversionHelper(users, defaults, rates));
    }

    @Test
    void keepsRecordedOriginalAmountInsteadOfUsingMarketRates() {
        Transaction transaction = transaction();
        transaction.setOriginalAmount(new BigDecimal("93.47"));
        transaction.setOriginalCurrency("EUR");
        when(transactions.findAllById(List.of(7L))).thenReturn(List.of(transaction));
        when(defaults.resolveForUser(1L)).thenReturn("EUR");
        SearchResultDto result = result(SearchResultType.TRANSACTION);

        enricher.populate(List.of(result), 1L);

        assertThat(result.getAmountInBaseCurrency()).isEqualByComparingTo("93.47");
        assertThat(result.getOriginalAmount()).isEqualByComparingTo("93.47");
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo("100");
        assertThat(result.getCurrency()).isEqualTo("USD");
        verifyNoInteractions(rates);
    }

    @Test
    void usesTheOwnedPairedTransferAmount() {
        Transaction transaction = transaction();
        transaction.setTransferId("pair");
        Transaction paired =
                Transaction.builder()
                        .id(8L)
                        .userId(1L)
                        .transferId("pair")
                        .type(TransactionType.INCOME)
                        .amount(new BigDecimal("95"))
                        .currency("EUR")
                        .build();
        Transaction otherUser =
                Transaction.builder()
                        .id(9L)
                        .userId(2L)
                        .transferId("pair")
                        .type(TransactionType.INCOME)
                        .amount(new BigDecimal("9999"))
                        .currency("EUR")
                        .build();
        when(transactions.findAllById(List.of(7L))).thenReturn(List.of(transaction));
        when(transactions.findTransferLegs(1L, List.of("pair")))
                .thenReturn(List.of(otherUser, transaction, paired));
        when(defaults.resolveForUser(1L)).thenReturn("EUR");
        SearchResultDto result = result(SearchResultType.TRANSACTION);

        enricher.populate(List.of(result), 1L);

        assertThat(result.getAmountInBaseCurrency()).isEqualByComparingTo("95");
        assertThat(result.getTransferAmount()).isEqualByComparingTo("95");
        assertThat(result.getTransferCurrency()).isEqualTo("EUR");
        verifyNoInteractions(rates);
    }

    @Test
    void usesTheTransactionDateForUnbookedConversions() {
        when(transactions.findAllById(List.of(7L))).thenReturn(List.of(transaction()));
        when(defaults.resolveForUser(1L)).thenReturn("EUR");
        reportingUser();
        when(rates.getExchangeRate("USD", "EUR", DATE)).thenReturn(new BigDecimal("0.8"));
        when(rates.convert(new BigDecimal("100"), "USD", "EUR", DATE))
                .thenReturn(new BigDecimal("80"));
        SearchResultDto result = result(SearchResultType.TRANSACTION);

        enricher.populate(List.of(result), 1L);

        assertThat(result.getAmountInBaseCurrency()).isEqualByComparingTo("80");
        verify(rates).getExchangeRate("USD", "EUR", DATE);
    }

    @Test
    void valuesCurrentHoldingsAtCurrentRatesDespiteTheAcquisitionDateOnTheSearchResult() {
        reportingUser();
        when(rates.getExchangeRate("USD", "EUR", null)).thenReturn(new BigDecimal("0.9"));
        when(rates.convert(new BigDecimal("100"), "USD", "EUR")).thenReturn(new BigDecimal("90"));
        SearchResultDto result = result(SearchResultType.ASSET);

        enricher.populate(List.of(result), 1L);

        assertThat(result.getAmountInBaseCurrency()).isEqualByComparingTo("90");
        assertThat(result.getDate()).isEqualTo(DATE);
        verifyNoInteractions(transactions);
    }

    @Test
    void marksUnavailableConversionWithoutRelabellingNativeMoney() {
        reportingUser();
        when(rates.getExchangeRate("USD", "EUR", null))
                .thenThrow(new IllegalStateException("Missing quote"));
        SearchResultDto result = result(SearchResultType.ACCOUNT);

        enricher.populate(List.of(result), 1L);

        assertThat(result.getIsConverted()).isFalse();
        assertThat(result.getAmount()).isEqualByComparingTo("100");
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    void neverUsesAnotherUsersTransactionForConversion() {
        Transaction otherUser = transaction();
        otherUser.setUserId(2L);
        when(transactions.findAllById(any())).thenReturn(List.of(otherUser));
        SearchResultDto result = result(SearchResultType.TRANSACTION);

        enricher.populate(List.of(result), 1L);

        assertThat(result.getAmountInBaseCurrency()).isNull();
        verifyNoInteractions(users, defaults, rates);
    }

    private void reportingUser() {
        when(users.findById(1L))
                .thenReturn(Optional.of(User.builder().baseCurrency("EUR").build()));
        when(defaults.resolve("EUR")).thenReturn("EUR");
    }

    private Transaction transaction() {
        return Transaction.builder()
                .id(7L)
                .userId(1L)
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100"))
                .currency("USD")
                .date(DATE)
                .build();
    }

    private SearchResultDto result(SearchResultType type) {
        return SearchResultDto.builder()
                .id(7L)
                .resultType(type)
                .amount(new BigDecimal("100"))
                .currency("USD")
                .date(DATE)
                .build();
    }
}
