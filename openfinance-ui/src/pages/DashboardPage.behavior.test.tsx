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

describe('dashboard request and presentation boundaries', () => {
  it.each([
    [
      { isAxiosError: true, response: { status: 503, data: { message: 'Rates unavailable' } } },
      'Rates unavailable',
    ],
    [
      { isAxiosError: true, response: { status: 503, data: { message: 42 } } },
      'An unexpected error occurred',
    ],
    [{ isAxiosError: true }, 'An unexpected error occurred'],
    [new Error('Offline'), 'Offline'],
    ['unknown', 'An unexpected error occurred'],
  ])('shows a safe message for summary failures %j', (error, expected) => {
    state.queries.summary = { data: undefined, isLoading: false, error };
    renderWithProviders(<DashboardPage />);
    expect(screen.getByText(expected as string)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Logout and Try Again' })).not.toBeInTheDocument();
  });

  it('clears credentials when recovering from a decryption failure', () => {
    state.queries.summary = {
      isLoading: false,
      error: {
        isAxiosError: true,
        response: { status: 400, data: { message: 'Decryption failed for account' } },
      },
    };
    renderWithProviders(<DashboardPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Logout and Try Again' }));
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(sessionStorage.getItem('encryption_key')).toBeNull();
  });

  it.each([
    'summary',
    'cash',
    'history',
    'allocation',
    'performance',
    'borrowing',
    'netWorthAllocation',
  ])('keeps the initial loader visible while %s is pending and no summary exists', query => {
    state.queries.summary.data = undefined;
    state.queries[query].isLoading = true;
    const { container } = renderWithProviders(<DashboardPage />);
    expect(container.querySelector('.animate-pulse')).not.toBeNull();
    expect(screen.queryByRole('button', { name: 'Cards' })).not.toBeInTheDocument();
  });

  it('does not render fabricated balances for an empty completed summary', () => {
    state.queries.summary.data = undefined;
    const { container } = renderWithProviders(<DashboardPage />);
    expect(container).toBeEmptyDOMElement();
  });

  it.each(['{broken', '{"netWorth":false}'])(
    'recovers or honors stored card visibility %s',
    stored => {
      localStorage.setItem('open_finance_dashboard_card_visibility', stored);
      renderWithProviders(<DashboardPage />);
      expect(screen.queryByLabelText('Period change') !== null).toBe(stored === '{broken');
      fireEvent.click(
        screen.getByRole('button', { name: i18n.t('cardsButton', { ns: 'dashboard' }) })
      );
      fireEvent.click(
        screen.getByRole('button', { name: i18n.t('resetLayout', { ns: 'dashboard' }) })
      );
      expect(screen.getByLabelText('Period change')).toBeInTheDocument();
      expect(localStorage.getItem('open_finance_dashboard_card_visibility')).toBeNull();
    }
  );

  it('keeps the card menu open for internal clicks and dismisses it outside', () => {
    renderWithProviders(<DashboardPage />);
    const button = screen.getByRole('button', { name: i18n.t('cardsButton', { ns: 'dashboard' }) });
    fireEvent.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
    fireEvent.mouseDown(screen.getByText(i18n.t('showCards', { ns: 'dashboard' })));
    expect(button).toHaveAttribute('aria-expanded', 'true');
    fireEvent.mouseDown(document.body);
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('renders newly available performance and interest cards and keeps them after changing breakpoint', () => {
    state.queries.performance.data = [{ assetId: 1 }];
    state.queries.interest.data = { accounts: [{ accountId: 1 }] };
    renderWithProviders(<DashboardPage />);
    expect(screen.getByText('PortfolioPerformanceCards')).toBeInTheDocument();
    expect(screen.getByText('EstimatedInterestCard')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Use phone' }));
    expect(screen.getByText('PortfolioPerformanceCards')).toBeInTheDocument();
  });

  it.each(['single-day', 'missing-opening', 'zero-opening', 'missing-closing'])(
    'does not calculate a misleading change for %s history',
    scenario => {
      sessionStorage.setItem(
        'dashboard_period',
        JSON.stringify({
          period: 'CUSTOM',
          range: {
            from: '2026-01-01',
            to: scenario === 'single-day' ? '2026-01-01' : '2026-01-31',
          },
        })
      );
      state.queries.history.data =
        scenario === 'missing-opening'
          ? [{ date: '2026-01-31', netWorth: 200 }]
          : scenario === 'missing-closing'
            ? [{ date: '2026-01-01', netWorth: 100 }]
            : [
                { date: '2026-01-01', netWorth: scenario === 'zero-opening' ? 0 : 100 },
                { date: '2026-01-31', netWorth: 200 },
              ];
      renderWithProviders(<DashboardPage />);
      expect(screen.getByLabelText('Period change')).toHaveTextContent('null');
    }
  );
});
