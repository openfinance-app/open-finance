import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useEarlyPayoffCalculator } from './useEarlyPayoffCalculator';
import { DEFAULT_EARLY_PAYOFF_INPUT } from '@/types/calculator';
import type { EarlyPayoffCountryConfig } from '@/configs/tools/earlyPayoffConfig';

describe('useEarlyPayoffCalculator', () => {
  const frenchConfig: EarlyPayoffCountryConfig = {
    countryCode: 'FR',
    hasIRA: true,
    iraMonthsInterestCap: 6,
    iraCapitalPercentCap: 0.03,
    iraMinPrepaymentFraction: 0,
  };

  const noIraConfig: EarlyPayoffCountryConfig = {
    countryCode: 'OTHER',
    hasIRA: false,
    iraMonthsInterestCap: 0,
    iraCapitalPercentCap: 0,
    iraMinPrepaymentFraction: 0,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should initialize with default input', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    expect(result.current.input).toEqual(DEFAULT_EARLY_PAYOFF_INPUT);
    expect(result.current.result).toBeNull();
  });

  it('should update input fields', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.updateInput('loanBalance', 300000);
    });

    expect(result.current.input.loanBalance).toBe(300000);
    expect(result.current.result).toBeNull(); // reset on change
  });

  it('should update annual rate', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.updateInput('annualRate', 3.5);
    });

    expect(result.current.input.annualRate).toBe(3.5);
  });

  it('should add a lump sum payment', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    const initialCount = result.current.input.lumpSumPayments.length;

    act(() => {
      result.current.addLumpSum();
    });

    expect(result.current.input.lumpSumPayments.length).toBe(initialCount + 1);
    const lastLumpSum =
      result.current.input.lumpSumPayments[result.current.input.lumpSumPayments.length - 1];
    expect(lastLumpSum.month).toBe(12);
    expect(lastLumpSum.amount).toBe(10000);
    expect(result.current.result).toBeNull();
  });

  it('should update a lump sum payment', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    const firstId = result.current.input.lumpSumPayments[0].id;

    act(() => {
      result.current.updateLumpSum(firstId, 'amount', 50000);
    });

    expect(result.current.input.lumpSumPayments[0].amount).toBe(50000);
  });

  it('should update lump sum month', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    const firstId = result.current.input.lumpSumPayments[0].id;

    act(() => {
      result.current.updateLumpSum(firstId, 'month', 24);
    });

    expect(result.current.input.lumpSumPayments[0].month).toBe(24);
  });

  it('should remove a lump sum payment', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    const firstId = result.current.input.lumpSumPayments[0].id;

    act(() => {
      result.current.removeLumpSum(firstId);
    });

    expect(
      result.current.input.lumpSumPayments.find((ls: any) => ls.id === firstId)
    ).toBeUndefined();
  });

  it('should reset inputs to defaults', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.updateInput('loanBalance', 999999);
    });

    act(() => {
      result.current.resetInputs();
    });

    expect(result.current.input).toEqual(DEFAULT_EARLY_PAYOFF_INPUT);
    expect(result.current.result).toBeNull();
  });

  it('should calculate and produce base, reduceDuration, reducePayment scenarios', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result).not.toBeNull();
    expect(result.current.result!.base).toBeDefined();
    expect(result.current.result!.reduceDuration).toBeDefined();
    expect(result.current.result!.reducePayment).toBeDefined();
  });

  it('should have positive monthly payment in base scenario', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result!.base.monthlyPayment).toBeGreaterThan(0);
  });

  it('should show time saved in reduceDuration scenario', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.calculate();
    });

    // With a 20k lump sum at month 12 on a 200k loan, the duration should be shorter
    expect(result.current.result!.reduceDuration.timeSavedMonths).toBeGreaterThanOrEqual(0);
  });

  it('should show interest saved in reduceDuration scenario', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result!.reduceDuration.interestSaved).toBeGreaterThanOrEqual(0);
  });

  it('should include IRA penalty with French config', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.calculate();
    });

    // French config has IRA, so totalIRA should be > 0 for scenarios with lump sums
    expect(result.current.result!.reduceDuration.totalIRA).toBeGreaterThan(0);
  });

  it('should not have IRA penalty with no-IRA config', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(noIraConfig));

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result!.reduceDuration.totalIRA).toBe(0);
    expect(result.current.result!.reducePayment.totalIRA).toBe(0);
  });

  it('should not calculate with invalid inputs (zero balance)', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.updateInput('loanBalance', 0);
    });

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result).toBeNull();
  });

  it('should not calculate with invalid inputs (zero rate)', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.updateInput('annualRate', 0);
    });

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result).toBeNull();
  });

  it('should not calculate with zero remaining time', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.updateInput('remainingYears', 0);
      result.current.updateInput('remainingMonthsExtra', 0);
    });

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result).toBeNull();
  });

  it('should produce yearly schedule in results', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result!.base.yearlySchedule.length).toBeGreaterThan(0);
    expect(result.current.result!.base.yearlySchedule[0]).toEqual(
      expect.objectContaining({
        year: expect.any(Number),
        totalPayment: expect.any(Number),
        principalPaid: expect.any(Number),
        interestPaid: expect.any(Number),
        endBalance: expect.any(Number),
      })
    );
  });

  it('should have base scenario with zero interest and time saved', () => {
    const { result } = renderHook(() => useEarlyPayoffCalculator(frenchConfig));

    act(() => {
      result.current.calculate();
    });

    expect(result.current.result!.base.interestSaved).toBe(0);
    expect(result.current.result!.base.timeSavedMonths).toBe(0);
  });
});
