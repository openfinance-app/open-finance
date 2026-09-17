/**
 * AlertBanner Component
 * TASK-8.2.11: Add budget alerts for warnings and exceeded budgets
 *
 * Displays warning notifications when budgets reach thresholds
 */

import { AlertTriangle, TrendingDown, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useTranslation } from 'react-i18next';

interface AlertBannerProps {
  variant?: 'warning' | 'error';
  title: string;
  message: string;
  onDismiss?: () => void;
  className?: string;
}

export function AlertBanner({
  variant = 'warning',
  title,
  message,
  onDismiss,
  className,
}: AlertBannerProps) {
  const isError = variant === 'error';
  const { t } = useTranslation('common');

  return (
    <div
      className={cn(
        'flex items-start gap-3 p-4 rounded-lg border',
        isError
          ? 'bg-error/10 border-error/20 text-error'
          : 'bg-warning/10 border-warning/20 text-warning',
        className
      )}
      role="alert"
    >
      {/* Icon */}
      <div className="flex-shrink-0 mt-0.5">
        {isError ? <TrendingDown className="h-5 w-5" /> : <AlertTriangle className="h-5 w-5" />}
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <h4 className="text-sm font-semibold mb-1">{title}</h4>
        <p className="text-sm opacity-90">{message}</p>
      </div>

      {/* Dismiss button */}
      {onDismiss && (
        <button
          onClick={onDismiss}
          className="flex-shrink-0 p-1 rounded hover:bg-black/10 transition-colors"
          aria-label={t('aria.dismissAlert')}
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
