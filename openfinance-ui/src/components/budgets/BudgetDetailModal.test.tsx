/**
 * Unit tests for BudgetDetailModal component
 *
 * Covers: rendering, loading state, error state, summary cards,
 * history table, chart, close button, backdrop click, Escape key.
 */
import { screen, fireEvent, within } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import React from 'react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { BudgetDetailModal } from './BudgetDetailModal';
import * as useBudgetsModule from '@/hooks/useBudgets';
import type { BudgetHistoryResponse, BudgetResponse } from '@/types/budget';

// ─── Context mocks ────────────────────────────────────────────────────────────

vi.mock('@/context/VisibilityContext', () => ({
  VisibilityProvider: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  useVisibility: vi.fn(() => ({ isAmountsVisible: true })),
}));

vi.mock('@/context/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  useAuthContext: vi.fn(() => ({ baseCurrency: 'USD' })),
}));

// ─── Hook mocks ───────────────────────────────────────────────────────────────

vi.mock('@/hooks/useBudgets', async importOriginal => {
  const actual = await importOriginal<typeof useBudgetsModule>();
  return {
    ...actual,
    useBudget: vi.fn(() => ({ data: undefined, isLoading: false })),
    useBudgetHistory: vi.fn(() => ({ data: undefined, isLoading: false })),
  };
});

// ─── Child component mocks ────────────────────────────────────────────────────

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children, inline }: { children: React.ReactNode; inline?: boolean }) => (
    <span data-testid="private-amount" data-inline={inline}>
      {children}
    </span>
  ),
}));

// ─── Typed mock helpers ───────────────────────────────────────────────────────

const mockUseBudget = vi.mocked(useBudgetsModule.useBudget);
const mockUseBudgetHistory = vi.mocked(useBudgetsModule.useBudgetHistory);

// ─── Mock data ────────────────────────────────────────────────────────────────

const mockBudget: BudgetResponse = {
  id: 1,
  categoryId: 10,
  categoryName: 'Groceries',
  categoryType: 'EXPENSE',
  amount: 500,
  currency: 'USD',
  period: 'MONTHLY',
  startDate: '2024-01-01',
  endDate: '2024-12-31',
  rollover: false,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockHistoryData: BudgetHistoryResponse = {
  budgetId: 1,
  categoryName: 'Groceries',
  amount: 500,
  currency: 'USD',
  period: 'MONTHLY',
  startDate: '2024-01-01',
  endDate: '2024-12-31',
  totalBudgeted: 1000,
  totalSpent: 750,
  history: [
    {
      label: 'January 2024',
      periodStart: '2024-01-01',
      periodEnd: '2024-01-31',
      budgeted: 500,
      spent: 350,
      remaining: 150,
      percentageSpent: 70,
      status: 'ON_TRACK',
    },
    {
      label: 'February 2024',
      periodStart: '2024-02-01',
      periodEnd: '2024-02-29',
      budgeted: 500,
      spent: 400,
      remaining: 100,
      percentageSpent: 80,
      status: 'WARNING',
    },
  ],
};

const mockOverBudgetHistory: BudgetHistoryResponse = {
  ...mockHistoryData,
  totalSpent: 1200,
  history: [
    {
      label: 'January 2024',
      periodStart: '2024-01-01',
      periodEnd: '2024-01-31',
      budgeted: 500,
      spent: 600,
      remaining: -100,
      percentageSpent: 120,
      status: 'EXCEEDED',
    },
  ],
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('BudgetDetailModal', () => {
  const defaultProps = {
    budgetId: 1,
    onClose: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockUseBudget.mockReturnValue({ data: mockBudget, isLoading: false } as any);
    mockUseBudgetHistory.mockReturnValue({ data: mockHistoryData, isLoading: false } as any);
  });

  // ── Rendering ──────────────────────────────────────────────────────────────

  describe('Rendering', () => {
    it('starts collapsed and lets the user expand and collapse the period breakdown', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      const toggle = screen.getByRole('button', { name: /Period Breakdown/ });
      expect(toggle).toHaveAttribute('aria-expanded', 'false');
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      fireEvent.click(toggle);
      expect(toggle).toHaveAttribute('aria-expanded', 'true');
      expect(screen.getByRole('table')).toBeVisible();
      fireEvent.click(toggle);
      expect(toggle).toHaveAttribute('aria-expanded', 'false');
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('offers the allowance explanation as help without expanding the breakdown', async () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.queryByText(/^Budgeted is the new allowance/)).not.toBeInTheDocument();
      fireEvent.focus(screen.getByRole('button', { name: /^Budgeted is the new allowance/ }));
      expect(await screen.findByRole('tooltip')).toHaveTextContent(
        'Carryover affects the result but is not added again to the plan total.'
      );
      expect(screen.getByRole('button', { name: /Period Breakdown/ })).toHaveAttribute(
        'aria-expanded',
        'false'
      );
    });

    it('shows twelve allowances and leaves future actuals and carry undetermined', () => {
      const history = Array.from({ length: 12 }, (_, i) => ({
        label: `Month ${i + 1}`,
        periodStart: `2026-${String(i + 1).padStart(2, '0')}-01`,
        periodEnd: `2026-${String(i + 1).padStart(2, '0')}-28`,
        budgeted: 300,
        carryover: i === 1 ? 100 : 0,
        available: i === 1 ? 400 : 300,
        spent: i === 0 ? 200 : 0,
        remaining: i === 0 ? 100 : 300,
        percentageSpent: i === 0 ? 66.67 : 0,
        status: 'ON_TRACK' as const,
        future: i > 1,
      }));
      mockUseBudgetHistory.mockReturnValue({
        data: { ...mockHistoryData, history, totalBudgeted: 3600, totalSpent: 200 },
        isLoading: false,
      } as ReturnType<typeof useBudgetsModule.useBudgetHistory>);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
      expect(screen.getByText('Period Breakdown (12 periods)')).toBeInTheDocument();
      expect(screen.getByRole('columnheader', { name: 'Carryover' })).toBeInTheDocument();
      const rows = within(screen.getByRole('table')).getAllByRole('row');
      expect(rows).toHaveLength(13);
      expect(within(rows[3]).getAllByText('—')).toHaveLength(3);
      const futureResult = within(rows[3]).getAllByRole('cell')[1];
      expect(futureResult).toHaveTextContent('Upcoming');
      expect(futureResult).not.toHaveTextContent(/Remaining|Over budget|\$/);
      expect(screen.getByText('$3,600.00')).toBeInTheDocument();
    });

    it('renders the budget category name in the header', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByText('Groceries History')).toBeInTheDocument();
    });

    it('renders period and date range subtitle', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByText(/Monthly budget/)).toBeInTheDocument();
      // Dates appear in both the subtitle and the history table, so use getAllByText
      expect(screen.getAllByText(/2024-01-01/).length).toBeGreaterThan(0);
      expect(screen.getAllByText(/2024-12-31/).length).toBeGreaterThan(0);
    });

    it('renders the four summary cards', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByText('Total Budgeted')).toBeInTheDocument();
      expect(screen.getByText('Total Spent')).toBeInTheDocument();
      expect(screen.getAllByText('Remaining').length).toBeGreaterThan(0);
      expect(screen.getByText('Overall Usage')).toBeInTheDocument();
    });

    it('renders the overall percentage', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      // totalSpent=750, totalBudgeted=1000 → 75.0%
      expect(screen.getByText('75.0%')).toBeInTheDocument();
    });

    it('renders history table with correct number of period rows', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
      expect(screen.getByText('Period Breakdown (2 periods)')).toBeInTheDocument();
      expect(screen.getByText('January 2024')).toBeInTheDocument();
      expect(screen.getByText('February 2024')).toBeInTheDocument();
    });

    it('renders table headers', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
      expect(screen.getByText('Period')).toBeInTheDocument();
      expect(screen.getByRole('columnheader', { name: 'Result' })).toBeInTheDocument();
      expect(screen.getByText('Budgeted')).toBeInTheDocument();
      expect(screen.getByText('Net spent')).toBeInTheDocument();
      expect(screen.getAllByText('Remaining').length).toBeGreaterThan(0);
    });

    it('labels the remaining amount in each period row', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
      for (const period of ['January 2024', 'February 2024']) {
        expect(
          within(screen.getByRole('row', { name: new RegExp(period) })).getByText('Remaining')
        ).toBeInTheDocument();
      }
    });

    it('renders the bar chart when chart data is available', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByTestId('responsive-container')).toBeInTheDocument();
    });
  });

  // ── Loading state ──────────────────────────────────────────────────────────

  describe('Loading state', () => {
    it('shows skeleton elements when budget is loading', () => {
      mockUseBudget.mockReturnValue({ data: undefined, isLoading: true } as any);
      mockUseBudgetHistory.mockReturnValue({ data: undefined, isLoading: true } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      // LoadingSkeleton uses the 'shimmer' CSS class (not 'animate-pulse')
      const skeletons = document.querySelectorAll('.shimmer');
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it('shows loading skeleton in header while loading', () => {
      mockUseBudget.mockReturnValue({ data: undefined, isLoading: true } as any);
      mockUseBudgetHistory.mockReturnValue({ data: undefined, isLoading: true } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      // No category name heading should appear yet
      expect(screen.queryByText('Groceries History')).not.toBeInTheDocument();
    });
  });

  // ── Error / empty state ────────────────────────────────────────────────────

  describe('Error state', () => {
    it('shows error message when history data fails to load', () => {
      mockUseBudget.mockReturnValue({ data: undefined, isLoading: false } as any);
      mockUseBudgetHistory.mockReturnValue({ data: undefined, isLoading: false } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByText('Failed to load budget history.')).toBeInTheDocument();
    });

    it('shows empty state when history array is empty', () => {
      mockUseBudgetHistory.mockReturnValue({
        data: { ...mockHistoryData, history: [] },
        isLoading: false,
      } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
      expect(screen.getByText('No history entries found for this budget.')).toBeInTheDocument();
    });
  });

  // ── Over budget state ──────────────────────────────────────────────────────

  describe('Over budget display', () => {
    it.each([
      {
        spent: 600,
        carryover: 0,
        remaining: -100,
        label: 'Over budget by 20.0%',
        amount: '$100.00',
      },
      { spent: 600, carryover: 200, remaining: 100, label: 'Remaining', amount: '$100.00' },
      {
        spent: 450,
        carryover: -100,
        remaining: -50,
        label: 'Over budget by 12.5%',
        amount: '$50.00',
      },
      { spent: 400, carryover: -100, remaining: 0, label: 'Remaining', amount: '$0.00' },
      {
        spent: 800,
        carryover: 200,
        remaining: -100,
        label: 'Over budget by 14.3%',
        amount: '$100.00',
      },
    ])('labels the period result with $carryover carryover and $spent spent', sample => {
      mockUseBudgetHistory.mockReturnValue({
        data: {
          ...mockHistoryData,
          history: [
            {
              ...mockHistoryData.history[0],
              spent: sample.spent,
              carryover: sample.carryover,
              available: 500 + sample.carryover,
              remaining: sample.remaining,
              status: sample.remaining < 0 ? 'EXCEEDED' : 'ON_TRACK',
            },
          ],
        },
        isLoading: false,
      } as ReturnType<typeof useBudgetsModule.useBudgetHistory>);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
      const row = screen.getByRole('row', { name: /January 2024/ });
      const result = within(row).getAllByRole('cell')[1];
      expect(result).toHaveTextContent(sample.label);
      expect(result).toHaveTextContent(sample.amount);
      expect(result).not.toHaveTextContent('-$');
    });

    it.each([0, -200])(
      'omits the over-budget percentage for an available allowance of %s',
      available => {
        mockUseBudgetHistory.mockReturnValue({
          data: {
            ...mockHistoryData,
            history: [
              {
                ...mockHistoryData.history[0],
                available,
                carryover: available - 500,
                spent: 100,
                remaining: available - 100,
                percentageSpent: null,
                status: 'EXCEEDED',
              },
            ],
          },
          isLoading: false,
        } as ReturnType<typeof useBudgetsModule.useBudgetHistory>);
        renderWithProviders(<BudgetDetailModal {...defaultProps} />);
        fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
        const result = within(screen.getByRole('row', { name: /January 2024/ })).getAllByRole(
          'cell'
        )[1];
        expect(result).toHaveTextContent('Over budget');
        expect(result).not.toHaveTextContent(/%|NaN|Infinity/);
      }
    );

    it('shows the overspent amount beside the period', () => {
      mockUseBudgetHistory.mockReturnValue({
        data: mockOverBudgetHistory,
        isLoading: false,
      } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
      const result = within(screen.getByRole('row', { name: /January 2024/ })).getAllByRole(
        'cell'
      )[1];
      expect(result).toHaveTextContent('$100.00');
      expect(result).toHaveTextContent('Over budget by 20.0%');
    });

    it('shows percentage > 100% when over budget', () => {
      mockUseBudgetHistory.mockReturnValue({
        data: mockOverBudgetHistory,
        isLoading: false,
      } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      // Overall usage is separate from the percentage over the allowance.
      expect(screen.getAllByText('120.0%').length).toBeGreaterThan(0);
    });
  });

  // ── Close behaviour ────────────────────────────────────────────────────────

  describe('Close behaviour', () => {
    it('calls onClose when X button is clicked', () => {
      const onClose = vi.fn();
      renderWithProviders(<BudgetDetailModal {...defaultProps} onClose={onClose} />);
      fireEvent.click(screen.getByLabelText('Close modal'));
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when backdrop is clicked', () => {
      const onClose = vi.fn();
      renderWithProviders(<BudgetDetailModal {...defaultProps} onClose={onClose} />);
      const backdrop = document.querySelector('.absolute.inset-0');
      act(() => {
        fireEvent.click(backdrop!);
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when Escape key is pressed', () => {
      const onClose = vi.fn();
      renderWithProviders(<BudgetDetailModal {...defaultProps} onClose={onClose} />);
      act(() => {
        fireEvent.keyDown(window, { key: 'Escape' });
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });

  // ── No Edit button ─────────────────────────────────────────────────────────

  describe('No Edit button', () => {
    it('does not render an Edit button (BudgetDetailModal has no edit)', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      // The only button in the header should be Close
      expect(screen.queryByRole('button', { name: /Edit/i })).not.toBeInTheDocument();
    });
  });

  // ── Privacy amounts ────────────────────────────────────────────────────────

  describe('PrivateAmount wrapping', () => {
    it('wraps monetary values in PrivateAmount components', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      const privateAmounts = screen.getAllByTestId('private-amount');
      expect(privateAmounts.length).toBeGreaterThan(0);
    });
  });
  it('keeps foreign history amounts in their native currency in totals and rows', () => {
    mockUseBudgetHistory.mockReturnValue({
      data: {
        ...mockHistoryData,
        currency: 'EUR',
        totalBudgeted: 100,
        totalSpent: 70,
        history: [{ ...mockHistoryData.history[0], budgeted: 100, spent: 70, remaining: 30 }],
      },
      isLoading: false,
    } as ReturnType<typeof useBudgetsModule.useBudgetHistory>);
    renderWithProviders(<BudgetDetailModal {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /Period Breakdown/ }));
    expect(screen.getAllByText('€100.00')).toHaveLength(2);
    expect(screen.getAllByText('€70.00')).toHaveLength(2);
    expect(screen.getAllByText('€30.00')).toHaveLength(2);
    expect(screen.queryByText('$100.00')).not.toBeInTheDocument();
  });
});
