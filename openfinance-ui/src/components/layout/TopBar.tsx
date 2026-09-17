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
 * Follows Finary design:
 * - 64px height
 * - Dark background with subtle bottom border
 * - Left: page title/breadcrumb
 * - Center: Global search bar (TASK-12.4.5)
 * - Right: action badges, icon buttons, CTA, user menu (Task 4.3.13)
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
        'bg-background border-b border-border'
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
        {/* Icon buttons */}
        <div className="hidden sm:flex items-center gap-1">
          <button
            onClick={toggleAmountsVisibility}
            className={cn(
              'p-2 rounded-lg transition-all duration-200',
              isAmountsVisible
                ? 'hover:bg-surface text-text-secondary'
                : 'bg-primary/10 text-primary hover:bg-primary/20'
            )}
            aria-label={isAmountsVisible ? t('hideAmounts') : t('showAmounts')}
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
