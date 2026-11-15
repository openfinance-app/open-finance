import { beforeEach, describe, expect, it, vi } from 'vitest';
import { cloneElement, type ReactElement, type ReactNode } from 'react';
import { fireEvent, screen, within } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { BudgetDetailModal } from '@/components/budgets/BudgetDetailModal';
import type { BudgetHistoryEntry, BudgetHistoryResponse } from '@/types/budget';
import i18n from '@/test/i18n-test';

interface Point {
  name: string;
  value: number;
  color: string;
  dataKey?: string;
  payload?: { period: BudgetHistoryEntry; exactValues?: Record<string, string> };
}
const state = vi.hoisted(() => ({
  history: vi.fn(),
  chart: vi.fn(),
  visible: true,
  tooltip: { active: true, payload: [] as Point[], label: 'January' },
}));
vi.mock('@/hooks/useBudgets', () => ({
  useBudget: () => ({}),
  useBudgetHistory: () => state.history(),
}));
vi.mock('@/context/VisibilityContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/VisibilityContext')>()),
  useVisibility: () => ({ isAmountsVisible: state.visible }),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => undefined }),
}));
vi.mock('@/components/alerts/AlertSettings', () => ({ AlertSettings: () => null }));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: number | string }) => <span>{String(amount)}</span>,
}));
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  BarChart: ({ children, data }: { children: ReactNode; data: unknown }) => {
    state.chart(data);
    return <div>{children}</div>;
  },
  Tooltip: ({ content }: { content: ReactElement }) => cloneElement(content, state.tooltip),
  YAxis: ({ tickFormatter }: { tickFormatter: (value: number) => string }) => (
    <span aria-label="Chart scale">{tickFormatter(100)}</span>
  ),
  Bar: () => null,
  XAxis: () => null,
  CartesianGrid: () => null,
  Legend: () => null,
}));
const t = (key: string): string => i18n.t(`budgets:${key}`);
const entry = (overrides: Partial<BudgetHistoryEntry> = {}): BudgetHistoryEntry => ({
  label: 'January',
  periodStart: '2024-01-01',
  periodEnd: '2024-01-31',
  budgeted: 100,
  spent: 75,
  remaining: 25,
  percentageSpent: 75,
  status: 'ON_TRACK',
  ...overrides,
});
function history(rows: BudgetHistoryEntry[], overrides: Partial<BudgetHistoryResponse> = {}): void {
  state.history.mockReturnValue({
    data: {
      budgetId: 1,
      categoryName: 'Groceries',
      amount: 100,
      currency: 'USD',
      period: 'MONTHLY',
      startDate: '2024-01-01',
      endDate: '2024-12-31',
      totalBudgeted: 100,
      totalSpent: 75,
      history: rows,
      ...overrides,
    },
  });
}
beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.visible = true;
  state.tooltip = { active: true, payload: [], label: 'January' };
  history([entry()]);
});

describe('budget history meaning and chart details', () => {
  it.each([
    { remaining: -20, spent: 120, available: 100, carryover: 0, expected: '20.0%' },
    { remaining: -20, spent: 170, budgeted: 100, carryover: 50, expected: '13.3%' },
    { remaining: -20, spent: 20, available: 0, carryover: -100, expected: null },
    { remaining: 25, spent: 75, carryover: 0, expected: 'remaining' },
  ])('explains overspending against available funds: %j', scenario => {
    const period = entry(scenario);
    history([period]);
    state.tooltip.payload = [
      { name: 'Spent', value: period.spent, color: 'red', payload: { period } },
    ];
    renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent(`Spent: ${period.spent}`);
    expect(tooltip).toHaveTextContent(String(Math.abs(period.remaining)));
    if (scenario.expected === 'remaining') expect(tooltip).toHaveTextContent(t('card.remaining'));
    else if (scenario.expected) expect(tooltip).toHaveTextContent(scenario.expected);
    else {
      expect(tooltip).toHaveTextContent(t('detail.overBudget'));
      expect(tooltip.textContent).not.toMatch(/NaN|Infinity/);
    }
    fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
    expect(within(screen.getByRole('table')).getByText('January').closest('tr')).toHaveTextContent(
      String(Math.abs(period.remaining))
    );
  });

  it('shows future allowances while withholding spending and result claims', () => {
    const period = entry({ future: true, budgeted: 100, spent: 0, remaining: 100 });
    history([period]);
    state.tooltip.payload = [{ name: 'Budgeted', value: 100, color: 'gold', payload: { period } }];
    renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
    expect(screen.getByRole('tooltip')).toHaveTextContent(t('detail.future'));
    expect(
      within(screen.getByRole('tooltip')).queryByText(t('detail.columns.result'))
    ).not.toBeInTheDocument();
    expect(state.chart).toHaveBeenCalledWith([
      expect.objectContaining({ [t('card.spent')]: null }),
    ]);
    fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
    const cells = within(
      screen.getByText('January', { selector: 'span' }).closest('tr')!
    ).getAllByRole('cell');
    expect(cells[1]).toHaveTextContent(t('detail.future'));
    expect(cells[3]).toHaveTextContent('—');
    expect(cells[4]).toHaveTextContent('—');
  });

  it.each([true, false])(
    'uses exact tooltip amounts with either a data key or a display name (dataKey=%s)',
    keyed => {
      const period = entry();
      state.tooltip.payload = [
        {
          name: 'Spent',
          value: 9007199254740992,
          color: 'red',
          dataKey: keyed ? 'value' : undefined,
          payload: { period, exactValues: { [keyed ? 'value' : 'Spent']: '9007199254740992.01' } },
        },
      ];
      renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
      expect(screen.getByRole('tooltip')).toHaveTextContent('9007199254740992.01');
    }
  );

  it.each([false, true])('does not show an empty or inactive hover (active=%s)', active => {
    state.tooltip.active = active;
    renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('shows numeric hover data without inventing missing period details', () => {
    state.tooltip.payload = [{ name: 'Spent', value: 75, color: 'red' }];
    renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
    expect(screen.getByRole('tooltip')).toHaveTextContent('Spent: 75');
    expect(
      within(screen.getByRole('tooltip')).queryByText(t('detail.columns.result'))
    ).not.toBeInTheDocument();
  });

  it('includes initial rollover once in remaining funds and spending percentage', () => {
    history([entry()], { initialCarryover: 50, totalBudgeted: 100, totalSpent: 75 });
    renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
    expect(
      screen.getByText(t('summary.remaining'), { selector: 'p' }).parentElement
    ).toHaveTextContent('75');
    expect(screen.getByText('50.0%')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
    expect(screen.getByText(t('detail.initialCarry'), { exact: false })).toHaveTextContent('50');
  });

  it('hides axis amounts when privacy is enabled and handles a zero total allowance', () => {
    state.visible = false;
    history([entry()], { totalBudgeted: 0, totalSpent: 20 });
    renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
    expect(screen.getByLabelText('Chart scale')).toHaveTextContent('••••••');
    expect(screen.getByText('0.0%')).toBeInTheDocument();
    expect(
      screen.getByText(t('summary.remaining'), { selector: 'p' }).parentElement
    ).toHaveTextContent('-20');
  });

  it('recognizes overspending beyond the precision of native JSON numbers', () => {
    history([entry()], {
      totalBudgeted: 9007199254740992,
      totalSpent: 9007199254740992,
      exactValues: { totalBudgeted: '9007199254740992.01', totalSpent: '9007199254740992.02' },
    });
    renderWithProviders(<BudgetDetailModal budgetId={1} onClose={vi.fn()} />);
    const remaining = screen.getByText(t('summary.remaining'), { selector: 'p' }).parentElement!;
    expect(remaining).toHaveTextContent('-0.01');
    expect(within(remaining).getByText('-0.01').parentElement).toHaveClass('text-error');
  });
});
