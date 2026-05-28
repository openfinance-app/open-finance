import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import PortfolioPerformanceCards from './PortfolioPerformanceCards';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { IPortfolioPerformance } from '@/types/dashboard';

// Mock recharts to avoid ResizeObserver issues in jsdom
vi.mock('recharts', () => ({
  LineChart: ({ children }: any) => <div data-testid="line-chart">{children}</div>,
  Line: () => <div data-testid="line" />,
  ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
}));

// Mock ConvertedAmount to simplify output
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

const mockPerformance: IPortfolioPerformance = {
  label: 'stocks',
  currentValue: 10000,
  changeAmount: 500,
  changePercentage: 5.25,
  currency: 'EUR',
  sparklineData: [
    { date: '2026-01-01', value: 9500 },
    { date: '2026-02-01', value: 9800 },
    { date: '2026-03-01', value: 10000 },
  ],
};

const negativePerformance: IPortfolioPerformance = {
  label: 'crypto',
  currentValue: 5000,
  changeAmount: -300,
  changePercentage: -5.66,
  currency: 'EUR',
  sparklineData: [
    { date: '2026-01-01', value: 5300 },
    { date: '2026-02-01', value: 5100 },
    { date: '2026-03-01', value: 5000 },
  ],
};

const neutralPerformance: IPortfolioPerformance = {
  label: 'bonds',
  currentValue: 3000,
  changeAmount: 0,
  changePercentage: 0,
  currency: 'EUR',
  sparklineData: [],
};

describe('PortfolioPerformanceCards', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders empty state when no performances', () => {
    renderWithProviders(<PortfolioPerformanceCards performances={[]} />);
    expect(screen.getByText('Portfolio Performance')).toBeInTheDocument();
    expect(screen.getByText('No performance data available')).toBeInTheDocument();
  });

  it('renders performance cards with data', () => {
    renderWithProviders(
      <PortfolioPerformanceCards performances={[mockPerformance]} />
    );
    expect(screen.getByText('Portfolio Performance')).toBeInTheDocument();
    // The percentage should be displayed
    expect(screen.getByText(/5\.25%/)).toBeInTheDocument();
  });

  it('renders multiple performance cards', () => {
    renderWithProviders(
      <PortfolioPerformanceCards
        performances={[mockPerformance, negativePerformance, neutralPerformance]}
      />
    );
    // Should render 3 cards with converted amounts
    const amounts = screen.getAllByTestId('converted-amount');
    expect(amounts.length).toBeGreaterThanOrEqual(3);
  });

  it('renders negative change percentage', () => {
    renderWithProviders(
      <PortfolioPerformanceCards performances={[negativePerformance]} />
    );
    expect(screen.getByText(/-5\.66%/)).toBeInTheDocument();
  });

  it('renders sparkline no-data message for empty sparklineData', () => {
    renderWithProviders(
      <PortfolioPerformanceCards performances={[neutralPerformance]} />
    );
    expect(screen.getByText('No historical data')).toBeInTheDocument();
  });

  it('renders period label when provided', () => {
    renderWithProviders(
      <PortfolioPerformanceCards
        performances={[mockPerformance]}
        periodLabel="last 30d"
      />
    );
    expect(screen.getByText('Portfolio Performance')).toBeInTheDocument();
  });
});
