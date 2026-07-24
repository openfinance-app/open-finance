package org.openfinance.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
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
        @DisplayName(
                "Not-achievable message formats the target amount without a hardcoded currency symbol")
        void shouldFormatNotAchievableMessageWithoutHardcodedCurrencySymbol() {
            // Huge target relative to tiny savings/contribution/return — unreachable within 50
            // years
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(BigDecimal.ZERO)
                            .monthlyExpenses(new BigDecimal("100000"))
                            .expectedAnnualReturn(new BigDecimal("0.01"))
                            .monthlyContribution(new BigDecimal("1"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            assertFalse(response.isAchievable());
            assertNotNull(response.getMessage());
            // No hardcoded currency symbol — the caller/frontend applies the user's actual currency
            assertFalse(response.getMessage().contains("€"));
            assertFalse(response.getMessage().contains("$"));
            // Target amount is still rendered with thousands separators, e.g. "30,000,000.00"
            assertTrue(response.getMessage().contains("30,000,000.00"));
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

        @Test
        @DisplayName("progressPercentage and sensitivity scenario returnRate are exact BigDecimal")
        void shouldComputeProgressAndSensitivityRatesInBigDecimal() {
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("375000"))
                            .monthlyExpenses(new BigDecimal("2500"))
                            .expectedAnnualReturn(new BigDecimal("7"))
                            .monthlyContribution(new BigDecimal("500"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            // Target = 30,000 / 0.04 = 750,000; progress = 375,000 / 750,000 * 100 = 50%
            assertEquals(0, new BigDecimal("50").compareTo(response.getProgressPercentage()));

            List<SensitivityScenario> scenarios = response.getSensitivityScenarios();
            SensitivityScenario baseline =
                    scenarios.stream()
                            .filter(
                                    s ->
                                            s.getScenarioType()
                                                    == SensitivityScenario.ScenarioType.BASELINE)
                            .findFirst()
                            .orElseThrow();
            assertEquals(0, new BigDecimal("7").compareTo(baseline.getReturnRate()));

            SensitivityScenario pessimistic =
                    scenarios.stream()
                            .filter(
                                    s ->
                                            s.getScenarioType()
                                                    == SensitivityScenario.ScenarioType.PESSIMISTIC)
                            .findFirst()
                            .orElseThrow();
            assertEquals(0, new BigDecimal("5").compareTo(pessimistic.getReturnRate()));

            SensitivityScenario optimistic =
                    scenarios.stream()
                            .filter(
                                    s ->
                                            s.getScenarioType()
                                                    == SensitivityScenario.ScenarioType.OPTIMISTIC)
                            .findFirst()
                            .orElseThrow();
            assertEquals(0, new BigDecimal("9").compareTo(optimistic.getReturnRate()));
        }

        @Test
        @DisplayName("Pessimistic sensitivity scenario is floored at -5%")
        void shouldFloorPessimisticScenarioAtMinusFivePercent() {
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("1000"))
                            .monthlyExpenses(new BigDecimal("100"))
                            .expectedAnnualReturn(new BigDecimal("-4"))
                            .monthlyContribution(new BigDecimal("50"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            SensitivityScenario pessimistic =
                    response.getSensitivityScenarios().stream()
                            .filter(
                                    s ->
                                            s.getScenarioType()
                                                    == SensitivityScenario.ScenarioType.PESSIMISTIC)
                            .findFirst()
                            .orElseThrow();

            // base(-4%) - 2% = -6%, floored to -5%
            assertEquals(0, new BigDecimal("-5").compareTo(pessimistic.getReturnRate()));
        }

        @Test
        @DisplayName("Optimistic sensitivity scenario is capped at 15%")
        void shouldCapOptimisticScenarioAtFifteenPercent() {
            FreedomCalculatorRequest request =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("1000"))
                            .monthlyExpenses(new BigDecimal("100"))
                            .expectedAnnualReturn(new BigDecimal("14"))
                            .monthlyContribution(new BigDecimal("50"))
                            .withdrawalRate(new BigDecimal("4"))
                            .build();

            FreedomCalculatorResponse response = service.calculateTimeToFreedom(request);

            SensitivityScenario optimistic =
                    response.getSensitivityScenarios().stream()
                            .filter(
                                    s ->
                                            s.getScenarioType()
                                                    == SensitivityScenario.ScenarioType.OPTIMISTIC)
                            .findFirst()
                            .orElseThrow();

            // base(14%) + 2% = 16%, capped to 15%
            assertEquals(0, new BigDecimal("15").compareTo(optimistic.getReturnRate()));
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
            assertEquals(0, new BigDecimal("4.0").compareTo(defaults.getDefaultWithdrawalRate()));
            assertEquals(0, new BigDecimal("2.5").compareTo(defaults.getDefaultInflationRate()));
        }
    }
}
