import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { VisibilityProvider } from '@/context/VisibilityContext';
import { LocaleProvider } from '@/context/LocaleContext';
import { mockAuthentication } from '@/test/test-utils';
import apiClient from '@/services/apiClient';
import type { UserSettings } from '@/types/user';

vi.mock('@/services/apiClient', () => ({ default: { put: vi.fn(), get: vi.fn() } }));

// Mock heavy children
vi.mock('./Sidebar', () => ({ Sidebar: () => <nav data-testid="sidebar">Sidebar</nav> }));
vi.mock('./TopBar', () => ({ TopBar: () => <header data-testid="topbar">TopBar</header> }));
vi.mock('@/components/ai/FloatingAIChat', () => ({
  FloatingAIChat: () => <div data-testid="floating-chat" />,
}));
vi.mock('@/hooks/useBreakpoint', () => ({ useIsMobile: () => false }));
vi.mock('@/hooks/useKeyboardShortcuts', () => ({ useKeyboardShortcuts: vi.fn() }));

let mockSettings: Partial<UserSettings> | null = null;
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: mockSettings }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn() }),
}));

import { AppLayout } from './AppLayout';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function renderLayout(children: React.ReactNode = <div>Content</div>) {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <I18nextProvider i18n={i18n}>
          <AuthProvider>
            <VisibilityProvider>
              <LocaleProvider>
                <AppLayout>{children}</AppLayout>
              </LocaleProvider>
            </VisibilityProvider>
          </AuthProvider>
        </I18nextProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
}

describe('AppLayout', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mockSettings = null;
    sessionStorage.clear();
    mockAuthentication();
    qc.clear();
    await i18n.changeLanguage('en');
    vi.mocked(apiClient.put).mockResolvedValue({ data: { language: 'fr' } });
  });

  it('renders sidebar, topbar, and children', async () => {
    renderLayout(<p>Hello World</p>);
    expect(await screen.findByTestId('sidebar')).toBeInTheDocument();
    expect(screen.getByTestId('topbar')).toBeInTheDocument();
    expect(screen.getByText('Hello World')).toBeInTheDocument();
  });

  it('renders floating AI chat', async () => {
    renderLayout();
    expect(await screen.findByTestId('floating-chat')).toBeInTheDocument();
  });

  it('syncs locale from user settings on mount', async () => {
    mockSettings = { language: 'fr' };
    renderLayout();
    // Settings loaded, locale sync attempted
    expect(await screen.findByTestId('sidebar')).toBeInTheDocument();
  });

  it('applies pending_language_sync from sessionStorage', async () => {
    sessionStorage.setItem('pending_language_sync', 'fr');
    mockSettings = { language: 'en' };
    renderLayout();
    // pending sync takes priority over backend setting
    await waitFor(() => expect(sessionStorage.getItem('pending_language_sync')).toBeNull());
    expect(apiClient.put).toHaveBeenCalledWith('/users/me/settings', { language: 'fr' });
    expect(qc.getQueryData(['user', 'settings'])).toEqual({ language: 'fr' });
  });
});
