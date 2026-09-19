/**
 * ImportProgress Component
 * Task 7.4.12: Create ImportProgress component
 *
 * Shows progress, status, and counts during import
 */
import { CheckCircle2, XCircle, Loader2, ExternalLink, AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import type { ImportSessionResponse } from '@/types/import';

interface ImportProgressProps {
  session: ImportSessionResponse;
  onViewTransactions?: () => void;
  onClose?: () => void;
}

export function ImportProgress({ session, onViewTransactions, onClose }: ImportProgressProps) {
  const { t } = useTranslation('import');
  const isComplete = session.status === 'COMPLETED';
  const isFailed = session.status === 'FAILED';
  const isCancelled = session.status === 'CANCELLED';
  const isInProgress = ['PENDING', 'PARSING', 'IMPORTING'].includes(session.status);

  // Calculate progress percentage
  const getProgressPercentage = (): number => {
    if (isComplete) return 100;
    if (isFailed || isCancelled) return 0;

    if (session.status === 'PARSING') return 25;
    if (session.status === 'IMPORTING') {
      // Calculate based on imported vs total
      if (session.totalTransactions > 0) {
        return 50 + (session.importedCount / session.totalTransactions) * 50;
      }
      return 50;
    }
    return 10;
  };

  const progress = getProgressPercentage();

  // Get status message
  const getStatusMessage = (): string => {
    switch (session.status) {
      case 'PENDING':
        return t('progress.preparing');
      case 'PARSING':
        return t('progress.parsing');
      case 'IMPORTING':
        return t('progress.importing', {
          current: session.importedCount,
          total: session.totalTransactions,
        });
      case 'COMPLETED':
        return t('progress.success');
      case 'FAILED':
        return session.errorMessage || t('progress.failed');
      case 'CANCELLED':
        return t('progress.cancelled');
      default:
        return t('progress.processing');
    }
  };

  return (
    <div className="space-y-6">
      {/* Status Icon and Message */}
      <div className="flex flex-col items-center space-y-4">
        {isInProgress && (
          <div className="p-4 bg-primary/10 rounded-full">
            <Loader2 className="h-12 w-12 text-primary animate-spin" />
          </div>
        )}

        {isComplete && (
          <div className="p-4 bg-green-500/10 rounded-full">
            <CheckCircle2 className="h-12 w-12 text-green-500" />
          </div>
        )}

        {isFailed && (
          <div className="p-4 bg-red-500/10 rounded-full">
            <XCircle className="h-12 w-12 text-red-500" />
          </div>
        )}

        {isCancelled && (
          <div className="p-4 bg-amber-500/10 rounded-full">
            <AlertTriangle className="h-12 w-12 text-amber-500" />
          </div>
        )}

        <div className="text-center">
          <h3 className="plate-label  mb-2">{getStatusMessage()}</h3>
          <p className="text-sm text-text-secondary">{session.fileName}</p>
        </div>
      </div>

      {/* Progress Bar */}
      {isInProgress && (
        <div className="space-y-2">
          <div className="w-full bg-surface-elevated rounded-full h-3 overflow-hidden">
            <div
              className="h-full bg-primary transition-all duration-500 ease-out"
              style={{ width: `${progress}%` }}
            />
          </div>
          <div className="flex justify-between text-xs text-text-tertiary">
            <span>{Math.round(progress)}%</span>
            <span>{t('progress.processing')}</span>
          </div>
        </div>
      )}

      {/* Statistics */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-4 text-center">
          <div className="text-2xl font-bold text-text-primary">{session.totalTransactions}</div>
          <div className="text-xs text-text-secondary mt-1">{t('progress.stats.total')}</div>
        </div>

        <div className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-4 text-center">
          <div className="text-2xl font-bold text-green-500">{session.importedCount}</div>
          <div className="text-xs text-text-secondary mt-1">{t('progress.stats.imported')}</div>
        </div>

        <div className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-4 text-center">
          <div className="text-2xl font-bold text-amber-500">{session.duplicateCount}</div>
          <div className="text-xs text-text-secondary mt-1">{t('progress.stats.duplicates')}</div>
        </div>

        <div className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-4 text-center">
          <div className="text-2xl font-bold text-red-500">{session.errorCount}</div>
          <div className="text-xs text-text-secondary mt-1">{t('progress.stats.errors')}</div>
        </div>
      </div>

      {/* Skipped Count (if any) */}
      {session.skippedCount > 0 && (
        <div className="bg-blue-500/5 border border-blue-500/20 rounded-lg p-4">
          <p className="text-sm text-text-secondary">
            {t('progress.skipped', { count: session.skippedCount })}
          </p>
        </div>
      )}

      {/* Error Message */}
      {isFailed && session.errorMessage && (
        <div className="bg-red-500/5 border border-red-500/20 rounded-lg p-4">
          <p className="text-sm text-red-500">{session.errorMessage}</p>
        </div>
      )}

      {/* Actions */}
      <div className="flex justify-center space-x-3">
        {isComplete && onViewTransactions && (
          <Button onClick={onViewTransactions} variant="primary">
            <ExternalLink className="h-4 w-4 mr-2" />
            {t('progress.viewTransactions')}
          </Button>
        )}

        {(isComplete || isFailed || isCancelled) && onClose && (
          <Button onClick={onClose} variant="secondary">
            {t('common:buttons.close')}
          </Button>
        )}
      </div>

      {/* Completion Time */}
      {session.completedAt && (
        <div className="text-center text-xs text-text-tertiary">
          {t('progress.completedAt', { date: new Date(session.completedAt).toLocaleString() })}
        </div>
      )}
    </div>
  );
}
