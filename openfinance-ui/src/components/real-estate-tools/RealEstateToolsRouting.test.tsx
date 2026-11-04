import type { ComponentProps } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { RealEstateToolsWrapper } from '@/components/real-estate-tools/RealEstateToolsWrapper';
import { RealEstateToolsHub } from '@/components/real-estate-tools/RealEstateToolsHub';
import i18n from '@/test/i18n-test';

const country = vi.hoisted(() => ({ isPropertyRentalAvailable: true, countryCode: 'FR' }));
const property = {
  currency: 'EUR',
  totalPrice: 312345,
  propertyTax: 1800,
  coOwnershipCharges: 150,
  credit: {
    monthlyPayment: 1000,
    annualCost: 12000,
    totalCost: 240000,
    assurance: 300,
    bankFees: 100,
  },
};
vi.mock('@/hooks/useCountryToolConfig', () => ({ useCountryToolConfig: () => country }));
vi.mock('@/components/real-estate-tools/BuyRentComparator', () => ({
  BuyRentComparator: ({
    onNavigateToRentalSimulator,
  }: ComponentProps<
    typeof import('@/components/real-estate-tools/BuyRentComparator').BuyRentComparator
  >) => (
    <button onClick={() => onNavigateToRentalSimulator?.(property)}>
      Use purchase in rental simulation
    </button>
  ),
}));
vi.mock('@/components/real-estate-tools/PropertyRentalSimulator', () => ({
  PropertyRentalSimulator: ({
    sharedData,
    onNavigateBack,
  }: ComponentProps<
    typeof import('@/components/real-estate-tools/PropertyRentalSimulator').PropertyRentalSimulator
  >) => (
    <div>
      <output aria-label="Shared property">{JSON.stringify(sharedData)}</output>
      <button onClick={onNavigateBack}>Back to purchase comparison</button>
    </div>
  ),
}));
beforeEach(() => {
  mockAuthentication();
  country.isPropertyRentalAvailable = true;
  country.countryCode = 'FR';
});
afterEach(() => window.history.replaceState({}, '', '/'));
const renderTools = () =>
  renderWithProviders(
    <Routes>
      <Route path="/real-estate/tools" element={<RealEstateToolsHub />} />
      <Route path="/real-estate/tools/*" element={<RealEstateToolsWrapper />} />
    </Routes>
  );

it('carries all purchase assumptions into rental simulation and supports navigating back', async () => {
  window.history.replaceState({}, '', '/real-estate/tools/buy-rent');
  const user = userEvent.setup();
  renderTools();
  await user.click(screen.getByRole('button', { name: 'Use purchase in rental simulation' }));
  expect(JSON.parse(screen.getByLabelText('Shared property').textContent!)).toEqual(property);
  expect(window.location.pathname).toBe('/real-estate/tools/rental');
  await user.click(screen.getByRole('button', { name: 'Back to purchase comparison' }));
  expect(window.location.pathname).toBe('/real-estate/tools/buy-rent');
});
it('redirects a direct rental URL to the hub when local tax rules are unavailable', async () => {
  country.isPropertyRentalAvailable = false;
  country.countryCode = 'US';
  window.history.replaceState({}, '', '/real-estate/tools/rental');
  renderTools();
  await waitFor(() => expect(window.location.pathname).toBe('/real-estate/tools'));
  expect(
    screen.getByRole('button', { name: i18n.t('realEstate:toolsHub.unavailableForCountry') })
  ).toBeDisabled();
  expect(screen.queryByLabelText('Shared property')).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByRole('button', { name: i18n.t('realEstate:toolsHub.openSimulator') })
  );
  expect(window.location.pathname).toBe('/real-estate/tools/buy-rent');
});
it('opens the rental simulator from the hub for France', async () => {
  window.history.replaceState({}, '', '/real-estate/tools');
  renderTools();
  await userEvent.click(
    screen.getAllByRole('button', { name: i18n.t('realEstate:toolsHub.openSimulator') })[1]
  );
  expect(window.location.pathname).toBe('/real-estate/tools/rental');
});
