package org.openfinance.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.calculator.*;

/** Unit tests for FinancialFreedomService. */
class FinancialFreedomServiceTest {

    private FinancialFreedomService service;

    @BeforeEach
    void setUp() {
        service = new FinancialFreedomService();
    }

    @Nested
    @DisplayName("calculateTimeToFreedom tests")
    class CalculateTimeToFreedomTests {

        @Test
        @DisplayName("Should calculate freedom timeline correctly")
        void shouldCalculateFreedomTimeline() {
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("50000"))
                            .monthlyExpenses(new BigDecimal("2500"))
                            .expectedAnnualReturn(new BigDecimal("7"))
                            .monthlyContribution(new BigDecimal("500"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            assertNotNull(response);
            assertTrue(response.getYearsToFreedom() >= 0);
            assertNotNull(response.getTargetSavingsAmount());
        }

        @Test
        @DisplayName("Should calculate target amount using 4% rule")
        void shouldCalculateTargetAmountCorrectly() {
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(BigDecimal.ZERO)
                            .monthlyExpenses(new BigDecimal("2500"))
                            .expectedAnnualReturn(new BigDecimal("7"))
                            .monthlyContribution(new BigDecimal("1000"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            // Target = €30,000 / 0.04 = €750,000
            assertEquals(new BigDecimal("750000.00"), response.getTargetSavingsAmount());
        }

        @Test
        @DisplayName("Should handle zero savings with contributions")
        void shouldHandleZeroSavingsWithContributions() {
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(BigDecimal.ZERO)
                            .monthlyExpenses(new BigDecimal("2000"))
                            .expectedAnnualReturn(new BigDecimal("7"))
                            .monthlyContribution(new BigDecimal("1000"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            assertNotNull(response);
            assertTrue(response.getYearsToFreedom() > 0);
        }

        @Test
        @DisplayName("Yearly projections compound in BigDecimal (10% on 100k, no contributions)")
        void shouldGenerateAnnualProjectionsExactly() {
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("100000"))
                            .monthlyExpenses(new BigDecimal("1000"))
                            .expectedAnnualReturn(new BigDecimal("10"))
                            .monthlyContribution(BigDecimal.ZERO)
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            // 100000 →(+10%) 110000 →(+10%) 121000 →(+10%) 133100
            assertEquals(
                    0,
                    response.getYearlyProjections()
                            .get(0)
                            .getEndingBalance()
                            .compareTo(new BigDecimal("110000")));
            assertEquals(
                    0,
                    response.getYearlyProjections()
                            .get(1)
                            .getEndingBalance()
                            .compareTo(new BigDecimal("121000")));
            assertEquals(
                    0,
                    response.getYearlyProjections()
                            .get(2)
                            .getEndingBalance()
                            .compareTo(new BigDecimal("133100")));
            assertEquals(
                    0,
                    response.getYearlyProjections()
                            .get(0)
                            .getInvestmentReturns()
                            .compareTo(new BigDecimal("10000")));
        }

        @Test
        @DisplayName("Months-to-target grows linearly by contribution at 0% return")
        void shouldComputeMonthsToTargetLinearlyAtZeroReturn() {
            // target = (40 × 12) / 0.04 = 12000; 1000/month at 0% return → exactly 12 months
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(BigDecimal.ZERO)
                            .monthlyExpenses(new BigDecimal("40"))
                            .expectedAnnualReturn(BigDecimal.ZERO)
                            .monthlyContribution(new BigDecimal("1000"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            assertEquals(1, response.getYearsToFreedom());
            assertEquals(0, response.getMonthsToFreedom());
        }
    }

    @Nested
    @DisplayName("calculateSavingsLongevity tests")
    class CalculateSavingsLongevityTests {

        @Test
        @DisplayName("Should calculate longevity correctly")
        void shouldCalculateLongevityCorrectly() {
            SavingsLongevityResult result =
                    service.calculateSavingsLongevity(
                            new BigDecimal("100000"), new BigDecimal("2500"), new BigDecimal("5"));

            assertNotNull(result);
            assertTrue(result.getYearsUntilDepletion() > 0);
            assertFalse(result.isInfinite());
        }

        @Test
        @DisplayName("Should detect infinite sustainability")
        void shouldDetectInfiniteSustainability() {
            // €1M savings at 5% = €4,167/month returns > €2,500 expenses
            SavingsLongevityResult result =
                    service.calculateSavingsLongevity(
                            new BigDecimal("1000000"), new BigDecimal("2500"), new BigDecimal("5"));

            assertTrue(result.isInfinite());
            assertNull(result.getDepletionYear());
        }
    }

    @Nested
    @DisplayName("getDefaults tests")
    class GetDefaultsTests {

        @Test
        @DisplayName("Should return valid defaults")
        void shouldReturnValidDefaults() {
            CalculationDefaults defaults = service.getDefaults();

            assertNotNull(defaults);
            assertEquals(4.0, defaults.getDefaultWithdrawalRate());
            assertEquals(2.5, defaults.getDefaultInflationRate());
        }
    }
}
