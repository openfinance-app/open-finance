import { type ReactNode, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { FloatingAIChat } from '@/components/ai/FloatingAIChat';
import { STORAGE_KEYS } from '@/constants/storage';
import { useIsMobile } from '@/hooks/useBreakpoint';
import { useUserSettings } from '@/hooks/useUserSettings';
import { useLocale } from '@/context/LocaleContext';
import { SidebarProvider } from '@/context/SidebarContext';
import { useKeyboardShortcuts } from '@/hooks/useKeyboardShortcuts';
import type { GlobalSearchHandle } from '@/components/search/GlobalSearch';
import { cn } from '@/lib/utils';
import { useTranslation } from 'react-i18next';

export interface AppLayoutProps {
  children: ReactNode;
}

/**
 * Inner shell — rendered inside SidebarProvider so hooks can access the context.
 */
function AppLayoutInner({ children }: AppLayoutProps) {
  const isMobile = useIsMobile();
  const location = useLocation();
  const { data: settings, isLoading: settingsLoading } = useUserSettings();
  const { locale, setLocale } = useLocale();
  const { t, i18n } = useTranslation();
  const [settingsReady, setSettingsReady] = useState(false);
  const hasSyncedRef = useRef(false);
  const searchRef = useRef<GlobalSearchHandle>(null);

  // Load user's locale preference after login/mount
  useEffect(() => {
    if (!settingsLoading && !hasSyncedRef.current) {
      hasSyncedRef.current = true;
      const pendingSync = sessionStorage.getItem(STORAGE_KEYS.PENDING_LANGUAGE_SYNC);
      let applying: Promise<unknown> = Promise.resolve();
      if (pendingSync && pendingSync !== settings?.language) {
        // User changed language on the login page before authenticating
        // We should push this new preference to the backend instead of reverting
        applying = setLocale(pendingSync);
      } else if (settings?.language && settings.language !== locale) {
        // Normal flow: use the backend setting
        applying = i18n.changeLanguage(settings.language);
      }
      void applying.finally(() => setSettingsReady(true));
    }
  }, [settings, settingsLoading, locale, setLocale, i18n]);

  // Global keyboard shortcuts (Ctrl/Cmd+K, Ctrl/Cmd+B, 1-9)
  useKeyboardShortcuts({
    onFocusSearch: () => searchRef.current?.focus(),
  });

  if (!settingsReady) {
    return (
      <div role="status" className="p-8 text-text-secondary">
        {t('loading')}
      </div>
    );
  }

  return (
    <div className="flex h-screen w-full bg-background overflow-hidden">
      <Sidebar />

      {/* Main content area */}
      <div className="flex flex-col flex-1 min-w-0">
        <TopBar searchRef={searchRef} />

        {/* Scrollable content */}
        <main
          className={cn(
            'flex-1 overflow-y-auto overflow-x-hidden',
            'bg-background',
            isMobile ? 'p-4' : 'p-6 lg:p-8'
          )}
        >
          <div className="mx-auto max-w-7xl">
            {/* Keyed by pathname so each route re-runs the entrance animation */}
            <div key={location.pathname} className="page-enter">
              {children}
            </div>
          </div>
        </main>
      </div>

      {/* Floating AI chat widget — available globally on all authenticated pages */}
      <FloatingAIChat />
    </div>
  );
}

/**
 * Main application layout with sidebar and top bar
 * - Fixed sidebar on left (240px width, collapsible to 72px)
 * - Fixed top bar (64px height)
 * - Scrollable main content area
 * - Responsive: mobile shows overlay sidebar
 * - Loads and applies user's locale preference on mount
 * - Registers global keyboard shortcuts (Ctrl+K, Ctrl+B, 1-9)
 */
export function AppLayout({ children }: AppLayoutProps) {
  return (
    <SidebarProvider>
      <AppLayoutInner>{children}</AppLayoutInner>
    </SidebarProvider>
  );
}
