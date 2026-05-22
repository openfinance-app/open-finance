import { useState, useEffect } from 'react';
import { NavLink, useLocation } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useSidebar } from '@/context/SidebarContext';
import {
  LayoutGrid,
  Wallet,
  Receipt,
  TrendingUp,
  CreditCard,
  Building2,
  Wrench,
  Users,
  Gift,
  User,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Menu,
  X,
  Upload,
  Calendar,
  Target,
  Calculator,
  Home,
  FolderTree,
  Sliders,
  History,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { AppLogo } from '@/components/ui/AppLogo';
import { useIsMobile } from '@/hooks/useBreakpoint';
import { useCountryToolConfig } from '@/hooks/useCountryToolConfig';
import { useHasSessionHistory } from '@/hooks/useHasSessionHistory';

interface NavItem {
  labelKey: string;
  icon: typeof LayoutGrid;
  href?: string;
  children?: NavItem[];
}

const BASE_NAV_ITEMS: NavItem[] = [
  { labelKey: 'dashboard', icon: LayoutGrid, href: '/dashboard' },
  {
    labelKey: 'institutions',
    icon: Wallet,
    href: '/institutions',
    children: [
      { labelKey: 'accounts', icon: Wallet, href: '/accounts' },
      { labelKey: 'assets', icon: TrendingUp, href: '/assets' },
      { labelKey: 'realEstate', icon: Building2, href: '/real-estate' },
      { labelKey: 'liabilities', icon: CreditCard, href: '/liabilities' },
    ],
  },
  {
    labelKey: 'transactions',
    icon: Receipt,
    href: '/transactions',
    children: [
      { labelKey: 'recurring', icon: Calendar, href: '/recurring-transactions' },
      { labelKey: 'payees', icon: User, href: '/payees' },
      { labelKey: 'categories', icon: FolderTree, href: '/categories' },
      { labelKey: 'rules', icon: Sliders, href: '/transaction-rules' },
    ],
  },
  { labelKey: 'import', icon: Upload, href: '/import' },
  { labelKey: 'budget', icon: Receipt, href: '/budget' },
  { labelKey: 'history', icon: History, href: '/history' },
  {
    labelKey: 'tools',
    icon: Wrench,
    children: [
      { labelKey: 'financialFreedom', icon: Target, href: '/tools/financial-freedom' },
      { labelKey: 'compoundInterest', icon: Calculator, href: '/tools/compound-interest' },
      { labelKey: 'loanCalculator', icon: Calculator, href: '/tools/loan-calculator' },
      { labelKey: 'earlyPayoff', icon: Calculator, href: '/tools/early-payoff' },
      { labelKey: 'buyVsRent', icon: Calculator, href: '/real-estate/tools/buy-rent' },
      { labelKey: 'propertyRental', icon: Home, href: '/real-estate/tools/rental' },
    ],
  },
  { labelKey: 'community', icon: Users, href: '/community' },
  { labelKey: 'premium', icon: Gift, href: '/premium' },
];

/**
 * Sidebar component with navigation
 * Follows Finary design:
 * - 240px width on desktop, 72px when collapsed
 * - Gold logo at top
 * - Icon + label navigation items
 * - Active state: lighter background + gold left border
 * - Collapsible with smooth transition
 * - Mobile: overlay with slide-in animation
 */
export function Sidebar() {
  const { isCollapsed, toggleCollapsed } = useSidebar();
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const isMobile = useIsMobile();
  const { t } = useTranslation('navigation');

  const sidebarWidth = isCollapsed ? 'w-[72px]' : 'w-[240px]';

  // Lock body scroll when mobile sidebar is open
  useEffect(() => {
    if (isMobile && isMobileOpen) {
      // Save original overflow style
      const originalOverflow = document.body.style.overflow;

      // Lock scroll
      document.body.style.overflow = 'hidden';

      // Restore original overflow when sidebar closes or component unmounts
      return () => {
        document.body.style.overflow = originalOverflow;
      };
    }
  }, [isMobile, isMobileOpen]);

  // Mobile overlay
  if (isMobile) {
    return (
      <>
        {/* Mobile menu button */}
        <button
          onClick={() => setIsMobileOpen(true)}
          className="fixed top-4 left-4 z-40 min-h-[44px] min-w-[44px] p-3 rounded-lg bg-surface border border-border hover:bg-surface-elevated lg:hidden flex items-center justify-center"
          aria-label={t('openMenu')}
        >
          <Menu size={20} className="text-text-primary" />
        </button>

        {/* Mobile overlay backdrop */}
        {isMobileOpen && (
          <div
            className="fixed inset-0 bg-background/80 backdrop-blur-sm z-40"
            onClick={() => setIsMobileOpen(false)}
          />
        )}

        {/* Mobile sidebar */}
        <aside
          className={cn(
            'fixed top-0 left-0 bottom-0 z-50',
            'w-[240px] bg-background border-r border-border',
            'transform transition-transform duration-200',
            isMobileOpen ? 'translate-x-0' : '-translate-x-full'
          )}
        >
          <SidebarContent
            isCollapsed={false}
            onClose={() => setIsMobileOpen(false)}
            showCloseButton
          />
        </aside>
      </>
    );
  }

  // Desktop sidebar
  return (
    <aside
      className={cn(
        'relative h-full bg-background border-r border-border',
        'transition-all duration-200',
        sidebarWidth,
        isCollapsed && 'overflow-visible'
      )}
    >
      <SidebarContent
        isCollapsed={isCollapsed}
        onToggle={toggleCollapsed}
      />
    </aside>
  );
}

interface SidebarContentProps {
  isCollapsed: boolean;
  onToggle?: () => void;
  onClose?: () => void;
  showCloseButton?: boolean;
}

function SidebarContent({ isCollapsed, onToggle, onClose, showCloseButton }: SidebarContentProps) {
  const { t } = useTranslation('navigation');
  const { pathname } = useLocation();

  const getInitialExpanded = () => {
    const expanded: Record<string, boolean> = { institutions: true, transactions: false };
    BASE_NAV_ITEMS.forEach((item) => {
      if (item.children?.some((child) => child.href && pathname.startsWith(child.href))) {
        expanded[item.labelKey] = true;
      }
    });
    return expanded;
  };

  const [expandedItems, setExpandedItems] = useState<Record<string, boolean>>(getInitialExpanded);

  useEffect(() => {
    BASE_NAV_ITEMS.forEach((item) => {
      if (item.children?.some((child) => child.href && pathname.startsWith(child.href))) {
        setExpandedItems((prev) => ({ ...prev, [item.labelKey]: true }));
      }
    });
  }, [pathname]);

  const { isPropertyRentalAvailable } = useCountryToolConfig();
  const hasSessionHistory = useHasSessionHistory();

  const navItems = BASE_NAV_ITEMS
    .filter((item) => item.labelKey !== 'history' || hasSessionHistory)
    .map((item) => {
      if (item.labelKey === 'tools' && item.children) {
        return {
          ...item,
          children: item.children.filter(
            (child) => child.labelKey !== 'propertyRental' || isPropertyRentalAvailable
          ),
        };
      }
      return item;
    });

  const toggleExpanded = (labelKey: string) => {
    setExpandedItems((prev) => ({
      ...prev,
      [labelKey]: !prev[labelKey],
    }));
  };

  const renderNavItem = (item: NavItem, isSubItem: boolean) => {
    const hasChildren = Boolean(item.children?.length);
    const label = t(item.labelKey);
    const baseClasses = cn(
      'flex items-center gap-3 px-3 rounded-lg',
      'text-text-secondary hover:text-text-primary',
      'hover:bg-surface transition-all duration-150',
      'relative group',
      'min-h-[44px]', // WCAG 2.5.5 - minimum touch target size
      isCollapsed && 'justify-center',
      isSubItem ? 'py-2' : 'py-3'
    );

    const labelClasses = cn('text-sm font-medium', isSubItem && 'text-xs');

    const content = (
      <>
        <item.icon size={20} className="shrink-0" />
        {!isCollapsed && <span className={labelClasses}>{label}</span>}

        {!isCollapsed && hasChildren && (
          <button
            type="button"
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              toggleExpanded(item.labelKey);
            }}
            className="ml-auto p-1 rounded hover:bg-surface-elevated"
            aria-label={
              expandedItems[item.labelKey]
                ? t('collapseItem', { item: label })
                : t('expandItem', { item: label })
            }
          >
            {expandedItems[item.labelKey] ? (
              <ChevronDown size={16} className="text-text-secondary" />
            ) : (
              <ChevronRight size={16} className="text-text-secondary" />
            )}
          </button>
        )}

        {/* Tooltip for collapsed state */}
        {isCollapsed && (
          <div className="absolute left-full ml-2 px-3 py-1.5 bg-surface border border-border rounded-lg shadow-lg opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50">
            <span className="text-sm text-text-primary">{label}</span>
          </div>
        )}
      </>
    );

    if (item.href) {
      return (
        <NavLink
          key={item.href}
          to={item.href}
          onClick={onClose}
          className={({ isActive }) =>
            cn(
              baseClasses,
              isActive && [
                'bg-surface text-text-primary',
                'before:absolute before:left-0 before:top-0 before:bottom-0',
                'before:w-1 before:bg-primary before:rounded-r',
              ]
            )
          }
        >
          {content}
        </NavLink>
      );
    }

    return (
      <div key={item.labelKey} className={baseClasses}>
        {content}
      </div>
    );
  };

  const renderNavItems = (items: NavItem[], isSubItem = false) =>
    items.map((item) => {
      const hasChildren = Boolean(item.children?.length);
      const isExpanded = expandedItems[item.labelKey];

      if (isCollapsed) {
        return (
          <div key={item.labelKey} className="space-y-1">
            {renderNavItem(item, isSubItem)}
            {hasChildren && renderNavItems(item.children || [], false)}
          </div>
        );
      }

      return (
        <div key={item.labelKey} className="space-y-1">
          {renderNavItem(item, isSubItem)}
          {hasChildren && isExpanded && (
            <div className="ml-4 pl-2 border-l border-border/50 space-y-1">
              {renderNavItems(item.children || [], true)}
            </div>
          )}
        </div>
      );
    });

  return (
    <div className="flex flex-col h-full py-6">
      {/* Logo */}
      <div className="flex items-center justify-between px-6 mb-8">
        <AppLogo showText={!isCollapsed} className={isCollapsed ? 'mx-auto' : ''} />

        {/* Close button (mobile) */}
        {showCloseButton && (
          <button
            onClick={onClose}
            className="min-h-[44px] min-w-[44px] p-3 rounded hover:bg-surface-elevated flex items-center justify-center"
            aria-label={t('closeMenu')}
          >
            <X size={20} className="text-text-secondary" />
          </button>
        )}
      </div>

      {/* Navigation */}
      <nav className={cn('flex-1 px-3 space-y-1 scrollbar-hide', isCollapsed ? 'overflow-visible' : 'overflow-y-auto')}>
        {renderNavItems(navItems)}
      </nav>

      {/* Collapse toggle (desktop) */}
      {onToggle && (
        <button
          onClick={onToggle}
          className="mx-3 mt-4 min-h-[44px] p-3 rounded-lg hover:bg-surface transition-colors flex items-center justify-center"
          aria-label={isCollapsed ? t('expandSidebar') : t('collapseSidebar')}
        >
          {isCollapsed ? (
            <ChevronRight size={20} className="text-text-secondary" />
          ) : (
            <div className="flex items-center gap-2 w-full">
              <ChevronLeft size={20} className="text-text-secondary" />
              <span className="text-sm text-text-secondary">{t('collapse')}</span>
            </div>
          )}
        </button>
      )}
    </div>
  );
}
