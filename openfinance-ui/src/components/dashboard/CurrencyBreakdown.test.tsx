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
  useLatestExchangeRates: (currencies: string[]) =>
    currencies.map(() => ({ data: { rate: 1.1 }, isLoading: false })),
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
    mockAuthentication();
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
});
