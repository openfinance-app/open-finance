import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, act } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

const submitOnboarding = vi.fn();

vi.mock('@/hooks/useAuth', () => ({
  useCompleteOnboarding: () => ({
    mutate: submitOnboarding,
    isPending: false,
  }),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: (props: any) => (
    <select data-testid="currency-selector" onChange={e => props.onValueChange?.(e.target.value)}>
      <option value="USD">USD</option>
    </select>
  ),
}));
vi.mock('@/components/settings/LanguageSelector', () => ({
  LanguageSelector: () => <div data-testid="language-selector" />,
}));
vi.mock('@/components/common/CountrySelector', () => ({
  CountrySelector: () => <div data-testid="country-selector" />,
}));

import OnboardingPage from './OnboardingPage';
import i18n from '@/test/i18n-test';

describe('OnboardingPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en');
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('submits the active language selected outside the form without a navigator reset', async () => {
    renderWithProviders(<OnboardingPage />);
    await act(() => i18n.changeLanguage('fr'));
    fireEvent.click(screen.getByRole('button', { name: /commencer/i }));
    await waitFor(() =>
      expect(submitOnboarding).toHaveBeenCalledWith(expect.objectContaining({ language: 'fr' }))
    );
    expect(i18n.language).toBe('fr');
  });

  it('renders the onboarding welcome header', () => {
    renderWithProviders(<OnboardingPage />);
    expect(screen.getByText('Welcome to Open Finance!')).toBeInTheDocument();
  });
});
