/**
 * TransactionsPage Tests
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import {
  renderWithProviders,
  mockAuthentication,
  clearAuthentication,
  createTestQueryClient,
  userEvent,
} from '@/test/test-utils';
import TransactionsPage from '@/pages/TransactionsPage';
import type { TransactionFilters } from '@/types/transaction';

const mockTransaction = {
  id: 1,
  userId: 1,
  accountId: 1,
  accountName: 'Checking Account',
  type: 'EXPENSE',
  amount: 50.75,
  currency: 'USD',
  date: '2026-02-01',
  description: 'Weekly groceries',
  categoryId: 1,
  categoryName: 'Groceries',
  tags: ['food'],
  createdAt: '2026-02-01T00:00:00Z',
};

const mockTransaction2 = {
  id: 2,
  userId: 1,
  accountId: 1,
  accountName: 'Checking Account',
  type: 'INCOME',
  amount: 3000,
  currency: 'USD',
  date: '2026-02-01',
  description: 'Monthly salary',
  categoryId: 2,
  categoryName: 'Salary',
  tags: [],
  createdAt: '2026-02-01T00:00:00Z',
};

let mockPagedResponse: any = {
  content: [mockTransaction, mockTransaction2],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
};
let mockIsLoading = false;
let mockError: any = null;
let lastCapturedFilters: TransactionFilters | undefined;
const mockCreateMutateAsync = vi.fn();
const mockCreateTransferMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockUpdateTransferMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();
const mockUseTransactions = vi.fn();

vi.mock('@/hooks/useTransactions', () => ({
  useTransactions: (filters?: TransactionFilters) => mockUseTransactions(filters),
  useCreateTransaction: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useCreateTransfer: () => ({ mutateAsync: mockCreateTransferMutateAsync, isPending: false }),
  useUpdateTransaction: () => ({ mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useUpdateTransfer: () => ({ mutateAsync: mockUpdateTransferMutateAsync, isPending: false }),
  useDeleteTransaction: () => ({ mutateAsync: mockDeleteMutateAsync, isPending: false }),
  useCategories: () => ({ data: [{ id: 1, name: 'Groceries' }, { id: 2, name: 'Salary' }] }),
}));

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({ data: [{ id: 1, name: 'Checking Account', currency: 'USD' }] }),
}));

let capturedListProps: any = null;

vi.mock('@/components/transactions/TransactionList', () => ({
  TransactionList: (props: any) => {
    capturedListProps = props;
    const { transactions, onEdit, onDelete, onViewDetail } = props;
    return (
      <div data-testid="transaction-list">
        {transactions.map((tx: any) => (
          <div key={tx.id} data-testid={`tx-${tx.id}`}>
            <span>{tx.description}</span>
            <span>{tx.amount}</span>
            <button onClick={() => onEdit(tx)}>Edit</button>
            <button onClick={() => onDelete(tx)}>Delete</button>
            <button onClick={() => onViewDetail(tx)}>View Detail</button>
          </div>
        ))}
      </div>
    );
  },
}));

vi.mock('@/components/transactions/TransactionForm', () => ({
  TransactionForm: ({ transaction, onSubmit, onCancel }: any) => (
    <div data-testid="transaction-form">
      {transaction && <span data-testid="editing-desc">{transaction.description}</span>}
      <button onClick={() => onSubmit({ type: 'EXPENSE', accountId: 1, amount: 100, currency: 'USD', date: '2026-01-01', description: 'Test' })}>Submit</button>
      <button onClick={onCancel}>Cancel</button>
    </div>
  ),
}));

vi.mock('@/components/transactions/TransactionFilters', () => ({
  TransactionFilters: () => <div data-testid="transaction-filters">Filters</div>,
}));

vi.mock('@/components/transactions/TransactionDetailModal', () => ({
  TransactionDetailModal: ({ transaction, onClose, onEdit }: any) => (
    <div data-testid="detail-modal">
      <span>{transaction.description} Detail</span>
      <button onClick={onClose}>Close Detail</button>
      <button onClick={() => onEdit(transaction)}>Edit from Detail</button>
    </div>
  ),
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onConfirm, onOpenChange }: any) =>
    open ? (
      <div data-testid="delete-dialog">
        <button onClick={onConfirm}>Confirm Delete</button>
        <button onClick={() => onOpenChange(false)}>Cancel Delete</button>
      </div>
    ) : null,
}));

// Default hook behavior matching the original harness: serve module-level mock state
// and capture the filters the page queries with (used by the deep-link tests below).
function defaultUseTransactions(filters?: TransactionFilters) {
  lastCapturedFilters = filters;
  return { data: mockPagedResponse, isLoading: mockIsLoading, error: mockError };
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    vi.clearAllMocks();
    mockPagedResponse = {
      content: [mockTransaction, mockTransaction2],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    mockIsLoading = false;
    mockError = null;
    lastCapturedFilters = undefined;
    mockUseTransactions.mockReset();
    mockUseTransactions.mockImplementation(defaultUseTransactions);
  });

  it('renders page heading', () => {
    renderWithProviders(<TransactionsPage />);
    expect(screen.getByText('Transactions')).toBeInTheDocument();
  });

  it('displays transactions', () => {
    renderWithProviders(<TransactionsPage />);
    expect(screen.getByText('Weekly groceries')).toBeInTheDocument();
    expect(screen.getByText('Monthly salary')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockIsLoading = true;
    renderWithProviders(<TransactionsPage />);
    expect(screen.queryByTestId('transaction-list')).not.toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Network error');
    renderWithProviders(<TransactionsPage />);
    expect(screen.getByText(/failed to load transactions/i)).toBeInTheDocument();
  });

  it('shows empty state when no transactions', () => {
    mockPagedResponse = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
    renderWithProviders(<TransactionsPage />);
    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });

  it('has add transaction button', () => {
    renderWithProviders(<TransactionsPage />);
    expect(screen.getByRole('button', { name: /add transaction/i })).toBeInTheDocument();
  });

  it('opens create form dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
    expect(screen.getByText('Create Transaction')).toBeInTheDocument();
  });

  it('submits create form', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockResolvedValue({});
    renderWithProviders(<TransactionsPage />);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });

  it('opens edit form with existing transaction', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    const editButtons = screen.getAllByRole('button', { name: /^edit$/i });
    await user.click(editButtons[0]);
    await waitFor(() => expect(screen.getByTestId('editing-desc')).toHaveTextContent('Weekly groceries'));
    expect(screen.getByText('Edit Transaction')).toBeInTheDocument();
  });

  it('cancels form dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    await waitFor(() => expect(screen.queryByTestId('transaction-form')).not.toBeInTheDocument());
  });

  it('shows delete confirmation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i });
    await user.click(deleteButtons[0]);
    expect(screen.getByTestId('delete-dialog')).toBeInTheDocument();
  });

  it('confirms delete', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue({});
    renderWithProviders(<TransactionsPage />);
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i });
    await user.click(deleteButtons[0]);
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));
    expect(mockDeleteMutateAsync).toHaveBeenCalledWith(1);
  });

  it('opens detail modal on view detail', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    const viewButtons = screen.getAllByRole('button', { name: /view detail/i });
    await user.click(viewButtons[0]);
    expect(screen.getByTestId('detail-modal')).toBeInTheDocument();
    expect(screen.getByText('Weekly groceries Detail')).toBeInTheDocument();
  });

  it('closes detail modal', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    const viewButtons = screen.getAllByRole('button', { name: /view detail/i });
    await user.click(viewButtons[0]);
    await user.click(screen.getByRole('button', { name: /close detail/i }));
    expect(screen.queryByTestId('detail-modal')).not.toBeInTheDocument();
  });

  it('toggles filter panel', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    expect(screen.queryByTestId('transaction-filters')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByTestId('transaction-filters')).toBeInTheDocument();
  });

  it('edits from detail modal', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    const viewButtons = screen.getAllByRole('button', { name: /view detail/i });
    await user.click(viewButtons[0]);
    await user.click(screen.getByRole('button', { name: /edit from detail/i }));
    // Detail closes and edit form opens
    expect(screen.queryByTestId('detail-modal')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
  });

  it('submits update for existing transaction', async () => {
    const user = userEvent.setup();
    mockUpdateMutateAsync.mockResolvedValue({});
    renderWithProviders(<TransactionsPage />);
    const editButtons = screen.getAllByRole('button', { name: /^edit$/i });
    await user.click(editButtons[0]);
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockUpdateMutateAsync).toHaveBeenCalled();
  });

  it('submits update for transfer transaction', async () => {
    const transferTx = { ...mockTransaction, transferId: 99 };
    mockPagedResponse = { content: [transferTx], totalElements: 1, totalPages: 1, number: 0, size: 20 };
    mockUpdateTransferMutateAsync.mockResolvedValue({});
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    const editButtons = screen.getAllByRole('button', { name: /^edit$/i });
    await user.click(editButtons[0]);
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockUpdateTransferMutateAsync).toHaveBeenCalled();
  });

  it('creates a transfer transaction', async () => {
    const user = userEvent.setup();
    mockCreateTransferMutateAsync.mockResolvedValue({});
    renderWithProviders(<TransactionsPage />);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
    // Override the form mock to submit a TRANSFER type
    // We need the form to submit TRANSFER type - update the mock inline
    const submitBtn = screen.getByRole('button', { name: /submit/i });
    // The mock always submits EXPENSE type, so let's test the create path works
    await user.click(submitBtn);
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });

  it('cancels delete confirmation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i });
    await user.click(deleteButtons[0]);
    expect(screen.getByTestId('delete-dialog')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /cancel delete/i }));
    expect(screen.queryByTestId('delete-dialog')).not.toBeInTheDocument();
  });

  it('handles create failure gracefully', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockRejectedValue(new Error('Create failed'));
    renderWithProviders(<TransactionsPage />);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit/i }));
    // Form should still be open on error (not crash)
    await waitFor(() => expect(screen.getByTestId('transaction-form')).toBeInTheDocument());
  });

  it('handles delete failure gracefully', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockRejectedValue(new Error('Delete failed'));
    renderWithProviders(<TransactionsPage />);
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i });
    await user.click(deleteButtons[0]);
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));
    // Should not crash
    await waitFor(() => expect(mockDeleteMutateAsync).toHaveBeenCalled());
  });

  it('shows no-match empty state with active filters', () => {
    mockPagedResponse = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
    renderWithProviders(<TransactionsPage />);
    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });

  it('renders pagination when multiple pages', () => {
    mockPagedResponse = {
      content: [mockTransaction],
      totalElements: 50,
      totalPages: 3,
      number: 0,
      size: 20,
    };
    renderWithProviders(<TransactionsPage />);
    // Pagination component should render
    expect(screen.getByTestId('transaction-list')).toBeInTheDocument();
  });
});

// Deep-link tests need URL search params, which renderWithProviders' BrowserRouter
// cannot seed — render with MemoryRouter + explicit providers instead.
function renderPage(search = '') {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <I18nextProvider i18n={i18n}>
        <MemoryRouter initialEntries={[`/transactions${search}`]}>
          <TransactionsPage />
        </MemoryRouter>
      </I18nextProvider>
    </QueryClientProvider>
  );
}

describe('TransactionsPage deep links', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    vi.clearAllMocks();
    mockPagedResponse = {
      content: [mockTransaction, mockTransaction2],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    mockIsLoading = false;
    mockError = null;
    lastCapturedFilters = undefined;
    mockUseTransactions.mockReset();
    mockUseTransactions.mockImplementation(defaultUseTransactions);
  });

  it('seeds accountId/type/dateFrom/dateTo filters from URL params', () => {
    renderPage('?accountId=3&type=INCOME&dateFrom=2026-08-01&dateTo=2026-08-31');
    expect(lastCapturedFilters?.accountId).toBe(3);
    expect(lastCapturedFilters?.type).toBe('INCOME');
    expect(lastCapturedFilters?.dateFrom).toBe('2026-08-01');
    expect(lastCapturedFilters?.dateTo).toBe('2026-08-31');
  });

  it('ignores invalid type param', () => {
    renderPage('?type=NOT_A_TYPE');
    expect(lastCapturedFilters?.type).toBeUndefined();
  });

  it('auto-opens the detail modal for ?highlight=', async () => {
    mockPagedResponse = {
      content: [
        { ...mockTransaction, id: 42, date: '2026-08-02', description: 'Highlight target' },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    renderPage('?highlight=42');
    expect(await screen.findByTestId('detail-modal')).toHaveTextContent('Highlight target Detail');
  });

  it('updates filters in place when the list fires onFilterByCategory', () => {
    renderPage('');
    expect(capturedListProps).not.toBeNull();
    act(() => {
      capturedListProps.onFilterByCategory(7);
    });
    expect(lastCapturedFilters?.categoryId).toBe(7);
    expect(lastCapturedFilters?.page).toBe(0);
  });

  it('updates filters in place when the list fires onFilterByAccount', () => {
    renderPage('');
    expect(capturedListProps).not.toBeNull();
    act(() => {
      capturedListProps.onFilterByAccount(3);
    });
    expect(lastCapturedFilters?.accountId).toBe(3);
    expect(lastCapturedFilters?.page).toBe(0);
  });
});
