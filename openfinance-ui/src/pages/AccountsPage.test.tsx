/**
 * AccountsPage Tests
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import AccountsPage from '@/pages/AccountsPage';

const mockAccount = {
  id: 1,
  userId: 1,
  name: 'Checking Account',
  type: 'CHECKING',
  currency: 'USD',
  balance: 5000,
  isActive: true,
  createdAt: '2023-01-01',
  baseCurrency: 'USD',
  isConverted: false,
  balanceInBaseCurrency: null,
};

const mockAccount2 = {
  id: 2,
  userId: 1,
  name: 'Savings Account',
  type: 'SAVINGS',
  currency: 'USD',
  balance: 15000,
  isActive: true,
  createdAt: '2023-01-01',
  baseCurrency: 'USD',
  isConverted: false,
  balanceInBaseCurrency: null,
};

let mockPagedResponse: any = {
  content: [mockAccount, mockAccount2],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
};
let mockIsLoading = false;
let mockError: any = null;
const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockCloseMutateAsync = vi.fn();
const mockReopenMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();

vi.mock('@/hooks/useAccounts', () => ({
  useAccountsSearch: () => ({
    data: mockPagedResponse,
    isLoading: mockIsLoading,
    error: mockError,
  }),
  useCreateAccount: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateAccount: () => ({ mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useCloseAccount: () => ({ mutateAsync: mockCloseMutateAsync, isPending: false }),
  useReopenAccount: () => ({ mutateAsync: mockReopenMutateAsync, isPending: false }),
  usePermanentDeleteAccount: () => ({ mutateAsync: mockDeleteMutateAsync, isPending: false }),
}));

vi.mock('@/components/accounts/AccountCard', () => ({
  AccountCard: ({ account, onEdit, onDelete, onClose, onReopen, onViewDetail }: any) => (
    <div data-testid={`account-card-${account.id}`}>
      <span>{account.name}</span>
      <span>{account.type}</span>
      <span>{account.balance}</span>
      <button onClick={() => onEdit(account)}>Edit</button>
      <button onClick={() => onDelete(account)}>Delete</button>
      <button onClick={() => onClose(account)}>Close</button>
      <button onClick={() => onReopen(account)}>Reopen</button>
      <button onClick={() => onViewDetail(account.id)}>View Detail</button>
    </div>
  ),
}));

vi.mock('@/components/accounts/AccountForm', () => ({
  AccountForm: ({ account, onSubmit, onCancel }: any) => (
    <div data-testid="account-form">
      {account && <span data-testid="editing-name">{account.name}</span>}
      <button onClick={() => onSubmit({ name: 'Test', type: 'CHECKING', currency: 'USD', balance: 1000 })}>Submit</button>
      <button onClick={onCancel}>Cancel</button>
    </div>
  ),
}));

vi.mock('@/components/accounts/AccountFilters', () => ({
  AccountFilters: () => <div data-testid="account-filters">Filters Panel</div>,
}));

vi.mock('@/components/accounts/AccountDetailModal', () => ({
  AccountDetailModal: ({ onClose }: any) => (
    <div data-testid="account-detail-modal">
      <button onClick={onClose}>Close Detail</button>
    </div>
  ),
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onConfirm, onCancel, title }: any) =>
    open ? (
      <div data-testid="confirmation-dialog">
        <span>{title}</span>
        <button onClick={onConfirm}>Confirm Action</button>
        <button onClick={onCancel}>Cancel Action</button>
      </div>
    ) : null,
}));

describe('AccountsPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    vi.clearAllMocks();
    mockPagedResponse = {
      content: [mockAccount, mockAccount2],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    mockIsLoading = false;
    mockError = null;
  });

  it('renders page heading', () => {
    renderWithProviders(<AccountsPage />);
    expect(screen.getByText('Accounts')).toBeInTheDocument();
  });

  it('displays account cards', () => {
    renderWithProviders(<AccountsPage />);
    expect(screen.getByText('Checking Account')).toBeInTheDocument();
    expect(screen.getByText('Savings Account')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockIsLoading = true;
    renderWithProviders(<AccountsPage />);
    expect(screen.queryByText('Checking Account')).not.toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Network error');
    renderWithProviders(<AccountsPage />);
    expect(screen.getByText(/failed to load/i)).toBeInTheDocument();
  });

  it('shows empty state when no accounts', () => {
    mockPagedResponse = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
    renderWithProviders(<AccountsPage />);
    expect(screen.getByText(/no accounts/i)).toBeInTheDocument();
  });

  it('has add account button', () => {
    renderWithProviders(<AccountsPage />);
    expect(screen.getByRole('button', { name: /add account/i })).toBeInTheDocument();
  });

  it('opens create form dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: /add account/i }));
    await waitFor(() => expect(screen.getByTestId('account-form')).toBeInTheDocument());
  });

  it('submits create form', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockResolvedValue({});
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: /add account/i }));
    await waitFor(() => expect(screen.getByTestId('account-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });

  it('opens edit form with existing account', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    const editButtons = screen.getAllByRole('button', { name: /^edit$/i });
    await user.click(editButtons[0]);
    await waitFor(() => expect(screen.getByTestId('editing-name')).toHaveTextContent('Checking Account'));
  });

  it('submits edit form', async () => {
    const user = userEvent.setup();
    mockUpdateMutateAsync.mockResolvedValue({});
    renderWithProviders(<AccountsPage />);
    const editButtons = screen.getAllByRole('button', { name: /^edit$/i });
    await user.click(editButtons[0]);
    await waitFor(() => expect(screen.getByTestId('account-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockUpdateMutateAsync).toHaveBeenCalled();
  });

  it('cancels form dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: /add account/i }));
    await waitFor(() => expect(screen.getByTestId('account-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    await waitFor(() => expect(screen.queryByTestId('account-form')).not.toBeInTheDocument());
  });

  it('shows delete confirmation dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i });
    await user.click(deleteButtons[0]);
    expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
  });

  it('confirms delete action', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue({});
    renderWithProviders(<AccountsPage />);
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i });
    await user.click(deleteButtons[0]);
    await user.click(screen.getByRole('button', { name: /confirm action/i }));
    expect(mockDeleteMutateAsync).toHaveBeenCalledWith(1);
  });

  it('shows close confirmation dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    const closeButtons = screen.getAllByRole('button', { name: /^close$/i });
    await user.click(closeButtons[0]);
    expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
  });

  it('confirms close action', async () => {
    const user = userEvent.setup();
    mockCloseMutateAsync.mockResolvedValue({});
    renderWithProviders(<AccountsPage />);
    const closeButtons = screen.getAllByRole('button', { name: /^close$/i });
    await user.click(closeButtons[0]);
    await user.click(screen.getByRole('button', { name: /confirm action/i }));
    expect(mockCloseMutateAsync).toHaveBeenCalledWith(1);
  });

  it('shows reopen confirmation dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    const reopenButtons = screen.getAllByRole('button', { name: /^reopen$/i });
    await user.click(reopenButtons[0]);
    expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
  });

  it('opens detail modal on view detail', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    const viewButtons = screen.getAllByRole('button', { name: /view detail/i });
    await user.click(viewButtons[0]);
    expect(screen.getByTestId('account-detail-modal')).toBeInTheDocument();
  });

  it('closes detail modal', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    const viewButtons = screen.getAllByRole('button', { name: /view detail/i });
    await user.click(viewButtons[0]);
    expect(screen.getByTestId('account-detail-modal')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /close detail/i }));
    expect(screen.queryByTestId('account-detail-modal')).not.toBeInTheDocument();
  });

  it('toggles filter panel', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    expect(screen.queryByTestId('account-filters')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /filter/i }));
    expect(screen.getByTestId('account-filters')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /filter/i }));
    expect(screen.queryByTestId('account-filters')).not.toBeInTheDocument();
  });
});
