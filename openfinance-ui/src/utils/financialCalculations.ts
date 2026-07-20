/**
 * Financial Calculation Utilities
 * 
 * Pure TypeScript functions for financial freedom calculations
 * Used for client-side calculations and validation
 */

import { DEFAULT_CURRENCY } from './currency';

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
  const monthlyRate = annualRate / 100 / 12;
  
  if (months === 0) {
    return presentValue;
  }
  
  if (monthlyRate === 0) {
    return presentValue + (monthlyContribution * months);
  }
  
  const compoundFactor = Math.pow(1 + monthlyRate, months);
  const futureValueFromSavings = presentValue * compoundFactor;
  const futureValueFromContributions = monthlyContribution * 
    ((compoundFactor - 1) / monthlyRate);
  
  return futureValueFromSavings + futureValueFromContributions;
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
  currentSavings: number,
  monthlyContribution: number,
  annualRate: number,
  targetAmount: number,
  maxMonths: number = 1200
): number {
  const monthlyRate = annualRate / 100 / 12;
  let balance = currentSavings;
  let months = 0;
  
  // Safety check for impossible scenarios
  if (monthlyRate <= 0 && monthlyContribution <= 0 && balance < targetAmount) {
    return maxMonths;
  }
  
  while (balance < targetAmount && months < maxMonths) {
    balance = balance * (1 + monthlyRate) + monthlyContribution;
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
export function calculateTargetAmount(
  annualExpenses: number,
  withdrawalRate: number
): number {
  if (withdrawalRate <= 0) {
    return Infinity;
  }
  return annualExpenses / (withdrawalRate / 100);
}

/**
 * Calculate savings longevity (how long savings will last)
 * 
 * @param currentSavings Current savings balance
 * @param monthlyExpenses Monthly expenses
 * @param annualReturnRate Annual return rate as percentage
 * @param maxMonths Maximum months to calculate (default 1200 = 100 years)
 * @returns Object with months until depletion and infinity flag
 */
export function calculateSavingsLongevity(
  currentSavings: number,
  monthlyExpenses: number,
  annualReturnRate: number,
  maxMonths: number = 1200
): {
  monthsUntilDepletion: number;
  isInfinite: boolean;
  finalBalance: number | null;
} {
  const monthlyRate = annualReturnRate / 100 / 12;
  let balance = currentSavings;
  let months = 0;
  
  // Check for infinite sustainability
  // If returns on current savings exceed monthly expenses
  if (monthlyRate > 0) {
    const monthlyReturn = currentSavings * monthlyRate;
    if (monthlyReturn >= monthlyExpenses) {
      return {
        monthsUntilDepletion: maxMonths,
        isInfinite: true,
        finalBalance: null,
      };
    }
  }
  
  // Calculate month by month depletion
  while (balance > 0 && months < maxMonths) {
    // Add investment returns
    balance = balance * (1 + monthlyRate);
    // Subtract monthly expenses
    balance = balance - monthlyExpenses;
    
    if (balance > 0) {
      months++;
    }
  }
  
  return {
    monthsUntilDepletion: months,
    isInfinite: false,
    finalBalance: balance > 0 ? balance : 0,
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
export function calculateRealReturn(
  nominalReturnRate: number,
  inflationRate: number
): number {
  const onePlusNominal = 1 + nominalReturnRate / 100;
  const onePlusInflation = 1 + inflationRate / 100;
  
  const realReturn = (onePlusNominal / onePlusInflation) - 1;
  return realReturn * 100;
}

/**
 * Calculate progress percentage toward goal
 * 
 * @param currentSavings Current savings balance
 * @param targetAmount Savings goal
 * @returns Progress as percentage (0-100)
 */
export function calculateProgressPercentage(
  currentSavings: number,
  targetAmount: number
): number {
  if (targetAmount <= 0) {
    return 100;
  }
  const progress = (currentSavings / targetAmount) * 100;
  return Math.min(progress, 100);
}

/**
 * Calculate annual passive income at target based on withdrawal rate
 * 
 * @param targetAmount Target savings amount
 * @param withdrawalRate Safe withdrawal rate as percentage
 * @returns Annual passive income
 */
export function calculatePassiveIncome(
  targetAmount: number,
  withdrawalRate: number
): number {
  return targetAmount * (withdrawalRate / 100);
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
  value: number,
  locale: string = 'en-US',
  currency: string = DEFAULT_CURRENCY
): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value);
}

/**
 * Format percentage for display
 * 
 * @param value Value to format as percentage
 * @param decimals Number of decimal places
 * @returns Formatted percentage string
 */
export function formatPercentage(value: number, decimals: number = 1): string {
  return `${value.toFixed(decimals)}%`;
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
