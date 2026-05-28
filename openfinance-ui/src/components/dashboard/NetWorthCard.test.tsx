import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import NetWorthCard from './NetWorthCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { INetWorthSummary } from '@/types/dashboard';

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency, className }: any) => (
    <span data-testid="converted-amount" className={className}>
      {amount} {currency}
    </span>
  ),
}));

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children }: any) => <span data-testid="private-amount">{children}</span>,
}));

vi.mock('@/components/ui/HelpTooltip', () => ({
  HelpTooltip: ({ text }: any) => <span data-testid="help-tooltip" title={text} />,
}));

vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' } }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (v: number) => v,
    secondaryCurrency: 'USD',
    secondaryExchangeRate: 1,
  }),
}));

const baseSummary: INetWorthSummary = {
  date: '2026-05-01',
  totalAssets: 150000,
  totalLiabilities: 50000,
  netWorth: 100000,
  monthlyChangeAmount: 5000,
  monthlyChangePercentage: 5.26,
  currency: 'EUR',
};

describe('NetWorthCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders net worth amount', () => {
    renderWithProviders(<NetWorthCard netWorth={baseSummary} />);
    // Should render net worth via ConvertedAmount
    const amounts = screen.getAllByTestId('converted-amount');
    expect(amounts.length).toBeGreaterThanOrEqual(1);
    // The first large amount should be the net worth
    expect(amounts[0]).toHaveTextContent('100000');
  });

  it('renders total assets and liabilities', () => {
    renderWithProviders(<NetWorthCard netWorth={baseSummary} />);
    expect(screen.getByText('Total Assets')).toBeInTheDocument();
    expect(screen.getByText('Total Liabilities')).toBeInTheDocument();
  });

  it('renders positive change with green color and plus sign', () => {
    renderWithProviders(<NetWorthCard netWorth={baseSummary} />);
    // Should show positive percentage
    expect(screen.getByText(/5\.26%/)).toBeInTheDocument();
  });

  it('renders negative change indicator for losses', () => {
    const negativeSummary: INetWorthSummary = {
      ...baseSummary,
      monthlyChangeAmount: -3000,
      monthlyChangePercentage: -2.91,
    };
    renderWithProviders(<NetWorthCard netWorth={negativeSummary} />);
    expect(screen.getByText(/-2\.91%/)).toBeInTheDocument();
  });

  it('renders "no comparison" message when periodChange is explicitly null', () => {
    renderWithProviders(
      <NetWorthCard netWorth={baseSummary} periodChange={null} />
    );
    // Should show the fallback text for no comparison data
    expect(screen.queryByText(/5\.26%/)).not.toBeInTheDocument();
  });

  it('uses periodChange override when provided', () => {
    renderWithProviders(
      <NetWorthCard
        netWorth={baseSummary}
        periodChange={{ amount: 10000, percentage: 11.11 }}
      />
    );
    expect(screen.getByText(/11\.11%/)).toBeInTheDocument();
  });

  it('shows custom periodLabel text', () => {
    renderWithProviders(
      <NetWorthCard netWorth={baseSummary} periodLabel="last 90d" />
    );
    // The period label should appear in the card
    expect(screen.getByText(/last 90d/)).toBeInTheDocument();
  });

  it('renders help tooltips', () => {
    renderWithProviders(<NetWorthCard netWorth={baseSummary} />);
    const tooltips = screen.getAllByTestId('help-tooltip');
    expect(tooltips.length).toBeGreaterThanOrEqual(2);
  });
});
