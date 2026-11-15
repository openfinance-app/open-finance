package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;

@ExtendWith(MockitoExtension.class)
@DisplayName("Duplicate identity preserves source amounts and bank payment distinctions")
class ImportDuplicateBoundaryTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 10);
    @Mock ExchangeRateService exchangeRates;

    @ParameterizedTest
    @CsvSource(
            value = {"NULL|bank", "''|bank", "' '|bank", "bank|NULL", "bank|''", "bank|' '"},
            delimiter = '|',
            nullValues = "NULL")
    void incompleteBankReferencesFallBackToTheMovement(
            String incomingReference, String bookedReference) {
        ImportedTransaction incoming = incoming();
        incoming.setReferenceNumber(incomingReference);
        Transaction booked = booked();
        booked.setExternalReference(bookedReference);
        assertThat(ImportDuplicateMatcher.matches(incoming, booked, true, exchangeRates)).isTrue();
        booked.setAmount(new BigDecimal("11"));
        assertThat(ImportDuplicateMatcher.matches(incoming, booked, true, exchangeRates)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"-2,false", "-1,true", "0,true", "1,true", "2,false"})
    void allowsOnlyOneDaysBankPostingDrift(int days, boolean duplicate) {
        Transaction booked = booked();
        booked.setDate(DATE.plusDays(days));
        assertThat(ImportDuplicateMatcher.matches(incoming(), booked, false, exchangeRates))
                .isEqualTo(duplicate);
    }

    @ParameterizedTest
    @CsvSource({
        "-10,EXPENSE,true",
        "10,INCOME,true",
        "-10,INCOME,false",
        "10,EXPENSE,false",
        "0,INCOME,true",
        "0,EXPENSE,false"
    })
    void incomeAndExpenseAreNeverInterchangeable(
            String amount, TransactionType type, boolean duplicate) {
        ImportedTransaction incoming = incoming();
        incoming.setAmount(new BigDecimal(amount));
        Transaction booked = booked();
        booked.setType(type);
        booked.setAmount(new BigDecimal(amount).abs());
        assertThat(ImportDuplicateMatcher.matches(incoming, booked, false, exchangeRates))
                .isEqualTo(duplicate);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "eur"})
    void absentOrMatchingSourceCurrencyUsesTheBookedNativeAmount(String currency) {
        ImportedTransaction incoming = incoming();
        incoming.setCurrency(currency);
        assertThat(ImportDuplicateMatcher.matches(incoming, booked(), false, exchangeRates))
                .isTrue();
        verifyNoInteractions(exchangeRates);
    }

    @ParameterizedTest
    @CsvSource({"10,true", "10.0001,false"})
    void originalForeignAmountTakesPrecedenceOverNewExchangeRates(
            String sourceAmount, boolean duplicate) {
        ImportedTransaction incoming = incoming();
        incoming.setCurrency("usd");
        incoming.setAmount(new BigDecimal(sourceAmount).negate());
        Transaction booked = booked();
        booked.setOriginalCurrency("USD");
        booked.setOriginalAmount(new BigDecimal("10"));
        booked.setAmount(new BigDecimal("8.15"));
        assertThat(ImportDuplicateMatcher.matches(incoming, booked, false, exchangeRates))
                .isEqualTo(duplicate);
        verifyNoInteractions(exchangeRates);
    }

    @ParameterizedTest
    @CsvSource({"10,true", "9.99,false"})
    void comparesLegacyForeignRowsAtTheOriginalTransactionDate(
            String converted, boolean duplicate) {
        ImportedTransaction incoming = incoming();
        incoming.setCurrency("USD");
        when(exchangeRates.convert(new BigDecimal("10"), "USD", "EUR", DATE))
                .thenReturn(new BigDecimal(converted));
        assertThat(ImportDuplicateMatcher.matches(incoming, booked(), false, exchangeRates))
                .isEqualTo(duplicate);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL|NULL|true",
                "' '|''|true",
                "Shop|NULL|false",
                "NULL|Shop|false",
                "Shop|Shop card|true",
                "Shop card|Shop|true"
            },
            delimiter = '|',
            nullValues = "NULL")
    void missingAndContainedMerchantNamesRemainDistinctFromAmountMatching(
            String sourceName, String bookedName, boolean duplicate) {
        ImportedTransaction incoming = incoming();
        incoming.setPayee(sourceName);
        Transaction booked = booked();
        booked.setDescription(bookedName);
        assertThat(ImportDuplicateMatcher.matches(incoming, booked, false, exchangeRates))
                .isEqualTo(duplicate);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "USD"})
    void sourceInvestmentIdentitySurvivesReviewEditsAndMissingCurrency(String sourceCurrency) {
        Transaction original = booked();
        original.setOriginalCurrency("USD");
        original.setOriginalAmount(new BigDecimal("12.500"));
        original.setDescription("  IKEA   Investment ");
        String identity = ImportDuplicateMatcher.investmentIdentity(original);
        Transaction edited = booked();
        edited.setAmount(new BigDecimal("99"));
        edited.setDescription("Reclassified by user");
        edited.setInvestmentImportIdentity(identity);
        ImportedTransaction incoming = incoming();
        incoming.setAmount(new BigDecimal("-12.50"));
        incoming.setCurrency(sourceCurrency);
        incoming.setOriginalPayee("ikea investment");
        incoming.setPayee("New category wording");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(ImportDuplicateMatcher.matches(incoming, edited, false, exchangeRates))
                    .isTrue();
        } finally {
            Locale.setDefault(previous);
        }
        incoming.setAmount(new BigDecimal("-12.51"));
        assertThat(ImportDuplicateMatcher.matches(incoming, edited, false, exchangeRates))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-amount", "missing-currency", "both-missing"})
    void incompleteOriginalMetadataCannotReplaceTheNativeIdentity(String missing) {
        Transaction original = booked();
        String expected = ImportDuplicateMatcher.investmentIdentity(original);
        if (!missing.equals("missing-amount") && !missing.equals("both-missing"))
            original.setOriginalAmount(new BigDecimal("99"));
        if (!missing.equals("missing-currency") && !missing.equals("both-missing"))
            original.setOriginalCurrency("USD");
        assertThat(ImportDuplicateMatcher.investmentIdentity(original)).isEqualTo(expected);
    }

    @Test
    void identityUsesThePersistedDescriptionLimitWithoutLosingDecimalPrecision() {
        Transaction first = booked();
        first.setCurrency(null);
        first.setDescription("a".repeat(255) + "b");
        Transaction second = booked();
        second.setCurrency(null);
        second.setDescription("a".repeat(255) + "c");
        assertThat(ImportDuplicateMatcher.investmentIdentity(first))
                .isEqualTo(ImportDuplicateMatcher.investmentIdentity(second));
        second.setAmount(new BigDecimal("10.000000000000000001"));
        assertThat(ImportDuplicateMatcher.investmentIdentity(first))
                .isNotEqualTo(ImportDuplicateMatcher.investmentIdentity(second));
        first.setDescription(null);
        second.setDescription("");
        second.setAmount(first.getAmount());
        assertThat(ImportDuplicateMatcher.investmentIdentity(first))
                .isEqualTo(ImportDuplicateMatcher.investmentIdentity(second));
    }

    private ImportedTransaction incoming() {
        return ImportedTransaction.builder()
                .amount(new BigDecimal("-10"))
                .currency("EUR")
                .transactionDate(DATE)
                .payee("Shop")
                .build();
    }

    private Transaction booked() {
        return Transaction.builder()
                .amount(new BigDecimal("10"))
                .currency("EUR")
                .date(DATE)
                .type(TransactionType.EXPENSE)
                .description("Shop")
                .build();
    }
}
