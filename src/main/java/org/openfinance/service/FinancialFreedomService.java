package org.openfinance.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.calculator.*;
import org.openfinance.exception.CalculationValidationException;
import org.springframework.stereotype.Service;

/**
 * Service for financial freedom and savings longevity calculations.
 *
 * <p>Provides comprehensive financial planning calculations including:
 *
 * <ul>
 *   <li><strong>Time to Financial Freedom:</strong> Calculates how long until savings can sustain
 *       lifestyle indefinitely based on the 4% rule
 *   <li><strong>Savings Longevity:</strong> Determines how long current savings will last given
 *       ongoing expenses
 *   <li><strong>Sensitivity Analysis:</strong> Shows impact of different return rates
 * </ul>
 *
 * <p><strong>Key Formulas:</strong>
 *
 * <ul>
 *   <li>Target Amount = Annual Expenses / (Withdrawal Rate / 100)
 *   <li>Monthly Return = Balance × (Annual Rate / 12)
 *   <li>Real Return = (1 + Nominal) / (1 + Inflation) - 1
 * </ul>
 *
 * <p>Requirement US 1: Calculate Time to Financial Freedom
 *
 * <p>Requirement US 2: Calculate Savings Longevity
 *
 * <p>Requirement US 3: Scenario Planning
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialFreedomService {

    /** Default withdrawal rate (4% rule). */
    private static final BigDecimal DEFAULT_WITHDRAWAL_RATE = new BigDecimal("4.0");

    /** Default inflation rate. */
    private static final BigDecimal DEFAULT_INFLATION_RATE = new BigDecimal("2.5");

    /** Maximum projection years to prevent excessive computation. */
    private static final int MAX_PROJECTION_YEARS = 50;

    /** Maximum longevity years for savings projection. */
    private static final int MAX_LONGEVITY_YEARS = 100;

    /** Months per year constant. */
    private static final int MONTHS_PER_YEAR = 12;

    /** Maximum reasonable return rate (30%). */
    private static final BigDecimal MAX_RETURN_RATE = new BigDecimal("30.0");

    /** Minimum reasonable return rate (-10%). */
    private static final BigDecimal MIN_RETURN_RATE = new BigDecimal("-10.0");

    /** 100, used for percent → fraction conversions. */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Working precision for iterative BigDecimal compounding. */
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    /**
     * Calculate time to financial freedom.
     *
     * <p>Determines how long it will take for savings to grow to the target amount needed to
     * sustain the user's lifestyle indefinitely based on the 4% rule.
     *
     * <p><strong>Process:</strong>
     *
     * <ol>
     *   <li>Calculate target savings (Annual Expenses / Withdrawal Rate)
     *   <li>Adjust return rate for inflation if requested
     *   <li>Iteratively calculate months to reach target
     *   <li>Generate year-by-year projections
     *   <li>Create sensitivity scenarios
     * </ol>
     *
     * @param request The calculation input parameters
     * @return Response containing timeline, projections, and sensitivity analysis
     */
    public FreedomCalculatorResponse calculateTimeToFreedom(FreedomCalculatorRequest request) {
        log.info(
                "Calculating financial freedom timeline: savings={}, expenses={}, return={}%",
                request.getCurrentSavings(),
                request.getMonthlyExpenses(),
                request.getExpectedAnnualReturn());

        // Validate inputs
        validateRequest(request);

        // Set defaults
        BigDecimal withdrawalRate =
                request.getWithdrawalRate() != null
                        ? request.getWithdrawalRate()
                        : DEFAULT_WITHDRAWAL_RATE;

        BigDecimal inflationRate =
                request.getInflationRate() != null
                        ? request.getInflationRate()
                        : DEFAULT_INFLATION_RATE;

        BigDecimal monthlyContribution =
                request.getMonthlyContribution() != null
                        ? request.getMonthlyContribution()
                        : BigDecimal.ZERO;

        // Calculate annual expenses
        BigDecimal annualExpenses =
                request.getMonthlyExpenses().multiply(BigDecimal.valueOf(MONTHS_PER_YEAR));

        // Calculate target savings amount
        BigDecimal targetAmount = calculateTargetAmount(annualExpenses, withdrawalRate);
        log.debug("Target savings amount: {}", targetAmount);

        // Adjust return rate for inflation if needed
        BigDecimal effectiveReturnRate = request.getExpectedAnnualReturn();
        if (Boolean.TRUE.equals(request.getAdjustForInflation())) {
            effectiveReturnRate =
                    calculateRealReturn(request.getExpectedAnnualReturn(), inflationRate);
            log.debug("Inflation-adjusted return rate: {}%", effectiveReturnRate);
        }

        // Calculate months to target
        long monthsToFreedom =
                calculateMonthsToTarget(
                        request.getCurrentSavings(),
                        monthlyContribution,
                        effectiveReturnRate,
                        targetAmount);

        // Determine if achievable
        boolean achievable = monthsToFreedom < (MAX_PROJECTION_YEARS * MONTHS_PER_YEAR);

        // Generate yearly projections
        List<ProjectionResult> projections =
                generateProjections(
                        request.getCurrentSavings(),
                        monthlyContribution,
                        effectiveReturnRate,
                        targetAmount,
                        MAX_PROJECTION_YEARS);

        // Generate sensitivity scenarios
        List<SensitivityScenario> scenarios =
                generateSensitivityScenarios(
                        request.getCurrentSavings(),
                        request.getMonthlyExpenses(),
                        monthlyContribution,
                        request.getExpectedAnnualReturn().doubleValue(),
                        withdrawalRate.doubleValue());

        // Calculate progress percentage
        double progressPercentage =
                request.getCurrentSavings()
                        .divide(targetAmount, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();

        // Calculate annual passive income at target
        BigDecimal annualPassiveIncome =
                targetAmount
                        .multiply(withdrawalRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Build message based on result
        String message = buildFreedomMessage(monthsToFreedom, achievable, targetAmount);

        return FreedomCalculatorResponse.builder()
                .yearsToFreedom((int) (monthsToFreedom / MONTHS_PER_YEAR))
                .monthsToFreedom(monthsToFreedom % MONTHS_PER_YEAR)
                .targetSavingsAmount(targetAmount)
                .progressPercentage(Math.min(progressPercentage, 100.0))
                .currentProgress(request.getCurrentSavings())
                .annualPassiveIncome(annualPassiveIncome)
                .sustainableIndefinitely(
                        achievable && monthsToFreedom >= MAX_PROJECTION_YEARS * MONTHS_PER_YEAR)
                .achievable(achievable)
                .yearlyProjections(projections)
                .sensitivityScenarios(scenarios)
                .message(message)
                .build();
    }

    /**
     * Calculate savings longevity.
     *
     * <p>Determines how long current savings will last given ongoing monthly expenses and expected
     * investment returns.
     *
     * <p><strong>Process:</strong>
     *
     * <ol>
     *   <li>Check if returns exceed expenses (infinite sustainability)
     *   <li>Iterate monthly until balance depletes or max reached
     *   <li>Generate depletion projections
     * </ol>
     *
     * <p><strong>Infinite Sustainability:</strong> If monthly investment returns exceed monthly
     * expenses, savings will last indefinitely.
     *
     * @param currentSavings Current total savings
     * @param monthlyExpenses Expected monthly expenses
     * @param annualReturnRate Expected annual return rate
     * @return Result containing longevity analysis and projections
     */
    public SavingsLongevityResult calculateSavingsLongevity(
            BigDecimal currentSavings, BigDecimal monthlyExpenses, BigDecimal annualReturnRate) {

        log.info(
                "Calculating savings longevity: savings={}, expenses={}, return={}%",
                currentSavings, monthlyExpenses, annualReturnRate);

        // Validate inputs
        if (currentSavings == null || currentSavings.compareTo(BigDecimal.ZERO) < 0) {
            throw CalculationValidationException.negativeValue(
                    "Current savings", currentSavings != null ? currentSavings.toString() : "null");
        }

        if (monthlyExpenses == null || monthlyExpenses.compareTo(BigDecimal.ZERO) < 0) {
            throw CalculationValidationException.negativeValue(
                    "Monthly expenses",
                    monthlyExpenses != null ? monthlyExpenses.toString() : "null");
        }

        if (annualReturnRate == null) {
            throw CalculationValidationException.required("Annual return rate");
        }

        BigDecimal monthlyRate =
                annualReturnRate
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // Check for infinite sustainability
        // If monthly returns on current savings exceed monthly expenses
        BigDecimal monthlyReturn = currentSavings.multiply(monthlyRate);
        boolean infinite =
                monthlyReturn.compareTo(monthlyExpenses) >= 0
                        && monthlyRate.compareTo(BigDecimal.ZERO) > 0;

        List<ProjectionResult> depletionProjections = new ArrayList<>();
        BigDecimal balance = currentSavings;
        int months = 0;
        final int MAX_MONTHS = MAX_LONGEVITY_YEARS * MONTHS_PER_YEAR;
        int projectionIndex = 0;

        if (infinite) {
            log.info("Savings will last indefinitely - returns exceed expenses");
        } else {
            // Calculate month-by-month depletion
            while (balance.compareTo(BigDecimal.ZERO) > 0 && months < MAX_MONTHS) {
                // Record yearly projection
                if (months % MONTHS_PER_YEAR == 0) {
                    int year = months / MONTHS_PER_YEAR;
                    BigDecimal startingBalance =
                            projectionIndex == 0
                                    ? currentSavings
                                    : depletionProjections.isEmpty()
                                            ? currentSavings
                                            : depletionProjections
                                                    .get(depletionProjections.size() - 1)
                                                    .getEndingBalance();

                    depletionProjections.add(
                            ProjectionResult.builder()
                                    .year(year)
                                    .startingBalance(startingBalance)
                                    .endingBalance(balance)
                                    .withdrawals(
                                            monthlyExpenses.multiply(
                                                    BigDecimal.valueOf(MONTHS_PER_YEAR)))
                                    .build());
                    projectionIndex++;
                }

                // Apply investment returns
                balance = balance.add(balance.multiply(monthlyRate));
                // Subtract expenses
                balance = balance.subtract(monthlyExpenses);
                months++;
            }
        }

        int yearsUntilDepletion = months / MONTHS_PER_YEAR;
        Integer depletionYear =
                yearsUntilDepletion > 0
                        ? Year.now().plusYears(yearsUntilDepletion).getValue()
                        : null;

        // Build message
        String message;
        if (infinite) {
            message =
                    "Your savings will last indefinitely because your investment returns exceed your monthly expenses.";
        } else if (months >= MAX_MONTHS) {
            message =
                    String.format(
                            "Your savings will last for at least %d years.", MAX_LONGEVITY_YEARS);
        } else {
            message =
                    String.format(
                            "Your savings will be depleted in approximately %d years and %d months.",
                            yearsUntilDepletion, months % MONTHS_PER_YEAR);
        }

        return SavingsLongevityResult.builder()
                .yearsUntilDepletion(yearsUntilDepletion)
                .totalMonthsUntilDepletion(months)
                .infinite(infinite)
                .depletionYear(depletionYear)
                .finalBalance(balance.compareTo(BigDecimal.ZERO) > 0 ? balance : BigDecimal.ZERO)
                .willDeplete(!infinite && months < MAX_MONTHS)
                .depletionProjections(depletionProjections)
                .monthlyExpenses(monthlyExpenses)
                .currentSavings(currentSavings)
                .annualReturnRate(annualReturnRate)
                .message(message)
                .build();
    }

    /**
     * Get calculation default parameters.
     *
     * @return Default values and valid ranges for calculator inputs
     */
    public CalculationDefaults getDefaults() {
        return CalculationDefaults.builder()
                .defaultWithdrawalRate(4.0)
                .defaultInflationRate(2.5)
                .minimumWithdrawalRate(0.5)
                .maximumWithdrawalRate(10.0)
                .minimumReturnRate(-10.0)
                .maximumReturnRate(30.0)
                .defaultReturnRate(7.0)
                .maxProjectionYears(MAX_PROJECTION_YEARS)
                .defaultProjectionYears(30)
                .defaultMonthlyContribution(0)
                .build();
    }

    // ==================== Private Helper Methods ====================

    /** Validates the calculation request. */
    private void validateRequest(FreedomCalculatorRequest request) {
        if (request.getCurrentSavings() == null
                || request.getCurrentSavings().compareTo(BigDecimal.ZERO) < 0) {
            throw CalculationValidationException.negativeValue(
                    "Current savings",
                    request.getCurrentSavings() != null
                            ? request.getCurrentSavings().toString()
                            : "null");
        }

        if (request.getMonthlyExpenses() == null
                || request.getMonthlyExpenses().compareTo(BigDecimal.ZERO) < 0) {
            throw CalculationValidationException.negativeValue(
                    "Monthly expenses",
                    request.getMonthlyExpenses() != null
                            ? request.getMonthlyExpenses().toString()
                            : "null");
        }

        if (request.getExpectedAnnualReturn() == null) {
            throw CalculationValidationException.required("Expected return rate");
        }

        if (request.getExpectedAnnualReturn().compareTo(MIN_RETURN_RATE) < 0
                || request.getExpectedAnnualReturn().compareTo(MAX_RETURN_RATE) > 0) {
            throw CalculationValidationException.outOfRange(
                    "Return rate",
                    request.getExpectedAnnualReturn().toString(),
                    MIN_RETURN_RATE.toString() + "%",
                    MAX_RETURN_RATE.toString() + "%");
        }

        // Check for impossible scenario
        BigDecimal monthlyContribution =
                request.getMonthlyContribution() != null
                        ? request.getMonthlyContribution()
                        : BigDecimal.ZERO;

        if (request.getCurrentSavings().compareTo(BigDecimal.ZERO) == 0
                && monthlyContribution.compareTo(BigDecimal.ZERO) == 0
                && request.getExpectedAnnualReturn().doubleValue() <= 0) {
            throw new CalculationValidationException(
                    "Cannot achieve financial freedom with no savings, no contributions, and non-positive returns");
        }
    }

    /**
     * Calculates the target savings amount needed.
     *
     * <p>Formula: Target = Annual Expenses / (Withdrawal Rate / 100)
     *
     * <p>Example: €50,000 expenses / 0.04 = €1,250,000 target
     *
     * @param annualExpenses Expected annual expenses
     * @param withdrawalRate Safe withdrawal rate as percentage
     * @return Target savings amount
     * @throws CalculationValidationException if withdrawal rate would cause division by zero
     */
    private BigDecimal calculateTargetAmount(BigDecimal annualExpenses, BigDecimal withdrawalRate) {
        if (withdrawalRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CalculationValidationException("Withdrawal rate must be greater than zero");
        }

        BigDecimal withdrawalDecimal =
                withdrawalRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        return annualExpenses.divide(withdrawalDecimal, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates months needed to reach target savings.
     *
     * <p>Uses iterative approach for accuracy with monthly contributions.
     *
     * @param currentSavings Starting balance
     * @param monthlyContribution Monthly addition to savings
     * @param annualRate Annual return rate as percentage
     * @param targetAmount Savings goal
     * @return Months until target is reached (may exceed MAX_PROJECTION_YEARS)
     */
    private long calculateMonthsToTarget(
            BigDecimal currentSavings,
            BigDecimal monthlyContribution,
            BigDecimal annualRate,
            BigDecimal targetAmount) {

        BigDecimal monthlyRate =
                annualRate.divide(HUNDRED, MC).divide(BigDecimal.valueOf(MONTHS_PER_YEAR), MC);
        BigDecimal onePlusMonthlyRate = BigDecimal.ONE.add(monthlyRate);
        BigDecimal balance = currentSavings;
        long months = 0;

        // Safety cap to prevent infinite loops
        final long MAX_MONTHS = MAX_PROJECTION_YEARS * MONTHS_PER_YEAR * 2L;

        while (balance.compareTo(targetAmount) < 0 && months < MAX_MONTHS) {
            balance = balance.multiply(onePlusMonthlyRate, MC).add(monthlyContribution);
            months++;

            // Safety check for impossible scenarios
            if (monthlyRate.compareTo(BigDecimal.ZERO) <= 0
                    && monthlyContribution.compareTo(BigDecimal.ZERO) <= 0) {
                return MAX_MONTHS; // Return max to indicate not achievable
            }
        }

        return months;
    }

    /**
     * Calculates real return rate after inflation.
     *
     * <p>Formula: Real = (1 + Nominal) / (1 + Inflation) - 1
     *
     * <p>More accurate than simply subtracting inflation from nominal rate.
     *
     * @param nominalReturn Nominal annual return as percentage
     * @param inflation Annual inflation rate as percentage
     * @return Real return rate after inflation
     */
    private BigDecimal calculateRealReturn(BigDecimal nominalReturn, BigDecimal inflation) {
        BigDecimal onePlusNominal =
                nominalReturn
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                        .add(BigDecimal.ONE);
        BigDecimal onePlusInflation =
                inflation
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                        .add(BigDecimal.ONE);

        BigDecimal realReturn =
                onePlusNominal
                        .divide(onePlusInflation, 10, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(100));

        return realReturn;
    }

    /**
     * Generates year-by-year projections until target is reached or max years.
     *
     * @param currentSavings Starting balance
     * @param monthlyContribution Monthly contribution
     * @param annualRate Annual return rate
     * @param targetAmount Savings goal
     * @param maxYears Maximum projection years
     * @return List of yearly projections
     */
    private List<ProjectionResult> generateProjections(
            BigDecimal currentSavings,
            BigDecimal monthlyContribution,
            BigDecimal annualRate,
            BigDecimal targetAmount,
            int maxYears) {

        List<ProjectionResult> projections = new ArrayList<>();
        BigDecimal balance = currentSavings;
        BigDecimal annualRateFraction = annualRate.divide(HUNDRED, MC);
        BigDecimal annualContribution =
                monthlyContribution.multiply(BigDecimal.valueOf(MONTHS_PER_YEAR));

        for (int year = 0; year <= maxYears; year++) {
            BigDecimal startingBalance = balance;

            // Calculate yearly returns
            BigDecimal yearlyReturns = balance.multiply(annualRateFraction, MC);

            // Update balance
            balance = balance.add(yearlyReturns).add(annualContribution);

            // Calculate progress (display percentage; response field is a double)
            double progress =
                    balance.divide(targetAmount, 6, RoundingMode.HALF_UP)
                            .multiply(HUNDRED)
                            .doubleValue();

            boolean targetReached = balance.compareTo(targetAmount) >= 0;

            projections.add(
                    ProjectionResult.builder()
                            .year(year)
                            .startingBalance(startingBalance)
                            .endingBalance(balance)
                            .contributions(annualContribution)
                            .investmentReturns(yearlyReturns)
                            .progressTowardTarget(Math.min(progress, 100.0))
                            .targetReached(targetReached)
                            .build());

            // Stop if target reached
            if (targetReached) {
                break;
            }
        }

        return projections;
    }

    /**
     * Generates sensitivity analysis scenarios.
     *
     * <p>Creates baseline, optimistic (+2%), and pessimistic (-2%) scenarios to show how different
     * return rates affect the timeline.
     *
     * @param currentSavings Starting balance
     * @param monthlyExpenses Monthly expenses
     * @param monthlyContribution Monthly contribution
     * @param baseReturnRate User's expected return rate
     * @param withdrawalRate Withdrawal rate
     * @return List of sensitivity scenarios
     */
    private List<SensitivityScenario> generateSensitivityScenarios(
            BigDecimal currentSavings,
            BigDecimal monthlyExpenses,
            BigDecimal monthlyContribution,
            double baseReturnRate,
            double withdrawalRate) {

        List<SensitivityScenario> scenarios = new ArrayList<>();

        BigDecimal annualExpenses = monthlyExpenses.multiply(BigDecimal.valueOf(MONTHS_PER_YEAR));
        BigDecimal targetAmount =
                calculateTargetAmount(annualExpenses, BigDecimal.valueOf(withdrawalRate));

        // Baseline scenario
        scenarios.add(
                createScenario(
                        "Baseline",
                        baseReturnRate,
                        currentSavings,
                        monthlyContribution,
                        targetAmount,
                        SensitivityScenario.ScenarioType.BASELINE));

        // Pessimistic scenario (-2%, minimum -5%)
        double pessimisticRate = Math.max(-5.0, baseReturnRate - 2.0);
        if (pessimisticRate != baseReturnRate) {
            scenarios.add(
                    createScenario(
                            "Pessimistic (-2%)",
                            pessimisticRate,
                            currentSavings,
                            monthlyContribution,
                            targetAmount,
                            SensitivityScenario.ScenarioType.PESSIMISTIC));
        }

        // Optimistic scenario (+2%, maximum 15%)
        double optimisticRate = Math.min(15.0, baseReturnRate + 2.0);
        if (optimisticRate != baseReturnRate) {
            scenarios.add(
                    createScenario(
                            "Optimistic (+2%)",
                            optimisticRate,
                            currentSavings,
                            monthlyContribution,
                            targetAmount,
                            SensitivityScenario.ScenarioType.OPTIMISTIC));
        }

        return scenarios;
    }

    /** Creates a single sensitivity scenario. */
    private SensitivityScenario createScenario(
            String label,
            double returnRate,
            BigDecimal currentSavings,
            BigDecimal monthlyContribution,
            BigDecimal targetAmount,
            SensitivityScenario.ScenarioType scenarioType) {

        long monthsToFreedom =
                calculateMonthsToTarget(
                        currentSavings,
                        monthlyContribution,
                        BigDecimal.valueOf(returnRate),
                        targetAmount);

        return SensitivityScenario.builder()
                .label(label)
                .returnRate(returnRate)
                .yearsToFreedom((int) (monthsToFreedom / MONTHS_PER_YEAR))
                .monthsToFreedom(monthsToFreedom % MONTHS_PER_YEAR)
                .scenarioType(scenarioType)
                .build();
    }

    /** Builds a human-readable message based on calculation results. */
    private String buildFreedomMessage(
            long monthsToFreedom, boolean isAchievable, BigDecimal targetAmount) {
        if (!isAchievable) {
            return String.format(
                    "Financial freedom is not achievable within %d years with current savings and contribution levels. "
                            + "Consider increasing monthly contributions or adjusting your target amount of %s.",
                    MAX_PROJECTION_YEARS, formatCurrency(targetAmount));
        }

        int years = (int) (monthsToFreedom / MONTHS_PER_YEAR);
        int months = (int) (monthsToFreedom % MONTHS_PER_YEAR);

        StringBuilder message = new StringBuilder();
        message.append(
                "Based on your current savings, monthly contributions, and expected returns, ");

        if (years == 0) {
            message.append(
                    String.format("you could achieve financial freedom in %d months.", months));
        } else if (months == 0) {
            message.append(
                    String.format("you could achieve financial freedom in %d years.", years));
        } else {
            message.append(
                    String.format(
                            "you could achieve financial freedom in %d years and %d months.",
                            years, months));
        }

        return message.toString();
    }

    /** Formats a BigDecimal as currency. */
    private String formatCurrency(BigDecimal amount) {
        return String.format("€%,.2f", amount.doubleValue());
    }
}
