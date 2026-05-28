import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useDocumentTitle', () => ({ useDocumentTitle: vi.fn() }));

let mockData: any = undefined;
let mockIsLoading = false;
let mockError: Error | null = null;

vi.mock('@/hooks/useRecurringTransactions', () => ({
  useRecurringTransactionsPaged: () => ({ data: mockData, isLoading: mockIsLoading, error: mockError }),
  useCreateRecurringTransaction: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateRecurringTransaction: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteRecurringTransaction: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePauseRecurringTransaction: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useResumeRecurringTransaction: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));
vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({ data: [], isLoading: false }),
}));
vi.mock('@/hooks/useTransactions', () => ({
  useCategories: () => ({ data: [], isLoading: false }),
}));
vi.mock('@/components/transactions/RecurringTransactionCard', () => ({
  RecurringTransactionCard: ({ recurringTransaction }: any) => (
    <div data-testid="recurring-card">{recurringTransaction.description}</div>
  ),
}));
vi.mock('@/components/transactions/RecurringTransactionDetailModal', () => ({
  RecurringTransactionDetailModal: () => null,
}));
vi.mock('@/components/transactions/RecurringTransactionForm', () => ({
  RecurringTransactionForm: () => <div data-testid="recurring-form" />,
}));
vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: () => null,
}));
vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: () => <select data-testid="account-selector" />,
}));

import RecurringTransactionsPage from './RecurringTransactionsPage';

describe('RecurringTransactionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockData = undefined;
    mockIsLoading = false;
    mockError = null;
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders the page title', () => {
    renderWithProviders(<RecurringTransactionsPage />);
    expect(screen.getByText('Recurring Transactions')).toBeInTheDocument();
  });

  it('shows empty state when no transactions', () => {
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RecurringTransactionsPage />);
    expect(screen.getByText(/no recurring/i)).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Network error');
    renderWithProviders(<RecurringTransactionsPage />);
    expect(screen.getByText(/network error/i)).toBeInTheDocument();
  });

  it('renders recurring transaction cards with data', () => {
    mockData = {
      content: [
        { id: 1, description: 'Monthly Rent', amount: 1000, isActive: true, isEnded: false, isDue: false },
        { id: 2, description: 'Netflix', amount: 15, isActive: true, isEnded: false, isDue: true },
      ],
      totalPages: 1,
      totalElements: 2,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RecurringTransactionsPage />);
    expect(screen.getByText('Monthly Rent')).toBeInTheDocument();
    expect(screen.getByText('Netflix')).toBeInTheDocument();
  });

  it('shows add button and opens form', async () => {
    const user = userEvent.setup();
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RecurringTransactionsPage />);
    const addButtons = screen.getAllByRole('button', { name: /add|new|create/i });
    const addButton = addButtons[0];
    await user.click(addButton);
    expect(screen.getByTestId('recurring-form')).toBeInTheDocument();
  });

  it('shows filter toggle button', () => {
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RecurringTransactionsPage />);
    expect(screen.getByRole('button', { name: /filter/i })).toBeInTheDocument();
  });

  it('displays stat badges', () => {
    mockData = {
      content: [
        { id: 1, description: 'Rent', amount: 1000, isActive: true, isEnded: false, isDue: false },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RecurringTransactionsPage />);
    // Stats show active/paused/ended/due counts
    expect(screen.getByText(/active/i)).toBeInTheDocument();
  });
});
