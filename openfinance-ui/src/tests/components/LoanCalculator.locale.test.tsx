import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
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

const settings = {
  userId: 1,
  numberFormat: '1 234,56',
  decimalPlacesOverrideEnabled: true,
  preferredDecimalPlaces: 4,
};
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: settings, isLoading: false }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn() }),
}));

describe('LoanCalculator Y-axis formatting', () => {
  beforeEach(() => {
    mockAuthentication();
    rechartsMocks.yTickFormatter = undefined;
  });
  it('uses the shared number and decimal preferences for chart axes', () => {
    renderWithProviders(<LoanCalculator />);
    expect(rechartsMocks.yTickFormatter?.(1500)).toBe('1,5000K');
  });
});
