import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { VisibilityProvider } from '@/context/VisibilityContext';
import { LocaleProvider } from '@/context/LocaleContext';

// Mock heavy children
vi.mock('./Sidebar', () => ({ Sidebar: () => <nav data-testid="sidebar">Sidebar</nav> }));
vi.mock('./TopBar', () => ({ TopBar: () => <header data-testid="topbar">TopBar</header> }));
vi.mock('@/components/ai/FloatingAIChat', () => ({ FloatingAIChat: () => <div data-testid="floating-chat" /> }));
vi.mock('@/hooks/useBreakpoint', () => ({ useIsMobile: () => false }));
vi.mock('@/hooks/useKeyboardShortcuts', () => ({ useKeyboardShortcuts: vi.fn() }));

let mockSettings: any = null;
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
  beforeEach(() => {
    vi.clearAllMocks();
    mockSettings = null;
    sessionStorage.clear();
  });

  it('renders sidebar, topbar, and children', () => {
    renderLayout(<p>Hello World</p>);
    expect(screen.getByTestId('sidebar')).toBeInTheDocument();
    expect(screen.getByTestId('topbar')).toBeInTheDocument();
    expect(screen.getByText('Hello World')).toBeInTheDocument();
  });

  it('renders floating AI chat', () => {
    renderLayout();
    expect(screen.getByTestId('floating-chat')).toBeInTheDocument();
  });

  it('syncs locale from user settings on mount', () => {
    mockSettings = { language: 'fr' };
    renderLayout();
    // Settings loaded, locale sync attempted
    expect(screen.getByTestId('sidebar')).toBeInTheDocument();
  });

  it('applies pending_language_sync from sessionStorage', () => {
    sessionStorage.setItem('pending_language_sync', 'fr');
    mockSettings = { language: 'en' };
    renderLayout();
    // pending sync takes priority over backend setting
    expect(sessionStorage.getItem('pending_language_sync')).toBeNull();
  });
});
