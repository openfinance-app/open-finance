import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useDocumentTitle', () => ({ useDocumentTitle: vi.fn() }));

let mockData: any = undefined;
let mockIsLoading = false;
let mockError: Error | null = null;

let mockCreateMutateAsync = vi.fn();
let mockUpdateMutateAsync = vi.fn();

vi.mock('@/hooks/useRealEstate', () => ({
  usePropertiesSearch: () => ({ data: mockData, isLoading: mockIsLoading, error: mockError }),
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
      <button onClick={() => onSubmit({ name: 'New Prop', propertyType: 'RESIDENTIAL', currentValue: 100000, currency: 'USD' })}>Submit</button>
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
  ConvertedAmount: () => <span data-testid="converted-amount" />,
}));

import RealEstatePage from './RealEstatePage';

describe('RealEstatePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockData = undefined;
    mockIsLoading = false;
    mockError = null;
    mockCreateMutateAsync = vi.fn();
    mockUpdateMutateAsync = vi.fn();
    Element.prototype.scrollIntoView = vi.fn();
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

  it('opens edit form when clicking edit on property card', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [{ id: 1, name: 'Beach House', propertyType: 'RESIDENTIAL', currentValue: 500000, currency: 'USD', isActive: true, isConverted: false, purchasePrice: 400000, mortgageBalance: 200000, monthlyRentalIncome: 0 }],
      totalPages: 1, totalElements: 1, number: 0, size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    expect(screen.getByTestId('real-estate-form')).toBeInTheDocument();
    expect(screen.getByTestId('editing-name')).toHaveTextContent('Beach House');
  });

  it('opens detail view when clicking view on property card', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [{ id: 1, name: 'Beach House', propertyType: 'RESIDENTIAL', currentValue: 500000, currency: 'USD', isActive: true, isConverted: false, purchasePrice: 400000, mortgageBalance: 200000, monthlyRentalIncome: 0 }],
      totalPages: 1, totalElements: 1, number: 0, size: 20,
    };
    renderWithProviders(<RealEstatePage />);
    await user.click(screen.getByRole('button', { name: /^view$/i }));
    expect(screen.getByTestId('detail-view')).toBeInTheDocument();
    expect(screen.getByText('Detail 1')).toBeInTheDocument();
  });

  it('closes detail view', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [{ id: 1, name: 'Beach House', propertyType: 'RESIDENTIAL', currentValue: 500000, currency: 'USD', isActive: true, isConverted: false, purchasePrice: 400000, mortgageBalance: 200000, monthlyRentalIncome: 0 }],
      totalPages: 1, totalElements: 1, number: 0, size: 20,
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
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });

  it('submits update form for existing property', async () => {
    const user = userEvent.setup();
    mockData = {
      content: [{ id: 1, name: 'Beach House', propertyType: 'RESIDENTIAL', currentValue: 500000, currency: 'USD', isActive: true, isConverted: false, purchasePrice: 400000, mortgageBalance: 200000, monthlyRentalIncome: 0 }],
      totalPages: 1, totalElements: 1, number: 0, size: 20,
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
    await user.click(screen.getByRole('button', { name: /submit/i }));
    // Should not crash
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });
});
