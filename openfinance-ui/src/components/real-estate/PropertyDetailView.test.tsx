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
  expect(close).toHaveBeenCalledOnce();
});
