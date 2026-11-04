import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { AllProviders, mockAuthentication } from '@/test/test-utils';
import { DEFAULT_BUY_RENT_INPUTS } from '@/types/realEstateTools';
import { createInvestmentInputs } from '@/test/simulation-fixtures';
import {
  useDebounce,
  useThrottle,
  useMemoizedBuyRentInputs,
  useMemoizedInvestmentInputs,
  useMemoizedFormatter,
  useCalculationPerformance,
  useSlowCalculationWarning,
  usePrefetchCalculations,
  useLazyChart,
} from '@/components/real-estate-tools/performance';
import { PREFETCH_IDLE_DELAY_MS, PREFETCH_FALLBACK_DELAY_MS } from '@/constants/timing';

beforeEach(() => {
  mockAuthentication();
  vi.useFakeTimers();
});
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('calculation scheduling', () => {
  it('debounces to the latest edit and cancels work on unmount', () => {
    const hook = renderHook(({ value }) => useDebounce(value, 100), {
      initialProps: { value: 1 },
      wrapper: AllProviders,
    });
    hook.rerender({ value: 2 });
    act(() => vi.advanceTimersByTime(50));
    hook.rerender({ value: 3 });
    act(() => vi.advanceTimersByTime(99));
    expect(hook.result.current).toBe(1);
    act(() => vi.advanceTimersByTime(1));
    expect(hook.result.current).toBe(3);
    hook.rerender({ value: 4 });
    hook.unmount();
    act(() => vi.advanceTimersByTime(100));
    expect(hook.result.current).toBe(3);
  });
  it('throttles rapid edits while delivering the latest trailing value', () => {
    const callback = vi.fn();
    const hook = renderHook(() => useThrottle(callback, 100), { wrapper: AllProviders });
    act(() => {
      hook.result.current('first');
      hook.result.current('obsolete');
      hook.result.current('latest');
    });
    expect(callback).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(100));
    expect(callback.mock.calls).toEqual([['first'], ['latest']]);
  });
  it('cancels a trailing callback when its component unmounts', () => {
    const callback = vi.fn();
    const hook = renderHook(() => useThrottle(callback, 100), { wrapper: AllProviders });
    act(() => {
      hook.result.current('first');
      hook.result.current('discard');
    });
    hook.unmount();
    act(() => vi.advanceTimersByTime(100));
    expect(callback).toHaveBeenCalledTimes(1);
  });
  it('prefetches after inactivity and allows an explicit refresh', () => {
    const calculate = vi.fn();
    const hook = renderHook(() => usePrefetchCalculations(DEFAULT_BUY_RENT_INPUTS, calculate), {
      wrapper: AllProviders,
    });
    act(() => vi.advanceTimersByTime(PREFETCH_IDLE_DELAY_MS + PREFETCH_FALLBACK_DELAY_MS));
    expect(calculate).toHaveBeenCalledOnce();
    expect(hook.result.current.isPrefetched).toBe(true);
    act(() => hook.result.current.resetPrefetch());
    act(() => vi.advanceTimersByTime(PREFETCH_IDLE_DELAY_MS + PREFETCH_FALLBACK_DELAY_MS));
    expect(calculate).toHaveBeenCalledTimes(2);
  });
  it('cancels fallback prefetch when the user leaves before it starts', () => {
    const calculate = vi.fn();
    const hook = renderHook(() => usePrefetchCalculations(DEFAULT_BUY_RENT_INPUTS, calculate), {
      wrapper: AllProviders,
    });
    act(() => vi.advanceTimersByTime(PREFETCH_IDLE_DELAY_MS));
    expect(hook.result.current.isPrefetched).toBe(false);
    hook.unmount();
    act(() => vi.advanceTimersByTime(PREFETCH_FALLBACK_DELAY_MS));
    expect(calculate).not.toHaveBeenCalled();
  });
  it('prefetches again after simulation inputs change', () => {
    const calculate = vi.fn();
    const hook = renderHook(({ inputs }) => usePrefetchCalculations(inputs, calculate), {
      initialProps: { inputs: DEFAULT_BUY_RENT_INPUTS },
      wrapper: AllProviders,
    });
    act(() => vi.advanceTimersByTime(PREFETCH_IDLE_DELAY_MS + PREFETCH_FALLBACK_DELAY_MS));
    hook.rerender({ inputs: { ...DEFAULT_BUY_RENT_INPUTS, currency: 'USD' } });
    expect(hook.result.current.isPrefetched).toBe(false);
    act(() => vi.advanceTimersByTime(PREFETCH_IDLE_DELAY_MS + PREFETCH_FALLBACK_DELAY_MS));
    expect(calculate).toHaveBeenCalledTimes(2);
  });
  it('cancels queued browser-idle work and ignores a callback already dispatched during unmount', () => {
    let queued: IdleRequestCallback | undefined;
    vi.stubGlobal(
      'requestIdleCallback',
      vi.fn((callback: IdleRequestCallback) => {
        queued = callback;
        return 7;
      })
    );
    const cancel = vi.fn();
    vi.stubGlobal('cancelIdleCallback', cancel);
    const calculate = vi.fn();
    const hook = renderHook(() => usePrefetchCalculations(DEFAULT_BUY_RENT_INPUTS, calculate), {
      wrapper: AllProviders,
    });
    act(() => vi.advanceTimersByTime(PREFETCH_IDLE_DELAY_MS));
    expect(calculate).not.toHaveBeenCalled();
    expect(queued).toBeDefined();
    hook.unmount();
    expect(cancel).toHaveBeenCalledWith(7);
    act(() => queued!({ didTimeout: false, timeRemaining: () => 10 }));
    expect(calculate).not.toHaveBeenCalled();
  });
  it('loads the chart only on demand and reuses the loaded component', async () => {
    const hook = renderHook(useLazyChart, { wrapper: AllProviders });
    expect(hook.result.current.ChartComponent).toBeNull();
    await act(async () => {
      await hook.result.current.loadChart();
    });
    expect(hook.result.current.ChartComponent).not.toBeNull();
    expect(hook.result.current.isLoading).toBe(false);
    const chart = hook.result.current.ChartComponent;
    await act(async () => {
      await hook.result.current.loadChart();
    });
    expect(hook.result.current.ChartComponent).toBe(chart);
  });
});

describe('memoized simulation inputs', () => {
  it('preserves an equivalent purchase input but invalidates currency changes', () => {
    const original = { ...structuredClone(DEFAULT_BUY_RENT_INPUTS), currency: 'EUR' };
    const hook = renderHook(({ inputs }) => useMemoizedBuyRentInputs(inputs), {
      initialProps: { inputs: original },
      wrapper: AllProviders,
    });
    hook.rerender({ inputs: structuredClone(original) });
    expect(hook.result.current).toBe(original);
    hook.rerender({ inputs: { ...original, currency: 'USD' } });
    expect(hook.result.current.currency).toBe('USD');
  });
  it('invalidates cached investment inputs when the tax year or household income changes', () => {
    const original = createInvestmentInputs();
    const hook = renderHook(({ inputs }) => useMemoizedInvestmentInputs(inputs), {
      initialProps: { inputs: original },
      wrapper: AllProviders,
    });
    hook.rerender({ inputs: structuredClone(original) });
    expect(hook.result.current).toBe(original);
    const changed = {
      ...original,
      tax: { ...original.tax!, incomeYear: 2025 as const, otherHouseholdIncome: 1000 },
    };
    hook.rerender({ inputs: changed });
    expect(hook.result.current.tax).toEqual(changed.tax);
  });
});

describe('calculation feedback', () => {
  it('reports average and most recent durations after batched completions', () => {
    const { result } = renderHook(() => useCalculationPerformance(), { wrapper: AllProviders });
    act(() => {
      result.current.trackCalculation(100);
      result.current.trackCalculation(300);
    });
    expect(result.current.performance).toEqual({
      totalCalculations: 2,
      averageCalculationTime: 200,
      lastCalculationTime: 300,
    });
  });
  it('resets a slow warning for the next calculation', () => {
    const now = vi.spyOn(performance, 'now');
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    now.mockReturnValue(100);
    const { result } = renderHook(() => useSlowCalculationWarning(500), { wrapper: AllProviders });
    act(() => result.current.startCalculation());
    now.mockReturnValue(800);
    act(() => expect(result.current.endCalculation()).toBe(700));
    expect(result.current.isSlow).toBe(true);
    expect(warn).toHaveBeenCalledOnce();
    act(() => result.current.startCalculation());
    now.mockReturnValue(900);
    act(() => result.current.endCalculation());
    expect(result.current.isSlow).toBe(false);
  });
  it('respects currency precision and percentage units', () => {
    const hook = renderHook(({ currency }) => useMemoizedFormatter(currency), {
      initialProps: { currency: 'USD' },
      wrapper: AllProviders,
    });
    expect(hook.result.current.formatCurrency(1234.567)).toBe('$1,234.57');
    expect(hook.result.current.formatPercent(5)).toBe('5.00%');
    expect(hook.result.current.formatCompact(1200000)).toBe('1.2M');
    hook.rerender({ currency: 'JPY' });
    expect(hook.result.current.formatCurrency(1234.567)).toBe('¥1,235');
  });
});
