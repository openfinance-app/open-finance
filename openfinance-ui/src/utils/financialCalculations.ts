import { ExactDecimal } from '@/utils/exact-decimal';
import { formatPercentage as formatPercent } from '@/utils/format';
/**
 * Financial Calculation Utilities
 *
 * Pure TypeScript functions for financial freedom calculations
 * Used for client-side calculations and validation
 */

import { DEFAULT_CURRENCY, formatCurrency as sharedFormatCurrency } from '@/utils/currency';
import { add, divide, multiply, percentage, pow, subtract } from '@/utils/money';

/**
 * Calculate future value with monthly contributions
 * FV = PV × (1 + r)^n + PMT × ((1 + r)^n - 1) / r
 *
 * @param presentValue Current savings amount
 * @param monthlyContribution Monthly contribution to savings
 * @param annualRate Annual interest rate as percentage
 * @param months Number of months to project
 * @returns Future value after specified months
 */
export function calculateFutureValue(
  presentValue: number,
  monthlyContribution: number,
  annualRate: number,
  months: number
): number {
  const monthlyRate = divide(divide(annualRate, 100), 12);

  if (months === 0) {
    return presentValue;
  }

  if (monthlyRate === 0) {
    return add(presentValue, multiply(monthlyContribution, months));
  }

  const compoundFactor = pow(add(1, monthlyRate), months);
  const futureValueFromSavings = multiply(presentValue, compoundFactor);
  const futureValueFromContributions = multiply(
    monthlyContribution,
    divide(subtract(compoundFactor, 1), monthlyRate)
  );

  return add(futureValueFromSavings, futureValueFromContributions);
}

/**
 * Calculate months needed to reach target savings
 * Uses iterative approach for accuracy with contributions
 *
 * @param currentSavings Current savings balance
 * @param monthlyContribution Monthly contribution amount
 * @param annualRate Annual return rate as percentage
 * @param targetAmount Savings goal
 * @param maxMonths Maximum months to calculate (default 1200 = 100 years)
 * @returns Months until target is reached, or maxMonths if not achievable
 */
export function calculateMonthsToTarget(
  currentSavings: number | string,
  monthlyContribution: number | string,
  annualRate: number,
  targetAmount: number | string,
  maxMonths: number = 1200
): number {
  const monthlyRate = new ExactDecimal(annualRate).div(1200);
  const contribution = new ExactDecimal(monthlyContribution);
  let balance = new ExactDecimal(currentSavings);
  if (monthlyRate.lte(0) && contribution.lte(0) && balance.lt(targetAmount)) return maxMonths;
  let months = 0;
  while (balance.lt(targetAmount) && months < maxMonths) {
    balance = balance.times(monthlyRate.plus(1)).plus(contribution);
    months++;
  }
  return months;
}

/**
 * Calculate target savings amount based on withdrawal rate
 * Target = Annual Expenses / Withdrawal Rate
 *
 * @param annualExpenses Expected annual expenses
 * @param withdrawalRate Safe withdrawal rate as percentage
 * @returns Target savings amount needed
 */
export function calculateTargetAmount(annualExpenses: number, withdrawalRate: number): number {
  if (withdrawalRate <= 0) {
    return Infinity;
  }
  return divide(annualExpenses, divide(withdrawalRate, 100));
}

/**
 * Calculate savings longevity (how long savings will last)
 *
 * @param currentSavings Current savings balance
 * @param monthlyExpenses Monthly expenses
 * @param annualReturnRate Annual return rate as percentage
 * @param maxMonths Maximum months to calculate (default 1200 = 100 years)
 * @returns Depletion month (including a final partially funded month), infinity flag and balance
 */
export function calculateSavingsLongevity(
  currentSavings: number | string,
  monthlyExpenses: number | string,
  annualReturnRate: number,
  maxMonths: number = 1200
): {
  monthsUntilDepletion: number;
  isInfinite: boolean;
  finalBalance: number | null;
  exceedsProjection?: boolean;
} {
  const monthlyRate = new ExactDecimal(annualReturnRate).div(1200);
  const expenses = new ExactDecimal(monthlyExpenses);
  let balance = new ExactDecimal(currentSavings);
  if (expenses.isZero() || (monthlyRate.gt(0) && balance.times(monthlyRate).gte(expenses))) {
    return { monthsUntilDepletion: maxMonths, isInfinite: true, finalBalance: null };
  }
  if (monthlyRate.isZero()) {
    return {
      monthsUntilDepletion: balance.div(expenses).ceil().toNumber(),
      isInfinite: false,
      finalBalance: 0,
    };
  }
  let months = 0;
  while (balance.gt(0) && months < maxMonths) {
    balance = balance.times(monthlyRate.plus(1)).minus(expenses);
    months++;
  }
  return {
    monthsUntilDepletion: months,
    isInfinite: false,
    finalBalance: balance.gt(0) ? balance.toNumber() : 0,
    exceedsProjection: balance.gt(0),
  };
}

/**
 * Calculate real return rate after inflation
 * Real Return = (1 + Nominal) / (1 + Inflation) - 1
 *
 * @param nominalReturnRate Nominal annual return as percentage
 * @param inflationRate Annual inflation as percentage
 * @returns Real return rate as percentage
 */
export function calculateRealReturn(nominalReturnRate: number, inflationRate: number): number {
  const onePlusNominal = add(1, divide(nominalReturnRate, 100));
  const onePlusInflation = add(1, divide(inflationRate, 100));

  const realReturn = subtract(divide(onePlusNominal, onePlusInflation), 1);
  return multiply(realReturn, 100);
}

/**
 * Calculate progress percentage toward goal
 *
 * @param currentSavings Current savings balance
 * @param targetAmount Savings goal
 * @returns Progress as percentage (0-100)
 */
export function calculateProgressPercentage(currentSavings: number, targetAmount: number): number {
  if (targetAmount <= 0) {
    return 100;
  }
  const progress = percentage(currentSavings, targetAmount);
  return Math.min(progress, 100);
}

/**
 * Calculate annual passive income at target based on withdrawal rate
 *
 * @param targetAmount Target savings amount
 * @param withdrawalRate Safe withdrawal rate as percentage
 * @returns Annual passive income
 */
export function calculatePassiveIncome(targetAmount: number, withdrawalRate: number): number {
  return multiply(targetAmount, divide(withdrawalRate, 100));
}

/**
 * Format currency value for display
 *
 * @param value Value to format
 * @param locale Currency locale (default 'en-US')
 * @param currency Currency code (default: app DEFAULT_CURRENCY)
 * @returns Formatted currency string
 */
export function formatCurrency(
  value: number | string,
  locale: string = 'en-US',
  currency: string = DEFAULT_CURRENCY
): string {
  return sharedFormatCurrency(value, currency, {
    numberFormat: locale.startsWith('fr')
      ? '1 234,56'
      : locale.startsWith('de')
        ? '1.234,56'
        : '1,234.56',
  });
}

/**
 * Format percentage for display
 *
 * @param value Value to format as percentage
 * @param decimals Number of decimal places
 * @returns Formatted percentage string
 */
export function formatPercentage(value: number, decimals: number = 1): string {
  return formatPercent(value, decimals);
}

/**
 * Format years and months for display
 *
 * @param months Total months
 * @returns Formatted string like "5 years, 3 months"
 */
export function formatTimeToFreedom(months: number): string {
  const years = Math.floor(months / 12);
  const remainingMonths = Math.round(months % 12);

  if (years === 0) {
    return `${remainingMonths} month${remainingMonths !== 1 ? 's' : ''}`;
  }

  if (remainingMonths === 0) {
    return `${years} year${years !== 1 ? 's' : ''}`;
  }

  return `${years} year${years !== 1 ? 's' : ''}, ${remainingMonths} month${remainingMonths !== 1 ? 's' : ''}`;
}

/**
 * Validate calculator inputs
 *
 * @param input Calculator input parameters
 * @returns Validation result with isValid flag and errors
 */
export function validateCalculatorInput(input: {
  currentSavings: number;
  monthlyExpenses: number;
  expectedAnnualReturn: number;
  monthlyContribution?: number;
  withdrawalRate?: number;
}): { isValid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (input.currentSavings < 0) {
    errors.push('Current savings cannot be negative');
  }

  if (input.monthlyExpenses < 0) {
    errors.push('Monthly expenses cannot be negative');
  }

  if (input.expectedAnnualReturn < -10 || input.expectedAnnualReturn > 30) {
    errors.push('Return rate must be between -10% and 30%');
  }

  if (input.monthlyContribution !== undefined && input.monthlyContribution < 0) {
    errors.push('Monthly contribution cannot be negative');
  }

  if (input.withdrawalRate !== undefined) {
    if (input.withdrawalRate < 0.5 || input.withdrawalRate > 10) {
      errors.push('Withdrawal rate must be between 0.5% and 10%');
    }
  }

  return {
    isValid: errors.length === 0,
    errors,
  };
}
