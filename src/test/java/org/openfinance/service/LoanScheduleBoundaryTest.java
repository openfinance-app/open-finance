package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AmortizationScheduleEntry;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Remaining loan schedules handle incomplete terms and overlapping deferments")
class LoanScheduleBoundaryTest {
    @Mock private LiabilityRepository loans;
    @Mock private LiabilityTrancheRepository tranches;
    @InjectMocks private LiabilityService service;

    private Liability loan() {
        Liability loan = new Liability();
        loan.setId(3L);
        loan.setUserId(7L);
        loan.setCurrency("EUR");
        loan.setPrincipal("1200");
        loan.setCurrentBalance("1200");
        loan.setInterestRate("0");
        loan.setStartDate(LocalDate.now().minusMonths(6));
        loan.setEndDate(LocalDate.now().plusMonths(6));
        return loan;
    }

    private List<AmortizationScheduleEntry> schedule(Liability loan) {
        when(loans.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(loan));
        return service.calculateAmortizationSchedule(3L, 7L);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "principal-missing",
                "principal-zero",
                "start-missing",
                "end-missing",
                "zero-term",
                "reversed-term"
            })
    void missingPaymentCannotBeDerivedFromIncompleteOrInvalidTerms(String condition) {
        Liability loan = loan();
        switch (condition) {
            case "principal-missing" -> loan.setPrincipal(null);
            case "principal-zero" -> loan.setPrincipal("0");
            case "start-missing" -> loan.setStartDate(null);
            case "end-missing" -> loan.setEndDate(null);
            case "zero-term" -> loan.setEndDate(loan.getStartDate());
            case "reversed-term" -> loan.setEndDate(loan.getStartDate().minusMonths(1));
            default -> throw new AssertionError(condition);
        }
        assertThat(schedule(loan)).isEmpty();
        assertThat(service.effectiveMonthlyPayment(loan)).isNull();
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL", "0", "-1"},
            nullValues = "NULL")
    void missingOrNonpositiveMinimumUsesTheContractTermWithoutOverpaying(String payment) {
        Liability loan = loan();
        loan.setMinimumPayment(payment);
        List<AmortizationScheduleEntry> schedule = schedule(loan);
        assertThat(schedule).hasSize(12);
        assertThat(schedule)
                .allSatisfy(
                        row -> {
                            assertThat(row.getPaymentAmount()).isEqualByComparingTo("100");
                            assertThat(row.getInterestPortion()).isZero();
                        });
        assertThat(schedule.getLast().getRemainingBalance()).isZero();
        assertThat(service.effectiveMonthlyPayment(loan)).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,1200,0", "0,1200,0", "-1,1200,0", "12,NULL,0", "12,0,0", "12,1200,12"},
            nullValues = "NULL")
    void insuranceRequiresBothPositivePrincipalAndPercentage(
            String percent, String principal, String monthly) {
        Liability loan = loan();
        loan.setMinimumPayment("100");
        loan.setPrincipal(principal);
        loan.setInsurancePercentage(percent);
        List<AmortizationScheduleEntry> schedule = schedule(loan);
        assertThat(schedule).hasSize(12);
        assertThat(schedule)
                .allSatisfy(
                        row -> {
                            assertThat(row.getInsurancePortion()).isEqualByComparingTo(monthly);
                            assertThat(row.getPaymentAmount())
                                    .isEqualByComparingTo(
                                            new BigDecimal("100").add(new BigDecimal(monthly)));
                        });
    }

    private LiabilityTranche tranche(TrancheStatus status, boolean interestOnly, LocalDate until) {
        return LiabilityTranche.builder()
                .status(status)
                .interestOnly(interestOnly)
                .interestOnlyUntil(until)
                .build();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "open-ended",
                "latest",
                "planned-ignored",
                "amortizing-ignored",
                "contract-later"
            })
    void combinesOnlyDrawnInterestOnlyWindowsWithoutOverridingALaterContractDeferral(
            String condition) {
        Liability loan = loan();
        loan.setMinimumPayment("100");
        LocalDate today = LocalDate.now();
        List<LiabilityTranche> supplied;
        int deferred;
        switch (condition) {
            case "open-ended" -> {
                supplied = List.of(tranche(TrancheStatus.DRAWN, true, null));
                deferred = 360;
            }
            case "latest" -> {
                supplied =
                        List.of(
                                tranche(TrancheStatus.DRAWN, true, today),
                                tranche(TrancheStatus.DRAWN, true, today.plusMonths(1)),
                                tranche(TrancheStatus.DRAWN, true, today.minusDays(1)));
                deferred = 2;
            }
            case "planned-ignored" -> {
                supplied = List.of(tranche(TrancheStatus.PLANNED, true, null));
                deferred = 0;
            }
            case "amortizing-ignored" -> {
                supplied = List.of(tranche(TrancheStatus.DRAWN, false, null));
                deferred = 0;
            }
            case "contract-later" -> {
                loan.setAmortizationStartDate(today.plusMonths(2));
                supplied = List.of(tranche(TrancheStatus.DRAWN, true, today));
                deferred = 2;
            }
            default -> throw new AssertionError(condition);
        }
        when(tranches.findByLiabilityIdAndUserId(3L, 7L)).thenReturn(supplied);
        List<AmortizationScheduleEntry> schedule = schedule(loan);
        assertThat(schedule.stream().filter(AmortizationScheduleEntry::isInterestOnlyPhase).count())
                .isEqualTo(deferred);
        if (!condition.equals("open-ended"))
            assertThat(schedule.getLast().getRemainingBalance()).isZero();
        else assertThat(schedule.getLast().getRemainingBalance()).isEqualByComparingTo("1200");
    }

    @Test
    void aFinalInstallmentIncludesInsuranceButDoesNotRepayMoreThanOutstandingPrincipal() {
        Liability loan = loan();
        loan.setCurrentBalance("5.01");
        loan.setMinimumPayment("100");
        loan.setInsurancePercentage("1");
        assertThat(schedule(loan))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getPrincipalPortion()).isEqualByComparingTo("5.01");
                            assertThat(row.getInsurancePortion()).isEqualByComparingTo("1");
                            assertThat(row.getPaymentAmount()).isEqualByComparingTo("6.01");
                            assertThat(row.getRemainingBalance()).isZero();
                        });
        assertThat(service.effectiveMonthlyPayment(loan)).isEqualByComparingTo("6.01");
    }
}
