import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { Account } from '@/types/account';
import type { RealEstateProperty } from '@/types/realEstate';
import FinancialMap from '@/components/dashboard/FinancialMap';
import i18n from '@/test/i18n-test';
import { WORLD_TOPOLOGY } from '@/utils/countryGeo';
const state = vi.hoisted(() => ({
  accounts: [] as Partial<Account>[],
  properties: [] as Partial<RealEstateProperty>[],
  country: undefined as string | undefined,
  accountLoading: false,
  assetLoading: false,
  propertyLoading: false,
  accountError: null as unknown,
  assetError: null as unknown,
  propertyError: null as unknown,
}));
vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({
    data: state.accounts,
    isLoading: state.accountLoading,
    error: state.accountError,
  }),
}));
vi.mock('@/hooks/useAssets', () => ({
  useAssets: () => ({ data: [], isLoading: state.assetLoading, error: state.assetError }),
}));
vi.mock('@/hooks/useRealEstate', () => ({
  useProperties: () => ({
    data: state.properties,
    isLoading: state.propertyLoading,
    error: state.propertyError,
  }),
}));
vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: { country: state.country, baseCurrency: 'EUR' } }),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => undefined }),
}));
const institution = { id: 1, name: 'Bank', country: 'FR' };
const mount = () => renderWithProviders(<FinancialMap baseCurrency="EUR" />);
const legend = () => fireEvent.click(screen.getByRole('button', { name: 'Toggle legend' }));

describe('Geographic totals and unavailable conversions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    state.accounts = [{ id: 1, balance: 100, currency: 'EUR', institution }];
    state.properties = [];
    state.country = 'FR';
    state.accountLoading = false;
    state.assetLoading = false;
    state.propertyLoading = false;
    state.accountError = null;
    state.assetError = null;
    state.propertyError = null;
  });

  it.each(['accountLoading', 'assetLoading', 'propertyLoading'] as const)(
    'waits until %s is complete before displaying portfolio totals',
    loading => {
      state[loading] = true;
      mount();
      expect(screen.queryByRole('button', { name: 'Toggle legend' })).not.toBeInTheDocument();
    }
  );

  it.each(['accountError', 'assetError', 'propertyError'] as const)(
    'surfaces a failed %s request instead of a partial total',
    error => {
      state[error] = new Error('Portfolio unavailable');
      mount();
      expect(screen.getByText('Portfolio unavailable')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Toggle legend' })).not.toBeInTheDocument();
    }
  );

  it('uses a localized fallback for an unexpected loading error', () => {
    state.assetError = 'unexpected';
    mount();
    expect(screen.getByText(i18n.t('dashboard:financialMap.loadError'))).toBeInTheDocument();
  });

  it.each(['account', 'property'])(
    'does not label an unconverted foreign %s value as EUR',
    source => {
      if (source === 'account')
        state.accounts = [
          {
            id: 1,
            balance: 900,
            currency: 'USD',
            baseCurrency: 'EUR',
            isConverted: false,
            institution,
          },
        ];
      else
        state.properties = [
          {
            id: 2,
            currentValue: 900,
            currency: 'USD',
            baseCurrency: 'EUR',
            isConverted: false,
            address: 'Paris, France',
          },
        ];
      mount();
      expect(screen.getByRole('alert')).toHaveTextContent(/could not be converted/i);
      if (source === 'account')
        expect(screen.queryByRole('button', { name: 'Toggle legend' })).not.toBeInTheDocument();
      else {
        legend();
        expect(screen.getAllByText(/100\.00/).length).toBeGreaterThan(0);
        expect(screen.queryByText(/900\.00/)).not.toBeInTheDocument();
      }
    }
  );

  it('retains exact converted cents when summing institution balances and property values', () => {
    state.accounts = [
      {
        id: 1,
        balance: 9007199254740992,
        currency: 'USD',
        baseCurrency: 'EUR',
        balanceInBaseCurrency: 9007199254740992,
        isConverted: true,
        institution,
        exactValues: { balanceInBaseCurrency: '9007199254740992.01' },
      },
    ];
    state.properties = [
      {
        id: 2,
        currentValue: 1,
        currency: 'EUR',
        address: 'Paris, France',
        exactValues: { currentValue: '0.02' },
      },
    ];
    mount();
    legend();
    expect(screen.getByText(/9,007,199,254,740,992\.03/)).toBeInTheDocument();
  });

  it.each([undefined, 'ZZ'])(
    'shows an empty state with no positions or usable home country (%s)',
    country => {
      state.accounts = [];
      state.country = country;
      mount();
      expect(screen.getByText(i18n.t('dashboard:financialMap.empty'))).toBeInTheDocument();
    }
  );

  it('still shows the home location when no portfolio country is available', () => {
    state.accounts = [];
    mount();
    expect(screen.queryByText(i18n.t('dashboard:financialMap.empty'))).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Toggle legend' })).not.toBeInTheDocument();
  });

  it('treats a known zero foreign balance as zero without requiring a conversion', () => {
    state.accounts = [
      { id: 1, balance: 0, currency: 'USD', baseCurrency: 'EUR', isConverted: false, institution },
    ];
    mount();
    legend();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getAllByText(/0\.00/).length).toBeGreaterThan(0);
  });

  it('uses an address if coordinates do not resolve and keeps unmapped property value in the legend', () => {
    state.properties = [
      {
        id: 1,
        currentValue: 10,
        currency: 'EUR',
        address: 'Paris, France',
        latitude: 0,
        longitude: 0,
      },
      { id: 2, currentValue: 20, currency: 'EUR', address: 'Unmapped address' },
    ];
    const { container } = mount();
    legend();
    expect(screen.getByText(/130\.00/)).toBeInTheDocument();
    expect(
      screen.getByText(i18n.t('dashboard:financialMap.unmapped', { count: 1 }))
    ).toBeInTheDocument();
    const marker = container.querySelector('.rsm-marker > g')!;
    fireEvent.mouseEnter(marker, { clientX: 100, clientY: 90 });
    expect(screen.getByText(/110\.00/)).toBeInTheDocument();
    fireEvent.mouseMove(marker, { clientX: 120, clientY: 100 });
    fireEvent.mouseLeave(marker);
    expect(screen.queryByText(/110\.00/)).not.toBeInTheDocument();
  });

  it.each(['France', 'Kosovo', 'Somaliland'])(
    'shows the geography name and only known portfolio values when hovering %s',
    async name => {
      const { container } = mount();
      const topology = WORLD_TOPOLOGY as {
        objects: { countries: { geometries: { properties: { name: string } }[] } };
      };
      const index = topology.objects.countries.geometries.findIndex(
        country => country.properties.name === name
      );
      expect(index).toBeGreaterThanOrEqual(0);
      await waitFor(() =>
        expect(container.querySelectorAll('.rsm-geography').length).toBeGreaterThan(index)
      );
      const geography = container.querySelectorAll('.rsm-geography')[index];
      const map = geography.closest('.relative.flex-1') as HTMLElement;
      vi.spyOn(map, 'getBoundingClientRect').mockReturnValue(new DOMRect(100, 50, 800, 380));

      fireEvent.mouseEnter(geography, { clientX: 150, clientY: 80 });
      const tooltip = screen.getByText(name).closest('.absolute');
      expect(tooltip).toHaveStyle({ left: '62px', top: '42px' });
      if (name === 'France') {
        expect(tooltip).toHaveTextContent(/100\.00/);
        expect(tooltip).toHaveTextContent(i18n.t('dashboard:financialMap.you'));
      } else {
        expect(tooltip).not.toHaveTextContent(/100\.00/);
        expect(tooltip?.querySelector('[class*="fi-"]')).toBeNull();
      }

      fireEvent.mouseMove(geography, { clientX: 170, clientY: 100 });
      expect(tooltip).toHaveStyle({ left: '82px', top: '62px' });
      fireEvent.mouseLeave(geography);
      expect(screen.queryByText(name)).not.toBeInTheDocument();
    }
  );
});
