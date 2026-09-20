import { useState, useRef, useEffect, forwardRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Bell, X, ExternalLink, Check, RefreshCw } from 'lucide-react';
import { useUnreadAlertCount, useUnreadAlerts, useMarkAlertAsRead } from '@/hooks/useAlerts';
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
    const { data: systemNotifications, isLoading: systemNotificationsLoading } = useNotifications();
    const markAsRead = useMarkAlertAsRead();
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

    const handleMarkAsRead = (alertId: string) => {
      markAsRead.mutate(alertId);
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
      markAsRead.mutate(alert.id);
      const keyword = encodeURIComponent(alert.budgetName);
      navigate(`/budget?alertKeyword=${keyword}`);
      onClose();
    };

    return (
      <div
        ref={ref}
        className="absolute right-0 mt-2 w-96 bg-surface rounded-lg shadow-xl border border-border overflow-hidden z-50 animate-fade-in"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-border">
          <h3 className="text-sm font-semibold text-text-primary">{t('notifications.title')}</h3>
          <button
            onClick={onClose}
            className="text-text-secondary hover:text-text-primary transition-colors"
            aria-label={t('notifications.close')}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

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
              <p className="text-sm">{t('notifications.empty')}</p>
              <p className="text-xs mt-1">{t('notifications.emptySub')}</p>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {/* System Notifications */}
              {visibleSystemNotifications.map((notification: INotification, index: number) =>
                notification.type === 'STALE_EXCHANGE_RATES' ? (
                  <StaleExchangeRatesNotificationItem
                    key={`system-${index}`}
                    notification={notification}
                    onUpdate={event => handleExchangeRateUpdate(event, notification)}
                    isUpdating={updateExchangeRates.isPending}
                    isSuccess={updateExchangeRates.isSuccess}
                  />
                ) : (
                  <SystemNotificationItem
                    key={`system-${index}`}
                    notification={notification}
                    onClick={() => handleSystemNotificationClick(notification)}
                  />
                )
              )}

              {/* Budget Alerts */}
              {visibleBudgetAlerts.map((alert: BudgetAlert) => (
                <BudgetAlertItem
                  key={alert.id}
                  alert={alert}
                  onNavigate={() => handleBudgetAlertClick(alert)}
                  onMarkRead={() => handleMarkAsRead(alert.id)}
                  isMarkingRead={markAsRead.isPending}
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
      className="px-4 py-3 hover:bg-background transition-colors cursor-pointer"
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onClick()}
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
    <div className="px-4 py-3 transition-colors" aria-label={notification.title}>
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
  onMarkRead: () => void;
  isMarkingRead: boolean;
}

function BudgetAlertItem({ alert, onNavigate, onMarkRead, isMarkingRead }: BudgetAlertItemProps) {
  const severity = getAlertSeverity(alert.threshold, alert.currentSpentPercentage);
  const severityColor = getAlertColor(severity);
  const { t } = useTranslation('common');

  return (
    <div
      className="px-4 py-3 hover:bg-background transition-colors cursor-pointer"
      onClick={onNavigate}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onNavigate()}
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
            <button
              onClick={e => {
                // Prevent the parent click (navigate) from firing
                e.stopPropagation();
                onMarkRead();
              }}
              disabled={isMarkingRead}
              className="inline-flex items-center gap-1 text-xs text-text-secondary hover:text-text-primary transition-colors disabled:opacity-50"
              aria-label={t('notifications.markAlertAsRead')}
            >
              <Check className="w-3 h-3" />
              {t('notifications.markRead')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
