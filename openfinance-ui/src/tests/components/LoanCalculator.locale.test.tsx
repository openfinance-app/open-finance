import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { LoanCalculator } from '@/components/loan-calculator/LoanCalculator';

// Capture the YAxis tickFormatter without relying on Recharts layout (which does not
// happen in jsdom because ResponsiveContainer has zero size).
const rechartsMocks = vi.hoisted(() => ({
  yTickFormatter: undefined as ((v: number) => string) | undefined,
}));

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: any) => <svg>{children}</svg>,
  AreaChart: ({ children }: any) => <g>{children}</g>,
  Area: () => null,
  XAxis: () => null,
  YAxis: (props: any) => {
    rechartsMocks.yTickFormatter = props.tickFormatter;
    return null;
  },
  CartesianGrid: () => null,
  Tooltip: () => null,
}));

vi.mock('@/context/AuthContext', async importOriginal => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return { ...actual, useAuthContext: () => ({ baseCurrency: 'USD', isAuthenticated: true }) };
});

const mockResult = {
  monthlyPayment: 1319.91,
  totalInterest: 116778.4,
  totalPayment: 316778.4,
  amortizationSchedule: Array.from({ length: 12 }, (_, i) => ({
    paymentNumber: i + 1,
    paymentAmount: 1319.91,
    principalPortion: 486 + i,
    interestPortion: 833 - i,
    remainingBalance: 200000 - i * 1000,
    cumulativeInterest: i * 100,
  })),
};

vi.mock('@/hooks/useLoanCalculator', () => ({
  useLoanCalculator: () => ({
    input: { principal: 200000, annualRate: 5, years: 20 },
    result: mockResult,
    updateInput: vi.fn(),
    resetInputs: vi.fn(),
    calculate: vi.fn(),
  }),
}));

vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({ format: (a: number) => `$${a.toFixed(2)}` }),
}));

describe('LoanCalculator Y-axis locale', () => {
  beforeEach(() => {
    rechartsMocks.yTickFormatter = undefined;
  });

  it('formats Y-axis ticks with the active i18n locale, not a hardcoded en-US', () => {
    renderWithProviders(<LoanCalculator />);

    expect(rechartsMocks.yTickFormatter).toBeTypeOf('function');

    // Record the locale each Intl.NumberFormat is constructed with, only around the
    // explicit tick call, to avoid capturing unrelated formatting during render.
    const locales: unknown[] = [];
    const OrigNumberFormat = Intl.NumberFormat;
    // @ts-expect-error deliberately override the Intl constructor for this assertion
    Intl.NumberFormat = function (locale: string, opts: Intl.NumberFormatOptions) {
      locales.push(locale);
      return new OrigNumberFormat(locale, opts);
    } as unknown as typeof Intl.NumberFormat;
    try {
      rechartsMocks.yTickFormatter!(1_000_000);
    } finally {
      Intl.NumberFormat = OrigNumberFormat;
    }

    expect(locales).toContain(i18n.language); // 'en' in the test harness
    expect(locales).not.toContain('en-US');
  });
});
