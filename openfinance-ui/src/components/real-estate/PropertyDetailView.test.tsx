import { waitFor, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { PropertyDetailView } from '@/components/real-estate/PropertyDetailView';
import type { RealEstateProperty } from '@/types/realEstate';
import i18n from '@/test/i18n-test';

const hooks = vi.hoisted(() => ({ property: vi.fn(), equity: vi.fn(), roi: vi.fn() }));
vi.mock('@/hooks/useRealEstate', () => ({
  useProperty: hooks.property,
  usePropertyEquity: hooks.equity,
  usePropertyROI: hooks.roi,
}));
vi.mock('@/components/assets/OwnershipActions', () => ({
  OwnershipActions: ({
    onComplete,
  }: ComponentProps<typeof import('@/components/assets/OwnershipActions').OwnershipActions>) => (
    <button onClick={onComplete}>Complete planned purchase</button>
  ),
}));
vi.mock('@/components/assets/AssetFinancingSection', () => ({
  AssetFinancingSection: ({ assetId }: { assetId: number }) => <p>Financing asset {assetId}</p>,
}));
vi.mock('@/components/real-estate/PropertyMovementsSection', () => ({
  PropertyMovementsSection: () => <p>Purchase costs</p>,
}));
vi.mock('@/components/real-estate/PropertyGallery', () => ({
  PropertyGallery: ({ propertyId }: { propertyId: number }) => <p>Gallery for {propertyId}</p>,
}));
vi.mock('@/components/attachments', () => ({
  AttachmentList: () => <p>Existing documents</p>,
  AttachmentUpload: () => <p>Upload document</p>,
}));
const property: RealEstateProperty = {
  id: 7,
  assetId: 9,
  userId: 1,
  name: 'Rental home',
  address: '1 Main Street',
  propertyType: 'RESIDENTIAL',
  purchasePrice: 100000,
  currentValue: 120000,
  allocatedDebt: 20000,
  purchaseDate: '2020-01-01',
  currency: 'USD',
  isActive: true,
  createdAt: '2020-01-01',
  updatedAt: '2026-01-01',
  appreciation: 20000,
  appreciationPercentage: 20,
  acquisitionType: 'PURCHASE',
  ownershipStatus: 'OWNED',
};
const tr = (key: string) => i18n.t(`realEstate:propertyDetail.${key}`);
beforeEach(() => {
  mockAuthentication();
  window.history.replaceState({}, '', '/real-estate');
  Element.prototype.scrollIntoView = vi.fn();
  hooks.property.mockReturnValue({ data: property, isLoading: false });
  hooks.equity.mockReturnValue({
    data: {
      currentValue: 120000,
      equity: 100000,
      equityPercentage: 83.33,
      currency: 'USD',
      hasMortgage: true,
      mortgageBalance: 20000,
      mortgageId: 3,
    },
  });
  hooks.roi.mockReturnValue({
    data: {
      totalROI: 25,
      annualizedReturn: 4.56,
      currency: 'USD',
      yearsOwned: 6,
      performanceBasis: 100000,
      currentValue: 120000,
      appreciation: 20000,
      appreciationPercentage: 20,
      isRentalProperty: true,
      monthlyRentalIncome: 750,
      totalRentalIncome: 54000,
      rentalYield: 7.5,
    },
  });
});
afterEach(() => window.history.replaceState({}, '', '/'));

it('shows equity and rental returns without mixing debt with performance, and opens supporting records', async () => {
  const user = userEvent.setup();
  renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
  expect(screen.getByRole('heading', { name: property.name })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: tr('tab.equity') }));
  expect(screen.getAllByText('$100,000.00').length).toBeGreaterThan(0);
  expect(screen.getByRole('link')).toHaveAttribute('href', expect.stringContaining('3'));
  await user.click(screen.getByRole('button', { name: tr('tab.roi') }));
  expect(screen.getByText('25.00%')).toBeInTheDocument();
  expect(screen.getByText('$750.00')).toBeInTheDocument();
  for (const [tab, text] of [
    ['costs', 'Purchase costs'],
    ['financing', 'Financing asset 9'],
    ['gallery', 'Gallery for 7'],
    ['attachments', 'Existing documents'],
  ]) {
    await user.click(screen.getByRole('button', { name: tr(`tab.${tab}`) }));
    expect(screen.getByText(text)).toBeInTheDocument();
  }
});
it('hides realized performance for a plan and passes the selected property to the purchase action', async () => {
  const plan = { ...property, acquisitionType: 'PLANNED', ownershipStatus: 'PLANNED' };
  hooks.property.mockReturnValue({ data: plan });
  const complete = vi.fn();
  renderWithProviders(
    <PropertyDetailView propertyId={7} onClose={vi.fn()} onCompletePurchase={complete} />
  );
  expect(screen.queryByRole('button', { name: tr('tab.roi') })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: tr('tab.equity') })).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Complete planned purchase' }));
  expect(complete).toHaveBeenCalledWith(plan);
});
it('lets the user close a failed detail request without showing stale property data', async () => {
  hooks.property.mockReturnValue({ error: new Error('Not found') });
  const close = vi.fn();
  renderWithProviders(<PropertyDetailView propertyId={7} onClose={close} />);
  expect(screen.getByText(tr('loadError'))).toBeInTheDocument();
  expect(screen.queryByText(property.name)).not.toBeInTheDocument();
  await userEvent.click(screen.getAllByRole('button', { name: tr('close') })[0]);
  await waitFor(() => expect(close).toHaveBeenCalledOnce());
});

it.each([
  [0, 0],
  [0, 2.3522],
  [48.8566, 0],
  [null, -73.9857],
  [-33.8688, null],
] as const)('shows valid coordinates including zero (%s, %s)', (latitude, longitude) => {
  hooks.property.mockReturnValue({ data: { ...property, latitude, longitude } });
  renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
  expect(screen.getByRole('heading', { name: tr('location') })).toBeInTheDocument();
  for (const [name, value] of [
    ['latitude', latitude],
    ['longitude', longitude],
  ] as const) {
    if (value === null) expect(screen.queryByText(tr(name))).not.toBeInTheDocument();
    else expect(screen.getByText(tr(name)).parentElement).toHaveTextContent(value.toFixed(6));
  }
});

it.each([null, 80000])(
  'shows gift valuation availability without assuming its cost is its value (%s)',
  receivedValue => {
    hooks.property.mockReturnValue({
      data: {
        ...property,
        acquisitionType: 'GIFT',
        receivedValue,
        purchasePrice: 1000,
        appreciation: null,
        appreciationPercentage: null,
        isActive: false,
        mortgageName: 'Home loan',
        rentalIncome: 700,
        notes: 'Roof replaced in 2024',
      },
    });
    renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
    expect(screen.getByText(i18n.t('realEstate:form.receivedDate'))).toBeInTheDocument();
    expect(
      screen.getByText(i18n.t('realEstate:form.acquisitionCost')).parentElement
    ).toHaveTextContent('$1,000.00');
    expect(
      screen.getByText(i18n.t('realEstate:form.receivedValue')).parentElement
    ).toHaveTextContent(receivedValue === null ? tr('na') : '$80,000.00');
    expect(screen.queryByText(i18n.t('realEstate:performanceUnavailable')) !== null).toBe(
      receivedValue === null
    );
    expect(screen.getByText('Home loan')).toBeInTheDocument();
    expect(screen.getByText('$700.00')).toBeInTheDocument();
    expect(screen.getByText('Roof replaced in 2024')).toBeInTheDocument();
    expect(screen.getByText(i18n.t('realEstate:card.inactive'))).toBeInTheDocument();
  }
);

it('keeps a closed property out of current valuation and return views', () => {
  hooks.property.mockReturnValue({
    data: { ...property, ownershipStatus: 'CLOSED', soldDate: '2025-01-01' },
  });
  renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
  expect(screen.queryByRole('button', { name: tr('tab.roi') })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: tr('tab.equity') })).not.toBeInTheDocument();
  expect(screen.queryByText(i18n.t('realEstate:form.currentValue'))).not.toBeInTheDocument();
});

it.each([
  { hasMortgage: false, mortgageBalance: 0, equity: 120000, equityPercentage: 100 },
  { hasMortgage: false, mortgageBalance: 125000, equity: -5000, equityPercentage: -4.17 },
  { hasMortgage: true, mortgageBalance: 0, equity: 0, equityPercentage: null },
])('explains debt and undefined ratios without inventing equity: %j', async values => {
  hooks.equity.mockReturnValue({
    data: {
      ...values,
      currentValue: values.equityPercentage === null ? 0 : 120000,
      currency: 'USD',
    },
  });
  renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: tr('tab.equity') }));
  expect(screen.queryByText(tr('noMortgage')) !== null).toBe(
    !values.hasMortgage && values.mortgageBalance === 0
  );
  expect(screen.queryByText(tr('mortgageBalance')) !== null).toBe(
    values.hasMortgage || values.mortgageBalance > 0
  );
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  const region = screen.getByRole('region', { name: tr('tab.equity') });
  expect(region.textContent).not.toMatch(/NaN|Infinity/);
  if (values.equity < 0) expect(within(region).getAllByText('-$5,000.00')).toHaveLength(2);
  if (values.equityPercentage === null) expect(region).toHaveTextContent('—');
});

it.each(['equity', 'roi'] as const)('handles a loading and then failed %s request', async tab => {
  const hook = hooks[tab];
  hook.mockReturnValue({ isLoading: true });
  const { rerender } = renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: tr(`tab.${tab}`) }));
  const error = tr(tab === 'equity' ? 'loadEquityError' : 'loadROIError');
  expect(screen.queryByText(error)).not.toBeInTheDocument();
  hook.mockReturnValue({ isLoading: false, data: undefined });
  rerender(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
  expect(screen.getByText(error)).toBeInTheDocument();
});

it.each([null, -20000])(
  'handles unavailable or negative returns for a non-rental property (%s)',
  async appreciation => {
    hooks.roi.mockReturnValue({
      data: {
        totalROI: null,
        annualizedReturn: null,
        performanceBasis: null,
        yearsOwned: null,
        currentValue: 80000,
        appreciation,
        appreciationPercentage: appreciation === null ? null : -20,
        currency: 'USD',
        isRentalProperty: false,
      },
    });
    renderWithProviders(<PropertyDetailView propertyId={7} onClose={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: tr('tab.roi') }));
    const region = screen.getByRole('region', { name: tr('tab.roi') });
    expect(within(region).getAllByText(tr('na')).length).toBeGreaterThanOrEqual(2);
    expect(region.textContent).not.toMatch(/NaN|Infinity/);
    if (appreciation !== null) expect(region).toHaveTextContent('-20.00%');
  }
);
