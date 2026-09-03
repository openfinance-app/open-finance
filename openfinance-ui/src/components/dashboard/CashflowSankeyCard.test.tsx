import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CashflowSankeyCard from './CashflowSankeyCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { useCashflowSankey } from '../../hooks/useDashboard';

const mockNavigate = vi.fn();
vi.mock('react-router', async importOriginal => {
  const actual = await importOriginal<typeof import('react-router')>();
  return { ...actual, useNavigate: () => mockNavigate };
});
vi.mock('../../hooks/useDashboard', () => ({ useCashflowSankey: vi.fn() }));
vi.mock('../ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: number }) => <span>{amount}</span>,
}));
vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({ format: (v: number) => String(v) }),
}));

const mockUseCashflowSankey = vi.mocked(useCashflowSankey);

const sankeyData = {
  totalIncome: 1000,
  totalExpenses: 600,
  surplus: 400,
  incomeSources: [{ name: 'Salary', amount: 1000, categoryId: 5 }],
  expenseCategories: [
    { name: 'Groceries', amount: 400, categoryId: 7 },
    { name: 'Uncategorized', amount: 200, categoryId: null },
  ],
  period: 30,
};

describe('CashflowSankeyCard navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockUseCashflowSankey.mockReturnValue({
      data: sankeyData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useCashflowSankey>);
  });

  it('navigates to income transactions from the income pill', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View income transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?type=INCOME&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });

  it('navigates to expense transactions from the expenses pill', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View expense transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?type=EXPENSE&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });

  it('navigates by categoryId when a flow is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View Salary transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?categoryId=5&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });

  it('navigates with noCategory for the Uncategorized flow', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View Uncategorized transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?noCategory=1&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });
});
