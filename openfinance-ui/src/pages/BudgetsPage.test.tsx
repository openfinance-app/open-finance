/**
 * Unit tests for BudgetsPage component
 */
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { renderWithProviders } from '@/test/test-utils';
import BudgetsPage from './BudgetsPage';

// Mock the hooks
vi.mock('@/hooks/useBudgets');
vi.mock('@/hooks/useDocumentTitle');
vi.mock('react-router', async () => {
  const actual = await vi.importActual<any>('react-router');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useSearchParams: vi.fn(() => [new URLSearchParams(), vi.fn()]),
  };
});

const mockNavigate = vi.fn();

// Mock LoadingSkeleton to have testId
vi.mock('@/components/LoadingComponents', () => ({
  LoadingSkeleton: ({ className }: { className: string }) => (
    <div data-testid="loading-skeleton" className={className} />
  ),
}));

// Mock other components that might be complex
vi.mock('@/components/ui/Dialog', () => ({
  Dialog: ({ children, open }: any) => open ? <div data-testid="dialog">{children}</div> : null,
  DialogContent: ({ children }: any) => <div>{children}</div>,
  DialogHeader: ({ children }: any) => <div>{children}</div>,
  DialogTitle: ({ children }: any) => <div>{children}</div>,
}));

vi.mock('@/components/budgets/BudgetCard', () => ({
  BudgetCard: ({ budget, onEdit, onDelete, onViewDetail }: any) => (
    <div data-testid={`budget-card-${budget.budgetId}`}>
      {budget.categoryName}
      <button data-testid={`edit-${budget.budgetId}`} onClick={() => onEdit(budget.budgetId)}>Edit</button>
      <button data-testid={`del-${budget.budgetId}`} onClick={() => onDelete(budget.budgetId)}>Del</button>
      <button data-testid={`view-${budget.budgetId}`} onClick={() => onViewDetail(budget.budgetId)}>View</button>
    </div>
  ),
}));

vi.mock('@/components/budgets/BudgetSummaryCard', () => ({
  BudgetSummaryCard: ({ summary }: { summary: any }) => (
    <div data-testid="budget-summary-card">Summary: {summary.totalBudgets} budgets</div>
  ),
}));

vi.mock('@/components/budgets/AlertBanner', () => ({
  AlertBanner: ({ title, onDismiss }: any) => (
    <div data-testid="alert-banner">
      {title}
      <button data-testid="dismiss-alert" onClick={onDismiss}>Dismiss</button>
    </div>
  ),
}));

vi.mock('@/components/budgets/BudgetForm', () => ({
  BudgetForm: ({ onSubmit, onCancel, serverError }: any) => (
    <div data-testid="budget-form">
      Budget Form
      {serverError && <span data-testid="form-error">{serverError}</span>}
      <button data-testid="form-submit" onClick={() => onSubmit({ categoryId: 1, amount: 100, period: 'MONTHLY' })}>Submit</button>
      <button data-testid="form-cancel" onClick={onCancel}>Cancel</button>
    </div>
  ),
}));

vi.mock('@/components/budgets/BudgetWizard', () => ({
  BudgetWizard: ({ open }: { open: boolean }) => (
    open ? <div data-testid="budget-wizard">Budget Wizard</div> : null
  ),
}));

vi.mock('@/components/budgets/BudgetDetailModal', () => ({
  BudgetDetailModal: ({ budgetId }: any) => (
    <div data-testid="budget-detail-modal">Detail {budgetId}</div>
  ),
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, title, onConfirm, onOpenChange }: any) => (
    open ? (
      <div data-testid="confirmation-dialog">
        {title}
        <button data-testid="confirm-btn" onClick={onConfirm}>Confirm</button>
        <button data-testid="cancel-btn" onClick={() => onOpenChange(false)}>Cancel</button>
      </div>
    ) : null
  ),
}));

// Import after mocking
import {
  useBudgetSummary,
  useCreateBudget,
  useUpdateBudget,
  useDeleteBudget,
  useBudget,
} from '@/hooks/useBudgets';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useNavigate } from 'react-router';
import type { BudgetSummaryResponse, BudgetProgressResponse } from '@/types/budget';

const mockUseBudgetSummary = vi.mocked(useBudgetSummary);
const mockUseCreateBudget = vi.mocked(useCreateBudget);
const mockUseUpdateBudget = vi.mocked(useUpdateBudget);
const mockUseDeleteBudget = vi.mocked(useDeleteBudget);
const mockUseBudget = vi.mocked(useBudget);
const mockUseDocumentTitle = vi.mocked(useDocumentTitle);
const mockUseNavigate = vi.mocked(useNavigate);

const createTestQueryClient = () => new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

const mockBudgetSummary: BudgetSummaryResponse = {
  totalBudgets: 2,
  totalBudgeted: 1000,
  totalSpent: 650,
  totalRemaining: 350,
  currency: 'USD',
  budgets: [
    {
      budgetId: 1,
      categoryName: 'Groceries',
      amount: 500,
      spent: 350.25,
      remaining: 149.75,
      percentageSpent: 70.05,
      status: 'ON_TRACK',
      currency: 'USD',
      period: 'MONTHLY',
    },
    {
      budgetId: 2,
      categoryName: 'Entertainment',
      amount: 500,
      spent: 300,
      remaining: 200,
      percentageSpent: 60,
      status: 'ON_TRACK',
      currency: 'USD',
      period: 'MONTHLY',
    },
  ],
};

const mockEmptySummary: BudgetSummaryResponse = {
  totalBudgets: 0,
  totalBudgeted: 0,
  totalSpent: 0,
  totalRemaining: 0,
  currency: 'USD',
  budgets: [],
};

describe('BudgetsPage', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = createTestQueryClient();
    vi.clearAllMocks();

    // Default mocks
    mockUseDocumentTitle.mockImplementation(() => {});
    mockUseBudget.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: null,
    } as any);
  });

  describe('Loading State', () => {
    it('shows loading skeletons when summary is loading', () => {
      mockUseBudgetSummary.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      renderWithProviders(<BudgetsPage />, { queryClient });

      const skeletons = screen.getAllByTestId('loading-skeleton');
      expect(skeletons.length).toBeGreaterThan(0);
    });
  });

  describe('Empty State', () => {
    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockEmptySummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);
    });

    it('shows "No budgets yet" empty state when there are no budgets', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });

      expect(screen.getByText('No budgets yet')).toBeInTheDocument();
      expect(screen.getByText('Create your first budget to start tracking your spending')).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: 'Add Budget' })).toHaveLength(2); // Header and empty state
    });
  });

  describe('Filters Toggle', () => {
    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockBudgetSummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);
    });

    it('renders Filters toggle button', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });

      expect(screen.getByRole('button', { name: /filters/i })).toBeInTheDocument();
    });

    it('shows BudgetFilters panel when Filters button is clicked', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });

      const filtersButton = screen.getByRole('button', { name: /filters/i });
      fireEvent.click(filtersButton);

      // BudgetFilters should be rendered (check for its content)
      expect(screen.getByLabelText('Search')).toBeInTheDocument();
      expect(screen.getByLabelText('Period')).toBeInTheDocument();
    });

    it('hides BudgetFilters panel when Filters button is clicked again', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });

      const filtersButton = screen.getByRole('button', { name: /filters/i });
      fireEvent.click(filtersButton);
      fireEvent.click(filtersButton);

      // BudgetFilters should not be rendered
      expect(screen.queryByLabelText('Search')).not.toBeInTheDocument();
      expect(screen.queryByLabelText('Period')).not.toBeInTheDocument();
    });
  });

  describe('Add Budget Button', () => {
    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockBudgetSummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);
    });

    it('opens create dialog when "Add Budget" button is clicked', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });

      const addButton = screen.getByRole('button', { name: 'Add Budget' });
      fireEvent.click(addButton);

      expect(screen.getByText('Create Budget')).toBeInTheDocument();
      expect(screen.getByTestId('budget-form')).toBeInTheDocument();
    });
  });

  describe('Budget Cards Display', () => {
    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockBudgetSummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);
    });

    it('shows budget cards when budgets exist', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });

      expect(screen.getByTestId('budget-card-1')).toBeInTheDocument();
      expect(screen.getByTestId('budget-card-2')).toBeInTheDocument();
      expect(screen.getByText('Groceries')).toBeInTheDocument();
      expect(screen.getByText('Entertainment')).toBeInTheDocument();
    });

    it('shows budget summary card when budgets exist', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });

      expect(screen.getByTestId('budget-summary-card')).toBeInTheDocument();
      expect(screen.getByText('Summary: 2 budgets')).toBeInTheDocument();
    });
  });

  describe('Document Title', () => {
    it('sets document title to "Budgets"', () => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockEmptySummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      renderWithProviders(<BudgetsPage />, { queryClient });

      expect(mockUseDocumentTitle).toHaveBeenCalledWith('Budgets');
    });
  });

  describe('Error State', () => {
    it('shows error message when summary loading fails', () => {
      mockUseBudgetSummary.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to load'),
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      renderWithProviders(<BudgetsPage />, { queryClient });

      expect(screen.getByText('Failed to load budgets. Please try again later.')).toBeInTheDocument();
    });
  });

  describe('Budget Actions', () => {
    const mockDeleteFn = vi.fn();

    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockBudgetSummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: mockDeleteFn,
        isPending: false,
      } as any);
    });

    it('renders budget card items for each budget', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      expect(screen.getByTestId('budget-card-1')).toBeInTheDocument();
      expect(screen.getByTestId('budget-card-2')).toBeInTheDocument();
    });

    it('renders period selector buttons', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      // Period labels are present (from BudgetSummaryCard data)
      expect(screen.getByTestId('budget-summary-card')).toBeInTheDocument();
    });
  });

  describe('Alert Banners', () => {
    const warningBudget = {
      budgetId: 3,
      categoryName: 'Dining',
      amount: 200,
      spent: 180,
      remaining: 20,
      percentageSpent: 90,
      status: 'WARNING' as const,
      currency: 'USD',
      period: 'MONTHLY' as const,
    };

    const exceededBudget = {
      budgetId: 4,
      categoryName: 'Shopping',
      amount: 300,
      spent: 350,
      remaining: -50,
      percentageSpent: 116.67,
      status: 'EXCEEDED' as const,
      currency: 'USD',
      period: 'MONTHLY' as const,
    };

    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: {
          ...mockBudgetSummary,
          budgets: [...mockBudgetSummary.budgets, warningBudget, exceededBudget],
        },
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: vi.fn(),
        isPending: false,
      } as any);
    });

    it('shows alert banners for warning/exceeded budgets', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      const alerts = screen.getAllByTestId('alert-banner');
      expect(alerts.length).toBeGreaterThan(0);
    });
  });

  describe('CRUD Operations', () => {
    const mockCreateFn = vi.fn();
    const mockUpdateFn = vi.fn();
    const mockDeleteFn = vi.fn();

    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockBudgetSummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({
        mutateAsync: mockCreateFn,
        isPending: false,
      } as any);

      mockUseUpdateBudget.mockReturnValue({
        mutateAsync: mockUpdateFn,
        isPending: false,
      } as any);

      mockUseDeleteBudget.mockReturnValue({
        mutateAsync: mockDeleteFn,
        isPending: false,
      } as any);
    });

    it('opens edit dialog when edit button is clicked', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByTestId('edit-1'));
      expect(screen.getByTestId('budget-form')).toBeInTheDocument();
    });

    it('opens delete confirmation when delete button is clicked', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByTestId('del-1'));
      expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
    });

    it('calls deleteBudget on confirm', async () => {
      mockDeleteFn.mockResolvedValue(undefined);
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByTestId('del-1'));
      fireEvent.click(screen.getByTestId('confirm-btn'));
      await waitFor(() => expect(mockDeleteFn).toHaveBeenCalledWith(1));
    });

    it('cancels delete dialog', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByTestId('del-1'));
      expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
      fireEvent.click(screen.getByTestId('cancel-btn'));
      expect(screen.queryByTestId('confirmation-dialog')).not.toBeInTheDocument();
    });

    it('calls createBudget on form submit', async () => {
      mockCreateFn.mockResolvedValue(undefined);
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByRole('button', { name: 'Add Budget' }));
      fireEvent.click(screen.getByTestId('form-submit'));
      await waitFor(() => expect(mockCreateFn).toHaveBeenCalled());
    });

    it('cancels form', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByRole('button', { name: 'Add Budget' }));
      expect(screen.getByTestId('budget-form')).toBeInTheDocument();
      fireEvent.click(screen.getByTestId('form-cancel'));
      expect(screen.queryByTestId('budget-form')).not.toBeInTheDocument();
    });

    it('opens view detail modal', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByTestId('view-1'));
      expect(screen.getByTestId('budget-detail-modal')).toBeInTheDocument();
    });

    it('handles create error', async () => {
      mockCreateFn.mockRejectedValue({ response: { data: { message: 'Duplicate budget' } } });
      renderWithProviders(<BudgetsPage />, { queryClient });
      fireEvent.click(screen.getByRole('button', { name: 'Add Budget' }));
      fireEvent.click(screen.getByTestId('form-submit'));
      await waitFor(() => expect(screen.getByTestId('form-error')).toHaveTextContent('Duplicate budget'));
    });
  });

  describe('Alert Dismiss', () => {
    const warningBudget = {
      budgetId: 3,
      categoryName: 'Dining',
      amount: 200,
      spent: 180,
      remaining: 20,
      percentageSpent: 90,
      status: 'WARNING' as const,
      currency: 'USD',
      period: 'MONTHLY' as const,
    };

    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: {
          ...mockBudgetSummary,
          budgets: [...mockBudgetSummary.budgets, warningBudget],
        },
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
      mockUseUpdateBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
      mockUseDeleteBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
    });

    it('dismisses alert and stores in localStorage', () => {
      const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
      renderWithProviders(<BudgetsPage />, { queryClient });
      const dismissBtns = screen.getAllByTestId('dismiss-alert');
      fireEvent.click(dismissBtns[0]);
      expect(setItemSpy).toHaveBeenCalledWith('dismissed_budget_alerts', expect.any(String));
      setItemSpy.mockRestore();
    });
  });

  describe('Pagination', () => {
    beforeEach(() => {
      // Create 25 budgets to test pagination
      const manyBudgets = Array.from({ length: 25 }, (_, i) => ({
        budgetId: i + 1,
        categoryName: `Category ${i + 1}`,
        amount: 100,
        spent: 50,
        remaining: 50,
        percentageSpent: 50,
        status: 'ON_TRACK' as const,
        currency: 'USD',
        period: 'MONTHLY' as const,
      }));

      mockUseBudgetSummary.mockReturnValue({
        data: { ...mockBudgetSummary, totalBudgets: 25, budgets: manyBudgets },
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
      mockUseUpdateBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
      mockUseDeleteBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
    });

    it('shows 20 budgets on first page by default', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      const cards = screen.getAllByTestId(/^budget-card-/);
      expect(cards.length).toBe(20);
    });
  });

  describe('Wizard', () => {
    beforeEach(() => {
      mockUseBudgetSummary.mockReturnValue({
        data: mockBudgetSummary,
        isLoading: false,
        error: null,
      } as any);

      mockUseCreateBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
      mockUseUpdateBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
      mockUseDeleteBudget.mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as any);
    });

    it('opens wizard when auto-create button is clicked', () => {
      renderWithProviders(<BudgetsPage />, { queryClient });
      const wizardBtn = screen.getByRole('button', { name: /auto/i });
      fireEvent.click(wizardBtn);
      expect(screen.getByTestId('budget-wizard')).toBeInTheDocument();
    });
  });
});