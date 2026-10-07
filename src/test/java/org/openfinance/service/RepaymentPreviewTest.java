package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.openfinance.dto.RepaymentPreviewResponse;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;

/**
 * Unit tests for the repayment auto-split preview (Task 5).
 *
 * <p>Covers {@link LiabilityService#getRepaymentPreview}: standard P&I split, insurance percentage,
 * interest-only tranches, zero rate, and missing (null) rate/insurance fields.
 */
@ExtendWith(MockitoExtension.class)
class RepaymentPreviewTest {

    @Mock private LiabilityRepository liabilityRepository;

    @Mock private LiabilityTrancheRepository liabilityTrancheRepository;

    @Mock private ExchangeRateService exchangeRateService;
    @Mock private NetWorthService netWorthService;

    @InjectMocks private LiabilityService liabilityService;

    private static final Long USER_ID = 1L;
    private static final Long LIABILITY_ID = 100L;
    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    private Liability liability(
            String balance, String rate, String principal, String insurancePct) {
        Liability liability = new Liability();
        liability.setId(LIABILITY_ID);
        liability.setUserId(USER_ID);
        liability.setName("Home Mortgage");
        liability.setType(LiabilityType.MORTGAGE);
        liability.setPrincipal(principal);
        liability.setCurrentBalance(balance);
        liability.setInterestRate(rate);
        liability.setInsurancePercentage(insurancePct);
        liability.setCurrency("USD");
        liability.setStartDate(LocalDate.of(2024, 1, 1));
        return liability;
    }

    private void givenLiability(Liability liability, List<LiabilityTranche> tranches) {
        org.mockito.Mockito.lenient()
                .when(
                        netWorthService.getLiabilityBalanceAt(
                                org.mockito.ArgumentMatchers.eq(liability),
                                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(new BigDecimal(liability.getCurrentBalance()));
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liability));
        when(liabilityTrancheRepository.findByLiabilityIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(tranches);
    }

    private LiabilityTranche interestOnlyTranche(LocalDate interestOnlyUntil) {
        return interestOnlyTranche(interestOnlyUntil, TrancheStatus.DRAWN);
    }

    private LiabilityTranche interestOnlyTranche(
            LocalDate interestOnlyUntil, TrancheStatus status) {
        return LiabilityTranche.builder()
                .id(5L)
                .userId(USER_ID)
                .liabilityId(LIABILITY_ID)
                .trancheNo(1)
                .plannedAmount(new BigDecimal("50000.00"))
                .interestOnly(true)
                .interestOnlyUntil(interestOnlyUntil)
                .status(status)
                .currency("USD")
                .build();
    }

    @Test
    void retainsThreeDecimalRepaymentAndInterest() {
        Liability loan = liability("12.345", "1.2", "12.345", null);
        loan.setCurrency("KWD");
        givenLiability(loan, List.of());
        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1.234"), DATE);
        assertThat(preview.getInterest()).isEqualByComparingTo("0.012");
        assertThat(preview.getPrincipal()).isEqualByComparingTo("1.222");
        assertThat(preview.getTotal()).isEqualByComparingTo("1.234");
    }

    // ---------- (a) Standard P&I ----------

    @Test
    @DisplayName("Standard P&I: interest = balance × rate / 1200, principal = total − interest")
    void standardPrincipalAndInterestSplit() {
        givenLiability(liability("50000.00", "5.25", "50000.00", null), List.of());

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE);

        assertThat(preview.getTotal()).isEqualByComparingTo("1200.00");
        assertThat(preview.getInterest()).isEqualByComparingTo("218.75");
        assertThat(preview.getPrincipal()).isEqualByComparingTo("981.25");
        assertThat(preview.getInsurance()).isEqualByComparingTo("0.00");
        assertThat(preview.getInterestOnly()).isFalse();
    }

    // ---------- (b) With insurance percentage ----------

    @Test
    @DisplayName(
            "Insurance: insurance = principal × pct / 1200, principal = total − interest − insurance")
    void insurancePercentageIsDeductedFromPrincipal() {
        givenLiability(liability("50000.00", "5.25", "50000.00", "0.5"), List.of());

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE);

        assertThat(preview.getInterest()).isEqualByComparingTo("218.75");
        assertThat(preview.getInsurance()).isEqualByComparingTo("20.83");
        assertThat(preview.getPrincipal()).isEqualByComparingTo("960.42");
        assertThat(preview.getInterestOnly()).isFalse();
    }

    // ---------- (c) Interest-only tranche ----------

    @Test
    @DisplayName("Active interest-only tranche (interestOnlyUntil ≥ date) yields zero principal")
    void interestOnlyTrancheActiveYieldsZeroPrincipal() {
        givenLiability(
                liability("50000.00", "5.25", "50000.00", "0.5"),
                List.of(interestOnlyTranche(DATE.plusMonths(6))));

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("239.58"), DATE);

        assertThat(preview.getInterestOnly()).isTrue();
        assertThat(preview.getPrincipal()).isEqualByComparingTo("0.00");
        assertThat(preview.getInterest()).isEqualByComparingTo("218.75");
        assertThat(preview.getInsurance()).isEqualByComparingTo("20.83");
    }

    @Test
    @DisplayName("Boundary: interestOnlyUntil == date still yields zero principal")
    void interestOnlyUntilEqualToDateYieldsZeroPrincipal() {
        givenLiability(
                liability("50000.00", "5.25", "50000.00", "0.5"),
                List.of(interestOnlyTranche(DATE)));

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("239.58"), DATE);

        assertThat(preview.getInterestOnly()).isTrue();
        assertThat(preview.getPrincipal()).isEqualByComparingTo("0.00");
        assertThat(preview.getInterest()).isEqualByComparingTo("218.75");
    }

    @Test
    @DisplayName("PLANNED interest-only tranche does not suppress principal")
    void plannedInterestOnlyTrancheIsIgnored() {
        givenLiability(
                liability("50000.00", "5.25", "50000.00", "0.5"),
                List.of(interestOnlyTranche(DATE.plusMonths(6), TrancheStatus.PLANNED)));

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE);

        assertThat(preview.getInterestOnly()).isFalse();
        assertThat(preview.getPrincipal()).isEqualByComparingTo("960.42");
    }

    @Test
    @DisplayName("CANCELLED interest-only tranche does not suppress principal")
    void cancelledInterestOnlyTrancheIsIgnored() {
        givenLiability(
                liability("50000.00", "5.25", "50000.00", "0.5"),
                List.of(interestOnlyTranche(DATE.plusMonths(6), TrancheStatus.CANCELLED)));

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE);

        assertThat(preview.getInterestOnly()).isFalse();
        assertThat(preview.getPrincipal()).isEqualByComparingTo("960.42");
    }

    @Test
    @DisplayName("Expired interest-only tranche (interestOnlyUntil < date) is ignored")
    void expiredInterestOnlyTrancheIsIgnored() {
        givenLiability(
                liability("50000.00", "5.25", "50000.00", "0.5"),
                List.of(interestOnlyTranche(DATE.minusDays(1))));

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE);

        assertThat(preview.getInterestOnly()).isFalse();
        assertThat(preview.getPrincipal()).isEqualByComparingTo("960.42");
    }

    // ---------- (d) Zero rate ----------

    @Test
    @DisplayName("Zero rate: interest is 0 and principal = total − insurance")
    void zeroRateYieldsZeroInterest() {
        givenLiability(liability("50000.00", "0", "50000.00", "0.5"), List.of());

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE);

        assertThat(preview.getInterest()).isEqualByComparingTo("0.00");
        assertThat(preview.getInsurance()).isEqualByComparingTo("20.83");
        assertThat(preview.getPrincipal()).isEqualByComparingTo("1179.17");
    }

    // ---------- (e) Missing rate / insurance ----------

    @Test
    @DisplayName("Missing rate and insurance are treated as zero: principal = total")
    void missingRateAndInsuranceTreatedAsZero() {
        Liability liability = liability("50000.00", null, "50000.00", null);
        givenLiability(liability, List.of());

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE);

        assertThat(preview.getInterest()).isEqualByComparingTo("0.00");
        assertThat(preview.getInsurance()).isEqualByComparingTo("0.00");
        assertThat(preview.getPrincipal()).isEqualByComparingTo("1200.00");
        assertThat(preview.getInterestOnly()).isFalse();
    }

    // ---------- (f) FX preview (Task 9) ----------

    @Test
    @DisplayName("FX preview: an input-currency total is converted to the liability currency first")
    void fxPreviewConvertsInputCurrencyTotalFirst() {
        givenLiability(liability("50000.00", "5.25", "50000.00", null), List.of());
        when(exchangeRateService.convert(new BigDecimal("1200.00"), "EUR", "USD", DATE))
                .thenReturn(new BigDecimal("1310.04"));

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE, "EUR");

        assertThat(preview.getTotal()).isEqualByComparingTo("1310.04");
        assertThat(preview.getInterest()).isEqualByComparingTo("218.75");
        assertThat(preview.getPrincipal()).isEqualByComparingTo("1091.29");
        assertThat(preview.getInsurance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("FX preview with input currency equal to the liability currency skips conversion")
    void fxPreviewMatchingInputCurrencySkipsConversion() {
        givenLiability(liability("50000.00", "5.25", "50000.00", null), List.of());

        RepaymentPreviewResponse preview =
                liabilityService.getRepaymentPreview(
                        USER_ID, LIABILITY_ID, new BigDecimal("1200.00"), DATE, "USD");

        assertThat(preview.getTotal()).isEqualByComparingTo("1200.00");
        assertThat(preview.getPrincipal()).isEqualByComparingTo("981.25");
    }

    @Test
    @DisplayName(
            "FX preview with a missing exchange rate throws a domain exception instead of a 500")
    void fxPreviewMissingRateThrowsDomainException() {
        when(liabilityRepository.findByIdAndUserId(LIABILITY_ID, USER_ID))
                .thenReturn(Optional.of(liability("50000.00", "5.25", "50000.00", null)));
        when(exchangeRateService.convert(new BigDecimal("1200.00"), "EUR", "USD", DATE))
                .thenThrow(
                        new IllegalStateException(
                                "No exchange rate available for EUR → USD on latest"));

        assertThatThrownBy(
                        () ->
                                liabilityService.getRepaymentPreview(
                                        USER_ID,
                                        LIABILITY_ID,
                                        new BigDecimal("1200.00"),
                                        DATE,
                                        "EUR"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");
    }
}
