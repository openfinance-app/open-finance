package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Liability;

class LoanPaymentDatesTest {
    @Test
    void retainsTheContractualDayAcrossShortMonthsAndLeapYears() {
        Liability loan = new Liability();
        loan.setAmortizationStartDate(LocalDate.of(2024, 3, 31));
        LocalDate first = LoanPaymentDates.nextPayment(loan, LocalDate.of(2024, 2, 20));
        assertThat(first).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(LoanPaymentDates.paymentAt(loan, first, 1)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(LoanPaymentDates.paymentAt(loan, first, 2)).isEqualTo(LocalDate.of(2024, 4, 30));
        assertThat(LoanPaymentDates.nextPayment(loan, LocalDate.of(2025, 2, 20)))
                .isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(LoanPaymentDates.principalDeferred(loan, first)).isTrue();
        assertThat(LoanPaymentDates.principalDeferred(loan, loan.getAmortizationStartDate()))
                .isFalse();
    }

    @Test
    void countsBothFirstAndLastAmortizingInstalments() {
        Liability loan = new Liability();
        loan.setStartDate(LocalDate.of(2020, 2, 10));
        loan.setAmortizationStartDate(LocalDate.of(2021, 8, 5));
        loan.setEndDate(LocalDate.of(2024, 7, 5));
        assertThat(LoanPaymentDates.amortizingMonths(loan)).isEqualTo(36);
    }
}
