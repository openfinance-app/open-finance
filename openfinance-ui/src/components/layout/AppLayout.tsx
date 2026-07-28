import { type ReactNode, useEffect, useRef } from 'react';
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

export interface AppLayoutProps {
  children: ReactNode;
}

/**
 * Inner shell — rendered inside SidebarProvider so hooks can access the context.
 */
function AppLayoutInner({ children }: AppLayoutProps) {
  const isMobile = useIsMobile();
  const { data: settings } = useUserSettings();
  const { locale, setLocale } = useLocale();
  const hasSyncedRef = useRef(false);
  const searchRef = useRef<GlobalSearchHandle>(null);

  // Load user's locale preference after login/mount
  useEffect(() => {
    if (settings && !hasSyncedRef.current) {
      const pendingSync = sessionStorage.getItem(STORAGE_KEYS.PENDING_LANGUAGE_SYNC);
      
      if (pendingSync && pendingSync !== settings.language) {
        // User changed language on the login page before authenticating
        // We should push this new preference to the backend instead of reverting
        void setLocale(pendingSync);
        sessionStorage.removeItem(STORAGE_KEYS.PENDING_LANGUAGE_SYNC);
      } else if (settings.language && settings.language !== locale) {
        // Normal flow: use the backend setting
        void setLocale(settings.language);
      }
      
      hasSyncedRef.current = true;
    }
  }, [settings, locale, setLocale]);

  // Global keyboard shortcuts (Ctrl/Cmd+K, Ctrl/Cmd+B, 1-9)
  useKeyboardShortcuts({
    onFocusSearch: () => searchRef.current?.focus(),
  });

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
            {children}
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

