package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.entity.Liability;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;

@DisplayName("Valuation and converted-loan policies distinguish cost, value and cash direction")
class ValuationContributionPolicyTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "explicit-zero",
                "explicit-positive",
                "native",
                "converted",
                "currency-only",
                "rate-only",
                "maintenance"
            })
    void capitalizedCostDoesNotAutomaticallyBecomeAnExplicitValuation(String source) {
        Transaction transaction =
                Transaction.builder()
                        .movementType(MovementType.CAPITAL_IMPROVEMENT)
                        .amount(new BigDecimal("120"))
                        .build();
        BigDecimal expected = new BigDecimal("120");
        switch (source) {
            case "explicit-zero" -> {
                transaction.setValuationChange(BigDecimal.ZERO);
                expected = BigDecimal.ZERO;
            }
            case "explicit-positive" -> {
                transaction.setValuationChange(new BigDecimal("20"));
                expected = new BigDecimal("20");
            }
            case "converted" -> {
                transaction.setOriginalCurrency("EUR");
                transaction.setConversionRate(new BigDecimal("1.2"));
                transaction.setOriginalAmount(new BigDecimal("100"));
                expected = new BigDecimal("100");
            }
            case "currency-only" -> transaction.setOriginalCurrency("EUR");
            case "rate-only" -> transaction.setConversionRate(new BigDecimal("1.2"));
            case "maintenance" -> {
                transaction.setMovementType(MovementType.MAINTENANCE);
                expected = BigDecimal.ZERO;
            }
            default -> {}
        }
        assertThat(ValuationContributions.amount(transaction)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "-1,-1,-1,true",
        "-1,-1,0,false",
        "0,-1,0,false",
        "1,-1,0,true",
        "-1,1,0,true",
        "-1,0,0,false",
        "-1,1,-1,false"
    })
    void independentValuationsOnlyAbsorbContributionsAlreadyKnownAtThatTime(
            int effectiveOffset, int recordedOffset, int anchorOffset, boolean expected) {
        LocalDate anchor = LocalDate.of(2025, 1, 1);
        LocalDateTime timestamp = anchor.atStartOfDay();
        LocalDate anchorDate = anchorOffset == -1 && recordedOffset == -1 ? null : anchor;
        LocalDateTime recorded =
                recordedOffset == -1 ? null : timestamp.plusSeconds(recordedOffset);
        LocalDateTime anchorRecorded = anchorOffset == -1 ? null : timestamp;
        assertThat(
                        ValuationContributions.applies(
                                anchor.plusDays(effectiveOffset),
                                recorded,
                                anchorDate,
                                anchorRecorded))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "INCOME,100,REPAYMENT",
        "EXPENSE,100,DISBURSEMENT",
        "INCOME,-100,DISBURSEMENT",
        "EXPENSE,-100,REPAYMENT",
        "INCOME,0,",
        "EXPENSE,0,"
    })
    void importedLoanDirectionUsesTheSignedBookedBalance(
            TransactionType type, String amount, MovementType expected) {
        Liability loan = new Liability();
        loan.setConvertedFromAccountId(10L);
        Transaction transaction =
                Transaction.builder()
                        .accountId(10L)
                        .type(type)
                        .amount(new BigDecimal(amount))
                        .build();
        assertThat(LoanMovementClassification.classify(loan, transaction)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {11L})
    void anUnrelatedAccountCannotBeClassifiedAsTheImportedDebt(Long sourceAccount) {
        Liability loan = new Liability();
        loan.setConvertedFromAccountId(sourceAccount);
        Transaction transaction =
                Transaction.builder()
                        .accountId(10L)
                        .amount(BigDecimal.TEN)
                        .type(TransactionType.EXPENSE)
                        .build();
        assertThat(LoanMovementClassification.classify(loan, transaction)).isNull();
    }

    @ParameterizedTest
    @EnumSource(MovementType.class)
    void explicitMovementLabelsArePreservedAndOnlyDebtPaymentsCountAsPayments(MovementType type) {
        Liability loan = new Liability();
        Transaction transaction = Transaction.builder().movementType(type).build();
        assertThat(LoanMovementClassification.classify(loan, transaction)).isEqualTo(type);
        assertThat(LoanMovementClassification.isPayment(type))
                .isEqualTo(
                        switch (type) {
                            case REPAYMENT, INTEREST, INSURANCE, FEE -> true;
                            default -> false;
                        });
    }
}
