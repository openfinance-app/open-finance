package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.LiabilityTrancheRequest;
import org.openfinance.dto.LiabilityTrancheResponse;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.RealEstateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Drawn loan tranches cannot rewrite recorded funding terms")
class LiabilityTrancheUpdatesTest {
    @Mock private LiabilityTrancheRepository tranches;
    @Mock private RealEstateRepository properties;
    @Mock private LiabilityTrancheService trancheService;
    @InjectMocks private LiabilityService service;
    private LiabilityTranche tranche;
    private static final LocalDate DATE = LocalDate.of(2024, 1, 10);

    @BeforeEach
    void setUp() {
        tranche =
                LiabilityTranche.builder()
                        .id(7L)
                        .liabilityId(3L)
                        .userId(1L)
                        .trancheNo(1)
                        .status(TrancheStatus.DRAWN)
                        .plannedAmount(new BigDecimal("100"))
                        .drawnAmount(new BigDecimal("100"))
                        .plannedDate(DATE)
                        .drawnDate(DATE)
                        .fee(new BigDecimal("5"))
                        .interestOnly(true)
                        .interestOnlyUntil(DATE.plusMonths(6))
                        .notes("Original")
                        .currency("USD")
                        .build();
        when(tranches.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(tranche));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "AMOUNT",
                "DATE",
                "FEE",
                "INTEREST_ONLY",
                "INTEREST_END",
                "STATUS",
                "PROPERTY",
                "CURRENCY"
            })
    void rejectsEveryIndependentChangeToDrawnTerms(String field) {
        LiabilityTrancheRequest request = new LiabilityTrancheRequest();
        switch (field) {
            case "AMOUNT" -> request.setPlannedAmount(new BigDecimal("100.000000000000000001"));
            case "DATE" -> request.setPlannedDate(DATE.plusDays(1));
            case "FEE" -> request.setFee(new BigDecimal("5.01"));
            case "INTEREST_ONLY" -> request.setInterestOnly(false);
            case "INTEREST_END" -> request.setInterestOnlyUntil(DATE.plusMonths(7));
            case "STATUS" -> request.setStatus(TrancheStatus.CANCELLED);
            case "PROPERTY" -> {
                request.setRealEstateId(9L);
                when(properties.findByIdAndUserId(9L, 1L))
                        .thenReturn(Optional.of(RealEstateProperty.builder().build()));
            }
            case "CURRENCY" -> request.setCurrency("EUR");
            default -> throw new AssertionError(field);
        }
        assertThatThrownBy(() -> service.updateTranche(1L, 7L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verify(tranches, never()).save(any());
        assertThat(tranche.getStatus()).isEqualTo(TrancheStatus.DRAWN);
        assertThat(tranche.getDrawnAmount()).isEqualByComparingTo("100");
    }

    @Test
    void acceptsUnchangedDrawnTermsRegardlessOfDecimalScaleAndAllowsClearingNotes() {
        tranche.setRealEstateId(9L);
        when(properties.findByIdAndUserId(9L, 1L))
                .thenReturn(Optional.of(RealEstateProperty.builder().build()));
        when(tranches.save(tranche)).thenReturn(tranche);
        when(trancheService.remainingOf(tranche)).thenReturn(new BigDecimal("75"));
        LiabilityTrancheRequest request =
                LiabilityTrancheRequest.builder()
                        .plannedAmount(new BigDecimal("100.0000"))
                        .plannedDate(DATE)
                        .fee(new BigDecimal("5.00"))
                        .interestOnly(true)
                        .interestOnlyUntil(DATE.plusMonths(6))
                        .status(TrancheStatus.DRAWN)
                        .realEstateId(9L)
                        .currency("usd")
                        .notes("")
                        .build();
        LiabilityTrancheResponse response = service.updateTranche(1L, 7L, request);
        assertThat(response.getNotes()).isEmpty();
        assertThat(response.getRemaining()).isEqualByComparingTo("75");
        assertThat(response.getRealEstateId()).isEqualTo(9L);
        assertThat(response.getPlannedAmount()).isEqualByComparingTo("100");
    }

    @Test
    void omittedDrawnTermsLeaveExistingValuesUntouched() {
        when(tranches.save(tranche)).thenReturn(tranche);
        LiabilityTrancheResponse response =
                service.updateTranche(1L, 7L, new LiabilityTrancheRequest());
        assertThat(response.getNotes()).isEqualTo("Original");
        assertThat(response.getPlannedDate()).isEqualTo(DATE);
        assertThat(response.getInterestOnly()).isTrue();
        assertThat(response.getFee()).isEqualByComparingTo("5");
    }

    @ParameterizedTest
    @EnumSource(
            value = TrancheStatus.class,
            names = {"PLANNED", "CANCELLED"})
    void undrawnTranchesCanChangePlansAndToggleCancellation(TrancheStatus from) {
        tranche.setStatus(from);
        tranche.setDrawnAmount(null);
        tranche.setDrawnDate(null);
        when(tranches.save(tranche)).thenReturn(tranche);
        TrancheStatus to =
                from == TrancheStatus.PLANNED ? TrancheStatus.CANCELLED : TrancheStatus.PLANNED;
        LiabilityTrancheRequest request =
                LiabilityTrancheRequest.builder()
                        .plannedAmount(new BigDecimal("200"))
                        .plannedDate(DATE.plusMonths(1))
                        .fee(BigDecimal.ZERO)
                        .interestOnly(false)
                        .interestOnlyUntil(DATE.plusMonths(12))
                        .notes("Revised plan")
                        .status(to)
                        .build();
        LiabilityTrancheResponse response = service.updateTranche(1L, 7L, request);
        assertThat(response.getStatus()).isEqualTo(to);
        assertThat(response.getPlannedAmount()).isEqualByComparingTo("200");
        assertThat(response.getFee()).isZero();
        assertThat(response.getInterestOnly()).isFalse();
        assertThat(response.getInterestOnlyUntil()).isEqualTo(DATE.plusMonths(12));
        assertThat(response.getDrawnAmount()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = TrancheStatus.class,
            names = {"DRAWN"})
    void undrawnTrancheCannotPretendThatFundingWasPosted(TrancheStatus to) {
        tranche.setStatus(TrancheStatus.PLANNED);
        assertThatThrownBy(
                        () ->
                                service.updateTranche(
                                        1L,
                                        7L,
                                        LiabilityTrancheRequest.builder().status(to).build()))
                .isInstanceOf(InvalidTransactionException.class);
        verify(tranches, never()).save(any());
    }
}
