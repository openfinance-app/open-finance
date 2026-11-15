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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityPrincipalAllocation;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.LiabilityPrincipalAllocationRepository;
import org.openfinance.repository.LiabilityTrancheRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tranche allocation respects ownership, posting dates and outstanding principal")
class LiabilityTrancheBoundaryTest {
    private static final LocalDate DATE = LocalDate.of(2024, 6, 15);
    @Mock LiabilityTrancheRepository tranches;
    @Mock LiabilityPrincipalAllocationRepository allocations;
    @InjectMocks LiabilityTrancheService service;
    private Liability loan;

    @BeforeEach
    void setup() {
        loan = new Liability();
        loan.setId(10L);
        loan.setUserId(3L);
        loan.setStartDate(DATE.minusYears(1));
        loan.setCurrentBalance("200");
    }

    @ParameterizedTest
    @CsvSource({"4,10", "3,11"})
    void foreignTranchesCannotBeDrawn(long owner, long liability) {
        LiabilityTranche tranche = tranche(1L, "100", DATE);
        tranche.setUserId(owner);
        tranche.setLiabilityId(liability);
        assertThatThrownBy(() -> service.draw(3L, loan, tranche, BigDecimal.ONE, DATE))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(tranches, allocations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.000000000000000001", "100.000000000000000001"})
    void invalidDrawAmountsNeverClaimTheTranche(String amount) {
        LiabilityTranche tranche = tranche(1L, "100", DATE);
        tranche.setStatus(TrancheStatus.PLANNED);
        assertThatThrownBy(() -> service.draw(3L, loan, tranche, new BigDecimal(amount), DATE))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(tranches, allocations);
    }

    @Test
    void aConcurrentDrawCannotBeReportedAsSuccessful() {
        LiabilityTranche tranche = tranche(1L, "100", DATE);
        tranche.setStatus(TrancheStatus.PLANNED);
        assertThatThrownBy(() -> service.draw(3L, loan, tranche, BigDecimal.TEN, DATE))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("already");
        assertThat(tranche.getStatus()).isEqualTo(TrancheStatus.PLANNED);
        verify(tranches, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "200.01"})
    void repaymentOutsideTheOutstandingBalanceDoesNotAllocate(String amount) {
        assertThatThrownBy(() -> service.allocateRepayment(3L, loan, tx(), new BigDecimal(amount)))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(tranches, allocations);
    }

    @Test
    void futureDrawsCannotFundAPastRepayment() {
        when(tranches.findByLiabilityIdAndUserId(10L, 3L))
                .thenReturn(List.of(tranche(1L, "150", DATE.plusDays(1))));
        assertThatThrownBy(
                        () ->
                                service.allocateRepayment(
                                        3L, loan, tx(), new BigDecimal("50.000000000000000001")))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("before it was drawn");
        verify(allocations, never()).save(any());
    }

    @Test
    void futureAndPlannedDrawsAreSkippedWhileOpeningDebtCanBeRepaid() {
        LiabilityTranche planned = tranche(2L, "100", DATE.minusDays(1));
        planned.setStatus(TrancheStatus.PLANNED);
        when(tranches.findByLiabilityIdAndUserId(10L, 3L))
                .thenReturn(List.of(tranche(1L, "150", DATE.plusDays(1)), planned));
        service.allocateRepayment(3L, loan, tx(), new BigDecimal("50"));
        ArgumentCaptor<LiabilityPrincipalAllocation> saved =
                ArgumentCaptor.forClass(LiabilityPrincipalAllocation.class);
        verify(allocations).save(saved.capture());
        assertThat(saved.getValue().getTrancheId()).isNull();
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("50");
    }

    @Test
    void legacyDrawWithoutADateRemainsRepayableAndMissingDrawAmountDoesNot() {
        LiabilityTranche legacy = tranche(1L, "100", null);
        LiabilityTranche incomplete = tranche(2L, null, DATE);
        when(tranches.findByLiabilityIdAndUserId(10L, 3L)).thenReturn(List.of(incomplete, legacy));
        service.allocateRepayment(3L, loan, tx(), new BigDecimal("50"));
        ArgumentCaptor<LiabilityPrincipalAllocation> saved =
                ArgumentCaptor.forClass(LiabilityPrincipalAllocation.class);
        verify(allocations).save(saved.capture());
        assertThat(saved.getValue().getTrancheId()).isEqualTo(1L);
        assertThat(service.remainingOf(incomplete)).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01"})
    void absentBalancesMeanZeroButNegativeBalancesCannotBeReconciled(String balance) {
        loan.setCurrentBalance(balance);
        if (balance != null && balance.startsWith("-"))
            assertThatThrownBy(() -> service.reconcile(loan))
                    .isInstanceOf(InvalidTransactionException.class);
        else {
            service.reconcile(loan);
            assertThat(loan.getCurrentBalance()).isEqualTo(balance);
        }
    }

    @Test
    void remainingAmountsRequireOwnershipAndDrawnTotalsExcludePlans() {
        LiabilityTranche drawn = tranche(1L, "100", DATE);
        LiabilityTranche planned = tranche(2L, "500", DATE);
        planned.setStatus(TrancheStatus.PLANNED);
        when(tranches.findByLiabilityIdAndUserId(10L, 3L))
                .thenReturn(List.of(drawn, planned, tranche(3L, null, DATE)));
        assertThat(service.sumDrawnOfDrawn(10L, 3L)).isEqualByComparingTo("100");
        drawn.setUserId(4L);
        assertThatThrownBy(() -> service.remainingByTrancheId(3L, List.of(drawn)))
                .isInstanceOf(InvalidTransactionException.class);
    }

    private Transaction tx() {
        return Transaction.builder().id(7L).date(DATE).build();
    }

    private LiabilityTranche tranche(Long id, String amount, LocalDate date) {
        return LiabilityTranche.builder()
                .id(id)
                .userId(3L)
                .liabilityId(10L)
                .trancheNo(id.intValue())
                .plannedAmount(new BigDecimal("100"))
                .drawnAmount(amount == null ? null : new BigDecimal(amount))
                .drawnDate(date)
                .status(TrancheStatus.DRAWN)
                .build();
    }
}
