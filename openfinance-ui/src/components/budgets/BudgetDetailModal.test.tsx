/**
 * Unit tests for BudgetDetailModal component
 *
 * Covers: rendering, loading state, error state, summary cards,
 * history table, chart, close button, backdrop click, Escape key.
 */
import { screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import React from 'react';
import { renderWithProviders } from '@/test/test-utils';
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
    mockUseBudget.mockReturnValue({ data: mockBudget, isLoading: false } as any);
    mockUseBudgetHistory.mockReturnValue({ data: mockHistoryData, isLoading: false } as any);
  });

  // ── Rendering ──────────────────────────────────────────────────────────────

  describe('Rendering', () => {
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
      expect(screen.getByText('Period Breakdown (2 periods)')).toBeInTheDocument();
      expect(screen.getByText('January 2024')).toBeInTheDocument();
      expect(screen.getByText('February 2024')).toBeInTheDocument();
    });

    it('renders table headers', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByText('Period')).toBeInTheDocument();
      expect(screen.getByText('Date Range')).toBeInTheDocument();
      expect(screen.getByText('Budgeted')).toBeInTheDocument();
      expect(screen.getByText('Spent')).toBeInTheDocument();
      expect(screen.getAllByText('Remaining').length).toBeGreaterThan(0);
    });

    it('renders status badges for history rows', () => {
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByText('On Track')).toBeInTheDocument();
      expect(screen.getByText('Warning')).toBeInTheDocument();
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
      expect(screen.getByText('No history entries found for this budget.')).toBeInTheDocument();
    });
  });

  // ── Over budget state ──────────────────────────────────────────────────────

  describe('Over budget display', () => {
    it('shows Exceeded status badge when budget is exceeded', () => {
      mockUseBudgetHistory.mockReturnValue({
        data: mockOverBudgetHistory,
        isLoading: false,
      } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      expect(screen.getByText('Exceeded')).toBeInTheDocument();
    });

    it('shows percentage > 100% when over budget', () => {
      mockUseBudgetHistory.mockReturnValue({
        data: mockOverBudgetHistory,
        isLoading: false,
      } as any);
      renderWithProviders(<BudgetDetailModal {...defaultProps} />);
      // Percentage appears in both the "Overall Usage" summary card and the history table row
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
});
