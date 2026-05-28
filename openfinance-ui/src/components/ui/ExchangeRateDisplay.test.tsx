import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { ExchangeRateDisplay } from './ExchangeRateDisplay';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

const defaultRate = {
  data: undefined,
  isLoading: false,
  isError: false,
  refetch: vi.fn(),
  isFetching: false,
};

// Mock hooks
vi.mock('@/hooks/useCurrency', () => ({
  useLatestExchangeRate: vi.fn().mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    isFetching: false,
  }),
}));

vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({
    format: (amount: number, currency: string) => `${amount.toFixed(2)} ${currency}`,
  }),
}));

vi.mock('@/utils/currency', () => ({
  formatExchangeRate: (rate: number) => rate.toFixed(4),
}));

describe('ExchangeRateDisplay', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mockAuthentication();
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as ReturnType<typeof vi.fn>).mockReturnValue(defaultRate);
  });

  it('renders "Same currency" when from and to are the same', () => {
    renderWithProviders(<ExchangeRateDisplay from="EUR" to="EUR" />);
    expect(screen.getByText('Same currency')).toBeInTheDocument();
  });

  it('renders loading state', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(<ExchangeRateDisplay from="USD" to="EUR" />);
    expect(screen.getByText('Loading exchange rate...')).toBeInTheDocument();
  });

  it('renders error state', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(<ExchangeRateDisplay from="USD" to="EUR" />);
    expect(screen.getByText('Unable to load exchange rate')).toBeInTheDocument();
  });

  it('renders error state with retry button when showRefresh is true', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(
      <ExchangeRateDisplay from="USD" to="EUR" showRefresh />
    );
    expect(screen.getByText('Retry')).toBeInTheDocument();
  });

  it('renders compact mode with rate', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: {
        rate: 0.85,
        inverseRate: 1.1765,
        rateDate: '2026-05-01',
        source: 'ECB',
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(
      <ExchangeRateDisplay from="USD" to="EUR" compact />
    );
    expect(screen.getByText(/1 USD = 0\.8500 EUR/)).toBeInTheDocument();
  });

  it('renders compact mode with amount conversion', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: {
        rate: 0.85,
        inverseRate: 1.1765,
        rateDate: '2026-05-01',
        source: 'ECB',
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(
      <ExchangeRateDisplay from="USD" to="EUR" amount={100} compact />
    );
    expect(screen.getByText(/100\.00 USD/)).toBeInTheDocument();
    expect(screen.getByText(/85\.00 EUR/)).toBeInTheDocument();
  });

  it('renders full mode with exchange rate info', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: {
        rate: 0.85,
        inverseRate: 1.1765,
        rateDate: '2026-05-01',
        source: 'ECB',
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(
      <ExchangeRateDisplay from="USD" to="EUR" />
    );
    expect(screen.getByText('Exchange Rate')).toBeInTheDocument();
    expect(screen.getByText('Source: ECB')).toBeInTheDocument();
  });

  it('renders full mode with amount conversion', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: {
        rate: 0.85,
        inverseRate: 1.1765,
        rateDate: '2026-05-01',
        source: 'ECB',
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(
      <ExchangeRateDisplay from="USD" to="EUR" amount={100} />
    );
    expect(screen.getByText('100.00 USD')).toBeInTheDocument();
    expect(screen.getByText('85.00 EUR')).toBeInTheDocument();
  });

  it('renders refresh button when showRefresh is true in full mode', async () => {
    const { useLatestExchangeRate } = await import('@/hooks/useCurrency');
    (useLatestExchangeRate as any).mockReturnValue({
      data: {
        rate: 0.85,
        inverseRate: 1.1765,
        rateDate: '2026-05-01',
        source: 'ECB',
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
      isFetching: false,
    });

    renderWithProviders(
      <ExchangeRateDisplay from="USD" to="EUR" showRefresh />
    );
    expect(screen.getByText('Refresh')).toBeInTheDocument();
  });
});
