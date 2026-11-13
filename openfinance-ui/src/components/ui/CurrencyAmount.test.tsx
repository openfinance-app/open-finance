import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { CurrencyAmount } from '@/components/ui/CurrencyAmount';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';

const state = vi.hoisted(() => ({
  mode: 'base' as AmountDisplayMode,
  rate: 0.8 as number | undefined,
  latest: vi.fn(),
  dated: vi.fn(),
}));
vi.mock('@/context/AuthContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ baseCurrency: 'EUR' }),
}));
vi.mock('@/context/CurrencyDisplayContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({ displayMode: state.mode, secondaryCurrency: null }),
}));
vi.mock('@/hooks/useCurrency', () => ({
  useLatestExchangeRate: (...args: unknown[]) => {
    state.latest(...args);
    return { data: state.rate == null ? undefined : { rate: state.rate } };
  },
  useExchangeRate: (...args: unknown[]) => {
    state.dated(...args);
    return { data: state.rate == null ? undefined : { rate: state.rate } };
  },
}));

describe('read-only currency amounts', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    state.mode = 'base';
    state.rate = 0.8;
  });

  it.each(['base', 'native', 'both'] as const)('honors %s display mode', mode => {
    state.mode = mode;
    renderWithProviders(<CurrencyAmount amount={100} currency="USD" />);
    if (mode !== 'native') expect(screen.getByText('€80.00')).toBeVisible();
    else expect(screen.queryByText('€80.00')).not.toBeInTheDocument();
    if (mode !== 'base') expect(screen.getByText('$100.00')).toBeVisible();
    else expect(screen.queryByText('$100.00')).not.toBeInTheDocument();
  });

  it('uses the supplied position rate without requesting another quote', () => {
    renderWithProviders(
      <CurrencyAmount
        amount={100}
        currency="USD"
        baseCurrency="EUR"
        exchangeRate={0.9}
        isConverted
      />
    );
    expect(screen.getByText('€90.00')).toBeVisible();
    expect(state.latest).toHaveBeenCalledWith('USD', 'EUR', undefined, false);
    expect(state.dated).toHaveBeenCalledWith('USD', 'EUR', undefined, false);
  });

  it('retains the exact supplied amount instead of recalculating it from a rounded rate', () => {
    renderWithProviders(
      <CurrencyAmount
        amount={100}
        currency="USD"
        baseCurrency="EUR"
        convertedAmount={93.47}
        exchangeRate={0.93}
        isConverted
      />
    );
    expect(screen.getByText('€93.47')).toBeVisible();
  });

  it('uses a dated quote for a recorded sale and no latest quote', () => {
    renderWithProviders(<CurrencyAmount amount={100} currency="USD" conversionDate="2024-06-01" />);
    expect(screen.getByText('€80.00')).toBeVisible();
    expect(state.dated).toHaveBeenCalledWith('USD', 'EUR', '2024-06-01', true);
    expect(state.latest).toHaveBeenCalledWith('USD', 'EUR', undefined, false);
  });

  it('keeps the native unit while the quote is unavailable', () => {
    state.rate = undefined;
    renderWithProviders(<CurrencyAmount amount={100} currency="USD" />);
    expect(screen.getByText('$100.00')).toBeVisible();
    expect(screen.queryByText('€100.00')).not.toBeInTheDocument();
  });

  it('does not override a server-declared conversion failure with a cached quote', () => {
    renderWithProviders(
      <CurrencyAmount
        amount={100}
        currency="USD"
        baseCurrency="EUR"
        convertedAmount={100}
        isConverted={false}
      />
    );
    expect(screen.getByText('$100.00')).toBeVisible();
    expect(screen.queryByText('€80.00')).not.toBeInTheDocument();
    expect(state.latest).toHaveBeenCalledWith('USD', 'EUR', undefined, false);
  });

  it('does not convert a base-currency amount twice', () => {
    renderWithProviders(<CurrencyAmount amount={100} currency="EUR" />);
    expect(screen.getByText('€100.00')).toBeVisible();
    expect(state.latest).toHaveBeenCalledWith('EUR', 'EUR', undefined, false);
  });
});
