import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import BudgetProgressCard from '@/components/dashboard/BudgetProgressCard';
import type { BudgetProgressResponse, BudgetSummaryResponse } from '@/types/budget';

const state = vi.hoisted(() => ({ query: vi.fn(), navigate: vi.fn() }));
vi.mock('@/hooks/useBudgets', () => ({ useBudgetSummary: () => state.query() }));
vi.mock('react-router', async importOriginal => ({
  ...(await importOriginal<typeof import('react-router')>()),
  useNavigate: () => state.navigate,
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => undefined }),
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: number | string }) => <span>{String(amount)}</span>,
}));

function budget(
  id: number,
  overrides: Partial<BudgetProgressResponse> = {}
): BudgetProgressResponse {
  return {
    budgetId: id,
    categoryName: `Category ${id}`,
    budgeted: 100,
    spent: 50,
    remaining: 50,
    percentageSpent: 50,
    currency: 'EUR',
    period: 'MONTHLY',
    startDate: '2000-01-01',
    endDate: '2099-12-31',
    daysRemaining: 10,
    status: 'ON_TRACK',
    ...overrides,
  };
}
function query(budgets: BudgetProgressResponse[]): void {
  const data: BudgetSummaryResponse = {
    budgets,
    totalBudgets: budgets.length,
    activeBudgets: budgets.length,
    period: 'MONTHLY',
    currency: 'EUR',
    totalBudgeted: 0,
    totalSpent: 0,
    totalRemaining: 0,
    averageSpentPercentage: 0,
  };
  state.query.mockReturnValue({ data, isLoading: false, isError: false });
}

describe('dashboard budget health', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it.each([true, false])('offers setup for an unavailable budget summary (error=%s)', isError => {
    state.query.mockReturnValue({ data: undefined, isLoading: false, isError });
    renderWithProviders(<BudgetProgressCard />);
    expect(screen.getByText(isError ? /Failed to load/i : /No budgets/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button'));
    expect(state.navigate).toHaveBeenCalledWith('/budget');
  });

  it('excludes expired and future budgets and offers the first-budget action', () => {
    query([budget(1, { endDate: '2000-01-01' }), budget(2, { startDate: '2099-01-01' })]);
    renderWithProviders(<BudgetProgressCard />);
    expect(screen.queryByText('Category 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Category 2')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button'));
    expect(state.navigate).toHaveBeenCalledWith('/budget');
  });

  it('prioritizes overspending, then warnings, then healthy budgets without changing cached order', () => {
    const budgets = [
      budget(1),
      budget(2, { status: 'WARNING', percentageSpent: 90 }),
      budget(3, { status: 'EXCEEDED', percentageSpent: 110 }),
      budget(4, { status: 'EXCEEDED', percentageSpent: null }),
      budget(5, { status: 'WARNING', percentageSpent: 80 }),
      budget(6),
      budget(7),
    ];
    query(budgets);
    renderWithProviders(<BudgetProgressCard />);
    const rows = screen.getAllByRole('button', { name: 'View budget details' });
    expect(rows.map(row => within(row).getByText(/^Category \d+$/).textContent)).toEqual([
      'Category 4',
      'Category 3',
      'Category 2',
      'Category 5',
      'Category 1',
    ]);
    expect(rows[0]).toHaveTextContent('—');
    expect(budgets.map(b => b.budgetId)).toEqual([1, 2, 3, 4, 5, 6, 7]);
    fireEvent.click(rows[0]);
    expect(state.navigate).toHaveBeenLastCalledWith('/budget?open=4');
    fireEvent.click(screen.getByRole('button', { name: /2 more/i }));
    expect(state.navigate).toHaveBeenLastCalledWith('/budget');
    fireEvent.click(screen.getByRole('button', { name: /View all/i }));
    expect(state.navigate).toHaveBeenLastCalledWith('/budget');
  });

  it.each([0, 50, 75, 100, 125])('shows the correct aggregate for %s percent spent', spent => {
    query([
      budget(1, {
        spent,
        percentageSpent: spent,
        remaining: 100 - spent,
        status: spent >= 100 ? 'EXCEEDED' : spent >= 75 ? 'WARNING' : 'ON_TRACK',
      }),
    ]);
    renderWithProviders(<BudgetProgressCard />);
    expect(screen.getByText(`${spent.toFixed(1)}%`)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View budget details' })).toHaveTextContent(
      `${spent}%`
    );
    expect(screen.queryByRole('button', { name: /more budgets/i })).not.toBeInTheDocument();
  });

  it('does not invent a percentage for an allowance of zero', () => {
    query([budget(1, { budgeted: 0, spent: 0, percentageSpent: null })]);
    renderWithProviders(<BudgetProgressCard />);
    expect(screen.getByRole('button', { name: 'View budget details' })).toHaveTextContent('—');
    expect(screen.getByText('0.0%')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/NaN|Infinity/);
  });
});
