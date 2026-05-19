package org.openfinance.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.calculator.CompoundInterestRequest;
import org.openfinance.dto.calculator.CompoundInterestResult;
import org.openfinance.dto.calculator.CompoundInterestYearlyBreakdown;
import org.springframework.stereotype.Service;

/**
 * Service for compound interest calculations.
 *
 * <p>Core formula (future value with regular contributions):
 *
 * <pre>
 *   FV = P × (1 + r/n)^(n×t)
 *      + PMT × [((1 + r/n)^(n×t) - 1) / (r/n)]  — end-of-period contributions
 *      + PMT × [((1 + r/n)^(n×t) - 1) / (r/n)] × (1 + r/n)  — beginning-of-period
 * </pre>
 *
 * Where:
 *
 * <ul>
 *   <li>P = principal
 *   <li>r = annual rate (decimal)
 *   <li>n = compounding periods per year
 *   <li>t = years
 *   <li>PMT = regular contribution per period
 * </ul>
 */
@Service
@Slf4j
public class CompoundInterestService {

    private static final int SCALE = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final MathContext MC = new MathContext(20, ROUNDING);

    /**
     * Calculate compound interest for the given request.
     *
     * @param request validated input parameters
     * @return full result including year-by-year breakdown
     */
    public CompoundInterestResult calculate(CompoundInterestRequest request) {
        log.info(
                "Calculating compound interest: principal={}, rate={}%, freq={}, years={}",
                request.getPrincipal(),
                request.getAnnualRate(),
                request.getCompoundingFrequency(),
                request.getYears());

        BigDecimal principal = request.getPrincipal();
        BigDecimal annualRate =
                request.getAnnualRate().divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
        int n = request.getCompoundingFrequency();
        int years = request.getYears();
        BigDecimal pmt =
                request.getRegularContribution() != null
                        ? request.getRegularContribution()
                        : BigDecimal.ZERO;

        if (years == 0) {
            CompoundInterestYearlyBreakdown yearZero =
                    CompoundInterestYearlyBreakdown.builder()
                            .year(0)
                            .startingBalance(principal.setScale(2, ROUNDING))
                            .contributions(BigDecimal.ZERO.setScale(2, ROUNDING))
                            .interestEarned(BigDecimal.ZERO.setScale(2, ROUNDING))
                            .endingBalance(principal.setScale(2, ROUNDING))
                            .cumulativeInterest(BigDecimal.ZERO.setScale(2, ROUNDING))
                            .cumulativePrincipal(principal.setScale(2, ROUNDING))
                            .build();
            return CompoundInterestResult.builder()
                    .finalBalance(principal.setScale(2, ROUNDING))
                    .principal(principal.setScale(2, ROUNDING))
                    .totalContributions(BigDecimal.ZERO.setScale(2, ROUNDING))
                    .totalInterest(BigDecimal.ZERO.setScale(2, ROUNDING))
                    .totalInvested(principal.setScale(2, ROUNDING))
                    .effectiveAnnualRate(BigDecimal.ZERO.setScale(4, ROUNDING))
                    .yearlyBreakdown(List.of(yearZero))
                    .build();
        }

        boolean atBeginning = request.isContributionAtBeginning();

        BigDecimal rPerPeriod = annualRate.divide(BigDecimal.valueOf(n), SCALE, ROUNDING);

        List<CompoundInterestYearlyBreakdown> breakdown =
                buildYearlyBreakdown(principal, rPerPeriod, n, years, pmt, atBeginning);

        BigDecimal finalBalance = breakdown.get(breakdown.size() - 1).getEndingBalance();
        BigDecimal totalContributions =
                pmt.multiply(BigDecimal.valueOf((long) n * years)).setScale(2, ROUNDING);
        BigDecimal totalInvested = principal.add(totalContributions).setScale(2, ROUNDING);
        BigDecimal totalInterest = finalBalance.subtract(totalInvested).setScale(2, ROUNDING);

        // Effective Annual Rate = (1 + r/n)^n - 1
        BigDecimal effectiveAnnualRate = computeEffectiveAnnualRate(annualRate, n);

        CompoundInterestResult result =
                CompoundInterestResult.builder()
                        .finalBalance(finalBalance.setScale(2, ROUNDING))
                        .principal(principal.setScale(2, ROUNDING))
                        .totalContributions(totalContributions)
                        .totalInterest(totalInterest)
                        .totalInvested(totalInvested)
                        .effectiveAnnualRate(effectiveAnnualRate)
                        .yearlyBreakdown(breakdown)
                        .build();

        log.info(
                "Compound interest calculated: finalBalance={}, totalInterest={}",
                result.getFinalBalance(),
                result.getTotalInterest());

        return result;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Build year-by-year breakdown using iterative period-level simulation. This approach avoids
     * precision issues of a single closed-form calculation and produces accurate per-year figures.
     */
    private List<CompoundInterestYearlyBreakdown> buildYearlyBreakdown(
            BigDecimal principal,
            BigDecimal rPerPeriod,
            int n,
            int years,
            BigDecimal pmt,
            boolean atBeginning) {

        List<CompoundInterestYearlyBreakdown> breakdown = new ArrayList<>(years);

        BigDecimal balance = principal;
        BigDecimal cumulativeInterest = BigDecimal.ZERO;
        BigDecimal cumulativePrincipalAndContributions = principal;

        for (int year = 1; year <= years; year++) {
            BigDecimal startingBalance = balance;
            BigDecimal yearlyInterest = BigDecimal.ZERO;
            BigDecimal yearlyContributions = BigDecimal.ZERO;

            for (int period = 0; period < n; period++) {
                if (atBeginning) {
                    balance = balance.add(pmt);
                    yearlyContributions = yearlyContributions.add(pmt);
                }

                BigDecimal interest = balance.multiply(rPerPeriod, MC);
                balance = balance.add(interest);
                yearlyInterest = yearlyInterest.add(interest);

                if (!atBeginning) {
                    balance = balance.add(pmt);
                    yearlyContributions = yearlyContributions.add(pmt);
                }
            }

            cumulativeInterest = cumulativeInterest.add(yearlyInterest);
            cumulativePrincipalAndContributions =
                    cumulativePrincipalAndContributions.add(yearlyContributions);

            breakdown.add(
                    CompoundInterestYearlyBreakdown.builder()
                            .year(year)
                            .startingBalance(startingBalance.setScale(2, ROUNDING))
                            .contributions(yearlyContributions.setScale(2, ROUNDING))
                            .interestEarned(yearlyInterest.setScale(2, ROUNDING))
                            .endingBalance(balance.setScale(2, ROUNDING))
                            .cumulativeInterest(cumulativeInterest.setScale(2, ROUNDING))
                            .cumulativePrincipal(
                                    cumulativePrincipalAndContributions.setScale(2, ROUNDING))
                            .build());
        }

        return breakdown;
    }

    /** Compute EAR = (1 + r/n)^n - 1, returned as a percentage rounded to 4 decimal places. */
    private BigDecimal computeEffectiveAnnualRate(BigDecimal annualRate, int n) {
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rPerPeriod = annualRate.divide(BigDecimal.valueOf(n), SCALE, ROUNDING);
        BigDecimal onePlusR = BigDecimal.ONE.add(rPerPeriod);
        BigDecimal ear = onePlusR.pow(n, MC).subtract(BigDecimal.ONE);
        // Convert back to percentage
        return ear.multiply(BigDecimal.valueOf(100)).setScale(4, ROUNDING);
    }
}
