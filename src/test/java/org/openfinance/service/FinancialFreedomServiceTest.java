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
