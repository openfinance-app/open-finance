import { type RefObject } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { NotificationBadge } from '@/components/alerts/NotificationBadge';
import { UserDropdownMenu } from './UserDropdownMenu';
import { GlobalSearch, type GlobalSearchHandle } from '@/components/search/GlobalSearch';
import { useVisibility } from '@/context/VisibilityContext';
import { cn } from '@/lib/utils';

interface TopBarProps {
  /** Optional ref forwarded to the GlobalSearch for programmatic focus (e.g. Ctrl+K) */
  searchRef?: RefObject<GlobalSearchHandle | null>;
}

/**
 * Top bar component
 * The Vault:
 * - 64px machined bar, engraved bottom hairline
 * - Center: Global search slot (TASK-12.4.5)
 * - Right: the vault lock (amounts privacy), notifications, user menu
 */
export function TopBar({ searchRef }: TopBarProps) {
  const { isAmountsVisible, toggleAmountsVisibility } = useVisibility();
  const { t } = useTranslation('navigation');

  return (
    <header
      className={cn(
        'sticky top-0 z-30',
        'h-16 flex items-center justify-between gap-4',
        'px-4 lg:px-6',
        'bg-background/95 backdrop-blur-sm border-b border-border',
        'shadow-[0_1px_0_0_rgb(255_255_255/0.03),0_8px_24px_-16px_rgb(0_0_0/0.6)]'
      )}
    >
      {/* Left section - Page title placeholder */}
      <div className="flex items-center gap-4 shrink-0"></div>

      {/* Center section - Global Search (TASK-12.4.5) */}
      <div className="hidden md:flex flex-1 max-w-2xl justify-center">
        <GlobalSearch ref={searchRef} />
      </div>

      {/* Right section - Actions */}
      <div className="flex items-center gap-3 shrink-0">
        {/* The vault lock — one mechanical control masks every amount */}
        <div className="hidden sm:flex items-center gap-1">
          <button
            onClick={toggleAmountsVisibility}
            className={cn(
              'p-2 rounded-lg border transition-all duration-200',
              isAmountsVisible
                ? 'border-transparent hover:bg-surface hover:border-border-strong text-text-secondary'
                : 'border-primary/40 bg-primary/15 text-primary shadow-[0_0_12px_-2px_rgb(197_162_84/0.4)] hover:bg-primary/20'
            )}
            aria-label={isAmountsVisible ? t('hideAmounts') : t('showAmounts')}
            aria-pressed={!isAmountsVisible}
            title={isAmountsVisible ? t('hideAmounts') : t('showAmounts')}
          >
            {isAmountsVisible ? <Eye size={20} /> : <EyeOff size={20} />}
          </button>

          <NotificationBadge />
        </div>

        {/* User dropdown menu (Task 4.3.13) */}
        <UserDropdownMenu />
      </div>
    </header>
  );
}
