import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
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

const mockUseCountryToolConfig = vi.fn();
vi.mock('@/hooks/useCountryToolConfig', () => ({
  useCountryToolConfig: () => mockUseCountryToolConfig(),
}));

const chartData = vi.fn();
const tooltipProps = vi.fn();
const legendProps = vi.fn();
const axisProps = vi.fn();

vi.mock('recharts', () => ({
  AreaChart: ({ children, data }: { children?: React.ReactNode; data: unknown }) => {
    chartData(data);
    return <svg>{children}</svg>;
  },
  Area: () => null,
  XAxis: () => null,
  YAxis: (props: unknown) => {
    axisProps(props);
    return null;
  },
  CartesianGrid: () => null,
  Tooltip: (props: unknown) => {
    tooltipProps(props);
    return null;
  },
  Legend: (props: unknown) => {
    legendProps(props);
    return null;
  },
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
    mockUseCountryToolConfig.mockReturnValue({ earlyPayoffConfig: { hasIRA: false } });
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

  it('adding a prepayment edits the plan without submitting the calculator', () => {
    renderWithProviders(<EarlyPayoffCalculator />);
    fireEvent.click(screen.getByRole('button', { name: /Add.*payment/i }));
    expect(defaultHookState.addLumpSum).toHaveBeenCalledOnce();
    expect(defaultHookState.calculate).not.toHaveBeenCalled();
  });

  it('removing a prepayment edits the plan without submitting the calculator', () => {
    mockUseEarlyPayoffCalculator.mockReturnValue({
      ...defaultHookState,
      input: {
        ...defaultHookState.input,
        lumpSumPayments: [{ id: 'payment-7', month: 6, amount: 5000 }],
      },
    });
    renderWithProviders(<EarlyPayoffCalculator />);
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(defaultHookState.removeLumpSum).toHaveBeenCalledWith('payment-7');
    expect(defaultHookState.calculate).not.toHaveBeenCalled();
  });

  it('keeps monetary edits as exact strings and submits only on Calculate', () => {
    renderWithProviders(<EarlyPayoffCalculator />);
    const fields = [
      ['Remaining Loan Balance', 'loanBalance', '9007199254740993.01'],
      ['Extra Monthly Payment', 'monthlyExtraPayment', '12.3456789'],
    ];
    for (const [label, key, value] of fields) {
      fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value } });
      expect(defaultHookState.updateInput).toHaveBeenCalledWith(key, value);
    }
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }));
    expect(defaultHookState.calculate).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
    expect(defaultHookState.resetInputs).toHaveBeenCalledOnce();
    expect(defaultHookState.calculate).toHaveBeenCalledOnce();
  });

  it('converts rate and term fields to numbers while preserving lump-sum money', () => {
    mockUseEarlyPayoffCalculator.mockReturnValue({
      ...defaultHookState,
      input: { ...defaultHookState.input, lumpSumPayments: [{ id: 'a', month: 6, amount: 5000 }] },
    });
    renderWithProviders(<EarlyPayoffCalculator />);
    for (const [id, key, value] of [
      ['Annual Interest Rate (%)', 'annualRate', '0'],
      ['Remaining Years', 'remainingYears', '0'],
      ['Additional Months (0–11)', 'remainingMonthsExtra', '11'],
    ]) {
      fireEvent.change(screen.getByLabelText(id), { target: { value } });
      expect(defaultHookState.updateInput).toHaveBeenCalledWith(key, Number(value));
    }
    fireEvent.change(screen.getByLabelText('Month'), { target: { value: '1' } });
    expect(defaultHookState.updateLumpSum).toHaveBeenCalledWith('a', 'month', 1);
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '10000.001' } });
    expect(defaultHookState.updateLumpSum).toHaveBeenCalledWith('a', 'amount', '10000.001');
  });

  it('shows validation errors and no obsolete result after a failed calculation', () => {
    mockUseEarlyPayoffCalculator.mockReturnValue({
      ...defaultHookState,
      result: null,
      error: 'Enter a positive loan balance',
    });
    renderWithProviders(<EarlyPayoffCalculator />);
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a positive loan balance');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it.each([
    ['Remaining Loan Balance', 'loanBalance', '0'],
    ['Extra Monthly Payment', 'monthlyExtraPayment', '0'],
    ['Annual Interest Rate (%)', 'annualRate', 0],
    ['Remaining Years', 'remainingYears', 0],
    ['Additional Months (0–11)', 'remainingMonthsExtra', 0],
  ])('clearing %s removes its previous value without calculating', (label, key, expected) => {
    mockUseEarlyPayoffCalculator.mockReturnValue({
      ...defaultHookState,
      input: {
        ...defaultHookState.input,
        monthlyExtraPayment: 100,
        remainingMonthsExtra: 6,
      },
    });
    renderWithProviders(<EarlyPayoffCalculator />);
    fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value: '' } });
    expect(defaultHookState.updateInput).toHaveBeenCalledWith(key, expected);
    expect(defaultHookState.calculate).not.toHaveBeenCalled();
  });

  it.each([
    ['Month', 'month', 0],
    ['Amount', 'amount', '0'],
  ])('clearing a lump-sum %s cannot leave its old value in the request', (label, key, expected) => {
    mockUseEarlyPayoffCalculator.mockReturnValue({
      ...defaultHookState,
      input: {
        ...defaultHookState.input,
        lumpSumPayments: [{ id: 'payment-7', month: 6, amount: 5000 }],
      },
    });
    renderWithProviders(<EarlyPayoffCalculator />);
    fireEvent.change(screen.getByLabelText(label), { target: { value: '' } });
    expect(defaultHookState.updateLumpSum).toHaveBeenCalledWith('payment-7', key, expected);
    expect(defaultHookState.calculate).not.toHaveBeenCalled();
  });

  it('renders a reset zero-valued plan as empty editable fields', () => {
    mockUseEarlyPayoffCalculator.mockReturnValue({
      ...defaultHookState,
      result: null,
      input: {
        loanBalance: 0,
        annualRate: 0,
        remainingYears: 0,
        remainingMonthsExtra: 0,
        monthlyExtraPayment: 0,
        lumpSumPayments: [{ id: 'new', month: 0, amount: 0 }],
      },
    });
    renderWithProviders(<EarlyPayoffCalculator />);
    for (const label of [
      'Remaining Loan Balance',
      'Annual Interest Rate (%)',
      'Remaining Years',
      'Additional Months (0–11)',
      'Extra Monthly Payment',
      'Month',
      'Amount',
    ]) {
      expect(
        screen.getByLabelText(label, { exact: label === 'Month' || label === 'Amount' })
      ).toHaveValue('');
    }
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it.each([
    ['base', 'Current Schedule'],
    ['reduceDuration', 'Reduce Duration'],
    ['reducePayment', 'Reduce Monthly Payment'],
  ])('labels %s consistently in the chart tooltip and legend', (key, label) => {
    renderWithProviders(<EarlyPayoffCalculator />);
    const tooltip = tooltipProps.mock.lastCall![0] as {
      formatter: (value: number | undefined, name: string) => string[];
      labelFormatter: (year: number) => string;
    };
    const legend = legendProps.mock.lastCall![0] as { formatter: (name: string) => string };
    expect(tooltip.formatter(1234.5, key)).toEqual(['$1,234.50', label]);
    expect(tooltip.formatter(undefined, key)).toEqual(['—', label]);
    expect(legend.formatter(key)).toBe(label);
    expect(tooltip.labelFormatter(3)).toBe('Year 3');
    const axis = axisProps.mock.lastCall![0] as { tickFormatter: (value: number) => string };
    expect(axis.tickFormatter(200000)).toBe('$200.00K');
  });

  it.each([false, true])(
    'shows each schedule, closing balances, and penalty columns only when applicable (IRA=%s)',
    hasIRA => {
      mockUseCountryToolConfig.mockReturnValue({ earlyPayoffConfig: { hasIRA } });
      const yearOne = {
        year: 1,
        totalPayment: 12000,
        principalPaid: 10000,
        interestPaid: 2000,
        lumpSum: 5000,
        ira: 200,
        endBalance: 10000,
      };
      const yearTwo = {
        year: 2,
        totalPayment: 11000,
        principalPaid: 10000,
        interestPaid: 1000,
        lumpSum: 0,
        ira: 0,
        endBalance: 0,
      };
      const base = { ...baseScenario, totalMonths: 24, yearlySchedule: [yearOne, yearTwo] };
      mockUseEarlyPayoffCalculator.mockReturnValue({
        ...defaultHookState,
        result: {
          base,
          reduceDuration: {
            ...base,
            totalMonths: 11,
            timeSavedMonths: 13,
            totalIRA: 200,
            netSavings: -50,
            yearlySchedule: [yearOne],
          },
          reducePayment: {
            ...base,
            totalMonths: 23,
            timeSavedMonths: 1,
            totalIRA: 200,
            netSavings: -25,
            finalMonthlyPayment: 750,
          },
        },
      });
      renderWithProviders(<EarlyPayoffCalculator />);
      expect(chartData).toHaveBeenLastCalledWith([
        { year: 0, base: 200000, reduceDuration: 200000, reducePayment: 200000 },
        { year: 1, base: 10000, reduceDuration: 10000, reducePayment: 10000 },
        { year: 2, base: 0, reduceDuration: 0, reducePayment: 0 },
      ]);
      for (const [name, rowCount] of [
        ['Current Schedule', 3],
        ['Reduce Duration', 2],
        ['Reduce Monthly Payment', 3],
      ] as const) {
        const button = screen.getByRole('button', { name });
        fireEvent.click(button);
        const schedule = screen.getAllByRole('table').at(-1)!;
        expect(within(schedule).getAllByRole('row')).toHaveLength(rowCount);
        expect(within(schedule).queryByRole('columnheader', { name: 'IRA' }) !== null).toBe(
          hasIRA && name !== 'Current Schedule'
        );
        expect(within(schedule).queryByRole('columnheader', { name: 'Lump Sum' }) !== null).toBe(
          name !== 'Current Schedule'
        );
        fireEvent.click(button);
        expect(screen.getAllByRole('table')).toHaveLength(1);
      }
    }
  );
});
