import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { TransactionList } from '@/components/transactions/TransactionList';
import { markSelectInteraction, wasSelectJustClosed } from '@/utils/selectClickGuard';
import type { PaymentMethod, Transaction } from '@/types/transaction';
import type { Payee } from '@/types/payee';
const state = vi.hoisted(() => ({
  payees: [] as Payee[],
  edit: vi.fn(),
  remove: vi.fn(),
  view: vi.fn(),
  category: vi.fn(),
  account: vi.fn(),
}));
vi.mock('@/hooks/usePayees', () => ({ useActivePayees: () => ({ data: state.payees }) }));
vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({ data: [{ id: 5, institution: { logo: '/logos/bank.svg' } }] }),
}));
vi.mock('@/hooks/useUserSettings', async original => ({
  ...(await original<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: { dateFormat: 'DD/MM/YYYY' } }),
}));
const transaction = (changes: Partial<Transaction> = {}): Transaction => ({
  id: 1,
  userId: 1,
  accountId: 5,
  type: 'EXPENSE',
  amount: 10,
  currency: 'EUR',
  date: '2024-01-01',
  isReconciled: false,
  createdAt: '2024-01-01',
  ...changes,
});
function mount(rows: Transaction[], filters = true) {
  return renderWithProviders(
    <TransactionList
      transactions={rows}
      onEdit={state.edit}
      onDelete={state.remove}
      onViewDetail={state.view}
      onFilterByAccount={filters ? state.account : undefined}
      onFilterByCategory={filters ? state.category : undefined}
    />
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.payees = [];
  Element.prototype.scrollIntoView = vi.fn();
});
describe('Transaction row metadata and actions', () => {
  it.each<[PaymentMethod, string]>([
    ['CASH', 'Cash'],
    ['CHEQUE', 'Cheque'],
    ['CREDIT_CARD', 'Credit Card'],
    ['DEBIT_CARD', 'Debit Card'],
    ['BANK_TRANSFER', 'Bank Transfer'],
    ['DEPOSIT', 'Deposit'],
    ['STANDING_ORDER', 'Standing Order'],
    ['DIRECT_DEBIT', 'Direct Debit'],
    ['ONLINE', 'Online'],
    ['OTHER', 'Other'],
  ])('identifies the %s payment method even without an account label', (paymentMethod, label) => {
    mount([transaction({ paymentMethod })]);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
  it.each(['INCOME', 'EXPENSE', 'TRANSFER'] as const)(
    'can open an unnamed %s transaction using its fallback title',
    type => {
      const row = transaction({ type });
      mount([row]);
      fireEvent.click(screen.getByText('No description'));
      expect(state.view).toHaveBeenCalledWith(row);
    }
  );
  it('uses the merchant as title when description is absent', () => {
    mount([transaction({ payee: 'Local store' })]);
    expect(screen.getAllByText('Local store')).toHaveLength(1);
    expect(screen.queryByText('No description')).not.toBeInTheDocument();
  });
  it('falls back to an initial when a merchant logo cannot load', () => {
    state.payees = [
      { id: 4, name: 'Market', isSystem: false, isActive: true, logo: '/missing.svg' },
    ];
    mount([transaction({ payee: 'Market', type: 'TRANSFER', toAccountName: 'Savings' })]);
    const logo = screen.getByAltText('Market');
    fireEvent.error(logo);
    expect(logo).toHaveClass('hidden');
    expect(logo.nextElementSibling).not.toHaveClass('hidden');
    expect(logo.nextElementSibling).toHaveTextContent('M');
    expect(screen.getByText('Savings')).toBeInTheDocument();
  });
  it('uses the category defaults and keeps category clicks separate from row detail', () => {
    mount([transaction({ categoryId: 3, categoryName: 'Custom category' })]);
    fireEvent.click(screen.getByRole('button', { name: 'Filter transactions by Custom category' }));
    expect(state.category).toHaveBeenCalledWith(3);
    expect(state.view).not.toHaveBeenCalled();
  });
  it('unmapped categories remain visible without an invalid filter action', () => {
    mount([transaction({ categoryName: 'Imported category' })]);
    expect(screen.getByText('Imported category').closest('button')).toBeNull();
    expect(state.category).not.toHaveBeenCalled();
  });
  it.each([true, false])('uses the denormalized account name with filters enabled=%s', filters => {
    mount([transaction({ accountName: 'Old account name' })], filters);
    const label = screen.getByText('Old account name');
    if (filters) {
      fireEvent.click(label);
      expect(state.account).toHaveBeenCalledWith(5);
      expect(state.view).not.toHaveBeenCalled();
    } else expect(label.closest('button')).toBeNull();
    const image = label.parentElement?.querySelector('img');
    expect(image).not.toBeNull();
    fireEvent.error(image!);
    expect(image).toHaveStyle({ display: 'none' });
  });
  it('edit and delete target the selected transaction without opening detail', () => {
    const row = transaction();
    mount([row]);
    fireEvent.click(screen.getByRole('button', { name: 'Edit transaction' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete transaction' }));
    expect(state.edit).toHaveBeenCalledWith(row);
    expect(state.remove).toHaveBeenCalledWith(row);
    expect(state.view).not.toHaveBeenCalled();
  });
  it('closing a selector cannot click through and open a transaction', async () => {
    mount([transaction({ description: 'Rent' })]);
    markSelectInteraction();
    fireEvent.click(screen.getByText('Rent'));
    expect(state.view).not.toHaveBeenCalled();
    await waitFor(() => expect(wasSelectJustClosed()).toBe(false));
    fireEvent.click(screen.getByText('Rent'));
    expect(state.view).toHaveBeenCalledTimes(1);
  });
  it.each([true, false])(
    'transfer pairing is independent of response order: income first=%s',
    incomeFirst => {
      const expense = transaction({
        description: 'Transfer out',
        type: 'EXPENSE',
        transferId: 'pair',
      });
      const income = transaction({
        id: 2,
        description: 'Transfer in',
        type: 'INCOME',
        transferId: 'pair',
      });
      mount(incomeFirst ? [income, expense] : [expense, income]);
      const edits = screen.getAllByRole('button', { name: 'Edit transaction' });
      expect(edits).toHaveLength(2);
      fireEvent.click(edits[0]);
      fireEvent.click(edits[1]);
      expect(state.edit.mock.calls.map(([row]: [Transaction]) => row.id)).toEqual([1, 2]);
    }
  );
});
