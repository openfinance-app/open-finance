package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;

@DisplayName("Loan posting preserves the imported opening balance and historical cash movements")
class LoanPostingPolicyTest {
    private static final LocalDate CONVERSION = LocalDate.of(2025, 1, 2);
    private static final LocalDateTime IMPORTED_AT = CONVERSION.atTime(12, 0);

    private Liability loan() {
        Liability liability = new Liability();
        liability.setId(7L);
        liability.setConvertedFromAccountId(9L);
        liability.setBalanceStartDate(CONVERSION);
        liability.setCreatedAt(IMPORTED_AT);
        liability.setStartDate(LocalDate.of(2020, 1, 1));
        return liability;
    }

    private Transaction payment() {
        return Transaction.builder()
                .accountId(3L)
                .date(CONVERSION.minusDays(1))
                .createdAt(IMPORTED_AT.minusSeconds(1))
                .type(TransactionType.EXPENSE)
                .currency("EUR")
                .amount(new BigDecimal("90"))
                .originalCurrency("USD")
                .originalAmount(new BigDecimal("100"))
                .conversionRate(new BigDecimal("0.9"))
                .build();
    }

    private TransactionRequest link() {
        return TransactionRequest.builder()
                .accountId(3L)
                .date(CONVERSION.minusDays(1))
                .type(TransactionType.EXPENSE)
                .currency("EUR")
                .amount(new BigDecimal("90.00"))
                .originalCurrency("USD")
                .originalAmount(new BigDecimal("100.00"))
                .conversionRate(new BigDecimal("0.9000"))
                .liabilityId(7L)
                .movementType(MovementType.REPAYMENT)
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("historicalBoundaries")
    void identifiesHistoryAtTheExactImportBoundary(
            String name, LocalDate date, LocalDateTime recordedAt, boolean historical) {
        assertThat(LoanPostingPolicy.isHistorical(loan(), date, recordedAt)).isEqualTo(historical);
    }

    static Stream<Arguments> historicalBoundaries() {
        return Stream.of(
                Arguments.of(
                        "before conversion, missing timestamp",
                        CONVERSION.minusDays(1),
                        null,
                        true),
                Arguments.of(
                        "on conversion before import", CONVERSION, IMPORTED_AT.minusNanos(1), true),
                Arguments.of("on conversion exactly at import", CONVERSION, IMPORTED_AT, true),
                Arguments.of(
                        "on conversion after import", CONVERSION, IMPORTED_AT.plusNanos(1), false),
                Arguments.of("on conversion without timestamp", CONVERSION, null, false),
                Arguments.of("after conversion", CONVERSION.plusDays(1), IMPORTED_AT, false),
                Arguments.of("unknown movement date", null, IMPORTED_AT, false));
    }

    @Test
    void missingConversionMetadataNeverInventsHistoricalMovements() {
        Liability loan = loan();
        loan.setCreatedAt(null);
        assertThat(LoanPostingPolicy.isHistorical(loan, CONVERSION, IMPORTED_AT)).isFalse();
        loan.setBalanceStartDate(null);
        assertThat(LoanPostingPolicy.isHistorical(loan, CONVERSION.minusDays(1), IMPORTED_AT))
                .isFalse();
        loan = loan();
        loan.setConvertedFromAccountId(null);
        assertThat(LoanPostingPolicy.isHistorical(loan, CONVERSION.minusDays(1), IMPORTED_AT))
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = MovementType.class,
            names = {"REPAYMENT", "INTEREST", "INSURANCE", "FEE"})
    void allowsMetadataLinkWithNumericallyEqualAmounts(MovementType type) {
        TransactionRequest request = link();
        request.setMovementType(type);
        assertThatCode(() -> LoanPostingPolicy.validateLink(loan(), request, payment()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "reject historical cash edit: {0}")
    @MethodSource("cashChanges")
    void rejectsEveryCashChangingEdit(String name, Consumer<TransactionRequest> change) {
        TransactionRequest request = link();
        change.accept(request);
        assertThatThrownBy(() -> LoanPostingPolicy.validateLink(loan(), request, payment()))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("without changing its cash movement");
    }

    private static Arguments change(String name, Consumer<TransactionRequest> edit) {
        return Arguments.of(name, edit);
    }

    static Stream<Arguments> cashChanges() {
        return Stream.of(
                change("transaction type", r -> r.setType(TransactionType.INCOME)),
                change("booking date", r -> r.setDate(CONVERSION)),
                change("account", r -> r.setAccountId(4L)),
                change("currency", r -> r.setCurrency("USD")),
                change("original currency", r -> r.setOriginalCurrency("GBP")),
                change("amount", r -> r.setAmount(new BigDecimal("90.01"))),
                change("missing amount", r -> r.setAmount(null)),
                change("original amount", r -> r.setOriginalAmount(new BigDecimal("100.01"))),
                change("missing original amount", r -> r.setOriginalAmount(null)),
                change("conversion rate", r -> r.setConversionRate(new BigDecimal("0.91"))),
                change("missing conversion rate", r -> r.setConversionRate(null)),
                change("missing movement type", r -> r.setMovementType(null)),
                change("new disbursement", r -> r.setMovementType(MovementType.DISBURSEMENT)),
                change("new tranche posting", r -> r.setTrancheId(2L)));
    }

    @Test
    void rejectsIncomeAndTransferLegsEvenWhenTheRequestMatches() {
        Transaction existing = payment();
        existing.setType(TransactionType.INCOME);
        TransactionRequest request = link();
        request.setType(TransactionType.INCOME);
        assertThatThrownBy(() -> LoanPostingPolicy.validateLink(loan(), request, existing))
                .isInstanceOf(InvalidTransactionException.class);
        existing.setType(TransactionType.EXPENSE);
        request.setType(TransactionType.EXPENSE);
        existing.setTransferId("transfer-pair");
        assertThatThrownBy(() -> LoanPostingPolicy.validateLink(loan(), request, existing))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void supportsUnlinkingAndSameCurrencyPaymentsWithoutFxMetadata() {
        Transaction existing = payment();
        existing.setOriginalAmount(null);
        existing.setConversionRate(null);
        existing.setOriginalCurrency(null);
        TransactionRequest request = link();
        request.setOriginalAmount(null);
        request.setConversionRate(null);
        request.setOriginalCurrency(null);
        request.setLiabilityId(null);
        request.setMovementType(null);
        assertThatCode(() -> LoanPostingPolicy.validateLink(loan(), request, existing))
                .doesNotThrowAnyException();
        request.setOriginalAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> LoanPostingPolicy.validateLink(loan(), request, existing))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void aNewPaymentCannotBeBackdatedIntoTheImportedBalance() {
        assertThatThrownBy(() -> LoanPostingPolicy.validateLink(loan(), link(), null))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("conversion date");
        TransactionRequest request = link();
        request.setDate(CONVERSION);
        assertThatCode(() -> LoanPostingPolicy.validateLink(loan(), request, null))
                .doesNotThrowAnyException();
        Transaction existing = payment();
        existing.setDate(CONVERSION.plusDays(1));
        request.setDate(CONVERSION.minusDays(1));
        assertThatThrownBy(() -> LoanPostingPolicy.validateLink(loan(), request, existing))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("conversion date");
    }

    @Test
    void validatesOriginationAndTodayInclusivelyWithoutConversionMetadata() {
        Liability loan = loan();
        loan.setBalanceStartDate(null);
        assertThatCode(() -> LoanPostingPolicy.validateDate(loan, loan.getStartDate()))
                .doesNotThrowAnyException();
        assertThatCode(() -> LoanPostingPolicy.validateDate(loan, LocalDate.now()))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                LoanPostingPolicy.validateDate(
                                        loan, loan.getStartDate().minusDays(1)))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> LoanPostingPolicy.validateDate(loan, LocalDate.now().plusDays(1)))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> LoanPostingPolicy.validateDate(loan, null))
                .isInstanceOf(InvalidTransactionException.class);
        loan.setStartDate(null);
        assertThatCode(() -> LoanPostingPolicy.validateDate(loan, CONVERSION))
                .doesNotThrowAnyException();
    }
}
