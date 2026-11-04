import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import FinancialMap from '@/components/dashboard/FinancialMap';
import i18n from '@/test/i18n-test';

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({
    data: [
      { id: 1, balance: 100, currency: 'EUR', institution: { country: 'FR' } },
      {
        id: 2,
        balance: 200,
        balanceInBaseCurrency: 150,
        currency: 'USD',
        institution: { country: 'US' },
      },
      { id: 3, balance: 999, currency: 'EUR', institution: null },
    ],
    isLoading: false,
  }),
}));
vi.mock('@/hooks/useAssets', () => ({
  useAssets: () => ({ data: [{ id: 1, accountId: 1, totalValue: 9999999 }], isLoading: false }),
}));
vi.mock('@/hooks/useRealEstate', () => ({
  useProperties: () => ({
    data: [
      {
        id: 1,
        latitude: 48.8566,
        longitude: 2.3522,
        currentValue: 1000,
        valueInBaseCurrency: 1250,
        address: 'Paris, France',
      },
      { id: 2, currentValue: 500, valueInBaseCurrency: 425, address: 'New York, United States' },
      { id: 3, currentValue: 25, address: 'Unknown location' },
    ],
    isLoading: false,
  }),
}));
vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: { country: 'FR', baseCurrency: 'EUR' } }),
}));
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
});

describe('geographic portfolio aggregation', () => {
  it('uses base-currency balances, excludes linked assets from double counting, and flags unmapped property values', () => {
    const { container } = renderWithProviders(<FinancialMap baseCurrency="EUR" />);
    fireEvent.click(screen.getByRole('button', { name: 'Toggle legend' }));
    expect(screen.getByText(/250\.00/)).toBeInTheDocument();
    expect(screen.getByText(/1,700\.00/)).toBeInTheDocument();
    expect(screen.queryByText(/9,999,999/)).not.toBeInTheDocument();
    expect(
      screen.getByText(i18n.t('financialMap.unmapped', { ns: 'dashboard', count: 1 }))
    ).toBeInTheDocument();
    const marker = container.querySelector('.rsm-marker > g');
    expect(marker).not.toBeNull();
    fireEvent.mouseEnter(marker!, { clientX: 10, clientY: 10 });
    expect(screen.getByText(/1,350\.00/)).toBeInTheDocument();
    fireEvent.mouseMove(marker!, { clientX: 20, clientY: 20 });
    fireEvent.mouseLeave(marker!);
  });
});
