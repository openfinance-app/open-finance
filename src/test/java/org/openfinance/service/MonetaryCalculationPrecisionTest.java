package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.config.BusinessRulesProperties;
import org.openfinance.dto.calculator.CompoundInterestRequest;
import org.openfinance.dto.calculator.CompoundInterestResult;
import org.openfinance.dto.calculator.FreedomCalculatorRequest;
import org.openfinance.dto.calculator.FreedomCalculatorResponse;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Transaction;
import org.openfinance.util.InvestmentHoldings;

class MonetaryCalculationPrecisionTest {
    @Test
    void partialDisposalKeepsFractionalCostBasisAtTheSupportedInputLimit() {
        BigDecimal cost = new BigDecimal("12345678901234567890123456.123456789012345678");
        LocalDate date = LocalDate.of(2026, 1, 1);
        Asset asset =
                Asset.builder()
                        .id(1L)
                        .purchaseDate(date)
                        .currency("EUR")
                        .importOpeningQuantity(new BigDecimal("2"))
                        .importOpeningCost(cost)
                        .build();
        Transaction disposal =
                Transaction.builder()
                        .id(1L)
                        .assetId(1L)
                        .date(date)
                        .investmentQuantity(new BigDecimal("-1"))
                        .build();
        assertThat(InvestmentHoldings.at(asset, List.of(disposal), date).cost())
                .isEqualByComparingTo(cost.divide(new BigDecimal("2")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "JPY", "KWD", "BTC"})
    void projectionsRetainFractionalInterestRegardlessOfCurrency(String currency) {
        CompoundInterestResult result =
                new CompoundInterestService()
                        .calculate(
                                CompoundInterestRequest.builder()
                                        .principal(new BigDecimal("100.001"))
                                        .annualRate(new BigDecimal("1.4753"))
                                        .compoundingFrequency(1)
                                        .years(1)
                                        .currency(currency)
                                        .build());

        assertThat(result.getTotalInterest()).isEqualByComparingTo("1.475314753");
        assertThat(result.getFinalBalance()).isEqualByComparingTo("101.476314753");
        assertThat(result.getYearlyBreakdown().getFirst().getInterestEarned())
                .isEqualByComparingTo(result.getTotalInterest());
    }

    @Test
    void zeroYearProjectionPreservesTheRecordedPrincipal() {
        BigDecimal principal = new BigDecimal("12345678901234567890123456.123456789012345678");
        CompoundInterestResult result =
                new CompoundInterestService()
                        .calculate(
                                CompoundInterestRequest.builder()
                                        .principal(principal)
                                        .annualRate(BigDecimal.ZERO)
                                        .compoundingFrequency(12)
                                        .years(0)
                                        .currency("JPY")
                                        .build());
        assertThat(result.getPrincipal()).isEqualByComparingTo(principal);
        assertThat(result.getFinalBalance()).isEqualByComparingTo(principal);
    }

    @Test
    void freedomTargetAndPassiveIncomeDoNotRoundToCents() {
        BigDecimal monthlyExpenses = new BigDecimal("1.23456789");
        FreedomCalculatorResponse result =
                new FinancialFreedomService(new BusinessRulesProperties())
                        .calculateTimeToFreedom(
                                FreedomCalculatorRequest.builder()
                                        .currentSavings(new BigDecimal("1000"))
                                        .monthlyExpenses(monthlyExpenses)
                                        .monthlyContribution(BigDecimal.ZERO)
                                        .expectedAnnualReturn(new BigDecimal("5"))
                                        .withdrawalRate(new BigDecimal("3.7"))
                                        .projectionYears(1)
                                        .adjustForInflation(false)
                                        .build());
        assertThat(result.getTargetSavingsAmount())
                .isEqualByComparingTo(
                        monthlyExpenses
                                .multiply(new BigDecimal("1200"))
                                .divide(new BigDecimal("3.7"), 18, RoundingMode.HALF_UP));
        assertThat(result.getAnnualPassiveIncome().setScale(8, RoundingMode.HALF_UP))
                .isEqualByComparingTo("14.81481468");
    }
}
