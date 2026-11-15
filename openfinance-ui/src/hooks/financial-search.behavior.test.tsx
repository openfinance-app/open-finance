import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useProperties, usePropertiesSearch } from '@/hooks/useRealEstate';
import { useAssetsSearch } from '@/hooks/useAssets';
import { useAccountsSearch } from '@/hooks/useAccounts';
import { useLiabilitiesPaged } from '@/hooks/useLiabilities';
import apiClient from '@/services/apiClient';
vi.mock('@/services/apiClient');
const common = {
  keyword: 'A & B.*',
  keywordRegex: true,
  currency: 'EUR',
  purchaseDateFrom: '2025-01-01',
  purchaseDateTo: '2025-12-31',
  valueMin: 0,
  valueMax: '10.000000000000000001',
  valueExact: '0',
  amountCurrencyMode: 'NATIVE' as const,
  page: 2,
  size: 10,
  sort: 'name,desc',
};
function Searches({ all }: { all: boolean }) {
  const properties = usePropertiesSearch(
    {
      ...common,
      propertyType: 'RESIDENTIAL',
      isActive: false,
      hasMortgage: false,
      priceMin: 0,
      priceMax: '10.000000000000000001',
      rentalIncomeMin: 0,
    },
    all
  );
  const assets = useAssetsSearch(
    {
      ...common,
      group: 'FINANCIAL',
      ownershipStatus: 'OWNED',
      type: 'STOCK',
      accountId: 7,
      symbol: 'A&B',
    },
    all
  );
  const accounts = useAccountsSearch(
    {
      keyword: common.keyword,
      keywordRegex: true,
      currency: 'EUR',
      type: 'SAVINGS',
      isActive: false,
      balanceMin: 0,
      balanceMax: '10.000000000000000001',
      balanceExact: '0',
      amountCurrencyMode: 'NATIVE',
      institution: 'A & B bank',
      lowBalance: true,
      page: 2,
      size: 10,
      sort: 'name,desc',
    },
    all
  );
  const loans = useLiabilitiesPaged(
    {
      search: common.keyword,
      searchRegex: true,
      balanceMin: 0,
      balanceMax: '10.000000000000000001',
      balanceExact: '0',
      amountCurrencyMode: 'NATIVE',
      page: 2,
      size: 10,
      sort: 'name,desc',
      type: 'MORTGAGE',
    },
    all
  );
  return (
    <output>
      {[properties, assets, accounts, loans].every(query => query.isSuccess)
        ? [properties, assets, accounts, loans]
            .map(query => query.data!.content.map(row => row.id).join(','))
            .join(';')
        : 'Loading'}
    </output>
  );
}
function PropertyList({ include }: { include: boolean }) {
  const list = useProperties({ propertyType: 'LAND', includeInactive: include });
  return <output>{list.isSuccess ? 'Loaded' : 'Loading'}</output>;
}
describe('financial search request contracts', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    vi.mocked(apiClient.get).mockImplementation(async url => {
      if (url.includes('/search?') || url.startsWith('/liabilities/paged?')) {
        const page = Number(new URL(url, 'http://test').searchParams.get('page'));
        return {
          data: {
            content: [{ id: page + 1 }],
            totalElements: 3,
            totalPages: 3,
            number: page,
            size: 1,
          },
        };
      }
      return { data: {} };
    });
  });
  it.each([false, true])(
    'retains exact zero, false and encoded filters across all-pages mode = %s',
    async all => {
      renderWithProviders(<Searches all={all} />);
      await waitFor(() => expect(screen.getByRole('status')).not.toHaveTextContent('Loading'));
      expect(screen.getByRole('status')).toHaveTextContent(
        all ? '1,2,3;1,2,3;1,2,3;1,2,3' : '3;3;3;3'
      );
      const paths = [
        '/real-estate/search',
        '/assets/search',
        '/accounts/search',
        '/liabilities/paged',
      ];
      const urls = vi.mocked(apiClient.get).mock.calls.map(([url]) => new URL(url, 'http://test'));
      for (const path of paths) {
        const requests = urls.filter(url => url.pathname === path);
        expect(requests.map(url => url.searchParams.get('page'))).toEqual(
          all ? ['0', '1', '2'] : ['2']
        );
        for (const url of requests) {
          const params = url.searchParams;
          expect(params.get('sort')).toBe('name,desc');
          expect(params.get('size')).toBe('10');
          expect(params.get(path === '/liabilities/paged' ? 'search' : 'keyword')).toBe('A & B.*');
          expect(params.get(path === '/liabilities/paged' ? 'searchRegex' : 'keywordRegex')).toBe(
            'true'
          );
          const prefix =
            path === '/accounts/search' || path === '/liabilities/paged' ? 'balance' : 'value';
          expect(params.get(`${prefix}Min`)).toBe('0');
          expect(params.get(`${prefix}Max`)).toBe('10.000000000000000001');
          expect(params.get(`${prefix}Exact`)).toBe('0');
          expect(params.get('amountCurrencyMode')).toBe('NATIVE');
          if (path === '/real-estate/search') {
            expect(params.get('isActive')).toBe('false');
            expect(params.get('hasMortgage')).toBe('false');
            expect(params.get('priceMin')).toBe('0');
            expect(params.get('priceMax')).toBe('10.000000000000000001');
            expect(params.get('rentalIncomeMin')).toBe('0');
            expect(params.get('propertyType')).toBe('RESIDENTIAL');
          } else if (path === '/assets/search') {
            expect(params.get('accountId')).toBe('7');
            expect(params.get('symbol')).toBe('A&B');
            expect(params.get('group')).toBe('FINANCIAL');
            expect(params.get('type')).toBe('STOCK');
          } else if (path === '/accounts/search') {
            expect(params.get('isActive')).toBe('false');
            expect(params.get('institution')).toBe('A & B bank');
            expect(params.get('lowBalance')).toBe('true');
          }
          if (['/real-estate/search', '/assets/search'].includes(path)) {
            expect(params.get('purchaseDateFrom')).toBe('2025-01-01');
            expect(params.get('purchaseDateTo')).toBe('2025-12-31');
            expect(params.get('currency')).toBe('EUR');
          }
        }
      }
    }
  );
  it.each([false, true])(
    'list explicitly retains includeInactive=%s instead of dropping false',
    async include => {
      renderWithProviders(<PropertyList include={include} />);
      expect(await screen.findByText('Loaded')).toBeVisible();
      const [url] = vi
        .mocked(apiClient.get)
        .mock.calls.find(([url]) => url.startsWith('/real-estate?'))!;
      expect(Object.fromEntries(new URL(url, 'http://test').searchParams)).toEqual({
        propertyType: 'LAND',
        includeInactive: String(include),
      });
    }
  );
});
