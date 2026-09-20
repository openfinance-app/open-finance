import { useState } from 'react';
import { useUnreadAlerts, useMarkAlertAsRead, useMarkAllAlertsAsRead } from '@/hooks/useAlerts';
import { getAlertSeverity, getAlertColor, type BudgetAlert } from '@/types/alert';
import { X, AlertTriangle, AlertCircle } from 'lucide-react';

/**
 * AlertBanner Component (Task 8.3.5)
 *
 * Displays budget warnings at the top of dashboard/budget pages.
 * Features:
 * - Shows top 3 most critical unread alerts
 * - Color-coded by severity (yellow warning, orange critical, red exceeded)
 * - "Mark as read" action per alert
 * - "Mark all as read" button
 * - Dismissable alerts (local state, doesn't mark as read)
 * - Slide-in animation
 * - Responsive design
 */
export function AlertBanner() {
  const { data: alerts, isLoading } = useUnreadAlerts();
  const markAsRead = useMarkAlertAsRead();
  const markAllAsRead = useMarkAllAlertsAsRead();
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  if (isLoading) return null;
  if (!alerts || alerts.length === 0) return null;

  // Filter out dismissed alerts and show top 3 most critical
  const visibleAlerts = alerts.filter((alert: BudgetAlert) => !dismissed.has(alert.id)).slice(0, 3);

  if (visibleAlerts.length === 0) return null;

  const handleDismiss = (alertId: string) => {
    setDismissed(prev => new Set(prev).add(alertId));
  };

  const handleMarkAsRead = (alertId: string) => {
    markAsRead.mutate(alertId);
  };

  const handleMarkAllAsRead = () => {
    markAllAsRead.mutate();
  };

  return (
    <div className="fixed top-0 left-0 right-0 z-40 animate-slide-down">
      <div className="bg-surface border-b-2 border-red-500 shadow-xl">
        <div className="max-w-7xl mx-auto px-4 py-3">
          {/* Header with "Mark all as read" */}
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-red-500" />
              <span className="text-sm font-semibold text-text-primary">
                Budget Alerts ({alerts.length} unread)
              </span>
            </div>
            <button
              onClick={handleMarkAllAsRead}
              disabled={markAllAsRead.isPending}
              className="text-xs text-text-secondary hover:text-text-primary transition-colors disabled:opacity-50"
            >
              Mark all as read
            </button>
          </div>

          {/* Alert Items */}
          <div className="space-y-2">
            {visibleAlerts.map((alert: BudgetAlert) => (
              <AlertItem
                key={alert.id}
                alert={alert}
                onMarkRead={() => handleMarkAsRead(alert.id)}
                onDismiss={() => handleDismiss(alert.id)}
                isMarkingRead={markAsRead.isPending}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

interface AlertItemProps {
  alert: BudgetAlert;
  onMarkRead: () => void;
  onDismiss: () => void;
  isMarkingRead: boolean;
}

function AlertItem({ alert, onMarkRead, onDismiss, isMarkingRead }: AlertItemProps) {
  const severity = getAlertSeverity(alert.threshold, alert.currentSpentPercentage);
  const severityColor = getAlertColor(severity);

  const Icon = severity === 'exceeded' ? AlertCircle : AlertTriangle;

  return (
    <div className="flex items-start gap-3 p-3 rounded-[10px] bg-surface border border-border shadow-plate transition-all hover:bg-surface-elevated">
      {/* Icon */}
      <Icon className="w-5 h-5 flex-shrink-0 mt-0.5" style={{ color: severityColor }} />

      {/* Alert Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <span className="text-sm font-semibold text-text-primary truncate">
            {alert.budgetName} ({alert.categoryName})
          </span>
          <span
            className="text-xs font-bold px-2 py-0.5 rounded"
            style={{ backgroundColor: severityColor + '20', color: severityColor }}
          >
            {alert.currentSpentPercentage?.toFixed(0)}%
          </span>
        </div>
        <p className="text-xs text-text-secondary">
          {alert.message || `Budget exceeded ${alert.threshold}% threshold`}
        </p>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2 flex-shrink-0">
        <button
          onClick={onMarkRead}
          disabled={isMarkingRead}
          className="text-xs text-text-secondary hover:text-text-primary transition-colors disabled:opacity-50"
        >
          Mark read
        </button>
        <button
          onClick={onDismiss}
          className="text-text-secondary hover:text-text-primary transition-colors"
          aria-label="Dismiss alert"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
