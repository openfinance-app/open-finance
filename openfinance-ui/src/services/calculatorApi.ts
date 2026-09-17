/**
 * Financial Freedom Calculator API Service
 *
 * API client for financial freedom and savings longevity calculations
 */

import apiClient from './apiClient';
import type {
  FreedomCalculatorInput,
  FreedomCalculatorResult,
  SavingsLongevityResult,
  CalculationDefaults,
} from '../types/calculator';

/**
 * Calculate time to financial freedom
 *
 * @param input Calculator input parameters
 * @returns Promise with calculation result
 */
export async function calculateTimeline(
  input: FreedomCalculatorInput
): Promise<FreedomCalculatorResult> {
  const response = await apiClient.post('/calculator/financial-freedom/timeline', {
    currentSavings: input.currentSavings,
    monthlyExpenses: input.monthlyExpenses,
    expectedAnnualReturn: input.expectedAnnualReturn,
    monthlyContribution: input.monthlyContribution ?? 0,
    withdrawalRate: input.withdrawalRate ?? 4,
    inflationRate: input.inflationRate ?? 2.5,
    adjustForInflation: input.adjustForInflation ?? false,
  });

  return response.data;
}

/**
 * Calculate savings longevity
 *
 * @param currentSavings Current total savings
 * @param monthlyExpenses Expected monthly expenses
 * @param annualReturnRate Expected annual return rate
 * @returns Promise with longevity calculation result
 */
export async function calculateLongevity(
  currentSavings: number,
  monthlyExpenses: number,
  annualReturnRate: number
): Promise<SavingsLongevityResult> {
  const response = await apiClient.post('/calculator/financial-freedom/longevity', null, {
    params: {
      currentSavings,
      monthlyExpenses,
      annualReturnRate,
    },
  });

  return response.data;
}

/**
 * Get default calculation parameters
 *
 * @returns Promise with default parameters
 */
export async function getCalculationDefaults(): Promise<CalculationDefaults> {
  const response = await apiClient.get('/calculator/financial-freedom/defaults');
  return response.data;
}
