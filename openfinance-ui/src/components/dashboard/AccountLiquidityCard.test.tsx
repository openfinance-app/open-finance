import { screen } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { Account } from '@/types/account';
import AccountLiquidityCard from '@/components/dashboard/AccountLiquidityCard';

const query = vi.hoisted(() => ({ data: [] as Account[], isLoading: false, isError: false }));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => query }));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: string }) => <span>{amount}</span>,
}));

function account(type: Account['type'], balance: number, values: Partial<Account> = {}): Account {
  return {
    id: 1,
    userId: 1,
    name: 'Account',
    createdAt: '2026-01-01',
    currency: 'EUR',
    type,
    balance,
    ownBalance: balance,
    isActive: true,
    ...values,
  };
}

beforeEach(() => {
  mockAuthentication();
  localStorage.removeItem('open_finance_amounts_visible');
  Object.assign(query, { data: [], isLoading: false, isError: false });
});

it('counts every open account as available unless its type is locked capital', () => {
  query.data = [
    account('CHECKING', -20),
    account('CASH', 5),
    account('SAVINGS', 100, {
      currency: 'USD',
      baseCurrency: 'EUR',
      isConverted: true,
      balanceInBaseCurrency: 90,
    }),
    account('LOCKED_SAVINGS', 150, { ownBalance: 50, holdingsValue: 100 }),
    account('LOCKED_SAVINGS', 400, { isActive: false }),
    account('CHECKING', 200, { isActive: false }),
    account('INVESTMENT', 800, { ownBalance: 150, holdingsValue: 650 }),
    account('CREDIT_CARD', -300),
    account('OTHER', 700),
  ];
  renderWithProviders(<AccountLiquidityCard baseCurrency="EUR" />);
  expect(screen.getAllByRole('definition').map(row => row.textContent)).toEqual(['1275', '150']);
});

it('withholds only the total missing a conversion and keeps an empty group at zero', () => {
  query.data = [account('LOCKED_SAVINGS', 100, { currency: 'USD', isConverted: false })];
  renderWithProviders(<AccountLiquidityCard baseCurrency="EUR" />);
  expect(screen.getAllByRole('definition').map(row => row.textContent)).toEqual([
    '0',
    'Exchange rate unavailable',
  ]);
});

it.each(['isLoading', 'isError'] as const)(
  'does not show zero balances when the request is %s',
  status => {
    query[status] = true;
    renderWithProviders(<AccountLiquidityCard baseCurrency="EUR" />);
    expect(screen.getByRole('status')).toBeVisible();
    expect(screen.queryByRole('definition')).toBeNull();
  }
);

it('shows the available share and hides it with the privacy setting', () => {
  query.data = [account('CHECKING', 100), account('LOCKED_SAVINGS', 300)];
  const { unmount } = renderWithProviders(<AccountLiquidityCard baseCurrency="EUR" />);
  expect(screen.getByRole('meter', { name: 'Available capital' })).toHaveAttribute(
    'aria-valuenow',
    '25'
  );
  expect(screen.getByText('25.00%')).toBeVisible();
  expect(screen.getByText('75.00%')).toBeVisible();
  expect(screen.getByRole('link', { name: 'Available capital' })).toHaveAttribute(
    'href',
    '/accounts?capital=available'
  );
  expect(screen.getByRole('link', { name: 'Locked capital' })).toHaveAttribute(
    'href',
    '/accounts?capital=locked'
  );
  unmount();
  localStorage.setItem('open_finance_amounts_visible', 'false');
  renderWithProviders(<AccountLiquidityCard baseCurrency="EUR" />);
  expect(screen.queryByRole('meter')).toBeNull();
  expect(screen.queryByText(/%/)).toBeNull();
});

it('keeps an overdrawn total visible without drawing a negative share', () => {
  query.data = [account('CHECKING', -100), account('LOCKED_SAVINGS', 300)];
  renderWithProviders(<AccountLiquidityCard baseCurrency="EUR" />);
  expect(screen.getAllByRole('definition').map(row => row.textContent)).toEqual(['-100', '300']);
  expect(screen.getByText('-50.00%')).toBeVisible();
  expect(screen.getByText('150.00%')).toBeVisible();
  expect(screen.queryByRole('meter')).toBeNull();
});

it('keeps both links available when the combined balance is zero', () => {
  query.data = [account('OTHER', -100), account('LOCKED_SAVINGS', 100)];
  renderWithProviders(<AccountLiquidityCard baseCurrency="EUR" />);
  expect(screen.queryByRole('meter')).toBeNull();
  expect(screen.queryByText(/%/)).toBeNull();
  expect(screen.getAllByRole('link')).toHaveLength(2);
});
