import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useAuth', () => ({
  useCompleteOnboarding: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: (props: any) => (
    <select data-testid="currency-selector" onChange={(e) => props.onValueChange?.(e.target.value)}>
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

describe('OnboardingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders the onboarding welcome header', () => {
    renderWithProviders(<OnboardingPage />);
    expect(screen.getByText('Welcome to Open Finance!')).toBeInTheDocument();
  });
});
