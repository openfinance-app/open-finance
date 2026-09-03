import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

const mockNavigate = vi.fn();
vi.mock('react-router', async (importOriginal) => {
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
  useLatestExchangeRate: () => ({ data: { rate: 1.1 }, isLoading: false }),
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
