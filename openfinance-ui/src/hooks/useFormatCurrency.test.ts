import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';

const { decimalsRef } = vi.hoisted(() => ({ decimalsRef: { current: null as number | null } }));

vi.mock('@/context/NumberFormatContext', () => ({
  useNumberFormat: () => ({
    numberFormat: '1,234.56' as const,
  }),
}));

vi.mock('@/context/DecimalPlacesContext', () => ({
  useDecimalPlaces: () => ({ effectiveDecimals: decimalsRef.current }),
}));

vi.mock('@/utils/currency', async importOriginal => {
  const actual = await importOriginal<typeof import('@/utils/currency')>();
  return {
    ...actual,
    formatCurrency: vi.fn(
      (amount: number, code?: string, options?: { decimals?: number }) =>
        `$${amount.toFixed(options?.decimals ?? 2)}`
    ),
    formatCurrencyCompact: vi.fn((amount: number) => `$${(amount / 1000).toFixed(0)}K`),
    formatCurrencyWithColor: vi.fn((amount: number) => ({
      formatted: `$${amount.toFixed(2)}`,
      className: amount >= 0 ? 'text-green' : 'text-red',
    })),
  };
});

import { useFormatCurrency } from './useFormatCurrency';

describe('useFormatCurrency', () => {
  beforeEach(() => {
    decimalsRef.current = null;
  });

  it('returns format function', () => {
    const { result } = renderHook(() => useFormatCurrency());
    expect(typeof result.current.format).toBe('function');
  });

  it('returns formatCompact function', () => {
    const { result } = renderHook(() => useFormatCurrency());
    expect(typeof result.current.formatCompact).toBe('function');
  });

  it('returns formatWithColor function', () => {
    const { result } = renderHook(() => useFormatCurrency());
    expect(typeof result.current.formatWithColor).toBe('function');
  });

  it('format calls formatCurrency with number format', () => {
    const { result } = renderHook(() => useFormatCurrency());
    const formatted = result.current.format(1234.56, 'USD');
    expect(formatted).toBe('$1234.56');
  });

  it('formatWithColor returns formatted and className', () => {
    const { result } = renderHook(() => useFormatCurrency());
    const res = result.current.formatWithColor(100, 'USD');
    expect(res).toHaveProperty('formatted');
    expect(res).toHaveProperty('className');
  });

  it('provides numberFormat from context', () => {
    const { result } = renderHook(() => useFormatCurrency());
    expect(result.current.numberFormat).toBe('1,234.56');
  });

  it('applies the decimal-places override to formatted output when enabled', () => {
    decimalsRef.current = 4;
    const { result } = renderHook(() => useFormatCurrency());
    // 4 decimals from the override.
    expect(result.current.format(1234.56789, 'USD')).toBe('$1234.5679');
    // Explicit decimals still win over the override.
    expect(result.current.format(1234.5, 'USD', { decimals: 2 })).toBe('$1234.50');
  });
});
