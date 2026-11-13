import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import i18n from '@/test/i18n-test';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { usePropertiesSearch } from '@/hooks/useRealEstate';

const importConfig = vi.hoisted(() => ({ data: { skroogeJsonEnabled: true }, isError: false }));
vi.mock('@/hooks/useImportConfig', () => ({ useImportConfig: () => importConfig }));

vi.mock('@/hooks/useDocumentTitle', () => ({ useDocumentTitle: vi.fn() }));
vi.mock('@/components/real-estate/BuyPropertyWizard', () => ({
  BuyPropertyWizard: ({
    plan,
    onClose,
    onPurchased,
  }: ComponentProps<
    typeof import('@/components/real-estate/BuyPropertyWizard').BuyPropertyWizard
  >) => (
    <div>
      <p>Purchase plan {plan?.name ?? 'new'}</p>
      <button
        onClick={() => {
          onPurchased?.(9);
          onClose();
        }}
      >
        Finish purchase
      </button>
      <button onClick={onClose}>Cancel purchase</button>
    </div>
  ),
}));

let mockData: any = undefined;
let mockIsLoading = false;
let mockError: Error | null = null;

let mockCreateMutateAsync = vi.fn();
let mockUpdateMutateAsync = vi.fn();

vi.mock('@/hooks/useRealEstate', () => ({
  useProperty: (id: number) => ({
    data: mockData?.content?.find((property: { id: number }) => property.id === id),
  }),
  usePropertiesSearch: vi.fn(() => ({
    data: mockData,
    isLoading: mockIsLoading,
    error: mockError,
  })),
  useCreateProperty: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateProperty: () => ({ mutateAsync: mockUpdateMutateAsync, isPending: false }),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: vi.fn(() => null),
    secondaryCurrency: null,
    secondaryExchangeRate: null,
  }),
}));
vi.mock('@/components/real-estate/PropertyCard', () => ({
  PropertyCard: ({ property, onEdit, onView }: any) => (
    <div data-testid="property-card">
      {property.name}
      <button onClick={() => onEdit(property)}>Edit</button>
      <button onClick={() => onView(property.id)}>View</button>
    </div>
  ),
}));
vi.mock('@/components/real-estate/RealEstateForm', () => ({
  RealEstateForm: ({ property, onSubmit, onCancel }: any) => (
    <div data-testid="real-estate-form">
      {property && <span data-testid="editing-name">{property.name}</span>}
      <button
        onClick={() =>
          onSubmit({
            name: 'New Prop',
            propertyType: 'RESIDENTIAL',
            currentValue: 100000,
            currency: 'USD',
          })
        }
      >
        Submit
      </button>
      <button onClick={onCancel}>Cancel</button>
    </div>
  ),
}));
vi.mock('@/components/real-estate/PropertyDetailView', () => ({
  PropertyDetailView: ({ propertyId, onClose }: any) => (
    <div data-testid="detail-view">
      <span>Detail {propertyId}</span>
      <button onClick={onClose}>Close Detail</button>
    </div>
  ),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: () => <select data-testid="currency-selector" />,
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: number }) => <span>{amount}</span>,
}));

import RealEstatePage from './RealEstatePage';

describe('RealEstatePage', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/real-estate');
    vi.clearAllMocks();
    mockAuthentication();
    importConfig.data.skroogeJsonEnabled = true;
    mockData = undefined;
    mockIsLoading = false;
    mockError = null;
    mockCreateMutateAsync = vi.fn();
    mockUpdateMutateAsync = vi.fn();
    Element.prototype.scrollIntoView = vi.fn();
  });
  afterEach(() => window.history.replaceState({}, '', '/'));

  it('shows the property conversion action when Skrooge import is enabled', () => {
    renderWithProviders(<RealEstatePage />);
    expect(
      screen.getByRole('button', { name: i18n.t('realEstate:conversion.action') })
    ).toBeInTheDocument();
  });

  it('hides conversion and ignores its deep links when disabled', async () => {
    importConfig.data.skroogeJsonEnabled = false;
    window.history.replaceState({}, '', '/real-estate?convert=8');
    renderWithProviders(<RealEstatePage />);
    expect(
      screen.queryByRole('button', { name: i18n.t('realEstate:conversion.action') })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('dialog', { name: i18n.t('realEstate:conversion.title') })
    ).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: i18n.t('realEstate:addProperty') }));
    expect(await screen.findByText(i18n.t('realEstate:createChoice.intro'))).toBeInTheDocument();
    expect(
      screen.queryByRole('dialog', { name: i18n.t('realEstate:conversion.title') })
    ).not.toBeInTheDocument();
  });

  it.each(['highlight=9', 'asset=4'])(
    'opens a property from a %s deep link and removes consumed parameters',
    async query => {
      mockData = {
        content: [
          {
            id: 9,
            assetId: 4,
            name: 'Linked home',
            propertyType: 'RESIDENTIAL',
            currentValue: 100000,
            purchasePrice: 80000,
            currency: 'EUR',
            isActive: true,
          },
        ],
        totalPages: 1,
        totalElements: 1,
        number: 0,
      };
      window.history.replaceState({}, '', `/real-estate?${query}`);
      renderWithProviders(<RealEstatePage />);
      expect(await screen.findByText('Detail 9')).toBeInTheDocument();
      await waitFor(() => expect(window.location.search).toBe(''));
    }
  );

  it('opens a planned purchase from a deep link and returns to the purchased property', async () => {
    mockData = {
      content: [
        {
          id: 9,
          assetId: 4,
          name: 'Planned home',
          acquisitionType: 'PLANNED',
          propertyType: 'RESIDENTIAL',
          currentValue: 0,
          purchasePrice: 80000,
          currency: 'EUR',
          isActive: true,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    };
    window.history.replaceState({}, '', '/real-estate?highlight=9&purchase=1');
    renderWithProviders(<RealEstatePage />);
    expect(await screen.findByText('Purchase plan Planned home')).toBeInTheDocument();
    expect(screen.queryByText('Detail 9')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Finish purchase' }));
    expect(screen.getByText('Detail 9')).toBeInTheDocument();
    expect(screen.queryByText('Purchase plan Planned home')).not.toBeInTheDocument();
  });

  it('opens creation from a deep link, changes acquisition choices, and cancels the buy wizard', async () => {
    window.history.replaceState({}, '', '/real-estate?create=1');
    const user = userEvent.setup();
    renderWithProviders(<RealEstatePage />);
    const tr = (key: string) => i18n.t(`realEstate:createChoice.${key}`);
    for (const choice of ['gift', 'planned']) {
      await user.click(screen.getByRole('button', { name: new RegExp(tr(choice)) }));
      expect(screen.getByRole('button', { name: 'Submit' })).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: tr('back') }));
    }
    await user.click(screen.getByRole('button', { name: new RegExp(tr('buy')) }));
    expect(screen.getByText('Purchase plan new')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cancel purchase' }));
    expect(screen.queryByText('Purchase plan new')).not.toBeInTheDocument();
    expect(window.location.search).toBe('');
  });

  it('renders the page title', () => {
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByText('Real Estate Portfolio')).toBeInTheDocument();
  });

  it('shows empty state when no properties', () => {
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByText(/no propert/i)).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Server error');
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByText(/error|failed/i)).toBeInTheDocument();
  });

  it('renders property cards with data', () => {
    mockData = {
      content: [
        {
          id: 1,
          name: 'Beach House',
          propertyType: 'RESIDENTIAL',
          currentValue: 500000,
          currency: 'USD',
          isActive: true,
          isConverted: false,
          purchasePrice: 400000,
          allocatedDebt: 200000,
          mortgageBalance: 200000,
          monthlyRentalIncome: 0,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByText('Beach House')).toBeInTheDocument();
  });

  it('shows add property button', () => {
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByRole('button', { name: /add property/i })).toBeInTheDocument();
  });

  it('opens form on add property click', async () => {
    const user = userEvent.setup();
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /add property/i }));
    await user.click(screen.getByRole('button', { name: /record a property I already own/i }));
    expect(screen.getByTestId('real-estate-form')).toBeInTheDocument();
  });

  it('shows summary cards with totals', () => {
    mockData = {
      content: [
        {
          id: 1,
          name: 'Apt',
          propertyType: 'RESIDENTIAL',
          currentValue: 300000,
          currency: 'USD',
          isActive: true,
          isConverted: false,
          purchasePrice: 250000,
          allocatedDebt: 100000,
          mortgageBalance: 100000,
          monthlyRentalIncome: 2000,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    // Summary section should show property data
    expect(screen.getByText('Apt')).toBeInTheDocument();
  });

  it('shows filter toggle button', () => {
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByRole('button', { name: /filter/i })).toBeInTheDocument();
  });

  it('can find inactive properties for reactivation and reset the active filter', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /filter/i }));
    await user.selectOptions(screen.getByLabelText('Status'), 'inactive');
    expect(usePropertiesSearch).toHaveBeenCalledWith(
      expect.objectContaining({ isActive: false, page: 0 })
    );
    await user.selectOptions(screen.getByLabelText('Status'), 'all');
    expect(usePropertiesSearch).toHaveBeenCalledWith(
      expect.objectContaining({ isActive: undefined, page: 0 })
    );
    await user.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.getByLabelText('Status')).toHaveValue('active');
  });

  it('opens edit form when clicking edit on property card', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [
        {
          id: 1,
          name: 'Beach House',
          propertyType: 'RESIDENTIAL',
          currentValue: 500000,
          currency: 'USD',
          isActive: true,
          isConverted: false,
          purchasePrice: 400000,
          allocatedDebt: 200000,
          mortgageBalance: 200000,
          monthlyRentalIncome: 0,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    expect(screen.getByTestId('real-estate-form')).toBeInTheDocument();
    expect(screen.getByTestId('editing-name')).toHaveTextContent('Beach House');
  });

  it('opens detail view when clicking view on property card', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [
        {
          id: 1,
          name: 'Beach House',
          propertyType: 'RESIDENTIAL',
          currentValue: 500000,
          currency: 'USD',
          isActive: true,
          isConverted: false,
          purchasePrice: 400000,
          allocatedDebt: 200000,
          mortgageBalance: 200000,
          monthlyRentalIncome: 0,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /^view$/i }));
    expect(screen.getByTestId('detail-view')).toBeInTheDocument();
    expect(screen.getByText('Detail 1')).toBeInTheDocument();
  });

  it('closes detail view', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [
        {
          id: 1,
          name: 'Beach House',
          propertyType: 'RESIDENTIAL',
          currentValue: 500000,
          currency: 'USD',
          isActive: true,
          isConverted: false,
          purchasePrice: 400000,
          allocatedDebt: 200000,
          mortgageBalance: 200000,
          monthlyRentalIncome: 0,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /^view$/i }));
    await user.click(screen.getByRole('button', { name: /close detail/i }));
    expect(screen.queryByTestId('detail-view')).not.toBeInTheDocument();
  });

  it('submits create form', async () => {
    const user = userEvent.setup();
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    mockCreateMutateAsync.mockResolvedValue({});
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /add property/i }));
    await user.click(screen.getByRole('button', { name: /record a property I already own/i }));
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });

  it('submits update form for existing property', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [
        {
          id: 1,
          name: 'Beach House',
          propertyType: 'RESIDENTIAL',
          currentValue: 500000,
          currency: 'USD',
          isActive: true,
          isConverted: false,
          purchasePrice: 400000,
          allocatedDebt: 200000,
          mortgageBalance: 200000,
          monthlyRentalIncome: 0,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    mockUpdateMutateAsync.mockResolvedValue({});
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockUpdateMutateAsync).toHaveBeenCalled();
  });

  it('cancels form dialog', async () => {
    const user = userEvent.setup();
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /add property/i }));
    await user.click(screen.getByRole('button', { name: /record a property I already own/i }));
    expect(screen.getByTestId('real-estate-form')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    expect(screen.queryByTestId('real-estate-form')).not.toBeInTheDocument();
  });

  it('shows loading skeleton', () => {
    mockIsLoading = true;
    renderWithProviders(<RealEstatePage />);
    expect(screen.queryByTestId('property-card')).not.toBeInTheDocument();
  });

  it('handles create failure gracefully', async () => {
    const user = userEvent.setup();
    mockData = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 };
    mockCreateMutateAsync.mockRejectedValue(new Error('Create failed'));
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /add property/i }));
    await user.click(screen.getByRole('button', { name: /record a property I already own/i }));
    await user.click(screen.getByRole('button', { name: /submit/i }));
    // Should not crash
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });
  it('keeps negative equity when a property has zero value', () => {
    mockData = {
      content: [
        {
          id: 1,
          name: 'Underwater',
          propertyType: 'RESIDENTIAL',
          currentValue: 0,
          currency: 'EUR',
          isActive: true,
          allocatedDebt: 80,
          mortgageBalance: 80,
          mortgageId: 10,
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByText('Total Equity').parentElement).toHaveTextContent('-80');
  });

  it('sums explicit financing allocations across properties', () => {
    mockData = {
      content: [1, 2].map(id => ({
        id,
        name: `Home ${id}`,
        propertyType: 'RESIDENTIAL',
        currentValue: 200,
        currency: 'EUR',
        isActive: true,
        allocatedDebt: id === 1 ? 40 : 60,
      })),
      totalPages: 1,
      totalElements: 2,
      number: 0,
      size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    expect(screen.getByText('Total Equity').parentElement).toHaveTextContent('300');
  });
});
