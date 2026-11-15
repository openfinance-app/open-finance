import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import BalanceVariationCard from '@/components/dashboard/BalanceVariationCard';
import NetWorthAllocationChart from '@/components/dashboard/NetWorthAllocationChart';
import InstitutionBreakdown from '@/components/dashboard/InstitutionBreakdown';
import type {
  IYearlyBalanceResponse,
  IYearlyDataPoint,
  INetWorthAllocation,
} from '@/types/dashboard';
import type { Account } from '@/types/account';
import type { Asset } from '@/types/asset';
import type { Liability } from '@/types/liability';

type AccountRow = Pick<Account, 'id' | 'currency' | 'ownBalance'> & Partial<Account>;
type AssetRow = Pick<Asset, 'id' | 'currency' | 'totalValue'> & Partial<Asset>;
type LiabilityRow = Pick<Liability, 'id' | 'currency' | 'currentBalance'> & Partial<Liability>;
const state = vi.hoisted(() => ({
  yearly: undefined as IYearlyBalanceResponse | undefined,
  accounts: { data: [] as AccountRow[] | undefined, isLoading: false, error: null as Error | null },
  assets: { data: [] as AssetRow[] | undefined, isLoading: false, error: null as Error | null },
  liabilities: {
    data: [] as LiabilityRow[] | undefined,
    isLoading: false,
    error: null as Error | null,
  },
  tooltip: 'rows' as 'rows' | 'inactive' | 'missing' | 'empty',
  width: 100,
  height: 60,
  visible: true,
  navigate: vi.fn(),
}));
vi.mock('@/hooks/useDashboard', () => ({
  useYearlyBalance: () => ({ data: state.yearly, isLoading: false, error: null }),
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => state.accounts }));
vi.mock('@/hooks/useAssets', () => ({ useAssets: () => state.assets }));
vi.mock('@/hooks/useLiabilities', () => ({ useLiabilities: () => state.liabilities }));
vi.mock('@/context/VisibilityContext', async original => ({
  ...(await original<typeof import('@/context/VisibilityContext')>()),
  useVisibility: () => ({ isAmountsVisible: state.visible }),
}));
vi.mock('react-router', async original => ({
  ...(await original<typeof import('react-router')>()),
  useNavigate: () => state.navigate,
}));
vi.mock('recharts', async () => {
  const React = await import('react');
  const Data = React.createContext<Record<string, unknown>[]>([]);
  const Frame = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
  const Chart = ({ data, children }: { data: Record<string, unknown>[]; children?: ReactNode }) => (
    <Data.Provider value={data}>{children}</Data.Provider>
  );
  return {
    ResponsiveContainer: Frame,
    BarChart: Chart,
    LineChart: Chart,
    Bar: Frame,
    CartesianGrid: () => null,
    Line: () => null,
    Cell: () => null,
    XAxis: ({ tickFormatter }: { tickFormatter?: (n: number) => string }) => (
      <output aria-label="Year axis">{tickFormatter?.(2024)}</output>
    ),
    YAxis: ({ tickFormatter }: { tickFormatter: (n: number) => string }) => (
      <output aria-label="Value axis">{tickFormatter(1200)}</output>
    ),
    Treemap: ({
      data,
      children,
      content,
    }: {
      data: Record<string, unknown>[];
      children: ReactNode;
      content: ReactElement<Record<string, unknown>>;
    }) => (
      <Data.Provider value={data}>
        <svg aria-label="Allocation rectangles">
          {React.cloneElement(content, { depth: 0, name: 'root' })}
          {data.map((point, index) =>
            React.cloneElement(content, {
              ...point,
              key: index,
              depth: 1,
              index,
              x: 0,
              y: 0,
              width: state.width,
              height: state.height,
            })
          )}
        </svg>
        {children}
      </Data.Provider>
    ),
    Tooltip: ({ content }: { content: ReactElement<Record<string, unknown>> }) => {
      const points = React.useContext(Data);
      return (
        <section aria-label="Chart details">
          {points.map((point, index) =>
            React.cloneElement(content, {
              key: index,
              active: state.tooltip !== 'inactive',
              payload:
                state.tooltip === 'missing'
                  ? undefined
                  : state.tooltip === 'empty'
                    ? []
                    : [{ payload: point }],
            })
          )}
        </section>
      );
    },
  };
});
const point = (
  variationPercentage: number | null = null
): IYearlyDataPoint & { exactValues: Record<string, string> } => ({
  year: 2024,
  amount: 9007199254740994,
  variationPercentage,
  exactValues: { amount: '9007199254740993.25' },
});
const allocation = (overrides: Partial<INetWorthAllocation> = {}): INetWorthAllocation => ({
  category: 'Cash',
  value: 100,
  percentage: 50,
  itemCount: 1,
  isLiability: false,
  currency: 'EUR',
  ...overrides,
});
beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.visible = true;
  state.tooltip = 'rows';
  state.width = 100;
  state.height = 60;
  state.yearly = {
    currency: 'EUR',
    years: [2024],
    netWorth: [point()],
    accounts: [{ id: 1, name: 'Daily', data: [point()] }],
    institutions: [{ id: 9, name: 'Bank', data: [point()] }],
  };
  state.accounts = { data: [], isLoading: false, error: null };
  state.assets = { data: [], isLoading: false, error: null };
  state.liabilities = { data: [], isLoading: false, error: null };
});

describe('Balance variation values and chart callbacks', () => {
  it.each(['netWorth', 'account:1', 'institution:9'])(
    'preserves server decimal precision for %s',
    scope => {
      renderWithProviders(<BalanceVariationCard />);
      fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: scope } });
      expect(screen.getAllByText('€9,007,199,254,740,993.25')).toHaveLength(2);
    }
  );
  it.each([null, -12.5, 0, 12.5])('tooltip shows the correct variation sign for %s', variation => {
    state.yearly!.netWorth = [{ year: 2024, amount: 200, variationPercentage: variation }];
    renderWithProviders(<BalanceVariationCard />);
    const tooltip = within(screen.getByRole('region', { name: 'Chart details' }));
    expect(tooltip.getByText('€200.00')).toBeInTheDocument();
    if (variation === null) expect(tooltip.queryByText(/%/)).not.toBeInTheDocument();
    else
      expect(
        tooltip.getByText(`${variation >= 0 ? '+' : ''}${variation.toFixed(2)}%`)
      ).toBeInTheDocument();
  });
  it.each(['inactive', 'missing', 'empty'] as const)(
    'does not show a tooltip for %s chart state',
    tooltip => {
      state.tooltip = tooltip;
      renderWithProviders(<BalanceVariationCard />);
      expect(screen.getByRole('region', { name: 'Chart details' })).toBeEmptyDOMElement();
    }
  );
  it.each(['bar', 'line'])('privacy hides monetary axis labels in the %s chart', chart => {
    state.visible = false;
    renderWithProviders(<BalanceVariationCard />);
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: chart } });
    expect(screen.getByLabelText('Value axis')).toHaveTextContent('••••');
    expect(screen.getByLabelText('Year axis')).toHaveTextContent('2024');
  });
  it.each([true, false])(
    'a refreshed response may remove a selected account: all removed=%s',
    removeAll => {
      const view = renderWithProviders(<BalanceVariationCard />);
      fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'account:1' } });
      state.yearly = {
        ...state.yearly!,
        accounts: removeAll ? [] : [{ id: 2, name: 'Replacement', data: [point()] }],
      };
      view.rerender(<BalanceVariationCard />);
      expect(screen.queryByText(/9,007/)).not.toBeInTheDocument();
      expect(screen.getByRole('region', { name: 'Chart details' })).toBeEmptyDOMElement();
    }
  );
});

describe('Net worth allocation tooltips', () => {
  it.each([true, false])('keeps liability signs and exact values: liability=%s', isLiability => {
    const data = {
      ...allocation({
        isLiability,
        currency: 'USD',
        value: isLiability ? -9007199254740994 : 9007199254740994,
      }),
      exactValues: { value: `${isLiability ? '-' : ''}9007199254740993.25` },
    };
    renderWithProviders(<NetWorthAllocationChart allocations={[data]} currency="EUR" />);
    const tooltip = within(screen.getByRole('region', { name: 'Chart details' }));
    expect(
      tooltip.getByText(`${isLiability ? '-' : ''}$9,007,199,254,740,993.25`)
    ).toBeInTheDocument();
    expect(tooltip.getByText(isLiability ? 'Liability' : 'Asset')).toBeInTheDocument();
    expect(tooltip.getByText('50.0%')).toBeInTheDocument();
  });
  it.each(['inactive', 'missing', 'empty'] as const)('hides %s tooltips', tooltip => {
    state.tooltip = tooltip;
    renderWithProviders(<NetWorthAllocationChart allocations={[allocation()]} currency="EUR" />);
    expect(screen.getByRole('region', { name: 'Chart details' })).toBeEmptyDOMElement();
  });
  it.each([
    [50, 60],
    [100, 30],
    [100, 60],
  ])('only labels rectangles with sufficient space: %sx%s', (width, height) => {
    state.width = width;
    state.height = height;
    renderWithProviders(
      <NetWorthAllocationChart
        allocations={[allocation({ category: 'Custom holding' })]}
        currency="EUR"
      />
    );
    const chart = within(screen.getByLabelText('Allocation rectangles'));
    if (width > 50 && height > 30) expect(chart.getByText('Custom holding')).toBeInTheDocument();
    else expect(chart.queryByText('Custom holding')).not.toBeInTheDocument();
  });
});

describe('Institution totals require complete conversion data', () => {
  it.each(['accounts', 'assets', 'liabilities'] as const)(
    'shows an error when %s cannot be loaded',
    resource => {
      state[resource].error = new Error('unavailable');
      renderWithProviders(<InstitutionBreakdown />);
      expect(screen.getByRole('alert')).toHaveTextContent(/load/i);
      expect(screen.queryByText('Total (base currency)')).not.toBeInTheDocument();
    }
  );
  it('missing query data is an empty state', () => {
    state.accounts.data = undefined;
    state.assets.data = undefined;
    state.liabilities.data = undefined;
    renderWithProviders(<InstitutionBreakdown />);
    expect(screen.queryByText('Total (base currency)')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
  it.each([false, true])('a missing or unconfirmed FX rate is flagged: supplied=%s', supplied => {
    state.accounts.data = [
      {
        id: 1,
        currency: 'USD',
        ownBalance: 200,
        exchangeRate: supplied ? 0.5 : undefined,
        isConverted: false,
      },
    ];
    renderWithProviders(<InstitutionBreakdown />);
    expect(screen.getByRole('alert')).toHaveTextContent(/could not be converted/i);
  });
  it('zero foreign balances do not require an exchange rate', () => {
    state.accounts.data = [{ id: 1, currency: 'USD', ownBalance: 0 }];
    renderWithProviders(<InstitutionBreakdown />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getAllByText('€0.00')).toHaveLength(2);
  });
  it('an asset with unavailable conversion cannot produce a misleading empty-state message', () => {
    state.assets.data = [{ id: 4, currency: 'USD', totalValue: 200, isConverted: false }];
    renderWithProviders(<InstitutionBreakdown />);
    expect(screen.getByRole('alert')).toHaveTextContent(/could not be converted/i);
    expect(screen.queryByText('Total (base currency)')).not.toBeInTheDocument();
  });
  it('adds only cash and owned holdings, and avoids double-counting account-backed debt', () => {
    const institution = { id: 9, name: 'Bank & Trust', logo: '/logos/bank.svg' };
    state.accounts.data = [
      {
        id: 1,
        currency: 'USD',
        ownBalance: 9007199254740994,
        exchangeRate: 0.5,
        isConverted: true,
        exactValues: { ownBalance: '9007199254740993.25', exchangeRate: '0.5' },
        institution,
      },
      { id: 2, currency: 'EUR', ownBalance: 999, isActive: false },
    ];
    state.assets.data = [
      {
        id: 4,
        accountId: 1,
        currency: 'USD',
        totalValue: 100,
        valueInBaseCurrency: 50,
        isConverted: true,
      },
      { id: 5, currency: 'EUR', totalValue: 900, isActive: false },
      { id: 6, currency: 'EUR', totalValue: 900, acquisitionType: 'PLANNED' },
    ];
    state.liabilities.data = [
      {
        id: 1,
        currency: 'USD',
        currentBalance: 100,
        balanceInBaseCurrency: 50,
        isConverted: true,
        institution,
      },
      { id: 2, currency: 'EUR', currentBalance: 999, representedByAccountId: 1 },
    ];
    renderWithProviders(<InstitutionBreakdown />);
    const bank = screen.getByRole('button', { name: 'View accounts at Bank & Trust' });
    expect(within(bank).getByText('€4,503,599,627,370,496.63')).toBeInTheDocument();
    expect(bank.querySelector('img')).toHaveAttribute('src', '/logos/bank.svg');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    fireEvent.click(bank);
    expect(state.navigate).toHaveBeenCalledWith('/accounts?institution=Bank%20%26%20Trust');
  });
});
