import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { GeneralSettings } from '@/components/settings/GeneralSettings';
import i18n from '@/test/i18n-test';
const state = vi.hoisted(() => ({
  loading: false,
  failed: false,
  missing: false,
  currency: 'USD' as string | null,
  save: vi.fn(),
}));
vi.mock('@/hooks/useCurrency', () => ({
  useCurrencies: () => ({
    isLoading: state.loading,
    isError: state.failed,
    data: state.missing
      ? undefined
      : [
          { code: 'USD', name: 'US Dollar', symbol: '$', isActive: true },
          { code: 'EUR', name: 'Euro', symbol: '€', isActive: true },
          { code: 'OLD', name: 'Archived currency', symbol: 'X', isActive: false },
        ],
  }),
}));
vi.mock('@/context/CurrencyDisplayContext', async original => ({
  ...(await original<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({
    secondaryCurrency: state.currency,
    setSecondaryCurrency: state.save,
  }),
}));
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: { language: 'en', country: 'FR', secondaryCurrency: 'USD' } }),
  useUpdateUserSettings: () => ({ mutate: vi.fn(), isPending: false }),
  useUpdateBaseCurrency: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: () => <span>Base currency</span>,
}));
vi.mock('@/components/common/CountrySelector', () => ({
  CountrySelector: () => <span>Country</span>,
}));
const tr = (key: string) => i18n.t(`settings:general.currencies.${key}`);
beforeEach(() => {
  mockAuthentication();
  state.loading = false;
  state.failed = false;
  state.missing = false;
  state.currency = 'USD';
  state.save.mockReset().mockResolvedValue(undefined);
  Element.prototype.scrollIntoView = vi.fn();
  Element.prototype.hasPointerCapture = vi.fn(() => false);
  Element.prototype.setPointerCapture = vi.fn();
  Element.prototype.releasePointerCapture = vi.fn();
});
describe('Secondary currency selection', () => {
  it('keeps typing focused when searching for a different secondary currency', async () => {
    const user = userEvent.setup();
    renderWithProviders(<GeneralSettings />);
    await user.click(screen.getByRole('combobox', { name: tr('secondaryCurrency') }));
    const search = screen.getByRole('textbox');
    await user.type(search, 'Euro');
    expect(search).toHaveValue('Euro');
    expect(search).toHaveFocus();
    expect(screen.queryByRole('option', { name: /USD/ })).not.toBeInTheDocument();
    await user.keyboard('{ArrowUp}{Enter}');
    await waitFor(() => expect(state.save).toHaveBeenCalledWith('EUR'));
  });

  it.each(['loading', 'failed', 'missing'])('explains an unavailable currency list: %s', mode => {
    state.loading = mode === 'loading';
    state.failed = mode === 'failed';
    state.missing = mode === 'missing';
    renderWithProviders(<GeneralSettings />);
    expect(
      screen.getByText(tr(mode === 'loading' ? 'loadingCurrencies' : 'loadError'))
    ).toBeVisible();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });
  it('searches currency symbols, hides inactive choices and resets the query after selection', async () => {
    renderWithProviders(<GeneralSettings />);
    await userEvent.click(screen.getByRole('combobox', { name: tr('secondaryCurrency') }));
    expect(screen.queryByRole('option', { name: /OLD/ })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '  €  ' } });
    expect(screen.queryByRole('option', { name: /USD/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('option', { name: /EUR/ }));
    await waitFor(() => expect(state.save).toHaveBeenCalledWith('EUR'));
    await userEvent.click(screen.getByRole('combobox', { name: tr('secondaryCurrency') }));
    expect(screen.getByRole('textbox')).toHaveValue('');
    expect(screen.getByRole('option', { name: /USD/ })).toBeVisible();
  });
  it('allows clearing the secondary currency even when search has no matching currencies', async () => {
    renderWithProviders(<GeneralSettings />);
    await userEvent.click(screen.getByRole('combobox', { name: tr('secondaryCurrency') }));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'no-such-currency' } });
    expect(screen.getByText(tr('noMatch'))).toBeVisible();
    await userEvent.click(screen.getByRole('option', { name: tr('none') }));
    await waitFor(() => expect(state.save).toHaveBeenCalledWith(null));
  });
});
