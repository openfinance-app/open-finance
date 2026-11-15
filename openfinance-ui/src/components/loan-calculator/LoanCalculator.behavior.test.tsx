import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { LoanCalculator } from '@/components/loan-calculator/LoanCalculator';
import type {
  LoanCalculatorInput,
  LoanCalculatorResult,
  LoanAmortizationEntry,
} from '@/types/calculator';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

interface ChartTooltip {
  formatter: (
    value: number,
    name: string,
    item: { payload?: LoanAmortizationEntry }
  ) => [string, string];
  labelFormatter: (label: string) => string;
}
const state = vi.hoisted(() => ({
  input: { principal: '100', annualRate: 5, years: 2 } as LoanCalculatorInput,
  result: null as LoanCalculatorResult | null,
  error: null as string | null,
  updateInput: vi.fn(),
  resetInputs: vi.fn(),
  calculate: vi.fn(),
  tooltip: undefined as ChartTooltip | undefined,
  xAxis: undefined as { tickFormatter: (value: number) => string } | undefined,
  yAxis: undefined as { tickFormatter: (value: number) => string } | undefined,
  chart: [] as LoanAmortizationEntry[],
}));
vi.mock('@/hooks/useLoanCalculator', () => ({ useLoanCalculator: () => state }));
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <>{children}</>,
  AreaChart: ({ children, data }: { children: ReactNode; data: LoanAmortizationEntry[] }) => {
    state.chart = data;
    return <>{children}</>;
  },
  Tooltip: (props: ChartTooltip) => {
    state.tooltip = props;
    return null;
  },
  XAxis: (props: { tickFormatter: (value: number) => string }) => {
    state.xAxis = props;
    return null;
  },
  YAxis: (props: { tickFormatter: (value: number) => string }) => {
    state.yAxis = props;
    return null;
  },
  Area: () => null,
  CartesianGrid: () => null,
}));

function result(exact: boolean): LoanCalculatorResult {
  return {
    monthlyPayment: 1,
    totalPayment: 13,
    totalInterest: 0,
    ...(exact
      ? { exactValues: { monthlyPayment: '1.01', totalPayment: '13.13', totalInterest: '0.13' } }
      : {}),
    amortizationSchedule: Array.from({ length: 13 }, (_, index) => ({
      paymentNumber: index + 1,
      principalPortion: 1,
      interestPortion: 0,
      paymentAmount: 1,
      remainingBalance: 12 - index,
      cumulativeInterest: 0,
      ...(exact
        ? {
            exactValues: {
              principalPortion: '1',
              interestPortion: '0.01',
              paymentAmount: '1.01',
              remainingBalance: String(12 - index),
            },
          }
        : {}),
    })),
  };
}

describe('Loan calculator result boundaries and editing', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    state.input = { principal: '100', annualRate: 5, years: 2 };
    state.result = null;
    state.error = null;
  });

  it.each([
    ['Loan Amount', 'principal', '0'],
    ['Annual Interest Rate (%)', 'annualRate', 0],
    ['Loan Term (Years)', 'years', 0],
  ] as const)('clearing %s sends a zero value to validation', (name, field, expected) => {
    renderWithProviders(<LoanCalculator />);
    fireEvent.change(screen.getByLabelText(name), { target: { value: '' } });
    expect(state.updateInput).toHaveBeenCalledWith(field, expected);
  });

  it('shows initially zero inputs as empty and preserves exact principal edits', () => {
    state.input = { principal: 0, annualRate: 0, years: 0 };
    renderWithProviders(<LoanCalculator />);
    for (const input of screen.getAllByRole('textbox')) expect(input).toHaveValue('');
    fireEvent.change(screen.getByLabelText('Loan Amount'), {
      target: { value: '9007199254740992.01' },
    });
    expect(state.updateInput).toHaveBeenCalledWith('principal', '9007199254740992.01');
  });

  it('renders validation feedback and resets without submitting the form', () => {
    state.error = 'Enter a positive term';
    renderWithProviders(<LoanCalculator />);
    expect(screen.getByRole('alert')).toHaveTextContent(state.error);
    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
    expect(state.resetInputs).toHaveBeenCalledOnce();
    expect(state.calculate).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }));
    expect(state.calculate).toHaveBeenCalledOnce();
  });

  it.each([false, true])(
    'keeps the final partial year and uses exact=%s schedule amounts',
    exact => {
      state.result = result(exact);
      renderWithProviders(<LoanCalculator />);
      const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
      expect(rows).toHaveLength(2);
      expect(rows[0]).toHaveTextContent(exact ? '12.12' : '12.00');
      expect(rows[1]).toHaveTextContent(exact ? '1.01' : '1.00');
      expect(rows[1]).toHaveTextContent('0.00');
      expect(state.chart.map(point => point.paymentNumber)).toEqual([1, 13]);
    }
  );

  it('formats chart labels and prefers exact tooltip balances over chart coordinates', () => {
    state.result = result(true);
    renderWithProviders(<LoanCalculator />);
    expect(state.xAxis?.tickFormatter(24)).toBe('Year 2');
    expect(state.yAxis?.tickFormatter(1000)).toMatch(/1(?:[.,]0+)?K/i);
    expect(state.tooltip?.labelFormatter('18')).toBe('Payment 18 (Year 1.5)');
    const point = {
      ...state.result.amortizationSchedule[0],
      exactValues: { remainingBalance: '9007199254740992.01' },
    };
    expect(
      state.tooltip?.formatter(9007199254740992, 'remainingBalance', { payload: point })
    ).toEqual([expect.stringContaining('9,007,199,254,740,992.01'), 'Balance']);
    expect(state.tooltip?.formatter(10, 'remainingBalance', {})).toEqual([
      expect.stringContaining('10.00'),
      'Balance',
    ]);
  });
});
