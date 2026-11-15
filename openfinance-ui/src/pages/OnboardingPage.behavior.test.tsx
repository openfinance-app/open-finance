import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import OnboardingPage from '@/pages/OnboardingPage';
import { DEFAULT_CURRENCY } from '@/utils/currency';

const mutation = vi.hoisted(() => ({ mutate: vi.fn(), isPending: false, isError: false }));
vi.mock('@/hooks/useAuth', () => ({ useCompleteOnboarding: () => mutation }));
vi.mock('@/components/settings/LanguageSelector', () => ({ LanguageSelector: () => null }));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    value,
    onValueChange,
    allowNone,
  }: ComponentProps<typeof import('@/components/ui/CurrencySelector').CurrencySelector>) => (
    <select
      aria-label={allowNone ? 'Secondary currency' : 'Base currency'}
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value)}
    >
      {['', 'USD', 'EUR', 'CAD', 'GBP', 'TWD'].map(code => (
        <option key={code} value={code}>
          {code || 'None'}
        </option>
      ))}
    </select>
  ),
}));
vi.mock('@/components/common/CountrySelector', () => ({
  CountrySelector: ({
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/common/CountrySelector').CountrySelector>) => (
    <select
      aria-label="Country"
      value={value}
      onChange={event => onValueChange(event.target.value)}
    >
      {['', 'US', 'FR', 'GB', 'CA', 'DE', 'TW', 'ZZ'].map(code => (
        <option key={code} value={code}>
          {code || 'Choose'}
        </option>
      ))}
    </select>
  ),
}));

describe('Onboarding preferences and recovery', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mutation.isPending = false;
    mutation.isError = false;
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-US');
    mockAuthentication();
    await i18n.changeLanguage('en');
  });
  afterEach(() => vi.restoreAllMocks());

  it.each([
    ['en-US', 'US', 'USD', 'MM/DD/YYYY', '1,234.56'],
    ['en-GB', 'GB', 'GBP', 'DD/MM/YYYY', '1,234.56'],
    ['en-CA', 'CA', 'CAD', 'DD/MM/YYYY', '1,234.56'],
    ['fr-CA', 'CA', 'CAD', 'DD/MM/YYYY', '1 234,56'],
    ['de-DE', 'DE', 'EUR', 'DD/MM/YYYY', '1.234,56'],
    ['fr', 'FR', 'EUR', 'DD/MM/YYYY', '1 234,56'],
    ['en', 'US', 'USD', 'MM/DD/YYYY', '1,234.56'],
    ['', 'US', 'USD', 'MM/DD/YYYY', '1,234.56'],
    ['en-ZZ', 'ZZ', DEFAULT_CURRENCY, 'DD/MM/YYYY', '1.234,56'],
    ['zh-Hant-TW', 'TW', DEFAULT_CURRENCY, 'DD/MM/YYYY', '1.234,56'],
  ])(
    'derives country and format defaults from browser locale %s',
    (locale, country, baseCurrency, dateFormat, numberFormat) => {
      vi.spyOn(navigator, 'language', 'get').mockReturnValue(locale);
      renderWithProviders(<OnboardingPage />);
      fireEvent.click(screen.getByRole('button', { name: i18n.t('onboarding:submit') }));
      expect(mutation.mutate).toHaveBeenCalledWith({
        country,
        baseCurrency,
        secondaryCurrency: '',
        language: 'en',
        dateFormat,
        numberFormat,
        amountDisplayMode: 'base',
      });
    }
  );

  it('submits explicit country, currency, date, number and amount display choices', () => {
    renderWithProviders(<OnboardingPage />);
    fireEvent.change(screen.getByRole('combobox', { name: 'Country' }), {
      target: { value: 'FR' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: 'Base currency' }), {
      target: { value: 'EUR' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: 'Secondary currency' }), {
      target: { value: 'GBP' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'YYYY-MM-DD' }));
    fireEvent.click(
      screen.getByRole('radio', {
        name: new RegExp(i18n.t('onboarding:numberFormat.frenchSwissStyle')),
      })
    );
    fireEvent.click(
      screen.getByRole('radio', {
        name: new RegExp(i18n.t('onboarding:amountDisplayMode.both') + ' '),
      })
    );
    fireEvent.click(screen.getByRole('button', { name: i18n.t('onboarding:submit') }));
    expect(mutation.mutate).toHaveBeenCalledWith({
      country: 'FR',
      baseCurrency: 'EUR',
      secondaryCurrency: 'GBP',
      language: 'en',
      dateFormat: 'YYYY-MM-DD',
      numberFormat: '1 234,56',
      amountDisplayMode: 'both',
    });
  });

  it('clears a previously selected secondary currency', () => {
    renderWithProviders(<OnboardingPage />);
    const secondary = screen.getByRole('combobox', { name: 'Secondary currency' });
    fireEvent.change(secondary, { target: { value: 'EUR' } });
    fireEvent.change(secondary, { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: i18n.t('onboarding:submit') }));
    expect(mutation.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ secondaryCurrency: '' })
    );
  });

  it.each(['Country', 'Base currency'])('requires %s before completing setup', label => {
    renderWithProviders(<OnboardingPage />);
    fireEvent.change(screen.getByRole('combobox', { name: label }), { target: { value: '' } });
    const submit = screen.getByRole('button', { name: i18n.t('onboarding:submit') });
    expect(submit).toBeDisabled();
    fireEvent.click(submit);
    expect(mutation.mutate).not.toHaveBeenCalled();
  });

  it('keeps selected preferences available for retry after a failed request', () => {
    mutation.isError = true;
    renderWithProviders(<OnboardingPage />);
    expect(screen.getByRole('alert')).toHaveTextContent(i18n.t('onboarding:error'));
    fireEvent.click(screen.getByRole('button', { name: 'DD/MM/YYYY' }));
    fireEvent.click(screen.getByRole('button', { name: i18n.t('onboarding:submit') }));
    expect(mutation.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ dateFormat: 'DD/MM/YYYY' })
    );
  });

  it('disables duplicate submissions while saving', () => {
    mutation.isPending = true;
    renderWithProviders(<OnboardingPage />);
    const submit = screen.getByRole('button', { name: i18n.t('onboarding:submitting') });
    expect(submit).toBeDisabled();
    fireEvent.click(submit);
    expect(mutation.mutate).not.toHaveBeenCalled();
  });

  it('uses the in-form language choice without resetting edited preferences', async () => {
    renderWithProviders(<OnboardingPage />);
    fireEvent.change(screen.getByRole('combobox', { name: 'Country' }), {
      target: { value: 'CA' },
    });
    fireEvent.click(screen.getByRole('button', { name: i18n.t('onboarding:language.french') }));
    await waitFor(() => expect(i18n.resolvedLanguage).toBe('fr'));
    fireEvent.click(screen.getByRole('button', { name: i18n.t('onboarding:submit') }));
    expect(mutation.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ country: 'CA', language: 'fr' })
    );
  });
});
