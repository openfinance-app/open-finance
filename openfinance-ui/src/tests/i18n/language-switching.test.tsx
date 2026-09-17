/**
 * Language Switching Test
 *
 * Verifies that UI text updates correctly when the user changes their language preference.
 * Tests the integration between LocaleContext, i18next, and React components.
 *
 * Note: Radix UI Select dropdown doesn't render in jsdom test environment,
 * so we test locale changes programmatically via context instead of UI interactions.
 *
 * Related: .spec/i18n-localization/tasks.md (Task 4.1.5)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { I18nextProvider, useTranslation } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { LocaleProvider, useLocale } from '@/context/LocaleContext';
import { LanguageSelector } from '@/components/settings/LanguageSelector';

// Mock apiClient to prevent actual API calls during tests
vi.mock('@/services/apiClient', () => ({
  default: {
    patch: vi.fn().mockResolvedValue({}),
  },
}));

/**
 * Test component that displays translated text
 * Used to verify that translations update when locale changes
 */
function TestTranslatedComponent() {
  const { t } = useTranslation('navigation');
  const { locale } = useLocale();

  return (
    <div>
      <div data-testid="current-locale">{locale}</div>
      <div data-testid="translated-text">{t('dashboard')}</div>
      <div data-testid="assets-text">{t('assets')}</div>
    </div>
  );
}

/**
 * Test component with a button to trigger locale change programmatically
 * (avoids Radix UI Select interaction which doesn't work in jsdom)
 */
function TestLocaleChanger({ targetLocale }: { targetLocale: string }) {
  const { setLocale } = useLocale();

  return (
    <button data-testid="change-locale-button" onClick={() => setLocale(targetLocale)}>
      Change to {targetLocale}
    </button>
  );
}

/**
 * Wrapper that provides all necessary i18n contexts
 */
function I18nTestWrapper({ children }: { children: React.ReactNode }) {
  return (
    <I18nextProvider i18n={i18n}>
      <LocaleProvider>{children}</LocaleProvider>
    </I18nextProvider>
  );
}

describe('Language Switching', () => {
  beforeEach(async () => {
    // Reset i18n to English before each test
    await i18n.changeLanguage('en');
  });

  describe('LanguageSelector Component', () => {
    it('should render language selector with current language', async () => {
      render(<LanguageSelector />, { wrapper: I18nTestWrapper });

      // Should show English label by default
      await waitFor(() => {
        expect(screen.getByText('English')).toBeInTheDocument();
      });
    });
  });

  describe('LocaleContext Integration', () => {
    it('should change locale when setLocale is called', async () => {
      render(
        <>
          <TestLocaleChanger targetLocale="fr" />
          <TestTranslatedComponent />
        </>,
        { wrapper: I18nTestWrapper }
      );

      // Initial state: English
      await waitFor(() => {
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Dashboard');
      });

      // Click button to change locale
      const changeButton = screen.getByTestId('change-locale-button');
      fireEvent.click(changeButton);

      // Verify locale changed
      await waitFor(() => {
        expect(screen.getByTestId('current-locale')).toHaveTextContent('fr');
      });

      // Verify translations updated to French
      await waitFor(() => {
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Tableau de bord');
      });
    });

    it('should update html lang attribute when locale changes', async () => {
      render(<TestLocaleChanger targetLocale="fr" />, { wrapper: I18nTestWrapper });

      // Initial state
      expect(document.documentElement.lang).toBe('en');

      // Change to French
      const changeButton = screen.getByTestId('change-locale-button');
      changeButton.click();

      // Verify HTML lang updated
      await waitFor(() => {
        expect(document.documentElement.lang).toBe('fr');
      });
    });
  });

  describe('UI Text Updates', () => {
    it('should update multiple translated strings when locale changes', async () => {
      render(
        <>
          <TestLocaleChanger targetLocale="fr" />
          <TestTranslatedComponent />
        </>,
        { wrapper: I18nTestWrapper }
      );

      // Initial English state
      await waitFor(() => {
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Dashboard');
        expect(screen.getByTestId('assets-text')).toHaveTextContent('Assets');
      });

      // Change to French
      const changeButton = screen.getByTestId('change-locale-button');
      fireEvent.click(changeButton);

      // Verify all translations updated
      await waitFor(() => {
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Tableau de bord');
        expect(screen.getByTestId('assets-text')).toHaveTextContent('Actifs');
      });
    });

    it('should switch back from French to English', async () => {
      // Start in French
      await i18n.changeLanguage('fr');

      render(
        <>
          <TestLocaleChanger targetLocale="en" />
          <TestTranslatedComponent />
        </>,
        { wrapper: I18nTestWrapper }
      );

      // Initial French state
      await waitFor(() => {
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Tableau de bord');
      });

      // Change to English
      const changeButton = screen.getByTestId('change-locale-button');
      fireEvent.click(changeButton);

      // Verify switched back to English
      await waitFor(() => {
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Dashboard');
        expect(screen.getByTestId('current-locale')).toHaveTextContent('en');
      });
    });
  });

  describe('Locale Persistence', () => {
    it('should maintain locale across component remounts', async () => {
      // Change to French
      await i18n.changeLanguage('fr');

      const { unmount } = render(<TestTranslatedComponent />, { wrapper: I18nTestWrapper });

      // Verify French
      await waitFor(() => {
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Tableau de bord');
      });

      // Unmount and remount
      unmount();

      render(<TestTranslatedComponent />, { wrapper: I18nTestWrapper });

      // Locale should persist
      await waitFor(() => {
        expect(screen.getByTestId('current-locale')).toHaveTextContent('fr');
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Tableau de bord');
      });
    });
  });

  describe('Translation Namespaces', () => {
    it('should load translations from multiple namespaces correctly', async () => {
      function MultiNamespaceComponent() {
        const { t: tNav } = useTranslation('navigation');
        const { t: tCommon } = useTranslation('common');
        const { t: tAuth } = useTranslation('auth');

        return (
          <div>
            <div data-testid="nav-text">{tNav('dashboard')}</div>
            <div data-testid="common-text">{tCommon('save')}</div>
            <div data-testid="auth-text">{tAuth('login.title')}</div>
          </div>
        );
      }

      render(
        <>
          <TestLocaleChanger targetLocale="fr" />
          <MultiNamespaceComponent />
        </>,
        { wrapper: I18nTestWrapper }
      );

      // Initial English
      await waitFor(() => {
        expect(screen.getByTestId('nav-text')).toHaveTextContent('Dashboard');
        expect(screen.getByTestId('common-text')).toHaveTextContent('Save');
        expect(screen.getByTestId('auth-text')).toHaveTextContent('Welcome Back');
      });

      // Switch to French
      const changeButton = screen.getByTestId('change-locale-button');
      fireEvent.click(changeButton);

      // Verify all namespaces updated
      await waitFor(() => {
        expect(screen.getByTestId('nav-text')).toHaveTextContent('Tableau de bord');
        expect(screen.getByTestId('common-text')).toHaveTextContent('Enregistrer');
        expect(screen.getByTestId('auth-text')).toHaveTextContent('Bienvenue');
      });
    });
  });

  describe('Error Handling', () => {
    it('should still change locale even if backend persistence fails', async () => {
      // Mock API failure
      const apiClient = await import('@/services/apiClient');
      vi.mocked(apiClient.default.patch).mockRejectedValueOnce(new Error('Network error'));

      render(
        <>
          <TestLocaleChanger targetLocale="fr" />
          <TestTranslatedComponent />
        </>,
        { wrapper: I18nTestWrapper }
      );

      // Change to French
      const changeButton = screen.getByTestId('change-locale-button');
      fireEvent.click(changeButton);

      // UI should still update despite API failure
      await waitFor(() => {
        expect(screen.getByTestId('current-locale')).toHaveTextContent('fr');
        expect(screen.getByTestId('translated-text')).toHaveTextContent('Tableau de bord');
      });
    });
  });
});
