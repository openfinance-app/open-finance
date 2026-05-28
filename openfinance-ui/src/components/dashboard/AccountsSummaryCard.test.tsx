import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AccountsSummaryCard from './AccountsSummaryCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { IAccountSummary } from '@/types/dashboard';

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: any) => (
    <span data-testid="converted-amount">
      {amount} {currency}
    </span>
  ),
}));

const mockNavigate = vi.fn();
vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return { ...actual, useNavigate: () => mockNavigate };
});

const accounts: IAccountSummary[] = [
  { id: 1, name: 'Main Checking', type: 'CHECKING', balance: 5000, currency: 'EUR', isActive: true },
  { id: 2, name: 'Savings', type: 'SAVINGS', balance: 20000, currency: 'EUR', isActive: true },
  { id: 3, name: 'Credit Card', type: 'CREDIT_CARD', balance: -1500, currency: 'EUR', isActive: true },
  { id: 4, name: 'Brokerage', type: 'INVESTMENT', balance: 50000, currency: 'USD', isActive: true },
  { id: 5, name: 'Cash Reserve', type: 'CASH', balance: 300, currency: 'EUR', isActive: true },
];

describe('AccountsSummaryCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders empty state when no accounts', () => {
    renderWithProviders(<AccountsSummaryCard accounts={[]} />);
    expect(screen.getByText('Accounts')).toBeInTheDocument();
    expect(screen.getByText('No accounts yet')).toBeInTheDocument();
  });

  it('renders all account names', () => {
    renderWithProviders(<AccountsSummaryCard accounts={accounts} />);
    expect(screen.getByText('Main Checking')).toBeInTheDocument();
    expect(screen.getByText('Savings')).toBeInTheDocument();
    expect(screen.getByText('Credit Card')).toBeInTheDocument();
    expect(screen.getByText('Brokerage')).toBeInTheDocument();
    expect(screen.getByText('Cash Reserve')).toBeInTheDocument();
  });

  it('renders account count badge', () => {
    renderWithProviders(<AccountsSummaryCard accounts={accounts} />);
    expect(screen.getByText('5 accounts')).toBeInTheDocument();
  });

  it('navigates to accounts page on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsSummaryCard accounts={accounts} />);

    const firstAccount = screen.getByText('Main Checking').closest('[role="button"]')!;
    await user.click(firstAccount);
    expect(mockNavigate).toHaveBeenCalledWith('/accounts');
  });

  it('navigates on keyboard Enter', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountsSummaryCard accounts={accounts} />);

    const firstAccount = screen.getByText('Main Checking').closest('[role="button"]')!;
    firstAccount.focus();
    await user.keyboard('{Enter}');
    expect(mockNavigate).toHaveBeenCalledWith('/accounts');
  });

  it('renders account type labels', () => {
    renderWithProviders(<AccountsSummaryCard accounts={accounts} />);
    expect(screen.getByText('CHECKING')).toBeInTheDocument();
    expect(screen.getByText('SAVINGS')).toBeInTheDocument();
    expect(screen.getByText('CREDIT CARD')).toBeInTheDocument();
  });

  it('renders balances via ConvertedAmount', () => {
    renderWithProviders(<AccountsSummaryCard accounts={accounts} />);
    const amounts = screen.getAllByTestId('converted-amount');
    expect(amounts.length).toBe(5);
  });
});
