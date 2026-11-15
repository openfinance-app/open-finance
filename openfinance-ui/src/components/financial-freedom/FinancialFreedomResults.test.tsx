import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { FinancialFreedomCalculator } from '@/components/financial-freedom/FinancialFreedomCalculator';
import { useFinancialFreedom } from '@/hooks/useFinancialFreedom';
import { useUserFinancialData } from '@/hooks/useUserFinancialData';
import { DEFAULT_FREEDOM_CALCULATOR_INPUT } from '@/types/calculator';
import type {
  FreedomCalculatorResult,
  SavingsLongevityResult,
  YearlyProjection,
} from '@/types/calculator';

vi.mock('@/hooks/useFinancialFreedom');
vi.mock('@/hooks/useUserFinancialData');
const chart = vi.fn();
vi.mock('@/components/financial-freedom/ProgressChart', () => ({
  ProgressChart: (props: unknown) => {
    chart(props);
    return <div aria-label="Projection chart" />;
  },
}));
vi.mock('@/components/financial-freedom/TimelineProjection', () => ({
  TimelineProjection: ({ projections }: { projections: YearlyProjection[] }) => (
    <table aria-label="Yearly projections">
      <tbody>
        {projections.map(p => (
          <tr key={p.year}>
            <td>{p.year}</td>
            <td>{p.endingBalance}</td>
          </tr>
        ))}
      </tbody>
    </table>
  ),
}));

const projectedYear: YearlyProjection = {
  year: 1,
  startingBalance: 50000,
  endingBalance: 60000,
  contributions: 6000,
  investmentReturns: 4000,
  withdrawals: 0,
  progressTowardTarget: 10,
  targetReached: false,
};
function result(overrides: Partial<FreedomCalculatorResult> = {}): FreedomCalculatorResult {
  return {
    yearsToFreedom: 5,
    monthsToFreedom: 6,
    targetSavingsAmount: 600000,
    progressPercentage: 10,
    currentProgress: 60000,
    annualPassiveIncome: 24000,
    isSustainableIndefinitely: false,
    isAchievable: true,
    yearlyProjections: [],
    sensitivityScenarios: [],
    message: '',
    ...overrides,
  };
}
function longevity(overrides: Partial<SavingsLongevityResult> = {}): SavingsLongevityResult {
  return {
    yearsUntilDepletion: 1,
    totalMonthsUntilDepletion: 13,
    isInfinite: false,
    exceedsProjection: false,
    depletionYear: 2027,
    finalBalance: 0,
    willDeplete: true,
    depletionProjections: [],
    monthlyExpenses: 1000,
    currentSavings: 13000,
    annualReturnRate: 0,
    message: '',
    ...overrides,
  };
}

const hookState = (): ReturnType<typeof useFinancialFreedom> => ({
  input: DEFAULT_FREEDOM_CALCULATOR_INPUT,
  result: result(),
  longevityResult: null,
  isLoading: false,
  error: null,
  defaults: null,
  updateInput: vi.fn(),
  resetInputs: vi.fn(),
  calculate: vi.fn(),
  calculateLocal: vi.fn(),
});

describe('financial freedom result contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    vi.mocked(useFinancialFreedom).mockReturnValue(hookState());
    vi.mocked(useUserFinancialData).mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });
  });

  it.each([true, false])(
    'renders yearly API projections and passes target reachability to the chart (%s)',
    isAchievable => {
      vi.mocked(useFinancialFreedom).mockReturnValue({
        ...hookState(),
        result: result({
          isAchievable,
          yearlyProjections: [projectedYear],
          exactValues: { targetSavingsAmount: '600000.001', currentProgress: '60000.002' },
        }),
      });
      renderWithProviders(<FinancialFreedomCalculator />);
      expect(screen.getByRole('table', { name: 'Yearly projections' })).toHaveTextContent('60000');
      expect(chart).toHaveBeenCalledWith(
        expect.objectContaining({
          data: [projectedYear],
          currentSavings: '60000.002',
          targetAmount: '600000.001',
          yearsToFreedom: isAchievable ? 5 : undefined,
        })
      );
    }
  );

  it.each([
    [0, 8, '8 months'],
    [2, 0, '2 years'],
    [2, 3, '2 years, 3 months'],
  ])('formats a %s-year %s-month timeline without redundant zero units', (years, months, text) => {
    vi.mocked(useFinancialFreedom).mockReturnValue({
      ...hookState(),
      result: result({ yearsToFreedom: Number(years), monthsToFreedom: Number(months) }),
    });
    renderWithProviders(<FinancialFreedomCalculator />);
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it.each([
    { state: longevity({ isInfinite: true }), text: 'Forever' },
    { state: longevity({ exceedsProjection: true, yearsUntilDepletion: 100 }), text: /100/ },
    {
      state: longevity({ yearsUntilDepletion: 0, totalMonthsUntilDepletion: 6 }),
      text: '6 months',
    },
    { state: longevity({ yearsUntilDepletion: 1, totalMonthsUntilDepletion: 12 }), text: '1 year' },
    { state: longevity(), text: /1 year 1 month/ },
  ])('distinguishes depletion from an infinite or bounded projection: $text', ({ state, text }) => {
    vi.mocked(useFinancialFreedom).mockReturnValue({ ...hookState(), longevityResult: state });
    renderWithProviders(<FinancialFreedomCalculator />);
    expect(screen.getAllByText(text).length).toBeGreaterThan(0);
    if (!state.isInfinite) expect(screen.queryByText('Forever')).not.toBeInTheDocument();
  });

  it('does not render a stale longevity response without a current timeline', () => {
    vi.mocked(useFinancialFreedom).mockReturnValue({
      ...hookState(),
      result: null,
      longevityResult: longevity(),
    });
    renderWithProviders(<FinancialFreedomCalculator />);
    expect(screen.queryByText('Time to Financial Freedom')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Calculate' })).toBeEnabled();
  });

  it('keeps calculation errors visible while account data is unavailable', () => {
    vi.mocked(useFinancialFreedom).mockReturnValue({
      ...hookState(),
      result: null,
      error: 'Invalid withdrawal rate',
      isLoading: true,
    });
    vi.mocked(useUserFinancialData).mockReturnValue({
      data: null,
      isLoading: false,
      error: 'Unavailable',
      refetch: vi.fn(),
    });
    renderWithProviders(<FinancialFreedomCalculator />);
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(screen.getByText('Invalid withdrawal rate')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Calculating/ })).toBeDisabled();
  });

  it('prevents applying account data while it is refreshing', () => {
    const state = hookState();
    vi.mocked(useFinancialFreedom).mockReturnValue(state);
    vi.mocked(useUserFinancialData).mockReturnValue({
      data: { totalSavings: 1000, averageMonthlyExpenses: 500, currency: 'USD' },
      isLoading: true,
      error: null,
      refetch: vi.fn(),
    });
    renderWithProviders(<FinancialFreedomCalculator />);
    const button = screen.getByRole('button', { name: /Use.*Data/i });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(state.updateInput).not.toHaveBeenCalled();
  });
});
