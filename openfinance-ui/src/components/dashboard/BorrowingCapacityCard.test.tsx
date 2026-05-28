import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import BorrowingCapacityCard from './BorrowingCapacityCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { IBorrowingCapacity } from '@/types/dashboard';

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: any) => (
    <span data-testid="converted-amount">{amount} {currency}</span>
  ),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (v: number) => v,
    secondaryCurrency: 'USD',
    secondaryExchangeRate: 1,
  }),
}));

const excellentCapacity: IBorrowingCapacity = {
  availableBorrowingCapacity: 50000,
  debtToIncomeRatio: 15,
  monthlyIncome: 5000,
  monthlyExpenses: 3000,
  monthlyDebtPayments: 750,
  recommendedMaxBorrowing: 1250,
  financialHealthStatus: 'EXCELLENT',
  analysisPeriod: 90,
  currency: 'EUR',
};

const poorCapacity: IBorrowingCapacity = {
  ...excellentCapacity,
  debtToIncomeRatio: 55,
  financialHealthStatus: 'POOR',
  availableBorrowingCapacity: 0,
};

describe('BorrowingCapacityCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders title and subtitle', () => {
    renderWithProviders(<BorrowingCapacityCard capacity={excellentCapacity} />);
    expect(screen.getByText('Borrowing Capacity')).toBeInTheDocument();
  });

  it('renders financial health status badge', () => {
    renderWithProviders(<BorrowingCapacityCard capacity={excellentCapacity} />);
    expect(screen.getByText('EXCELLENT')).toBeInTheDocument();
  });

  it('renders debt-to-income ratio', () => {
    renderWithProviders(<BorrowingCapacityCard capacity={excellentCapacity} />);
    expect(screen.getByText('15.0%')).toBeInTheDocument();
  });

  it('renders financial breakdown sections', () => {
    renderWithProviders(<BorrowingCapacityCard capacity={excellentCapacity} />);
    expect(screen.getByText('Monthly Income')).toBeInTheDocument();
    expect(screen.getByText('Monthly Expenses')).toBeInTheDocument();
    expect(screen.getByText('Monthly Debt Payments')).toBeInTheDocument();
    expect(screen.getByText('Recommended Max Borrowing')).toBeInTheDocument();
  });

  it('renders excellent insight for low DTI', () => {
    renderWithProviders(<BorrowingCapacityCard capacity={excellentCapacity} />);
    expect(screen.getByText('Insight:')).toBeInTheDocument();
  });

  it('renders poor health status', () => {
    renderWithProviders(<BorrowingCapacityCard capacity={poorCapacity} />);
    expect(screen.getByText(/Poor/i)).toBeInTheDocument();
    expect(screen.getByText('55.0%')).toBeInTheDocument();
  });

  it('renders converted amounts', () => {
    renderWithProviders(<BorrowingCapacityCard capacity={excellentCapacity} />);
    const amounts = screen.getAllByTestId('converted-amount');
    // Should have: available capacity, income, expenses, debt, recommended
    expect(amounts.length).toBeGreaterThanOrEqual(5);
  });
});
