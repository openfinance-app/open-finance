/**
 * useFinancialFreedom Hook
 * 
 * Custom hook for managing financial freedom calculator state and calculations
 */

import { useState, useCallback, useEffect } from 'react';
import {
  calculateTimeline,
  calculateLongevity,
  getCalculationDefaults,
} from '../services/calculatorApi';
import {
  DEFAULT_FREEDOM_CALCULATOR_INPUT,
  type FreedomCalculatorInput,
  type FreedomCalculatorResult,
  type SavingsLongevityResult,
  type CalculationDefaults,
} from '../types/calculator';
import {
  calculateSavingsLongevity,
  calculateMonthsToTarget,
  calculateTargetAmount,
  calculateRealReturn,
} from '../utils/financialCalculations';
import { divide, multiply, percentage } from '@/utils/money';

/**
 * State interface for the calculator hook
 */
interface CalculatorState {
  /** Current input values */
  input: FreedomCalculatorInput;
  /** Calculation result */
  result: FreedomCalculatorResult | null;
  /** Longevity result */
  longevityResult: SavingsLongevityResult | null;
  /** Loading state */
  isLoading: boolean;
  /** Error message */
  error: string | null;
  /** Default parameters */
  defaults: CalculationDefaults | null;
}

/**
 * Default state for the calculator
 */
const defaultState: CalculatorState = {
  input: DEFAULT_FREEDOM_CALCULATOR_INPUT,
  result: null,
  longevityResult: null,
  isLoading: false,
  error: null,
  defaults: null,
};

/**
 * Hook for managing financial freedom calculator
 */
export function useFinancialFreedom() {
  const [state, setState] = useState<CalculatorState>(defaultState);

  // Load defaults on mount
  useEffect(() => {
    const loadDefaults = async () => {
      try {
        const defaults = await getCalculationDefaults();
        setState((prev) => ({ ...prev, defaults }));
      } catch (error) {
        console.error('Failed to load calculation defaults:', error);
      }
    };

    loadDefaults();
  }, []);

  /**
   * Update input field
   */
  const updateInput = useCallback(<K extends keyof FreedomCalculatorInput>(
    key: K,
    value: FreedomCalculatorInput[K]
  ) => {
    setState((prev) => ({
      ...prev,
      input: { ...prev.input, [key]: value },
      error: null,
    }));
  }, []);

  /**
   * Reset inputs to defaults
   */
  const resetInputs = useCallback(() => {
    setState((prev) => ({
      ...prev,
      input: DEFAULT_FREEDOM_CALCULATOR_INPUT,
      result: null,
      longevityResult: null,
      error: null,
    }));
  }, []);

  /**
   * Calculate financial freedom timeline (API)
   */
  const calculate = useCallback(async (input?: FreedomCalculatorInput) => {
    const inputToUse = input ?? state.input;

    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    try {
      const result = await calculateTimeline(inputToUse);
      setState((prev) => ({
        ...prev,
        result,
        isLoading: false,
      }));

      // Also calculate longevity for comparison
      const longevity = await calculateLongevity(
        inputToUse.currentSavings,
        inputToUse.monthlyExpenses,
        inputToUse.expectedAnnualReturn
      );
      setState((prev) => ({
        ...prev,
        longevityResult: longevity,
      }));

      return result;
    } catch (error) {
      const errorMessage = error instanceof Error
        ? error.message
        : 'Calculation failed. Please check your inputs.';

      setState((prev) => ({
        ...prev,
        isLoading: false,
        error: errorMessage,
      }));

      throw error;
    }
  }, [state.input]);

  /**
   * Calculate locally (no API call)
   */
  const calculateLocal = useCallback((): FreedomCalculatorResult | null => {
    const { input } = state;

    // Validate inputs
    if (input.currentSavings < 0 || input.monthlyExpenses < 0) {
      setState((prev) => ({
        ...prev,
        error: 'Invalid input values',
      }));
      return null;
    }

    // Calculate values
    const withdrawalRate = input.withdrawalRate ?? 4;
    const monthlyContribution = input.monthlyContribution ?? 0;
    const inflationRate = input.inflationRate ?? 2.5;
    const adjustForInflation = input.adjustForInflation ?? false;

    const annualExpenses = multiply(input.monthlyExpenses, 12);
    const effectiveReturnRate = adjustForInflation
      ? calculateRealReturn(input.expectedAnnualReturn, inflationRate)
      : input.expectedAnnualReturn;

    const targetAmount = calculateTargetAmount(annualExpenses, withdrawalRate);
    const monthsToFreedom = calculateMonthsToTarget(
      input.currentSavings,
      monthlyContribution,
      effectiveReturnRate,
      targetAmount
    );

    const yearsToFreedom = Math.floor(monthsToFreedom / 12);
    const monthsRemainder = Math.round(monthsToFreedom % 12);

    const isAchievable = monthsToFreedom < 600; // 50 years max
    const progressPercentage = percentage(input.currentSavings, targetAmount);
    const annualPassiveIncome = multiply(targetAmount, divide(withdrawalRate, 100));

    const result: FreedomCalculatorResult = {
      yearsToFreedom,
      monthsToFreedom: monthsRemainder,
      targetSavingsAmount: targetAmount,
      progressPercentage: Math.min(progressPercentage, 100),
      currentProgress: input.currentSavings,
      annualPassiveIncome,
      isSustainableIndefinitely: isAchievable && monthsToFreedom >= 600,
      isAchievable,
      withdrawalRate,
      yearlyProjections: [],
      sensitivityScenarios: [],
      message: isAchievable
        ? `Based on your inputs, you could achieve financial freedom in ${yearsToFreedom} years and ${monthsRemainder} months.`
        : 'Financial freedom is not achievable within 50 years with current inputs.',
    };

    setState((prev) => ({
      ...prev,
      result,
      isLoading: false,
    }));

    // Calculate longevity based on target amount if achievable, otherwise current savings
    const longevity = calculateSavingsLongevity(
      Math.max(input.currentSavings, targetAmount),
      input.monthlyExpenses,
      input.expectedAnnualReturn
    );

    setState((prev) => ({
      ...prev,
      longevityResult: {
        yearsUntilDepletion: Math.floor(longevity.monthsUntilDepletion / 12),
        totalMonthsUntilDepletion: longevity.monthsUntilDepletion,
        isInfinite: longevity.isInfinite,
        depletionYear: longevity.isInfinite ? null : new Date().getFullYear() + Math.floor(longevity.monthsUntilDepletion / 12),
        finalBalance: longevity.finalBalance,
        willDeplete: !longevity.isInfinite && longevity.monthsUntilDepletion < 1200,
        depletionProjections: [],
        monthlyExpenses: input.monthlyExpenses,
        currentSavings: input.currentSavings,
        annualReturnRate: input.expectedAnnualReturn,
        message: longevity.isInfinite
          ? 'Your savings will last indefinitely.'
          : `Your savings will last approximately ${Math.floor(longevity.monthsUntilDepletion / 12)} years.`,
      },
    }));

    return result;
  }, [state.input]);

  return {
    /** Current input values */
    input: state.input,
    /** Calculation result */
    result: state.result,
    /** Longevity result */
    longevityResult: state.longevityResult,
    /** Loading state */
    isLoading: state.isLoading,
    /** Error message */
    error: state.error,
    /** Default parameters */
    defaults: state.defaults,
    /** Update input field */
    updateInput,
    /** Reset inputs to defaults */
    resetInputs,
    /** Calculate using API */
    calculate,
    /** Calculate locally */
    calculateLocal,
  };
}
