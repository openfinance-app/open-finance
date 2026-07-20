import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';

vi.mock('@/context/NumberFormatContext', () => ({
    useNumberFormat: () => ({
        numberFormat: '1,234.56' as const,
    }),
}));

vi.mock('@/utils/currency', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/utils/currency')>();
    return {
        ...actual,
        formatCurrency: vi.fn((amount: number, code?: string) => `$${amount.toFixed(2)}`),
        formatCurrencyCompact: vi.fn((amount: number) => `$${(amount / 1000).toFixed(0)}K`),
        formatCurrencyWithColor: vi.fn((amount: number) => ({
            formatted: `$${amount.toFixed(2)}`,
            className: amount >= 0 ? 'text-green' : 'text-red',
        })),
    };
});

import { useFormatCurrency } from './useFormatCurrency';

describe('useFormatCurrency', () => {
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
});
