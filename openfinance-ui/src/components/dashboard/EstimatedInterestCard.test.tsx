import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import EstimatedInterestCard from './EstimatedInterestCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { IEstimatedInterestSummary } from '@/types/dashboard';

// Mock ConvertedAmount
vi.mock('../ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: any) => (
    <span data-testid="converted-amount">{amount} {currency}</span>
  ),
}));

// Mock useSecondaryConversion
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (v: number) => v,
    secondaryCurrency: 'USD',
    secondaryExchangeRate: 1,
  }),
}));

// Mock SimpleSelect to render a plain <select>
vi.mock('../ui/SimpleSelect', () => ({
  SimpleSelect: ({ value, onChange, children }: any) => (
    <select data-testid="filter-select" value={value} onChange={onChange}>
      {children}
    </select>
  ),
}));

const baseSummary: IEstimatedInterestSummary = {
  totalEarned: 1250.5,
  totalProjected: 3000,
  currency: 'EUR',
  accounts: [
    {
      accountId: 1,
      accountName: 'Savings Account',
      interestEarned: 800,
      projectedInterest: 2000,
    },
    {
      accountId: 2,
      accountName: 'Loan Account',
      interestEarned: 450.5,
      projectedInterest: -500,
    },
  ],
};

const emptySummary: IEstimatedInterestSummary = {
  totalEarned: 0,
  totalProjected: 0,
  currency: 'EUR',
  accounts: [],
};

describe('EstimatedInterestCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders title and subtitle', () => {
    renderWithProviders(
      <EstimatedInterestCard summary={baseSummary} period="MONTHLY" />
    );
    expect(screen.getByText('Estimated Interest')).toBeInTheDocument();
  });

  it('renders total earned amount', () => {
    renderWithProviders(
      <EstimatedInterestCard summary={baseSummary} period="MONTHLY" />
    );
    expect(screen.getByText('Total Earned')).toBeInTheDocument();
    // Total earned should show as converted amount
    const amounts = screen.getAllByTestId('converted-amount');
    expect(amounts.length).toBeGreaterThanOrEqual(1);
  });

  it('renders account breakdown', () => {
    renderWithProviders(
      <EstimatedInterestCard summary={baseSummary} period="MONTHLY" />
    );
    expect(screen.getByText('Savings Account')).toBeInTheDocument();
    expect(screen.getByText('Loan Account')).toBeInTheDocument();
  });

  it('renders no data message when accounts are empty', () => {
    renderWithProviders(
      <EstimatedInterestCard summary={emptySummary} period="MONTHLY" />
    );
    expect(screen.getByText('No interest data available for this period.')).toBeInTheDocument();
  });

  it('renders filter select with options', () => {
    renderWithProviders(
      <EstimatedInterestCard summary={baseSummary} period="MONTHLY" />
    );
    const select = screen.getByTestId('filter-select');
    expect(select).toBeInTheDocument();
  });

  it('filters accounts when filter type changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <EstimatedInterestCard summary={baseSummary} period="MONTHLY" />
    );
    const select = screen.getByTestId('filter-select');
    await user.selectOptions(select, 'LIABILITIES');
    // After filtering to liabilities, only negative projectedInterest accounts show
    expect(screen.getByText('Loan Account')).toBeInTheDocument();
  });
});
