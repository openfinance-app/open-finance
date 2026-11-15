import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { PropertyCard } from '@/components/real-estate/PropertyCard';
import { PropertyDetailView } from '@/components/real-estate/PropertyDetailView';
import type { RealEstateProperty } from '@/types/realEstate';
import type { ConvertedAmountProps } from '@/components/ui/ConvertedAmount';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  remove: vi.fn(),
  property: vi.fn(),
  equity: vi.fn(),
  roi: vi.fn(),
}));
vi.mock('@/hooks/useRealEstate', () => ({
  useDeleteProperty: () => ({ mutateAsync: state.remove }),
  useProperty: () => state.property(),
  usePropertyEquity: () => state.equity(),
  usePropertyROI: () => state.roi(),
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency, convertedAmount, baseCurrency }: ConvertedAmountProps) => (
    <span>
      {currency} {String(amount)}
      {convertedAmount == null ? '' : ` (${baseCurrency} ${convertedAmount})`}
    </span>
  ),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => undefined }),
}));
vi.mock('@/components/real-estate/PropertyCoverImage', () => ({ PropertyCoverImage: () => null }));
vi.mock('@/components/assets/OwnershipActions', () => ({ OwnershipActions: () => null }));
vi.mock('@/components/assets/AssetFinancingSection', () => ({ AssetFinancingSection: () => null }));
vi.mock('@/components/real-estate/PropertyMovementsSection', () => ({
  PropertyMovementsSection: () => null,
}));
vi.mock('@/components/real-estate/PropertyGallery', () => ({ PropertyGallery: () => null }));
vi.mock('@/components/attachments', () => ({
  AttachmentList: () => null,
  AttachmentUpload: () => null,
}));

const property: RealEstateProperty = {
  id: 7,
  userId: 1,
  name: 'Home',
  address: 'Main Street',
  propertyType: 'RESIDENTIAL',
  purchasePrice: 100000,
  currentValue: 120000,
  allocatedDebt: 0,
  purchaseDate: '2024-01-01',
  currency: 'USD',
  isActive: true,
  createdAt: '2024-01-01',
  updatedAt: '2024-01-01',
};
const t = (key: string): string => i18n.t(`realEstate:${key}`);
const card = (overrides: Partial<RealEstateProperty> = {}, onView = vi.fn(), onEdit = vi.fn()) =>
  renderWithProviders(
    <PropertyCard
      property={{ ...property, ...overrides }}
      onView={onView}
      onEdit={onEdit}
      isHighlighted
    />
  );

beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  Element.prototype.scrollIntoView = vi.fn();
  state.remove.mockResolvedValue(undefined);
  state.property.mockReturnValue({ data: property });
  state.equity.mockReturnValue({});
  state.roi.mockReturnValue({});
});
afterEach(() => vi.restoreAllMocks());

describe('property card decisions', () => {
  it.each([
    ['PURCHASE', 'card.purchased', 'form.purchasePrice'],
    ['GIFT', 'form.receivedDate', 'form.acquisitionCost'],
    ['PLANNED', 'form.targetDate', 'form.targetPrice'],
  ] as const)(
    'uses the appropriate values and labels for %s',
    (acquisitionType, dateLabel, costLabel) => {
      card({ acquisitionType, isActive: false });
      expect(screen.getByText(t(dateLabel))).toBeInTheDocument();
      expect(screen.getByText(t(costLabel)).parentElement).toHaveTextContent('USD 100000');
      expect(screen.queryByText(t('form.currentValue')) !== null).toBe(
        acquisitionType !== 'PLANNED'
      );
      expect(screen.getByText(t('card.inactive'))).toBeInTheDocument();
      expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({
        behavior: 'smooth',
        block: 'center',
      });
    }
  );

  it.each([
    { mortgageId: 2, allocatedDebt: 0, equity: 80000 },
    { mortgageId: null, allocatedDebt: 130000, equity: -10000 },
    { mortgageId: null, allocatedDebt: 0, equity: 120000 },
    { mortgageId: 2, allocatedDebt: 10000, equity: undefined },
  ])('shows equity only when debt and a calculated equity are available: %j', values => {
    card(values);
    const expected = Boolean(
      (values.mortgageId || values.allocatedDebt > 0) && values.equity != null
    );
    expect(screen.queryByText(t('card.equity')) !== null).toBe(expected);
    if (expected)
      expect(screen.getByText(t('card.equity')).parentElement).toHaveTextContent(
        `USD ${values.equity}`
      );
  });

  it.each([-20000, 0, 20000])('shows signed appreciation and rental income (%s)', appreciation => {
    card({ appreciation, appreciationPercentage: appreciation / 1000, rentalIncome: 800 });
    expect(screen.getByText(t('card.appreciation')).parentElement).toHaveTextContent(
      `USD ${appreciation}`
    );
    expect(screen.getByText(t('card.monthlyRent')).parentElement).toHaveTextContent('USD 800');
  });

  it('keeps actions separate from opening the card and cancels deletion without a request', async () => {
    const view = vi.fn();
    const edit = vi.fn();
    card({}, view, edit);
    await userEvent.click(screen.getByRole('button', { name: /edit property/i }));
    expect(edit).toHaveBeenCalledWith(property);
    expect(view).not.toHaveBeenCalled();
    await userEvent.click(
      screen.getByRole('button', { name: i18n.t('common:aria.viewPropertyDetails') })
    );
    expect(view).toHaveBeenCalledExactlyOnceWith(7);
    await userEvent.click(screen.getByRole('button', { name: /delete property/i }));
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' })
    );
    expect(state.remove).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('heading', { name: 'Home' }));
    expect(view).toHaveBeenCalledTimes(2);
  });

  it.each([true, false])(
    'retains a failed deletion for retry and closes a successful one (success=%s)',
    async success => {
      vi.spyOn(console, 'error').mockImplementation(() => {});
      if (!success) state.remove.mockRejectedValue(new Error('In use'));
      card();
      await userEvent.click(screen.getByRole('button', { name: /delete property/i }));
      await userEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete', exact: true })
      );
      await waitFor(() => expect(state.remove).toHaveBeenCalledExactlyOnceWith(7));
      if (success)
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      else expect(screen.getByRole('dialog')).toHaveTextContent('Home');
    }
  );
});

describe('exact property conversions', () => {
  const converted: Partial<RealEstateProperty> = {
    currency: 'EUR',
    baseCurrency: 'USD',
    isConverted: true,
    exchangeRate: 2,
    purchasePrice: 9007199254740992,
    appreciation: -9007199254740992,
    appreciationPercentage: -20,
    allocatedDebt: 1,
    equity: -9007199254740992,
    rentalIncome: 0.01,
    exactValues: {
      purchasePrice: '9007199254740992.01',
      appreciation: '-9007199254740992.01',
      equity: '-9007199254740992.01',
      rentalIncome: '0.01',
      exchangeRate: '2',
    },
  };

  it('preserves cents in purchase, appreciation and equity amounts on cards', () => {
    card(converted);
    expect(screen.getByText(t('form.purchasePrice')).parentElement).toHaveTextContent(
      'USD 18014398509481984.02'
    );
    expect(screen.getByText(t('card.appreciation')).parentElement).toHaveTextContent(
      'USD -18014398509481984.02'
    );
    expect(screen.getByText(t('card.equity')).parentElement).toHaveTextContent(
      'USD -18014398509481984.02'
    );
  });

  it('preserves exact conversions in overview, equity and return analysis', async () => {
    state.property.mockReturnValue({ data: { ...property, ...converted } });
    state.equity.mockReturnValue({
      data: {
        currency: 'EUR',
        currentValue: 100,
        equity: -9007199254740992,
        mortgageBalance: 9007199254740992,
        hasMortgage: true,
        exactValues: { equity: '-9007199254740992.01', mortgageBalance: '9007199254740992.01' },
      },
    });
    state.roi.mockReturnValue({
      data: {
        currency: 'EUR',
        currentValue: 100,
        performanceBasis: 9007199254740992,
        appreciation: -9007199254740992,
        appreciationPercentage: -20,
        totalROI: -20,
        annualizedReturn: -5,
        isRentalProperty: true,
        monthlyRentalIncome: 0.01,
        totalRentalIncome: 0.12,
        exactValues: {
          performanceBasis: '9007199254740992.01',
          appreciation: '-9007199254740992.01',
          monthlyRentalIncome: '0.01',
          totalRentalIncome: '0.12',
        },
      },
    });
    renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
    expect(screen.getByText(t('form.purchasePrice')).parentElement).toHaveTextContent(
      'USD 18014398509481984.02'
    );
    await userEvent.click(screen.getByRole('button', { name: t('propertyDetail.tab.equity') }));
    expect(screen.getByRole('region', { name: t('propertyDetail.tab.equity') })).toHaveTextContent(
      'USD -18014398509481984.02'
    );
    await userEvent.click(screen.getByRole('button', { name: t('propertyDetail.tab.roi') }));
    expect(screen.getByText(t('propertyDetail.performanceBasis')).parentElement).toHaveTextContent(
      'USD 18014398509481984.02'
    );
    expect(screen.getByText(t('propertyDetail.totalRentalIncome')).parentElement).toHaveTextContent(
      'USD 0.24'
    );
  });
});
