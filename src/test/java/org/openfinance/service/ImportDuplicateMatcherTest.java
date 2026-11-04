package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;

@ExtendWith(MockitoExtension.class)
@DisplayName("Duplicate matching balances spelling tolerance with payment identity")
class ImportDuplicateMatcherTest {
    @Mock ExchangeRateService exchangeRates;

    @ParameterizedTest
    @CsvSource({
        "Monthly Internet,Monthly Internat,true",
        "Corner shop,Corner shop card 123,true",
        "Rent,Rant,false",
        "Shop A,Shop B,false",
        "'  CORNER   SHOP  ',Corner Shop,true"
    })
    void toleratesMinorPayeeNoiseWithoutCollapsingDistinctMerchants(
            String importedName, String bookedName, boolean duplicate) {
        assertThat(
                        ImportDuplicateMatcher.matches(
                                imported(importedName), booked(bookedName), false, exchangeRates))
                .isEqualTo(duplicate);
    }

    @Test
    void bankReferencesOverrideFuzzyPayeeSimilarity() {
        ImportedTransaction imported = imported("Shop");
        imported.setReferenceNumber("bank-1");
        Transaction booked = booked("Shop");
        booked.setExternalReference("bank-2");
        assertThat(ImportDuplicateMatcher.matches(imported, booked, true, exchangeRates)).isFalse();
        booked.setExternalReference("bank-1");
        booked.setDescription("Other bank wording");
        assertThat(ImportDuplicateMatcher.matches(imported, booked, true, exchangeRates)).isTrue();
    }

    @Test
    void cannotMatchWhenOnlyOnePayeeIsMissingOrTheMovementIsATransfer() {
        assertThat(
                        ImportDuplicateMatcher.matches(
                                imported(null), booked("Known"), false, exchangeRates))
                .isFalse();
        assertThat(
                        ImportDuplicateMatcher.matches(
                                imported(null), booked(null), false, exchangeRates))
                .isTrue();
        Transaction transfer = booked("Shop");
        transfer.setType(TransactionType.TRANSFER);
        assertThat(ImportDuplicateMatcher.matches(imported("Shop"), transfer, false, exchangeRates))
                .isFalse();
    }

    @Test
    void missingHistoricalExchangeRateCannotCreateAForeignCurrencyDuplicate() {
        ImportedTransaction imported = imported("Shop");
        imported.setCurrency("USD");
        when(exchangeRates.convert(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Missing historical rate"));
        assertThat(ImportDuplicateMatcher.matches(imported, booked("Shop"), false, exchangeRates))
                .isFalse();
    }

    private ImportedTransaction imported(String payee) {
        return ImportedTransaction.builder()
                .amount(new BigDecimal("-12.50"))
                .currency("EUR")
                .transactionDate(LocalDate.of(2026, 1, 2))
                .payee(payee)
                .build();
    }

    private Transaction booked(String payee) {
        return Transaction.builder()
                .amount(new BigDecimal("12.50"))
                .currency("EUR")
                .type(TransactionType.EXPENSE)
                .date(LocalDate.of(2026, 1, 2))
                .description(payee)
                .build();
    }
}
