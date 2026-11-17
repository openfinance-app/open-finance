import { type ReactNode } from 'react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import DashboardPage from '@/pages/DashboardPage';
import i18n from '@/test/i18n-test';

interface Query {
  data?: unknown;
  isLoading: boolean;
  error?: unknown;
}
const state = vi.hoisted(() => ({ queries: {} as Record<string, Query> }));
vi.mock('@/hooks/useDashboard', () => ({
  useDashboardSummary: () => state.queries.summary,
  useCashFlow: () => state.queries.cash,
  useNetWorthHistory: () => state.queries.history,
  useAssetAllocation: () => state.queries.allocation,
  usePortfolioPerformance: () => state.queries.performance,
  useBorrowingCapacity: () => state.queries.borrowing,
  useNetWorthAllocation: () => state.queries.netWorthAllocation,
  useEstimatedInterest: () => state.queries.interest,
  useTransactionsByPeriod: () => state.queries.transactions,
}));
vi.mock('react-grid-layout/legacy', () => ({
  WidthProvider: (component: unknown) => component,
  Responsive: ({
    children,
    onBreakpointChange,
  }: {
    children: ReactNode;
    onBreakpointChange: (value: string) => void;
  }) => (
    <div>
      <button onClick={() => onBreakpointChange('xs')}>Use phone</button>
      {children}
    </div>
  ),
}));
vi.mock('@/components/dashboard/RecentTransactionsCard', () => ({
  default: () => <div>RecentTransactionsCard</div>,
}));
vi.mock('@/components/dashboard/CashFlowChart', () => ({
  default: () => <div>CashFlowChart</div>,
}));
vi.mock('@/components/dashboard/NetWorthTrendChart', () => ({
  default: () => <div>NetWorthTrendChart</div>,
}));
vi.mock('@/components/dashboard/CurrencyBreakdown', () => ({
  default: () => <div>CurrencyBreakdown</div>,
}));
vi.mock('@/components/dashboard/InstitutionBreakdown', () => ({
  default: () => <div>InstitutionBreakdown</div>,
}));
vi.mock('@/components/dashboard/AssetAllocationChart', () => ({
  default: () => <div>AssetAllocationChart</div>,
}));
vi.mock('@/components/dashboard/PortfolioPerformanceCards', () => ({
  default: () => <div>PortfolioPerformanceCards</div>,
}));
vi.mock('@/components/dashboard/InsightsCard', () => ({ default: () => <div>InsightsCard</div> }));
vi.mock('@/components/dashboard/BorrowingCapacityCard', () => ({
  default: () => <div>BorrowingCapacityCard</div>,
}));
vi.mock('@/components/dashboard/NetWorthAllocationChart', () => ({
  default: () => <div>NetWorthAllocationChart</div>,
}));
vi.mock('@/components/dashboard/CashFlowCalendar', () => ({
  default: () => <div>CashFlowCalendar</div>,
}));
vi.mock('@/components/dashboard/CashflowSankeyCard', () => ({
  default: () => <div>CashflowSankeyCard</div>,
}));
vi.mock('@/components/dashboard/EstimatedInterestCard', () => ({
  default: () => <div>EstimatedInterestCard</div>,
}));
vi.mock('@/components/dashboard/BudgetProgressCard', () => ({
  default: () => <div>BudgetProgressCard</div>,
}));
vi.mock('@/components/dashboard/RssFeedCard', () => ({ default: () => <div>RssFeedCard</div> }));
vi.mock('@/components/dashboard/BalanceVariationCard', () => ({
  default: () => <div>BalanceVariationCard</div>,
}));
vi.mock('@/components/dashboard/FinancialMap', () => ({ default: () => <div>FinancialMap</div> }));
vi.mock('@/components/dashboard/NetWorthCard', () => ({
  default: ({ periodChange }: { periodChange: unknown }) => (
    <output aria-label="Period change">{JSON.stringify(periodChange)}</output>
  ),
}));

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  mockAuthentication();
  for (const key of [
    'summary',
    'cash',
    'history',
    'allocation',
    'performance',
    'borrowing',
    'netWorthAllocation',
    'interest',
    'transactions',
  ])
    state.queries[key] = { data: undefined, isLoading: false, error: null };
  state.queries.summary.data = {
    netWorth: {
      netWorth: 1000,
      totalAssets: 1200,
      totalLiabilities: 200,
      currency: 'EUR',
      date: '2026-01-31',
    },
    accounts: [],
    recentTransactions: [],
    snapshotDate: '2026-01-31',
    totalAccounts: 0,
    totalTransactions: 0,
    baseCurrency: 'EUR',
  };
});
afterEach(() => vi.restoreAllMocks());

let displayOverride: string | null = null;
vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({
    data: {
      dateFormat: 'YYYY-MM-DD',
      timezone: displayOverride ?? 'UTC',
      useDeviceTimezone: displayOverride === null,
    },
  }),
}));
afterEach(() => vi.useRealTimers());

describe('Net worth variation at the reporting calendar boundary', () => {
  it.each([
    {
      label: 'Paris before midnight',
      instant: '2026-11-15T22:51:00Z',
      reportingDate: '2026-11-15',
      override: 'Europe/Paris',
    },
    {
      label: 'Paris after midnight with a UTC server',
      instant: '2026-11-15T23:51:00Z',
      reportingDate: '2026-11-16',
      override: 'Europe/Paris',
    },
    {
      label: 'UTC account override',
      instant: '2026-11-15T23:51:00Z',
      reportingDate: '2026-11-15',
      override: 'UTC',
    },
    {
      label: 'New York account override',
      instant: '2026-11-15T23:51:00Z',
      reportingDate: '2026-11-15',
      override: 'America/New_York',
    },
  ])('$label', ({ instant, reportingDate, override }) => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(instant));
    displayOverride = override;
    state.queries.summary.data = {
      netWorth: {
        netWorth: 200,
        totalAssets: 200,
        totalLiabilities: 0,
        currency: 'EUR',
        date: reportingDate,
      },
      accounts: [],
      recentTransactions: [],
      snapshotDate: reportingDate,
      totalAccounts: 0,
      totalTransactions: 0,
      baseCurrency: 'EUR',
    };
    state.queries.history.data = [
      { date: '2026-11-01', netWorth: 100, currency: 'EUR' },
      { date: reportingDate, netWorth: 200, currency: 'EUR' },
    ];
    renderWithProviders(<DashboardPage />);
    expect(screen.getByLabelText('Period change')).toHaveTextContent(
      JSON.stringify({ amount: '100', percentage: 100 })
    );
  });
});
