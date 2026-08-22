import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { EarlyPayoffCalculator } from './EarlyPayoffCalculator';

const mockUseAuthContext = vi.fn();
vi.mock('@/context/AuthContext', async importOriginal => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return {
    ...actual,
    useAuthContext: () => mockUseAuthContext(),
  };
});

const mockUseEarlyPayoffCalculator = vi.fn();
vi.mock('@/hooks/useEarlyPayoffCalculator', () => ({
  useEarlyPayoffCalculator: () => mockUseEarlyPayoffCalculator(),
}));

vi.mock('@/hooks/useCountryToolConfig', () => ({
  useCountryToolConfig: () => ({ earlyPayoffConfig: { hasIRA: false } }),
}));

vi.mock('recharts', () => ({
  AreaChart: ({ children }: { children?: React.ReactNode }) => <svg>{children}</svg>,
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  Legend: () => null,
  ResponsiveContainer: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
}));

const yearlySchedule = [] as Array<{ year: number; endBalance: number }>;

const baseScenario = {
  monthlyPayment: 1000,
  finalMonthlyPayment: 1000,
  totalMonths: 240,
  totalInterest: 138000,
  totalIRA: 0,
  totalLumpSum: 0,
  interestSaved: 0,
  netSavings: 0,
  timeSavedMonths: 0,
  yearlySchedule,
};

const defaultHookState = {
  input: {
    loanBalance: 200000,
    annualRate: 3.5,
    remainingYears: 20,
    remainingMonthsExtra: 0,
    monthlyExtraPayment: 0,
    lumpSumPayments: [] as Array<{ id: string; month: number; amount: number }>,
  },
  result: {
    base: baseScenario,
    reduceDuration: { ...baseScenario, monthlyPayment: 1100, totalMonths: 180 },
    reducePayment: { ...baseScenario, monthlyPayment: 1200, finalMonthlyPayment: 900 },
  },
  updateInput: vi.fn(),
  addLumpSum: vi.fn(),
  updateLumpSum: vi.fn(),
  removeLumpSum: vi.fn(),
  resetInputs: vi.fn(),
  calculate: vi.fn(),
};

describe('EarlyPayoffCalculator', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    mockUseAuthContext.mockReturnValue({ baseCurrency: 'USD', isAuthenticated: true });
    mockUseEarlyPayoffCalculator.mockReturnValue(defaultHookState);
  });

  it('renders the base scenario monthly payment', () => {
    renderWithProviders(<EarlyPayoffCalculator />);

    expect(screen.getAllByText('$1,000.00').length).toBeGreaterThan(0);
  });

  it('shows the monthly payment tooltip with ConvertedAmount on hover', async () => {
    renderWithProviders(<EarlyPayoffCalculator />);

    const visible = screen.getAllByText('$1,000.00')[0];
    expect(visible).toBeInTheDocument();

    const trigger = visible.closest('[data-state]') as HTMLElement;
    fireEvent.pointerMove(trigger);

    const tooltip = await waitFor(() => screen.getByRole('tooltip'));
    expect(tooltip).toHaveTextContent('$1,000.00');
    expect(tooltip.querySelector('[data-testid="converted-amount"]')).not.toBeNull();
  });

  it('shows the total interest tooltip with ConvertedAmount on hover', async () => {
    renderWithProviders(<EarlyPayoffCalculator />);

    const visible = screen.getAllByText('$138,000.00')[0];
    const trigger = visible.closest('[data-state]') as HTMLElement;
    fireEvent.pointerMove(trigger);

    const tooltip = await waitFor(() => screen.getByRole('tooltip'));
    expect(tooltip).toHaveTextContent('$138,000.00');
    expect(tooltip.querySelector('[data-testid="converted-amount"]')).not.toBeNull();
  });
});
