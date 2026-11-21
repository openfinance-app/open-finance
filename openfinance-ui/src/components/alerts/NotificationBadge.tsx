import { useState, useRef, useEffect, forwardRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Bell, X, ExternalLink, RefreshCw } from 'lucide-react';
import {
  useUnreadAlertCount,
  useUnreadAlerts,
  useMarkAlertAsRead,
  useMarkAllAlertsAsRead,
} from '@/hooks/useAlerts';
import { Button } from '@/components/ui/Button';
import {
  useNotifications,
  useNotificationCount,
  useMarkNotificationAsRead,
  useUpdateExchangeRatesFromNotification,
} from '@/hooks/useNotifications';
import { getAlertSeverity, getAlertColor, type BudgetAlert } from '@/types/alert';
import {
  getNotificationColor,
  getNotificationIcon,
  type INotification,
  type NotificationType,
} from '@/types/notification';
import { useNavigate } from 'react-router';

/**
 * NotificationBadge Component
 *
 * Displays system notifications including:
 * - Budget alerts        → /budget?alertKeyword=<budgetName>
 * - Stale asset quotes   → /assets
 * - Stale exchange rates → inline "Update Now" action (POST /notifications/actions/update-exchange-rates)
 * - Uncategorized txns   → /transactions?noCategory=1
 * - Transactions w/o payee → /transactions?noPayee=1
 * - Low account balances → /accounts?lowBalance=1
 */

/** Notifications with inline actions that should NOT navigate away. */
const INLINE_ACTION_TYPES: NotificationType[] = ['STALE_EXCHANGE_RATES'];

/** Build the correct deep-link URL for each system notification type. */
function resolveActionUrl(type: NotificationType, fallbackUrl: string): string {
  switch (type) {
    case 'BUDGET_ALERT':
      return '/budget';
    case 'STALE_QUOTES':
      return '/assets';
    case 'UNCATEGORIZED_TRANSACTIONS':
      return '/transactions?noCategory=1';
    case 'UNLINKED_PAYEE':
      return '/transactions?noPayee=1';
    case 'LOW_BALANCE':
      return '/accounts?lowBalance=1';
    default:
      return fallbackUrl;
  }
}

export function NotificationBadge() {
  const { t } = useTranslation('common');
  const { data: budgetAlertCount = 0 } = useUnreadAlertCount();
  const { data: systemNotificationCount = 0 } = useNotificationCount();
  const totalCount = budgetAlertCount + systemNotificationCount;
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node) &&
        !buttonRef.current?.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    }

    // Close dropdown on Escape key
    function handleEscapeKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false);
        buttonRef.current?.focus();
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleEscapeKey);
      return () => {
        document.removeEventListener('mousedown', handleClickOutside);
        document.removeEventListener('keydown', handleEscapeKey);
      };
    }
  }, [isOpen]);

  const displayCount = totalCount > 99 ? '99+' : totalCount.toString();

  return (
    <div className="relative">
      {/* Bell Icon Button */}
      <button
        ref={buttonRef}
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 rounded-lg hover:bg-surface-elevated transition-colors"
        aria-label={t('notifications.unread', { count: totalCount })}
        aria-expanded={isOpen}
      >
        <Bell className="w-5 h-5 text-text-secondary" />
        {totalCount > 0 && (
          <span className="absolute top-1 right-1 flex items-center justify-center w-5 h-5 text-xs font-bold text-white bg-red-500 rounded-full">
            {displayCount}
          </span>
        )}
      </button>

      {/* Dropdown */}
      {isOpen && <NotificationDropdown onClose={() => setIsOpen(false)} ref={dropdownRef} />}
    </div>
  );
}

interface NotificationDropdownProps {
  onClose: () => void;
}

const NotificationDropdown = forwardRef<HTMLDivElement, NotificationDropdownProps>(
  ({ onClose }, ref) => {
    const { data: budgetAlerts, isLoading: budgetAlertsLoading } = useUnreadAlerts();
    const {
      data: systemNotifications,
      isLoading: systemNotificationsLoading,
      dismiss,
      dismissAll,
    } = useNotifications();
    const markAllAsRead = useMarkAllAlertsAsRead();
    const closeButtonRef = useRef<HTMLButtonElement>(null);
    const listRef = useRef<HTMLDivElement>(null);
    const markNotificationAsRead = useMarkNotificationAsRead();
    const updateExchangeRates = useUpdateExchangeRatesFromNotification();
    const navigate = useNavigate();

    const isLoading = budgetAlertsLoading || systemNotificationsLoading;

    const { t } = useTranslation('common');

    // Show last 5 unread budget alerts
    const visibleBudgetAlerts = budgetAlerts?.slice(0, 5) || [];
    const visibleSystemNotifications = systemNotifications || [];

    const hasNotifications =
      visibleBudgetAlerts.length > 0 || visibleSystemNotifications.length > 0;

    const handleClearAll = () => {
      if (visibleSystemNotifications.length) dismissAll();
      if (budgetAlerts?.length) markAllAsRead.mutate();
      closeButtonRef.current?.focus();
    };

    const focusNextDismiss = (button: HTMLButtonElement) => {
      const buttons = Array.from(
        listRef.current?.querySelectorAll<HTMLButtonElement>(
          '[data-dismiss-notification]:enabled'
        ) ?? []
      );
      const index = buttons.indexOf(button);
      const next = buttons[index + 1] ?? buttons[index - 1] ?? closeButtonRef.current;
      next?.focus();
    };

    const handleSystemNotificationClick = (notification: INotification) => {
      markNotificationAsRead(notification);
      // Notifications with inline actions don't navigate
      if (INLINE_ACTION_TYPES.includes(notification.type)) return;
      navigate(resolveActionUrl(notification.type, notification.actionUrl));
      onClose();
    };

    const handleExchangeRateUpdate = (e: React.MouseEvent, notification: INotification) => {
      markNotificationAsRead(notification);
      e.stopPropagation();
      updateExchangeRates.mutate();
    };

    const handleBudgetAlertClick = (alert: BudgetAlert) => {
      navigate(`/budget?open=${alert.budgetId}`);
      onClose();
    };

    return (
      <div
        ref={ref}
        role="region"
        aria-label={t('notifications.title')}
        className="fixed left-4 right-4 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-auto mt-2 sm:w-96 bg-surface rounded-lg shadow-xl border border-border overflow-hidden z-50 animate-fade-in"
      >
        {/* Header */}
        <div className="flex items-center justify-between gap-2 pl-4 pr-2 py-1.5 border-b border-border">
          <h3 className="text-sm font-semibold text-text-primary">{t('notifications.title')}</h3>
          <div className="flex items-center gap-1">
            {!isLoading && hasNotifications && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-xs text-text-secondary hover:bg-surface-elevated hover:text-text-primary"
                onClick={handleClearAll}
                isLoading={markAllAsRead.isPending}
              >
                {t('notifications.clearAll')}
              </Button>
            )}
            <Button
              ref={closeButtonRef}
              type="button"
              variant="ghost"
              size="icon"
              onClick={onClose}
              className="md:h-8 md:w-8 text-text-secondary hover:bg-surface-elevated hover:text-text-primary"
              aria-label={t('notifications.close')}
            >
              <X className="w-4 h-4" />
            </Button>
          </div>
        </div>

        {markAllAsRead.isError && !!budgetAlerts?.length && (
          <p role="alert" className="px-4 py-2 text-sm text-error">
            {t('notifications.clearError')}
          </p>
        )}

        {/* Notification List */}
        <div className="max-h-96 overflow-y-auto">
          {isLoading ? (
            <div className="px-4 py-8 text-center text-text-secondary">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto" />
              <p className="mt-2 text-sm">{t('notifications.loading')}</p>
            </div>
          ) : !hasNotifications ? (
            <div className="px-4 py-8 text-center text-text-secondary">
              <Bell className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p role="status" className="text-sm">
                {t('notifications.empty')}
              </p>
              <p className="text-xs mt-1">{t('notifications.emptySub')}</p>
            </div>
          ) : (
            <div ref={listRef} role="list" className="divide-y divide-border">
              {/* System Notifications */}
              {visibleSystemNotifications.map(notification => (
                <NotificationRow
                  key={notification.type}
                  dismissLabel={t('notifications.dismiss', { name: notification.title })}
                  onDismiss={() => dismiss(notification)}
                  focusNextDismiss={focusNextDismiss}
                >
                  {notification.type === 'STALE_EXCHANGE_RATES' ? (
                    <StaleExchangeRatesNotificationItem
                      notification={notification}
                      onUpdate={event => handleExchangeRateUpdate(event, notification)}
                      isUpdating={updateExchangeRates.isPending}
                      isSuccess={updateExchangeRates.isSuccess}
                    />
                  ) : (
                    <SystemNotificationItem
                      notification={notification}
                      onClick={() => handleSystemNotificationClick(notification)}
                    />
                  )}
                </NotificationRow>
              ))}

              {/* Budget Alerts */}
              {visibleBudgetAlerts.map((alert: BudgetAlert) => (
                <BudgetAlertItem
                  key={alert.id}
                  alert={alert}
                  onNavigate={() => handleBudgetAlertClick(alert)}
                  focusNextDismiss={focusNextDismiss}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    );
  }
);

NotificationDropdown.displayName = 'NotificationDropdown';

interface NotificationRowProps {
  children: React.ReactNode;
  dismissLabel: string;
  onDismiss: () => void;
  focusNextDismiss: (button: HTMLButtonElement) => void;
  isPending?: boolean;
}

function NotificationRow({
  children,
  dismissLabel,
  onDismiss,
  focusNextDismiss,
  isPending,
}: NotificationRowProps) {
  return (
    <div role="listitem" className="relative">
      {children}
      <Button
        type="button"
        variant="ghost"
        size="icon"
        data-dismiss-notification
        className="absolute right-1 top-0.5 md:right-2 md:top-1.5 md:h-7 md:w-7 text-text-tertiary hover:bg-surface-elevated hover:text-text-primary focus-visible:text-text-primary"
        aria-label={dismissLabel}
        title={dismissLabel}
        disabled={isPending}
        onClick={event => {
          focusNextDismiss(event.currentTarget);
          onDismiss();
        }}
      >
        <X aria-hidden="true" className="size-3.5" />
      </Button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// SystemNotificationItem
// ---------------------------------------------------------------------------

interface SystemNotificationItemProps {
  notification: INotification;
  onClick: () => void;
}

function SystemNotificationItem({ notification, onClick }: SystemNotificationItemProps) {
  const color = getNotificationColor(notification.severity);
  const icon = getNotificationIcon(notification.type);

  return (
    <div
      className="pl-4 pr-12 md:pr-11 py-3 hover:bg-background transition-colors cursor-pointer"
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={event => {
        if (event.target === event.currentTarget && (event.key === 'Enter' || event.key === ' ')) {
          event.preventDefault();
          onClick();
        }
      }}
      aria-label={notification.title}
    >
      <div className="flex items-start gap-3">
        {/* Icon */}
        <div className="text-2xl flex-shrink-0">{icon}</div>

        {/* Notification Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2 mb-1">
            <span className="text-sm font-medium text-text-primary truncate">
              {notification.title}
            </span>
            {notification.count > 0 && (
              <span
                className="text-xs font-bold px-2 py-0.5 rounded flex-shrink-0"
                style={{ backgroundColor: color + '20', color }}
              >
                {notification.count}
              </span>
            )}
          </div>
          <p className="text-xs text-text-secondary line-clamp-2 mb-2">{notification.message}</p>
          <span className="inline-flex items-center gap-1 text-xs text-primary">
            {notification.actionLabel}
            <ExternalLink className="w-3 h-3" />
          </span>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// StaleExchangeRatesNotificationItem — inline "Update Now" action
// ---------------------------------------------------------------------------

interface StaleExchangeRatesNotificationItemProps {
  notification: INotification;
  onUpdate: (e: React.MouseEvent) => void;
  isUpdating: boolean;
  isSuccess: boolean;
}

function StaleExchangeRatesNotificationItem({
  notification,
  onUpdate,
  isUpdating,
  isSuccess,
}: StaleExchangeRatesNotificationItemProps) {
  const color = getNotificationColor(notification.severity);
  const icon = getNotificationIcon(notification.type);
  const { t } = useTranslation('common');

  return (
    <div className="pl-4 pr-12 md:pr-11 py-3 transition-colors" aria-label={notification.title}>
      <div className="flex items-start gap-3">
        {/* Icon */}
        <div className="text-2xl flex-shrink-0">{icon}</div>

        {/* Notification Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2 mb-1">
            <span className="text-sm font-medium text-text-primary truncate">
              {notification.title}
            </span>
            {notification.count > 0 && (
              <span
                className="text-xs font-bold px-2 py-0.5 rounded flex-shrink-0"
                style={{ backgroundColor: color + '20', color }}
              >
                {t('notifications.daysAgo', { count: notification.count })}
              </span>
            )}
          </div>
          <p className="text-xs text-text-secondary line-clamp-2 mb-2">
            {isSuccess ? t('notifications.ratesUpdated') : notification.message}
          </p>

          {/* Inline action button */}
          {!isSuccess && (
            <button
              onClick={onUpdate}
              disabled={isUpdating}
              className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded transition-colors disabled:opacity-50"
              style={{
                backgroundColor: color + '20',
                color,
              }}
              aria-label={t('notifications.updateRates')}
            >
              <RefreshCw className={`w-3 h-3 ${isUpdating ? 'animate-spin' : ''}`} />
              {isUpdating ? t('notifications.updating') : notification.actionLabel}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// BudgetAlertItem
// ---------------------------------------------------------------------------

interface BudgetAlertItemProps {
  alert: BudgetAlert;
  onNavigate: () => void;
  focusNextDismiss: (button: HTMLButtonElement) => void;
}

function BudgetAlertItem({ alert, onNavigate, focusNextDismiss }: BudgetAlertItemProps) {
  const severity = getAlertSeverity(alert.threshold, alert.currentSpentPercentage);
  const severityColor = getAlertColor(severity);
  const { t } = useTranslation('common');
  const markRead = useMarkAlertAsRead();
  const dismiss = () => markRead.mutate(alert.id);
  const openBudget = () => {
    dismiss();
    onNavigate();
  };

  return (
    <NotificationRow
      dismissLabel={t('notifications.dismissBudgetAlert', {
        name: alert.budgetName,
        threshold: alert.threshold,
      })}
      onDismiss={dismiss}
      focusNextDismiss={focusNextDismiss}
      isPending={markRead.isPending}
    >
      <div
        className="pl-4 pr-12 md:pr-11 py-3 hover:bg-background transition-colors cursor-pointer"
        onClick={openBudget}
        role="button"
        tabIndex={0}
        onKeyDown={event => {
          if (
            event.target === event.currentTarget &&
            (event.key === 'Enter' || event.key === ' ')
          ) {
            event.preventDefault();
            openBudget();
          }
        }}
        aria-label={t('notifications.budgetAlert', { name: alert.budgetName })}
      >
        <div className="flex items-start gap-3">
          {/* Severity Indicator */}
          <div
            className="w-2 h-2 rounded-full flex-shrink-0 mt-1.5"
            style={{ backgroundColor: severityColor }}
          />

          {/* Alert Content */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between gap-2 mb-1">
              <span className="text-sm font-medium text-text-primary truncate">
                {alert.budgetName}
              </span>
              <span
                className="text-xs font-bold px-2 py-0.5 rounded flex-shrink-0"
                style={{ backgroundColor: severityColor + '20', color: severityColor }}
              >
                {alert.currentSpentPercentage?.toFixed(0)}%
              </span>
            </div>
            <p className="text-xs text-text-secondary line-clamp-2">
              {alert.message || t('notifications.budgetThreshold', { threshold: alert.threshold })}
            </p>

            {/* Actions row */}
            <div className="mt-2 flex items-center gap-3">
              <span className="inline-flex items-center gap-1 text-xs text-primary">
                {t('notifications.viewBudget')}
                <ExternalLink className="w-3 h-3" />
              </span>
            </div>
          </div>
        </div>
      </div>
      {markRead.isError && (
        <p role="alert" className="px-4 pb-3 text-xs text-error">
          {t('notifications.dismissError')}
        </p>
      )}
    </NotificationRow>
  );
}
