import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import RealEstatePage from '@/pages/RealEstatePage';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useViewStateStore } from '@/stores/viewState';
import type { PropertySearchFilters, RealEstateProperty } from '@/types/realEstate';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  all: [] as RealEstateProperty[],
  matching: [] as RealEstateProperty[],
  visible: [] as RealEstateProperty[],
  totalPages: 1,
  totalElements: 1,
  query: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  property: undefined as RealEstateProperty | undefined,
  linkedAsset: undefined as { realEstateId: number } | undefined,
}));
vi.mock('@/hooks/useRealEstate', () => ({
  usePropertiesSearch: (filters: PropertySearchFilters, all?: boolean, enabled?: boolean) => {
    state.query(filters, all, enabled);
    return {
      data: {
        content: all ? (enabled === undefined ? state.all : state.matching) : state.visible,
        number: filters.page ?? 0,
        size: filters.size ?? 20,
        totalPages: state.totalPages,
        totalElements: state.totalElements,
      },
      isLoading: false,
      error: null,
    };
  },
  useProperty: () => ({ data: state.property }),
  useCreateProperty: () => ({ mutateAsync: state.create, isPending: false }),
  useUpdateProperty: () => ({ mutateAsync: state.update, isPending: false }),
}));
vi.mock('@/hooks/useAssets', () => ({ useAsset: () => ({ data: state.linkedAsset }) }));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: [] }) }));
vi.mock('@/hooks/useImportConfig', () => ({
  useImportConfig: () => ({ data: { skroogeJsonEnabled: true } }),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => null, secondaryCurrency: null }),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    value,
    onValueChange,
  }: {
    value?: string;
    onValueChange: (currency: string) => void;
  }) => (
    <select
      aria-label="Currency"
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value)}
    >
      <option value="">All currencies</option>
      <option>EUR</option>
      <option>USD</option>
    </select>
  ),
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: string | number }) => <span>{amount}</span>,
}));
vi.mock('@/components/ui/Pagination', () => ({
  Pagination: ({
    currentPage,
    onPageChange,
    onPageSizeChange,
  }: ComponentProps<typeof import('@/components/ui/Pagination').Pagination>) => (
    <nav aria-label="Property pages">
      <span>Page index {currentPage}</span>
      <button onClick={() => onPageChange(currentPage + 1)}>Next page</button>
      <button onClick={() => onPageSizeChange?.(50)}>50 per page</button>
    </nav>
  ),
}));
vi.mock('@/components/real-estate/PropertyCard', () => ({
  PropertyCard: ({
    property,
    onEdit,
  }: ComponentProps<typeof import('@/components/real-estate/PropertyCard').PropertyCard>) => (
    <article aria-label={property.name}>
      <button onClick={() => onEdit?.(property)}>Edit {property.name}</button>
    </article>
  ),
}));
vi.mock('@/components/real-estate/PropertyDetailView', () => ({
  PropertyDetailView: ({
    propertyId,
    onClose,
  }: ComponentProps<
    typeof import('@/components/real-estate/PropertyDetailView').PropertyDetailView
  >) => (
    <section aria-label="Property detail">
      Detail {propertyId}
      <button onClick={onClose}>Close detail</button>
    </section>
  ),
}));
vi.mock('@/components/real-estate/RealEstateForm', () => ({
  RealEstateForm: ({
    onSubmit,
    onCancel,
  }: ComponentProps<typeof import('@/components/real-estate/RealEstateForm').RealEstateForm>) => (
    <div>
      <button
        onClick={() =>
          onSubmit({
            name: 'Edited',
            propertyType: 'RESIDENTIAL',
            currentValue: '100',
            currency: 'EUR',
            purchasePrice: '100',
            purchaseDate: '2024-01-01',
            address: '',
          })
        }
      >
        Save property
      </button>
      <button onClick={onCancel}>Cancel edit</button>
    </div>
  ),
}));
vi.mock('@/components/real-estate/ConvertPropertyWizard', () => ({
  ConvertPropertyWizard: ({
    initialAssetId,
    onConverted,
    onClose,
  }: ComponentProps<
    typeof import('@/components/real-estate/ConvertPropertyWizard').ConvertPropertyWizard
  >) => (
    <section aria-label="Convert holding">
      Holding {initialAssetId ?? 'choose'}
      <button onClick={() => onConverted(8)}>Finish conversion</button>
      <button onClick={onClose}>Cancel conversion</button>
    </section>
  ),
}));
vi.mock('@/components/real-estate/BuyPropertyWizard', () => ({
  BuyPropertyWizard: () => <p>Purchase wizard</p>,
}));

function property(overrides: Partial<RealEstateProperty> = {}): RealEstateProperty {
  return {
    id: 1,
    userId: 1,
    name: 'Home',
    address: '',
    propertyType: 'RESIDENTIAL',
    currentValue: 100,
    purchasePrice: 50,
    purchaseDate: '2024-01-01',
    currency: 'EUR',
    allocatedDebt: 30,
    rentalIncome: 5,
    isActive: true,
    createdAt: '2024-01-01',
    updatedAt: '2024-01-01',
    ...overrides,
  };
}
function summary(label: string) {
  return screen.getByText(i18n.t(`realEstate:summary.${label}`)).parentElement!;
}
function filters() {
  fireEvent.click(
    screen.getByRole('button', { name: i18n.t('realEstate:filters.label'), exact: true })
  );
}

describe('Property page filters, summaries and operation errors', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    useViewStateStore.getState().clear();
    window.history.replaceState({}, '', '/real-estate');
    state.all = [
      property(),
      property({ id: 2, name: 'Shop', propertyType: 'COMMERCIAL', currentValue: 200 }),
    ];
    state.matching = [state.all[1]];
    state.visible = state.all;
    state.totalPages = 3;
    state.totalElements = 50;
    state.property = undefined;
    state.linkedAsset = undefined;
    state.create.mockResolvedValue(property());
    state.update.mockResolvedValue(property());
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('shows all matching property totals and clearing controls when property type is the only filter', () => {
    renderWithProviders(<RealEstatePage />);
    filters();
    fireEvent.change(screen.getByLabelText(i18n.t('realEstate:filters.propertyType')), {
      target: { value: 'COMMERCIAL' },
    });
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ propertyType: 'COMMERCIAL', page: 0 }),
      true,
      true
    );
    expect(summary('totalPortfolioValue')).toHaveTextContent('300Filtered: 200');
    expect(summary('totalEquity')).toHaveTextContent('240Filtered: 170');
    expect(summary('totalMortgageDebt')).toHaveTextContent('60Filtered: 30');
    expect(summary('monthlyRentalIncome')).toHaveTextContent('10/moFiltered: 5');
    fireEvent.click(screen.getByRole('button', { name: i18n.t('realEstate:filters.clear') }));
    expect(screen.getByLabelText(i18n.t('realEstate:filters.propertyType'))).toHaveValue('');
    expect(screen.queryAllByText('Filtered:', { exact: false })).toHaveLength(0);
  });

  it('describes empty property-type results as a filter mismatch', () => {
    state.visible = [];
    renderWithProviders(<RealEstatePage />);
    filters();
    fireEvent.change(screen.getByLabelText(i18n.t('realEstate:filters.propertyType')), {
      target: { value: 'LAND' },
    });
    expect(screen.getByText(i18n.t('realEstate:empty.noMatch'))).toBeInTheDocument();
  });

  it.each([
    ['filters.search', 'keyword', 'Garden'],
    ['filters.propertyType', 'propertyType', 'LAND'],
    ['filters.sortBy', 'sort', 'currentValue,desc'],
  ])('changing %s resets pagination and clearing removes the criterion', (label, key, value) => {
    renderWithProviders(<RealEstatePage />);
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    filters();
    fireEvent.change(screen.getByLabelText(i18n.t(`realEstate:${label}`)), { target: { value } });
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ [key]: value, page: 0 }),
      undefined,
      undefined
    );
    if (key !== 'sort') {
      fireEvent.change(screen.getByLabelText(i18n.t(`realEstate:${label}`)), {
        target: { value: '' },
      });
      expect(state.query).toHaveBeenCalledWith(
        expect.objectContaining({ [key]: undefined }),
        undefined,
        undefined
      );
    }
  });

  it('switches regex and currency filters without retaining stale criteria', () => {
    renderWithProviders(<RealEstatePage />);
    filters();
    const toggle = screen.getByRole('button', { name: i18n.t('common:regexSearch.toggleLabel') });
    fireEvent.click(toggle);
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ keywordRegex: true }),
      undefined,
      undefined
    );
    fireEvent.click(toggle);
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ keywordRegex: undefined }),
      undefined,
      undefined
    );
    fireEvent.change(screen.getByRole('combobox', { name: 'Currency' }), {
      target: { value: 'USD' },
    });
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ currency: 'USD' }),
      undefined,
      undefined
    );
  });

  it('resets the page when page size changes', () => {
    renderWithProviders(<RealEstatePage />);
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(screen.getByText('Page index 1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '50 per page' }));
    expect(state.query).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 50 }),
      undefined,
      undefined
    );
  });

  it.each([0, 1])('returns to a valid page after deletion leaves %s pages', remaining => {
    const { rerender } = renderWithProviders(<RealEstatePage />);
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    state.totalPages = remaining;
    state.visible = [];
    rerender(<RealEstatePage />);
    expect(state.query).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }), true, false);
    expect(useViewStateStore.getState().latest['/real-estate'].searchFilters).toEqual(
      expect.objectContaining({ page: 0 })
    );
  });

  it('converts debt and rent with the same exact exchange rate as property value', () => {
    state.all = [
      property({
        currency: 'USD',
        isConverted: true,
        valueInBaseCurrency: 90,
        currentValue: 100,
        allocatedDebt: 30,
        rentalIncome: 5,
        exchangeRate: 0.9,
        exactValues: {
          valueInBaseCurrency: '90.009',
          allocatedDebt: '30.01',
          rentalIncome: '5.01',
          exchangeRate: '0.9',
        },
      }),
    ];
    renderWithProviders(<RealEstatePage />);
    expect(summary('totalPortfolioValue')).toHaveTextContent('90.009');
    expect(summary('totalMortgageDebt')).toHaveTextContent('27.009');
    expect(summary('totalEquity')).toHaveTextContent('63');
    expect(summary('monthlyRentalIncome')).toHaveTextContent('4.509');
  });

  it.each([1, 2])(
    'excludes %s unconvertible foreign properties from every total and reports it',
    excluded => {
      state.all = [
        property(),
        ...Array.from({ length: excluded }, (_, index) =>
          property({ id: index + 2, currency: 'USD', isConverted: false, currentValue: 900 })
        ),
      ];
      renderWithProviders(<RealEstatePage />);
      expect(summary('totalPortfolioValue')).toHaveTextContent('100');
      expect(summary('totalMortgageDebt')).toHaveTextContent('30');
      expect(summary('totalEquity')).toHaveTextContent('70');
      expect(summary('monthlyRentalIncome')).toHaveTextContent('5');
      expect(
        screen.getByText(
          new RegExp(i18n.t('realEstate:conversionWarning.property', { count: excluded }))
        )
      ).toHaveTextContent(
        i18n.t('realEstate:conversionWarning.message', {
          baseCurrency: 'EUR',
          verb: i18n.t(
            `realEstate:conversionWarning.${excluded === 1 ? 'verbSingular' : 'verbPlural'}`
          ),
        })
      );
    }
  );

  it('excludes planned purchases from both current wealth and rent', () => {
    state.all = [
      property({ acquisitionType: 'PLANNED', currentValue: 1000000, rentalIncome: 5000 }),
    ];
    renderWithProviders(<RealEstatePage />);
    for (const label of [
      'totalPortfolioValue',
      'totalEquity',
      'totalMortgageDebt',
      'monthlyRentalIncome',
    ]) {
      expect(within(summary(label)).getByText('0')).toBeInTheDocument();
    }
  });

  it.each(['7', '-1', '0', 'not-a-number', '1.5', '9007199254740992'])(
    'validates a conversion deep-link asset %s and consumes only that parameter',
    id => {
      window.history.replaceState({}, '', `/real-estate?convert=${id}&keep=yes`);
      renderWithProviders(<RealEstatePage />);
      expect(screen.getByRole('region', { name: 'Convert holding' })).toHaveTextContent(
        id === '7' ? 'Holding 7' : 'Holding choose'
      );
      expect(window.location.search).toBe('?keep=yes');
      fireEvent.click(screen.getByRole('button', { name: 'Finish conversion' }));
      expect(screen.getByRole('region', { name: 'Property detail' })).toHaveTextContent('Detail 8');
      expect(screen.queryByRole('region', { name: 'Convert holding' })).not.toBeInTheDocument();
    }
  );

  it('waits for the linked property before consuming a purchase deep link', () => {
    window.history.replaceState({}, '', '/real-estate?highlight=9&purchase=1');
    const { rerender } = renderWithProviders(<RealEstatePage />);
    expect(window.location.search).toContain('purchase=1');
    state.property = property({ id: 8 });
    rerender(<RealEstatePage />);
    expect(window.location.search).toContain('purchase=1');
    state.property = property({ id: 9, acquisitionType: 'PURCHASE' });
    rerender(<RealEstatePage />);
    expect(screen.getByRole('region', { name: 'Property detail' })).toHaveTextContent('Detail 9');
    expect(screen.queryByText('Purchase wizard')).not.toBeInTheDocument();
    expect(window.location.search).toBe('');
  });

  it.each([
    { isAxiosError: true, response: { data: { message: 'Property was sold elsewhere' } } },
    { isAxiosError: true, response: { data: {} } },
    { isAxiosError: true, response: { status: 500 } },
    { isAxiosError: true },
    new Error('offline'),
  ])('keeps a failed edit open with an actionable error', async error => {
    state.update.mockRejectedValue(error);
    renderWithProviders(<RealEstatePage />);
    fireEvent.click(screen.getByRole('button', { name: 'Edit Home' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save property' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'response' in error && error.response?.data && 'message' in error.response.data
        ? error.response.data.message
        : i18n.t('realEstate:saveError')
    );
    expect(screen.getByRole('button', { name: 'Save property' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel edit' }));
    fireEvent.click(screen.getByRole('button', { name: 'Edit Home' }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });
});
