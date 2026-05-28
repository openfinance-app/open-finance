import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import { CurrencyDisplayProvider, useCurrencyDisplay } from './CurrencyDisplayContext';
import type { AmountDisplayMode } from './CurrencyDisplayContext';

describe('CurrencyDisplayContext', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CurrencyDisplayProvider>{children}</CurrencyDisplayProvider>
  );

  it('provides default display mode of base', () => {
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });
    expect(result.current.displayMode).toBe('base');
  });

  it('provides null secondary currency by default', () => {
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });
    expect(result.current.secondaryCurrency).toBeNull();
  });

  it('updates display mode', () => {
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });

    act(() => {
      result.current.setDisplayMode('native');
    });

    expect(result.current.displayMode).toBe('native');
  });

  it('persists display mode to localStorage', () => {
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });

    act(() => {
      result.current.setDisplayMode('both');
    });

    expect(localStorage.getItem('open_finance_amount_display_mode')).toBe('both');
  });

  it('sets and clears secondary currency', () => {
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });

    act(() => {
      result.current.setSecondaryCurrency('USD');
    });
    expect(result.current.secondaryCurrency).toBe('USD');
    expect(localStorage.getItem('open_finance_secondary_currency')).toBe('USD');

    act(() => {
      result.current.setSecondaryCurrency(null);
    });
    expect(result.current.secondaryCurrency).toBeNull();
    expect(localStorage.getItem('open_finance_secondary_currency')).toBeNull();
  });

  it('trims whitespace from secondary currency', () => {
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });

    act(() => {
      result.current.setSecondaryCurrency('  EUR  ');
    });
    expect(result.current.secondaryCurrency).toBe('EUR');
  });

  it('treats empty string as null for secondary currency', () => {
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });

    act(() => {
      result.current.setSecondaryCurrency('');
    });
    expect(result.current.secondaryCurrency).toBeNull();
  });

  it('reads initial mode from localStorage', () => {
    localStorage.setItem('open_finance_amount_display_mode', 'native');
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });
    expect(result.current.displayMode).toBe('native');
  });

  it('reads initial secondary currency from localStorage', () => {
    localStorage.setItem('open_finance_secondary_currency', 'GBP');
    const { result } = renderHook(() => useCurrencyDisplay(), { wrapper });
    expect(result.current.secondaryCurrency).toBe('GBP');
  });

  it('throws when used outside provider', () => {
    expect(() => {
      renderHook(() => useCurrencyDisplay());
    }).toThrow('useCurrencyDisplay must be used within a CurrencyDisplayProvider');
  });
});
