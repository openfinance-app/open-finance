import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { PropertyCard } from '@/components/real-estate/PropertyCard';
import { RealEstateForm } from '@/components/real-estate/RealEstateForm';
import type { RealEstateProperty } from '@/types/realEstate';

vi.mock('@/hooks/useRealEstate', () => ({ useDeleteProperty: () => ({ mutateAsync: vi.fn() }) }));
vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' } }),
}));
vi.mock('@/components/real-estate/PropertyCoverImage', () => ({ PropertyCoverImage: () => null }));
vi.mock('@/components/ui/ExchangeRateDisplay', () => ({ ExchangeRateInline: () => null }));
vi.mock('@/components/ui/LiabilitySelector', () => ({
  LiabilitySelector: () => <select aria-label="Mortgage" />,
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    value,
    onValueChange,
  }: {
    value: string;
    onValueChange: (value: string) => void;
  }) => (
    <select aria-label="Currency" value={value} onChange={e => onValueChange(e.target.value)}>
      <option value="EUR">EUR</option>
    </select>
  ),
}));

describe('Zero-value properties', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  it('preserves exact property values when only the name changes', async () => {
    const exact = '10006858.9112567483';
    const property: RealEstateProperty = {
      id: 1,
      userId: 1,
      name: 'Home',
      address: '1 Current Street',
      propertyType: 'RESIDENTIAL',
      purchasePrice: Number(exact),
      currentValue: Number(exact),
      rentalIncome: Number(exact),
      exactValues: { purchasePrice: exact, currentValue: exact, rentalIncome: exact },
      allocatedDebt: 0,
      currency: 'EUR',
      acquisitionType: 'PURCHASE',
      purchaseDate: '2020-01-01',
      isActive: true,
      createdAt: '',
      updatedAt: '',
    };
    const submit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithProviders(
      <RealEstateForm property={property} onSubmit={submit} onCancel={vi.fn()} isLoading={false} />
    );
    await user.type(screen.getByLabelText(/property name/i), ' renamed');
    await user.click(screen.getByRole('button', { name: /update property/i }));
    await waitFor(() =>
      expect(submit).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Home renamed',
          purchasePrice: exact,
          currentValue: exact,
          rentalIncome: exact,
        })
      )
    );
  });

  it('renders a planned property whose appreciation percentage is undefined', () => {
    const property: RealEstateProperty = {
      id: 1,
      userId: 1,
      name: 'Future home',
      address: '1 Future Street',
      propertyType: 'RESIDENTIAL',
      purchasePrice: 0,
      currentValue: 0,
      currency: 'EUR',
      acquisitionType: 'PLANNED',
      purchaseDate: '2030-01-01',
      isActive: true,
      createdAt: '',
      updatedAt: '',
      appreciation: 0,
      appreciationPercentage: null,
    };
    renderWithProviders(<PropertyCard property={property} onEdit={vi.fn()} onView={vi.fn()} />);
    expect(screen.getByText('Future home')).toBeInTheDocument();
    expect(screen.getByText(/planned/i)).toBeInTheDocument();
  });

  it('shows equity for allocated financing without a primary mortgage', () => {
    const property: RealEstateProperty = {
      id: 2,
      userId: 1,
      name: 'Direct-funded home',
      address: '2 Current Street',
      propertyType: 'RESIDENTIAL',
      purchasePrice: 100,
      currentValue: 80,
      currency: 'EUR',
      purchaseDate: '2026-01-01',
      isActive: true,
      createdAt: '',
      updatedAt: '',
      mortgageId: null,
      allocatedDebt: 80,
      equity: 0,
    };
    renderWithProviders(<PropertyCard property={property} onEdit={vi.fn()} onView={vi.fn()} />);
    expect(screen.getByText('Equity').parentElement).toHaveTextContent('€0.00');
  });

  it('submits a planned property with zero values and the selected future date', async () => {
    const submit = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<RealEstateForm onSubmit={submit} onCancel={vi.fn()} />);
    await user.selectOptions(screen.getByLabelText('Acquisition'), 'PLANNED');
    await user.type(screen.getByLabelText(/property name/i), 'Future home');
    await user.type(screen.getByLabelText(/address/i), '1 Future Street');
    const future = `${new Date().getFullYear() + 2}-01-01`;
    const date = screen.getByLabelText(/purchase date/i);
    await user.clear(date);
    await user.type(date, future);
    await user.click(screen.getByRole('button', { name: /create property/i }));
    await waitFor(() =>
      expect(submit).toHaveBeenCalledWith(
        expect.objectContaining({
          acquisitionType: 'PLANNED',
          purchasePrice: '0',
          currentValue: '0',
          purchaseDate: future,
        })
      )
    );
  });
});
