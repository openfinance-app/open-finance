/**
 * Test Utilities
 * 
 * Common helpers and wrappers for testing React components
 */

import type { ReactElement, ReactNode } from 'react';
import { render } from '@testing-library/react';
import type { RenderOptions } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router';
import { I18nextProvider } from 'react-i18next';
import i18n from './i18n-test';
import { AuthProvider } from '@/context/AuthContext';
import { VisibilityProvider } from '@/context/VisibilityContext';
import { CurrencyDisplayProvider } from '@/context/CurrencyDisplayContext';
import { NumberFormatProvider } from '@/context/NumberFormatContext';

/**
 * Create a new QueryClient for each test to ensure test isolation
 */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false, // Disable retries in tests
        gcTime: Infinity, // Keep cache during tests
      },
      mutations: {
        retry: false,
      },
    },
  });
}

/**
 * Wrapper component that provides all necessary context providers
 */
interface AllProvidersProps {
  children: ReactNode;
  queryClient?: QueryClient;
}

export function AllProviders({ children, queryClient }: AllProvidersProps) {
  const client = queryClient || createTestQueryClient();

  return (
    <QueryClientProvider client={client}>
      <I18nextProvider i18n={i18n}>
        <BrowserRouter>
          <AuthProvider>
            <NumberFormatProvider>
              <CurrencyDisplayProvider>
                <VisibilityProvider>{children}</VisibilityProvider>
              </CurrencyDisplayProvider>
            </NumberFormatProvider>
          </AuthProvider>
        </BrowserRouter>
      </I18nextProvider>
    </QueryClientProvider>
  );
}

/**
 * Custom render function that wraps components with all providers
 * 
 * @example
 * renderWithProviders(<MyComponent />);
 * 
 * @example
 * const queryClient = createTestQueryClient();
 * renderWithProviders(<MyComponent />, { queryClient });
 */
interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  queryClient?: QueryClient;
}

export function renderWithProviders(
  ui: ReactElement,
  options?: CustomRenderOptions
) {
  const { queryClient, ...renderOptions } = options || {};

  return render(ui, {
    wrapper: ({ children }) => (
      <AllProviders queryClient={queryClient}>{children}</AllProviders>
    ),
    ...renderOptions,
  });
}

/**
 * Simulate user authentication by setting localStorage and sessionStorage items
 */
export function mockAuthentication() {
  const mockToken = 'mock-jwt-token-12345';
  const mockEncryptionKey = 'mock-encryption-key-67890';
  const mockUser = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
  };

  // Auth token and user in localStorage (persistent)
  localStorage.setItem('auth_token', mockToken);
  localStorage.setItem('auth_user', JSON.stringify(mockUser));

  // Encryption key in sessionStorage (per-session)
  sessionStorage.setItem('encryption_session', mockEncryptionKey);

  return { mockToken, mockEncryptionKey, mockUser };
}

/**
 * Clear authentication from localStorage and sessionStorage
 */
export function clearAuthentication() {
  localStorage.removeItem('auth_token');
  localStorage.removeItem('auth_user');
  sessionStorage.removeItem('encryption_session');
}

/**
 * Wait for async operations to complete
 */
export const waitFor = (ms: number) =>
  new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Re-export everything from @testing-library/react
 */
export * from '@testing-library/react';
export { userEvent } from '@testing-library/user-event';
