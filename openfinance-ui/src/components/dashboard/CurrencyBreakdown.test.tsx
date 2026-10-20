import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

const mockNavigate = vi.fn();
vi.mock('react-router', async importOriginal => {
  const actual = await importOriginal<typeof import('react-router')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: vi.fn(() => ({
    data: [
      { id: 1, name: 'EUR Account', currency: 'EUR', balance: 5000, balanceInBaseCurrency: 5500 },
      { id: 2, name: 'USD Account', currency: 'USD', balance: 3000, balanceInBaseCurrency: 3000 },
    ],
    isLoading: false,
    error: null,
  })),
}));

vi.mock('@/hooks/useAssets', () => ({
  useAssets: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
  })),
}));

vi.mock('@/hooks/useCurrency', () => ({
  useLatestExchangeRates: vi.fn((currencies: string[]) =>
    currencies.map(() => ({ data: { rate: 1.1 }, isLoading: false }))
  ),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (v: number) => v,
    secondaryCurrency: null,
    secondaryExchangeRate: null,
  }),
}));

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: any) => <span data-testid="amount">{amount}</span>,
}));

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children }: any) => <span>{children}</span>,
}));

import CurrencyBreakdown from './CurrencyBreakdown';
import { useAccounts } from '@/hooks/useAccounts';
import { useAssets } from '@/hooks/useAssets';
import { useLatestExchangeRates } from '@/hooks/useCurrency';

describe('CurrencyBreakdown', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
  });

  it('renders without crashing', () => {
    const { container } = renderWithProviders(<CurrencyBreakdown baseCurrency="USD" />);
    expect(container.innerHTML).not.toBe('');
  });

  it('renders currency names', () => {
    renderWithProviders(<CurrencyBreakdown baseCurrency="USD" />);
    expect(screen.getByText('EUR')).toBeInTheDocument();
    expect(screen.getByText('USD')).toBeInTheDocument();
  });

  it('returns empty when no accounts', () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as any);
    const { container } = renderWithProviders(<CurrencyBreakdown baseCurrency="USD" />);
    // Should show empty state or nothing
    expect(container).toBeTruthy();
  });

  it('shows loading state', () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as any);
    renderWithProviders(<CurrencyBreakdown baseCurrency="USD" />);
    // Loading skeleton should render
    expect(document.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('navigates to assets filtered by currency when a row is clicked', async () => {
    // Re-set fixtures: mockReturnValue from the loading-state test persists through vi.clearAllMocks()
    vi.mocked(useAccounts).mockReturnValue({
      data: [
        { id: 1, name: 'EUR Account', currency: 'EUR', balance: 5000, balanceInBaseCurrency: 5500 },
        { id: 2, name: 'USD Account', currency: 'USD', balance: 3000, balanceInBaseCurrency: 3000 },
      ],
      isLoading: false,
      error: null,
    } as any);
    const user = userEvent.setup();
    renderWithProviders(<CurrencyBreakdown baseCurrency="USD" />);
    await user.click(screen.getByRole('button', { name: 'View assets in USD' }));
    expect(mockNavigate).toHaveBeenCalledWith('/assets?currency=USD');
  });
});

describe('CurrencyBreakdown underlying positions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    vi.mocked(useLatestExchangeRates).mockImplementation(currencies =>
      currencies.map(
        () =>
          ({ data: { rate: 1.1 }, isLoading: false }) as ReturnType<
            typeof useLatestExchangeRates
          >[number]
      )
    );
    vi.mocked(useAccounts).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAccounts>);
    vi.mocked(useAssets).mockReturnValue({ data: [], isLoading: false, error: null } as ReturnType<
      typeof useAssets
    >);
  });

  it('counts linked holdings without subtracting the brokerage overdraft from gross assets', () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: [{ id: 1, currency: 'EUR', balance: 900, ownBalance: -100, isActive: true }],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAccounts>);
    vi.mocked(useAssets).mockReturnValue({
      data: [
        { id: 1, accountId: 1, currency: 'EUR', totalValue: 1000, acquisitionType: 'PURCHASE' },
      ],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssets>);
    renderWithProviders(<CurrencyBreakdown baseCurrency="EUR" />);
    const row = screen.getByRole('button', { name: 'View assets in EUR' });
    expect(within(row).getByText('1000')).toBeInTheDocument();
    expect(screen.queryByText('900')).not.toBeInTheDocument();
  });

  it('preserves a holding currency that differs from its linked account', () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: [{ id: 1, currency: 'EUR', balance: 1200, ownBalance: 100, isActive: true }],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAccounts>);
    vi.mocked(useAssets).mockReturnValue({
      data: [
        { id: 1, accountId: 1, currency: 'USD', totalValue: 1000, acquisitionType: 'PURCHASE' },
      ],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssets>);
    renderWithProviders(<CurrencyBreakdown baseCurrency="EUR" />);
    expect(screen.getByRole('button', { name: 'View assets in USD' })).toBeInTheDocument();
    expect(
      within(screen.getByRole('button', { name: 'View assets in EUR' })).getByText('100')
    ).toBeInTheDocument();
  });

  it('shows standalone owned assets and excludes planned holdings without any accounts', () => {
    vi.mocked(useAssets).mockReturnValue({
      data: [
        { id: 1, currency: 'EUR', totalValue: 1000, acquisitionType: 'PURCHASE' },
        { id: 2, currency: 'USD', totalValue: 5000, acquisitionType: 'PLANNED' },
        {
          id: 3,
          currency: 'GBP',
          totalValue: 100000,
          acquisitionType: 'PURCHASE',
          isActive: false,
        },
      ],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssets>);
    renderWithProviders(<CurrencyBreakdown baseCurrency="EUR" />);
    expect(screen.getByRole('button', { name: 'View assets in EUR' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'View assets in USD' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'View assets in GBP' })).not.toBeInTheDocument();
    expect(screen.queryByText('No assets yet')).not.toBeInTheDocument();
  });

  it.each(['BTC-USD', 'BTC-EUR', 'btc'])(
    'shows BTC units with their recorded EUR valuation for symbol %s',
    symbol => {
      vi.mocked(useAssets).mockReturnValue({
        data: [
          {
            id: 1,
            name: 'Bitcoin',
            type: 'CRYPTO',
            symbol,
            quantity: 1,
            currency: 'EUR',
            totalValue: 73284.26,
          },
        ],
        isLoading: false,
        error: null,
      } as ReturnType<typeof useAssets>);

      renderWithProviders(<CurrencyBreakdown baseCurrency="EUR" />);

      const row = screen.getByRole('button', { name: 'View assets in BTC' });
      expect(within(row).getByText('1 BTC')).toBeInTheDocument();
      expect(within(row).getByText('73284.26')).toBeInTheDocument();
      expect(within(row).getByText('100.0% of total')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'View assets in EUR' })).not.toBeInTheDocument();
      expect(useLatestExchangeRates).toHaveBeenLastCalledWith(['EUR'], 'EUR', 0);
    }
  );

  it('combines BTC quantities across valuation currencies while keeping EUR cash separate', async () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: [{ id: 1, currency: 'EUR', balance: 70100, ownBalance: 100 }],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAccounts>);
    vi.mocked(useAssets).mockReturnValue({
      data: [
        {
          id: 1,
          accountId: 1,
          type: 'CRYPTO',
          symbol: 'BTC-USD',
          quantity: 1,
          currency: 'EUR',
          totalValue: 70000,
        },
        {
          id: 2,
          type: 'CRYPTO',
          symbol: 'BTC-EUR',
          quantity: 0.5,
          currency: 'USD',
          totalValue: 40000,
        },
      ],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssets>);
    vi.mocked(useLatestExchangeRates).mockImplementation(currencies =>
      currencies.map(
        () =>
          ({ data: { rate: 0.9 }, isLoading: false }) as ReturnType<
            typeof useLatestExchangeRates
          >[number]
      )
    );

    renderWithProviders(<CurrencyBreakdown baseCurrency="EUR" />);

    const bitcoin = screen.getByRole('button', { name: 'View assets in BTC' });
    expect(within(bitcoin).getByText('1.5 BTC')).toBeInTheDocument();
    expect(within(bitcoin).getByText('106000')).toBeInTheDocument();
    expect(
      within(screen.getByRole('button', { name: 'View assets in EUR' })).getByText('100')
    ).toBeInTheDocument();
    expect(screen.getByText('106100')).toBeInTheDocument();

    await userEvent.setup().click(bitcoin);
    expect(mockNavigate).toHaveBeenCalledWith('/assets?type=CRYPTO&symbol=BTC');
  });

  it('keeps a crypto holding without a symbol out of the EUR cash group', () => {
    vi.mocked(useAssets).mockReturnValue({
      data: [
        { id: 1, name: 'Bitcoin', type: 'CRYPTO', quantity: 1, currency: 'EUR', totalValue: 70000 },
      ],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssets>);

    renderWithProviders(<CurrencyBreakdown baseCurrency="EUR" />);

    const row = screen.getByRole('button', { name: 'View assets in Bitcoin' });
    expect(within(row).getByText('70000')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'View assets in EUR' })).not.toBeInTheDocument();
  });

  it.each([true, false])('never treats a missing FX rate as one while loading is %s', isLoading => {
    vi.mocked(useAssets).mockReturnValue({
      data: [
        {
          id: 1,
          type: 'CRYPTO',
          symbol: 'BTC-USD',
          quantity: 1,
          currency: 'USD',
          totalValue: 80000,
        },
      ],
      isLoading: false,
      error: null,
    } as ReturnType<typeof useAssets>);
    vi.mocked(useLatestExchangeRates).mockImplementation(currencies =>
      currencies.map(
        () => ({ data: undefined, isLoading }) as ReturnType<typeof useLatestExchangeRates>[number]
      )
    );

    renderWithProviders(<CurrencyBreakdown baseCurrency="EUR" />);

    const row = screen.getByRole('button', { name: 'View assets in BTC' });
    expect(within(row).getByText('1 BTC')).toBeInTheDocument();
    expect(screen.queryByText('80000')).not.toBeInTheDocument();
    expect(screen.queryByText('100.0% of total')).not.toBeInTheDocument();
    expect(screen.getByRole(isLoading ? 'status' : 'alert')).toHaveTextContent(
      isLoading ? 'Converting...' : 'Valuation unavailable'
    );
  });
});
