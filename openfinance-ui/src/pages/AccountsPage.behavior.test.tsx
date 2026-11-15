import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import AccountsPage from '@/pages/AccountsPage';
import type { Account, AccountFilters } from '@/types/account';
import type { AccountCard } from '@/components/accounts/AccountCard';
import type { AccountFilters as AccountFiltersComponent } from '@/components/accounts/AccountFilters';
import type { AccountForm } from '@/components/accounts/AccountForm';
import type { AccountDetailModal } from '@/components/accounts/AccountDetailModal';
import type { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { useViewStateStore } from '@/stores/viewState';
import { useAccountsSearch } from '@/hooks/useAccounts';
import { FETCH_ALL_PAGE_SIZE } from '@/constants/pagination';

const close = vi.fn();
const reopen = vi.fn();
const remove = vi.fn();
const create = vi.fn();
const update = vi.fn();
let accounts: Account[];
let filtered: Account[];
let totalPages: number;
let totalElements: number;
let filteredElements: number;
let searchError: unknown;
let filteredLoading: boolean;
let closePending: boolean;

vi.mock('@/hooks/useAccounts', () => ({
  useAccountsSearch: vi.fn(),
  useCreateAccount: () => ({ mutateAsync: create, isPending: false }),
  useUpdateAccount: () => ({ mutateAsync: update, isPending: false }),
  useCloseAccount: () => ({ mutateAsync: close, isPending: closePending }),
  useReopenAccount: () => ({ mutateAsync: reopen, isPending: false }),
  usePermanentDeleteAccount: () => ({ mutateAsync: remove, isPending: false }),
}));
vi.mock('@/components/accounts/AccountCard', () => ({
  AccountCard: ({
    account,
    onEdit,
    onDelete,
    onClose,
    onReopen,
    onViewDetail,
  }: ComponentProps<typeof AccountCard>) => (
    <article aria-label={account.name}>
      <button onClick={() => onEdit(account)}>Edit {account.name}</button>
      <button onClick={() => onDelete(account)}>Delete {account.name}</button>
      <button onClick={() => onClose(account)}>Close {account.name}</button>
      <button onClick={() => onReopen(account)}>Reopen {account.name}</button>
      <button onClick={() => onViewDetail?.(account)}>View {account.name}</button>
    </article>
  ),
}));
vi.mock('@/components/accounts/AccountFilters', () => ({
  AccountFilters: ({
    filters,
    onFiltersChange,
  }: ComponentProps<typeof AccountFiltersComponent>) => (
    <section aria-label="Account filters">
      <button onClick={() => onFiltersChange({ ...filters, balanceMin: 0 })}>
        Nonnegative balances
      </button>
      <button onClick={() => onFiltersChange({})}>Clear filters</button>
    </section>
  ),
}));
vi.mock('@/components/accounts/AccountForm', () => ({
  AccountForm: ({
    account,
    onSubmit,
    existingAccountNames,
  }: ComponentProps<typeof AccountForm>) => (
    <section aria-label="Account editor">
      <output aria-label="Existing names">{existingAccountNames?.join(', ')}</output>
      <button
        onClick={() =>
          onSubmit({
            name: account?.name ?? 'New',
            type: 'CHECKING',
            currency: 'EUR',
            initialBalance: '0',
          })
        }
      >
        Save account
      </button>
    </section>
  ),
}));
vi.mock('@/components/accounts/AccountDetailModal', () => ({
  AccountDetailModal: ({ accountId, onClose }: ComponentProps<typeof AccountDetailModal>) => (
    <section aria-label={`Details ${accountId}`}>
      <button onClick={onClose}>Close details</button>
    </section>
  ),
}));
vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({
    open,
    title,
    description,
    onConfirm,
    onOpenChange,
    loading,
  }: ComponentProps<typeof ConfirmationDialog>) =>
    open ? (
      <section aria-label={title}>
        <p>{description}</p>
        <button disabled={loading} onClick={onConfirm}>
          Confirm action
        </button>
        <button onClick={() => onOpenChange(false)}>Cancel action</button>
      </section>
    ) : null,
}));

const account = (id: number, overrides: Partial<Account> = {}): Account => ({
  id,
  userId: 1,
  name: `Account ${id}`,
  type: 'CHECKING',
  currency: 'EUR',
  balance: 100,
  ownBalance: 100,
  isActive: true,
  createdAt: '2024-01-01',
  ...overrides,
});

function seedFilters(filters: AccountFilters) {
  useViewStateStore
    .getState()
    .save('previous', '/accounts', 'filters', { page: 0, size: 20, sort: 'name,asc', ...filters });
}

function currentFilters() {
  return vi
    .mocked(useAccountsSearch)
    .mock.calls.filter(([, all]) => !all)
    .at(-1)?.[0];
}

beforeEach(() => {
  mockAuthentication();
  window.history.replaceState({}, '', '/accounts');
  useViewStateStore.getState().clear();
  vi.resetAllMocks();
  Element.prototype.scrollIntoView = vi.fn();
  accounts = [account(1), account(2)];
  filtered = [accounts[0]];
  totalPages = 1;
  totalElements = 2;
  filteredElements = 1;
  searchError = null;
  filteredLoading = false;
  closePending = false;
  vi.mocked(useAccountsSearch).mockImplementation((filters, all) => {
    const hasFilter = Object.entries(filters ?? {}).some(
      ([key, value]) => !['page', 'size', 'sort'].includes(key) && value !== undefined
    );
    return {
      data: {
        content: all ? (hasFilter ? filtered : accounts) : accounts.slice(0, 1),
        totalElements: all && hasFilter ? filteredElements : totalElements,
        totalPages: all ? 1 : totalPages,
        number: filters?.page ?? 0,
        size: filters?.size ?? 20,
      },
      error: all ? null : searchError,
      isLoading: Boolean(all && hasFilter && filteredLoading),
    } as ReturnType<typeof useAccountsSearch>;
  });
});

describe('account page decisions', () => {
  it.each([false, true])(
    'does not present partial conversions as global or filtered totals (reverse order: %s)',
    reverse => {
      accounts = [
        account(1, {
          currency: 'USD',
          balance: 100,
          baseCurrency: 'EUR',
          isConverted: true,
          balanceInBaseCurrency: 90,
        }),
        account(2, { currency: 'USD', balance: 50, baseCurrency: 'EUR', isConverted: false }),
      ];
      if (reverse) accounts.reverse();
      seedFilters({ type: 'CHECKING' });
      filtered = accounts;
      filteredElements = 2;
      renderWithProviders(<AccountsPage />);
      expect(
        screen.getAllByText(/Total unavailable: some balances could not be converted/)
      ).toHaveLength(2);
      expect(screen.queryByText('USD → EUR')).not.toBeInTheDocument();
      expect(screen.queryByText(/90\.00/)).not.toBeInTheDocument();
      expect(screen.getAllByText(/150\.00/)).toHaveLength(2);
    }
  );

  it('sums complete foreign conversions independently from native currency balances', () => {
    accounts = [
      account(1, {
        currency: 'USD',
        balance: 100,
        baseCurrency: 'EUR',
        isConverted: true,
        balanceInBaseCurrency: 90,
      }),
      account(2, {
        currency: 'USD',
        balance: 50,
        baseCurrency: 'EUR',
        isConverted: true,
        balanceInBaseCurrency: 45,
      }),
      account(3, { balance: 20 }),
    ];
    totalElements = 3;
    seedFilters({ currency: 'USD' });
    filtered = [accounts[0]];
    renderWithProviders(<AccountsPage />);
    expect(screen.getByText('USD → EUR')).toBeInTheDocument();
    expect(screen.getByText(/155\.00/)).toBeInTheDocument();
    expect(screen.getByText(/135\.00/)).toBeInTheDocument();
    expect(screen.getAllByText(/90\.00/)).toHaveLength(2);
  });

  it.each(['global', 'filtered'])(
    'withholds a %s total if the all-records response was truncated',
    scope => {
      seedFilters({ currency: 'EUR' });
      if (scope === 'global') totalElements = 10;
      else filteredElements = 5;
      renderWithProviders(<AccountsPage />);
      expect(
        screen.getByText(/Total unavailable: some balances could not be converted/)
      ).toBeInTheDocument();
    }
  );

  it.each<AccountFilters>([
    { isActive: false },
    { balanceMin: 0 },
    { balanceMax: 0 },
    { balanceExact: 0 },
    { lowBalance: true },
  ])('explains that an empty filtered list needs different filters: %j', filters => {
    accounts = [];
    seedFilters(filters);
    renderWithProviders(<AccountsPage />);
    expect(
      screen.getByText('No accounts match your filters. Try adjusting your search criteria.')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Create your first account to start tracking your finances')
    ).not.toBeInTheDocument();
  });
  it.each<AccountFilters>([
    { keyword: 'cash' },
    { type: 'CASH' },
    { currency: 'EUR' },
    { institution: 'bank' },
    { isActive: false },
    { balanceMin: 0 },
    { balanceMax: 0 },
    { balanceExact: 0 },
    { lowBalance: true },
  ])('shows filtered totals even for false or zero filters: %j', filters => {
    seedFilters(filters);
    renderWithProviders(<AccountsPage />);
    expect(screen.getAllByText(/Filtered:/).length).toBeGreaterThan(0);
    expect(screen.getByText('(1 of 2 accounts)')).toBeInTheDocument();
    expect(currentFilters()).toEqual(expect.objectContaining(filters));
  });

  it('resets pagination when filtering and preserves the selected page size', async () => {
    seedFilters({ page: 2, size: 50 });
    totalPages = 4;
    totalElements = 170;
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: /^Filters$/ }));
    await user.click(screen.getByRole('button', { name: 'Nonnegative balances' }));
    expect(currentFilters()).toEqual(expect.objectContaining({ page: 0, size: 50, balanceMin: 0 }));
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(currentFilters()?.page).toBe(1);
    await user.selectOptions(screen.getByRole('combobox', { name: /rows per page/i }), '20');
    expect(currentFilters()).toEqual(expect.objectContaining({ page: 0, size: 20, balanceMin: 0 }));
    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.queryByText(/Filtered:/)).not.toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('region', { name: 'Account filters' })).not.toBeInTheDocument();
  });

  it.each([0, 1, 2])(
    'returns from a deleted last page to the last remaining page (%i pages)',
    remaining => {
      seedFilters({ page: 3 });
      totalPages = remaining;
      renderWithProviders(<AccountsPage />);
      expect(currentFilters()?.page).toBe(Math.max(0, remaining - 1));
    }
  );

  it('uses every matching account for subtotals instead of the visible page', () => {
    seedFilters({ currency: 'EUR' });
    filtered = [account(1, { balance: 12 }), account(2, { balance: 23 })];
    filteredElements = 2;
    renderWithProviders(<AccountsPage />);
    expect(screen.getAllByText(/35\.00/).length).toBeGreaterThan(0);
    expect(useAccountsSearch).toHaveBeenCalledWith(
      expect.objectContaining({ currency: 'EUR', page: 0, size: FETCH_ALL_PAGE_SIZE }),
      true
    );
  });

  it('does not display incomplete totals while the filtered query is loading', () => {
    seedFilters({ isActive: false });
    filteredLoading = true;
    renderWithProviders(<AccountsPage />);
    expect(screen.queryByText('Total Balance')).not.toBeInTheDocument();
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
  });

  it.each(['close', 'reopen', 'delete'] as const)(
    'reports %s failures without retrying when the error dialog is dismissed',
    async action => {
      const mutation = { close, reopen, delete: remove }[action];
      mutation.mockRejectedValue({});
      const user = userEvent.setup();
      renderWithProviders(<AccountsPage />);
      await user.click(
        screen.getByRole('button', {
          name: `${action[0].toUpperCase()}${action.slice(1)} Account 1`,
        })
      );
      await user.click(screen.getByRole('button', { name: 'Confirm action' }));
      await screen.findByText(new RegExp(`Failed to ${action} account`));
      await user.click(screen.getByRole('button', { name: 'Confirm action' }));
      expect(mutation).toHaveBeenCalledExactlyOnceWith(1);
      expect(screen.queryByRole('region', { name: 'Action Failed' })).not.toBeInTheDocument();
    }
  );

  it.each([
    [
      { response: { data: { message: 'Account has linked loans' } }, message: 'Network failure' },
      'Account has linked loans',
    ],
    [new Error('Connection lost'), 'Connection lost'],
  ])('preserves the most specific action error', async (error, message) => {
    reopen.mockRejectedValue(error);
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: 'Reopen Account 1' }));
    await user.click(screen.getByRole('button', { name: 'Confirm action' }));
    expect(await screen.findByText(new RegExp(String(message)))).toBeInTheDocument();
  });

  it('cancels a destructive action without sending a request', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: 'Delete Account 1' }));
    await user.click(screen.getByRole('button', { name: 'Cancel action' }));
    expect(remove).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Confirm action' })).not.toBeInTheDocument();
  });

  it('prevents repeated confirmations while a mutation is pending', async () => {
    closePending = true;
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: 'Close Account 1' }));
    expect(screen.getByRole('button', { name: 'Confirm action' })).toBeDisabled();
  });

  it('keeps an unsuccessful edit open and excludes its own name from duplicate checks', async () => {
    update.mockRejectedValue(new Error('Save failed'));
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    await user.click(screen.getByRole('button', { name: 'Edit Account 1' }));
    expect(screen.getByRole('status', { name: 'Existing names' })).toHaveTextContent('Account 2');
    expect(screen.getByRole('status', { name: 'Existing names' })).not.toHaveTextContent(
      'Account 1'
    );
    await user.click(screen.getByRole('button', { name: 'Save account' }));
    await waitFor(() => expect(update).toHaveBeenCalled());
    expect(screen.getByRole('region', { name: 'Account editor' })).toBeInTheDocument();
  });

  it('opens and clears a highlighted account without losing other URL parameters', async () => {
    window.history.replaceState({}, '', '/accounts?highlight=42&lowBalance=1');
    const user = userEvent.setup();
    renderWithProviders(<AccountsPage />);
    expect(screen.getByRole('region', { name: 'Details 42' })).toBeInTheDocument();
    expect(currentFilters()?.lowBalance).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Close details' }));
    expect(window.location.search).toBe('?lowBalance=1');
    expect(screen.queryByRole('region', { name: 'Details 42' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'View Account 1' }));
    expect(screen.getByRole('region', { name: 'Details 1' })).toBeInTheDocument();
  });

  it.each([
    [400, 'Decryption failed for this record', true],
    [500, 'Decryption failed for this record', false],
    [400, 'Invalid filter', false],
    [400, undefined, false],
  ])(
    'only requests a new password for a decryption failure (%s, %s)',
    (status, message, decrypt) => {
      searchError = { response: { status, data: { message } } };
      renderWithProviders(<AccountsPage />);
      expect(screen.queryByText('Cannot Decrypt Account Data') !== null).toBe(decrypt);
      expect(screen.queryByText(/Failed to load accounts/) !== null).toBe(!decrypt);
    }
  );
});
