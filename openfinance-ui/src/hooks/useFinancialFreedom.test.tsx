/**
 * Tests for useFinancialFreedom Hook
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { useFinancialFreedom } from './useFinancialFreedom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import * as calculatorApi from '../services/calculatorApi';

// Mock the calculator API
vi.mock('../services/calculatorApi');

const mockCalculateTimeline = vi.mocked(calculatorApi.calculateTimeline);
const mockCalculateLongevity = vi.mocked(calculatorApi.calculateLongevity);
const mockGetCalculationDefaults = vi.mocked(calculatorApi.getCalculationDefaults);

describe('useFinancialFreedom', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Mock defaults
    mockGetCalculationDefaults.mockResolvedValue({
      defaultWithdrawalRate: 4.0,
      defaultInflationRate: 2.5,
      defaultReturnRate: 7.0,
      maxProjectionYears: 30,
      defaultMonthlyContribution: 0,
      minimumWithdrawalRate: 0.5,
      maximumWithdrawalRate: 10.0,
      minimumReturnRate: -10.0,
      maximumReturnRate: 30.0,
    });
  });

  describe('initialization', () => {
    it('should initialize with default state', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      expect(result.current.input).toBeDefined();
      expect(result.current.result).toBeNull();
      expect(result.current.longevityResult).toBeNull();
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBeNull();
    });

    it('should load defaults on mount', async () => {
      const { result } = renderHook(() => useFinancialFreedom());

      await waitFor(() => {
        expect(result.current.defaults).not.toBeNull();
      });

      expect(mockGetCalculationDefaults).toHaveBeenCalledTimes(1);
      expect(result.current.defaults?.defaultWithdrawalRate).toBe(4.0);
    });

    it('should handle defaults loading error gracefully', async () => {
      mockGetCalculationDefaults.mockRejectedValue(new Error('API Error'));
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      const { result } = renderHook(() => useFinancialFreedom());

      await waitFor(() => {
        expect(consoleSpy).toHaveBeenCalled();
      });

      expect(result.current.defaults).toBeNull();
      consoleSpy.mockRestore();
    });
  });

  describe('updateInput', () => {
    it('should update a single input field', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
      });

      expect(result.current.input.currentSavings).toBe(50000);
    });

    it('should update multiple input fields', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.updateInput('monthlyExpenses', 2500);
        result.current.updateInput('expectedAnnualReturn', 7);
      });

      expect(result.current.input.currentSavings).toBe(50000);
      expect(result.current.input.monthlyExpenses).toBe(2500);
      expect(result.current.input.expectedAnnualReturn).toBe(7);
    });

    it('should clear error when updating input', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      // Set an error state
      act(() => {
        result.current.calculateLocal();
      });

      // Update input should clear error
      act(() => {
        result.current.updateInput('currentSavings', 50000);
      });

      expect(result.current.error).toBeNull();
    });
  });

  describe('resetInputs', () => {
    it('should reset inputs to defaults', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      // Modify inputs
      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.updateInput('monthlyExpenses', 2500);
      });

      // Reset
      act(() => {
        result.current.resetInputs();
      });

      expect(result.current.input.currentSavings).toBe(50000);
      expect(result.current.input.monthlyExpenses).toBe(2500);
    });

    it('should clear error when resetting', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      // Set error state
      act(() => {
        result.current.updateInput('currentSavings', -1000);
        result.current.calculateLocal();
      });

      // Reset should clear error
      act(() => {
        result.current.resetInputs();
      });

      expect(result.current.error).toBeNull();
    });
  });

  describe('calculate (API)', () => {
    const mockTimelineResult = {
      yearsToFreedom: 15,
      monthsToFreedom: 6,
      targetSavingsAmount: 750000,
      progressPercentage: 6.67,
      currentProgress: 50000,
      annualPassiveIncome: 30000,
      isSustainableIndefinitely: false,
      isAchievable: true,
      yearlyProjections: [],
      sensitivityScenarios: [],
      message: 'You can achieve financial freedom in 15 years and 6 months.',
    };

    const mockLongevityResult = {
      yearsUntilDepletion: 25,
      totalMonthsUntilDepletion: 300,
      isInfinite: false,
      depletionYear: 2051,
      finalBalance: 0,
      willDeplete: true,
      depletionProjections: [],
      monthlyExpenses: 2500,
      currentSavings: 50000,
      annualReturnRate: 7,
      message: 'Your savings will last approximately 25 years.',
    };

    it('should calculate timeline using API', async () => {
      mockCalculateTimeline.mockResolvedValue(mockTimelineResult);
      mockCalculateLongevity.mockResolvedValue(mockLongevityResult);

      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.updateInput('monthlyExpenses', 2500);
        result.current.updateInput('expectedAnnualReturn', 7);
      });

      await act(async () => {
        await result.current.calculate();
      });

      expect(mockCalculateTimeline).toHaveBeenCalledWith(
        expect.objectContaining({
          currentSavings: 50000,
          monthlyExpenses: 2500,
          expectedAnnualReturn: 7,
        })
      );

      expect(result.current.result).toEqual(mockTimelineResult);
      expect(result.current.longevityResult).toEqual(mockLongevityResult);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBeNull();
    });

    it('should set loading state during calculation', async () => {
      mockCalculateTimeline.mockImplementation(
        () => new Promise(resolve => setTimeout(() => resolve(mockTimelineResult), 100))
      );
      mockCalculateLongevity.mockResolvedValue(mockLongevityResult);

      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
      });

      let calculatePromise: Promise<any>;
      act(() => {
        calculatePromise = result.current.calculate();
      });

      expect(result.current.isLoading).toBe(true);

      await act(async () => {
        await calculatePromise;
      });

      expect(result.current.isLoading).toBe(false);
    });

    it('should handle API errors', async () => {
      mockCalculateTimeline.mockRejectedValue(new Error('API Error'));

      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
      });

      await act(async () => {
        try {
          await result.current.calculate();
        } catch (error) {
          // Expected to throw
        }
      });

      expect(result.current.error).toBe('API Error');
      expect(result.current.isLoading).toBe(false);
    });

    it('should handle non-Error exceptions', async () => {
      mockCalculateTimeline.mockRejectedValue('String error');

      const { result } = renderHook(() => useFinancialFreedom());

      await act(async () => {
        try {
          await result.current.calculate();
        } catch (error) {
          // Expected to throw
        }
      });

      expect(result.current.error).toBe('Calculation failed. Please check your inputs.');
    });
  });

  describe('calculateLocal', () => {
    it('should calculate locally without API call', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.updateInput('monthlyExpenses', 2500);
        result.current.updateInput('expectedAnnualReturn', 7);
        result.current.updateInput('monthlyContribution', 500);
        result.current.updateInput('withdrawalRate', 4);
      });

      act(() => {
        result.current.calculateLocal();
      });

      expect(result.current.result).not.toBeNull();
      expect(result.current.result?.targetSavingsAmount).toBe(750000);
      expect(result.current.result?.isAchievable).toBeDefined();
      expect(mockCalculateTimeline).not.toHaveBeenCalled();
    });

    it('should calculate with inflation adjustment', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.updateInput('monthlyExpenses', 2500);
        result.current.updateInput('expectedAnnualReturn', 7);
        result.current.updateInput('inflationRate', 2.5);
        result.current.updateInput('adjustForInflation', true);
      });

      act(() => {
        result.current.calculateLocal();
      });

      expect(result.current.result).not.toBeNull();
      // Real return should be used (lower than nominal)
      expect(result.current.result?.yearsToFreedom).toBeGreaterThan(0);
    });

    it('should handle negative inputs', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', -1000);
        result.current.updateInput('monthlyExpenses', 2500);
      });

      let calculationResult: any;
      act(() => {
        calculationResult = result.current.calculateLocal();
      });

      expect(calculationResult).toBeNull();
      expect(result.current.error).toBe('Invalid input values');
    });

    it('should calculate longevity result', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 1000000);
        result.current.updateInput('monthlyExpenses', 2500);
        result.current.updateInput('expectedAnnualReturn', 5);
      });

      act(() => {
        result.current.calculateLocal();
      });

      expect(result.current.longevityResult).not.toBeNull();
      expect(result.current.longevityResult?.isInfinite).toBe(true);
    });

    it('should use default values for optional fields', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.updateInput('monthlyExpenses', 2500);
        result.current.updateInput('expectedAnnualReturn', 7);
        // Don't set optional fields
      });

      act(() => {
        result.current.calculateLocal();
      });

      expect(result.current.result).not.toBeNull();
      expect(result.current.result?.targetSavingsAmount).toBe(750000); // Uses default 4% withdrawal
    });

    it('should mark as not achievable for very long timelines', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 1000);
        result.current.updateInput('monthlyExpenses', 5000);
        result.current.updateInput('expectedAnnualReturn', 2);
        result.current.updateInput('monthlyContribution', 100);
      });

      act(() => {
        result.current.calculateLocal();
      });

      expect(result.current.result).not.toBeNull();
      expect(result.current.result?.isAchievable).toBe(false);
    });
  });

  describe('integration', () => {
    it('should maintain state across multiple operations', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      // Update inputs
      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.updateInput('monthlyExpenses', 2500);
      });

      // Calculate locally
      act(() => {
        result.current.calculateLocal();
      });

      const firstResult = result.current.result;

      // Update another input
      act(() => {
        result.current.updateInput('expectedAnnualReturn', 10);
      });

      // Calculate again
      act(() => {
        result.current.calculateLocal();
      });

      const secondResult = result.current.result;

      // Results should be different
      expect(firstResult).not.toEqual(secondResult);
      expect(secondResult?.yearsToFreedom).toBeLessThan(firstResult?.yearsToFreedom || 0);
    });

    it('should clear results when resetting', () => {
      const { result } = renderHook(() => useFinancialFreedom());

      act(() => {
        result.current.updateInput('currentSavings', 50000);
        result.current.calculateLocal();
      });

      expect(result.current.result).not.toBeNull();

      act(() => {
        result.current.resetInputs();
      });

      // Result should be cleared
      expect(result.current.result).toBeNull();
    });
  });
});
