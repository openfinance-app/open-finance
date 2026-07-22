package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AccountResponse;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.repository.InterestRateVariationRepository;

/**
 * Unit tests for {@link InterestCalculatorService#calculateInterestEstimate}.
 *
 * <p>The expected values are hand-computed from the compound-interest formula {@code A = P × (1 +
 * r/n)^(n·t) − P} with {@code t = 1} year, so the test is independent of the internal arithmetic
 * type (double vs BigDecimal). It pins the observable 2-decimal result while the implementation is
 * migrated from {@code double} to {@code BigDecimal}.
 */
@ExtendWith(MockitoExtension.class)
class InterestCalculatorServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock private InterestRateVariationRepository variationRepository;
    @Mock private AccountService accountService;

    @InjectMocks private InterestCalculatorService service;

    private void givenAccount(BigDecimal balance, InterestPeriod period) {
        AccountResponse account =
                AccountResponse.builder()
                        .balance(balance)
                        .isInterestEnabled(true)
                        .interestPeriod(period)
                        .build();
        when(accountService.getAccountById(ACCOUNT_ID, USER_ID)).thenReturn(account);
    }

    private void givenRate(String ratePct, String taxPct) {
        InterestRateVariation variation =
                InterestRateVariation.builder()
                        .accountId(ACCOUNT_ID)
                        .rate(new BigDecimal(ratePct))
                        .taxRate(new BigDecimal(taxPct))
                        .validFrom(LocalDate.now().minusYears(1))
                        .build();
        when(variationRepository.findByAccountIdOrderByValidFromDesc(ACCOUNT_ID))
                .thenReturn(List.of(variation));
    }

    @Test
    @DisplayName("Annual compounding, no tax: 5% on 10,000 = 500.00")
    void annualNoTax() {
        givenAccount(new BigDecimal("10000"), InterestPeriod.ANNUAL);
        givenRate("5", "0");

        assertThat(service.calculateInterestEstimate(ACCOUNT_ID, USER_ID, "1Y"))
                .isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Monthly compounding, no tax: 12% on 10,000 = 1268.25")
    void monthlyCompoundingNoTax() {
        givenAccount(new BigDecimal("10000"), InterestPeriod.MONTHLY);
        givenRate("12", "0");

        // (1 + 0.12/12)^12 − 1 = 1.01^12 − 1 = 0.12682503... × 10000 = 1268.2503 → 1268.25
        assertThat(service.calculateInterestEstimate(ACCOUNT_ID, USER_ID, "1Y"))
                .isEqualByComparingTo("1268.25");
    }

    @Test
    @DisplayName("Annual compounding with 30% tax: net 5% on 10,000 = 350.00")
    void annualWithTax() {
        givenAccount(new BigDecimal("10000"), InterestPeriod.ANNUAL);
        givenRate("5", "30");

        assertThat(service.calculateInterestEstimate(ACCOUNT_ID, USER_ID, "1Y"))
                .isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("Zero balance yields zero interest")
    void zeroBalance() {
        givenAccount(BigDecimal.ZERO, InterestPeriod.ANNUAL);

        assertThat(service.calculateInterestEstimate(ACCOUNT_ID, USER_ID, "1Y"))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Interest-disabled account yields zero interest")
    void interestDisabled() {
        AccountResponse account =
                AccountResponse.builder()
                        .balance(new BigDecimal("10000"))
                        .isInterestEnabled(false)
                        .interestPeriod(InterestPeriod.ANNUAL)
                        .build();
        when(accountService.getAccountById(ACCOUNT_ID, USER_ID)).thenReturn(account);

        assertThat(service.calculateInterestEstimate(ACCOUNT_ID, USER_ID, "1Y"))
                .isEqualByComparingTo("0");
    }
}
