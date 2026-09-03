import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import RecentTransactionsCard from './RecentTransactionsCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { Transaction } from '@/types/transaction';

const mockNavigate = vi.fn();
vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: any) => (
    <span data-testid="converted-amount">{amount} {currency}</span>
  ),
}));

vi.mock('@/components/ui/Badge', () => ({
  Badge: ({ children }: any) => <span data-testid="badge">{children}</span>,
}));

vi.mock('@/hooks/usePayees', () => ({
  useActivePayees: () => ({ data: [] }),
}));

vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' } }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn() }),
}));

const transactions: Transaction[] = [
  {
    id: 1,
    description: 'Salary',
    amount: 3000,
    date: '2026-05-01',
    type: 'INCOME',
    accountId: 1,
    currency: 'EUR',
    categoryName: 'Salary',
    categoryColor: '#22c55e',
    categoryIcon: '💰',
  } as Transaction,
  {
    id: 2,
    description: 'Grocery Store',
    amount: -150,
    date: '2026-05-02',
    type: 'EXPENSE',
    accountId: 1,
    currency: 'EUR',
    payee: 'Carrefour',
    categoryName: 'Food',
    categoryColor: '#ef4444',
    categoryIcon: '🛒',
  } as Transaction,
  {
    id: 3,
    description: 'Transfer to Savings',
    amount: -500,
    date: '2026-05-03',
    type: 'TRANSFER',
    accountId: 1,
    currency: 'EUR',
  } as Transaction,
];

describe('RecentTransactionsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders empty state when no transactions', () => {
    renderWithProviders(<RecentTransactionsCard transactions={[]} />);
    expect(screen.getByText('Transactions')).toBeInTheDocument();
    expect(screen.getByText('No transactions found')).toBeInTheDocument();
  });

  it('renders loading state', () => {
    renderWithProviders(
      <RecentTransactionsCard transactions={[]} isLoading={true} />
    );
    expect(screen.getByText('Transactions')).toBeInTheDocument();
  });

  it('renders transaction descriptions', () => {
    renderWithProviders(
      <RecentTransactionsCard transactions={transactions} />
    );
    expect(screen.getAllByText('Salary').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Grocery Store')).toBeInTheDocument();
    expect(screen.getByText('Transfer to Savings')).toBeInTheDocument();
  });

  it('renders "View all" link', () => {
    renderWithProviders(
      <RecentTransactionsCard transactions={transactions} />
    );
    expect(screen.getByText(/View all/)).toBeInTheDocument();
  });

  it('renders period label when provided', () => {
    renderWithProviders(
      <RecentTransactionsCard transactions={transactions} periodLabel="Last 30 days" />
    );
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
  });

  it('renders period label in empty state', () => {
    renderWithProviders(
      <RecentTransactionsCard transactions={[]} periodLabel="Last 7 days" />
    );
    expect(screen.getByText('Last 7 days')).toBeInTheDocument();
  });
});

describe('RecentTransactionsCard navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  const tx = {
    id: 11,
    type: 'EXPENSE',
    amount: 20,
    currency: 'EUR',
    date: '2026-08-02',
    accountId: 3,
    accountName: 'Checking',
    categoryId: 5,
    categoryName: 'Groceries',
    payee: 'Carrefour',
  } as unknown as Transaction;

  it('navigates to the transaction detail on row click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RecentTransactionsCard transactions={[tx]} periodLabel="" isLoading={false} />);
    await user.click(screen.getByRole('button', { name: 'View transaction details' }));
    expect(mockNavigate).toHaveBeenCalledWith('/transactions?highlight=11');
  });

  // it('filters by account when the account chip is clicked (no row navigation)', async () => {
  //   const user = userEvent.setup();
  //   renderWithProviders(<RecentTransactionsCard transactions={[tx]} periodLabel="" isLoading={false} />);
  //   await user.click(screen.getByRole('button', { name: 'Filter transactions by Checking' }));
  //   expect(mockNavigate).toHaveBeenCalledTimes(1);
  //   expect(mockNavigate).toHaveBeenCalledWith('/transactions?accountId=3');
  // });

  // it('filters by category when the category chip is clicked', async () => {
  //   const user = userEvent.setup();
  //   renderWithProviders(<RecentTransactionsCard transactions={[tx]} periodLabel="" isLoading={false} />);
  //   await user.click(screen.getByRole('button', { name: 'Filter transactions by Groceries' }));
  //   expect(mockNavigate).toHaveBeenCalledTimes(1);
  //   expect(mockNavigate).toHaveBeenCalledWith('/transactions?categoryId=5');
  // });
});
