import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { PropertySelector } from '@/components/real-estate-tools/PropertySelector';
import { DEFAULT_BUY_RENT_INPUTS } from '@/types/realEstateTools';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({ convert: vi.fn(), loading: false, error: false }));
vi.mock('@/hooks/useRealEstate', () => ({
  useProperties: () => ({
    data: [
      {
        id: 1,
        name: 'London flat',
        address: 'Chelsea, London',
        propertyType: 'RESIDENTIAL',
        purchasePrice: 123456.78,
        rentalIncome: 987.65,
        currency: 'GBP',
      },
      {
        id: 2,
        name: 'Paris home',
        address: 'Paris, France',
        propertyType: 'RESIDENTIAL',
        purchasePrice: 250000,
        rentalIncome: null,
        currency: 'EUR',
      },
    ],
    isLoading: state.loading,
    isError: state.error,
  }),
}));
vi.mock('@/hooks/useCurrency', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useCurrency')>()),
  useConvertCurrency: () => ({ mutateAsync: state.convert, isPending: false }),
}));
const tr = (key: string) => i18n.t(key, { ns: 'realEstate' });
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
  state.convert.mockReset().mockResolvedValue({ exchangeRate: 1.2 });
  state.loading = false;
  state.error = false;
  Element.prototype.scrollIntoView = vi.fn();
  Element.prototype.hasPointerCapture = vi.fn(() => false);
  Element.prototype.setPointerCapture = vi.fn();
  Element.prototype.releasePointerCapture = vi.fn();
});

describe('using an existing property in a simulation', () => {
  it('searches addresses and converts purchase price and rent with the same exchange rate', async () => {
    const user = userEvent.setup();
    const selected = vi.fn();
    renderWithProviders(<PropertySelector onPropertySelect={selected} />);
    await user.click(screen.getByRole('combobox'));
    fireEvent.change(screen.getByPlaceholderText(tr('propertySelector.searchProperties')), {
      target: { value: ' CHELSEA ' },
    });
    expect(screen.queryByRole('option', { name: /Paris home/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('option', { name: /London flat/ }));
    await waitFor(() =>
      expect(selected).toHaveBeenCalledWith(
        expect.objectContaining({
          purchase: expect.objectContaining({ propertyPrice: 148148.14 }),
          rental: expect.objectContaining({ monthlyRent: 1185.18, securityDeposit: 1185.18 }),
        })
      )
    );
    expect(state.convert).toHaveBeenCalledWith({
      amount: 1,
      fromCurrency: 'GBP',
      toCurrency: 'EUR',
    });
    await user.click(screen.getByRole('combobox'));
    expect(screen.getByPlaceholderText(tr('propertySelector.searchProperties'))).toHaveValue('');
  });

  it('uses the configured rent when none is recorded and needs no conversion for the base currency', async () => {
    const user = userEvent.setup();
    const selected = vi.fn();
    renderWithProviders(<PropertySelector onPropertySelect={selected} />);
    await user.click(screen.getByRole('combobox'));
    await user.click(screen.getByRole('option', { name: /Paris home/ }));
    expect(selected).toHaveBeenCalledWith(
      expect.objectContaining({
        purchase: expect.objectContaining({ propertyPrice: 250000 }),
        rental: expect.objectContaining({
          monthlyRent: DEFAULT_BUY_RENT_INPUTS.rental.monthlyRent,
        }),
      })
    );
    expect(state.convert).not.toHaveBeenCalled();
  });

  it.each([0, -1, Number.NaN])(
    'rejects an unusable exchange rate (%s) without overwriting the form',
    async rate => {
      state.convert.mockResolvedValue({ exchangeRate: rate });
      const selected = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(<PropertySelector onPropertySelect={selected} />);
      await user.click(screen.getByRole('combobox'));
      await user.click(screen.getByRole('option', { name: /London flat/ }));
      expect(await screen.findByRole('alert')).toHaveTextContent(
        tr('validation.currencyConversion')
      );
      expect(selected).not.toHaveBeenCalled();
    }
  );

  it('shows a no-match result without selecting a different property', async () => {
    const selected = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<PropertySelector onPropertySelect={selected} />);
    await user.click(screen.getByRole('combobox'));
    fireEvent.change(screen.getByPlaceholderText(tr('propertySelector.searchProperties')), {
      target: { value: 'Missing' },
    });
    expect(screen.getByText(tr('propertySelector.noMatch'))).toBeInTheDocument();
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
    expect(selected).not.toHaveBeenCalled();
  });
});
