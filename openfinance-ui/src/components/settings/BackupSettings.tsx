/**
 * BackupSettings - Backup and restore settings component
 * 
 * Implements TASK-12.5.7:
 * - Display backup information and quick access
 * - Link to full BackupPage for managing backups
 * 
 * Requirements: REQ-2.14.2 (Data Backup & Restore)
 */
import { useNavigate } from 'react-router';
import { ROUTES } from '@/constants/routes';
import { Database, Clock, HardDrive, ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useListBackups } from '@/hooks/useBackup';
import { useUserSettings } from '@/hooks/useUserSettings';
import { formatDate as globalFormatDate } from '@/utils/date';
import { Button } from '@/components/ui/Button';

/**
 * Backup settings component with overview and navigation
 */
export function BackupSettings() {
  const navigate = useNavigate();
  const { t } = useTranslation('backup');
  const { data: backups, isLoading } = useListBackups();
  const { data: settings } = useUserSettings();

  // Calculate statistics
  const completedBackups = backups?.filter(b => b.status === 'COMPLETED') || [];
  const automaticBackups = completedBackups.filter(b => b.backupType === 'AUTOMATIC');
  const latestBackup = completedBackups.length > 0
    ? completedBackups.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0]
    : null;

  const totalSize = completedBackups.reduce((sum, b) => sum + b.fileSize, 0);
  const formattedTotalSize = formatBytes(totalSize);

  function formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
  }

  function formatDate(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return t('overview.timeAgo.justNow');
    if (diffMins < 60) return t('overview.timeAgo.minuteAgo', { count: diffMins });
    if (diffHours < 24) return t('overview.timeAgo.hourAgo', { count: diffHours });
    if (diffDays < 7) return t('overview.timeAgo.dayAgo', { count: diffDays });

    return globalFormatDate(date, settings?.dateFormat);
  }

  return (
    <div className="space-y-6">
      {/* Section Header */}
      <div>
        <h2 className="text-xl font-semibold text-text-primary mb-2">{t('overview.sectionTitle')}</h2>
        <p className="text-text-secondary text-sm">{t('overview.sectionDescription')}</p>
      </div>

      {/* Backup Overview Card */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-4">
            <Database className="h-5 w-5 text-primary" />
            <h3 className="text-base font-medium text-text-primary">{t('overview.cardTitle')}</h3>
          </div>
          <p className="text-xs text-text-secondary">
            {t('overview.cardDescription')}
          </p>
        </div>

        {/* Loading State */}
        {isLoading && (
          <div className="space-y-3 mb-6">
            <div className="h-4 bg-surface-elevated animate-pulse rounded w-3/4"></div>
            <div className="h-4 bg-surface-elevated animate-pulse rounded w-1/2"></div>
          </div>
        )}

        {/* Statistics */}
        {!isLoading && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            {/* Total Backups */}
            <div className="bg-background/50 rounded-lg p-4 border border-border">
              <div className="flex items-center gap-2 mb-2">
                <Database className="h-4 w-4 text-primary" />
                <span className="text-xs text-text-secondary">{t('overview.totalBackups')}</span>
              </div>
              <p className="text-2xl font-bold text-text-primary">
                {completedBackups.length}
              </p>
              {automaticBackups.length > 0 && (
                <p className="text-xs text-text-muted mt-1">
                  {t('overview.automatic', { count: automaticBackups.length })}
                </p>
              )}
            </div>

            {/* Last Backup */}
            <div className="bg-background/50 rounded-lg p-4 border border-border">
              <div className="flex items-center gap-2 mb-2">
                <Clock className="h-4 w-4 text-primary" />
                <span className="text-xs text-text-secondary">{t('overview.lastBackup')}</span>
              </div>
              <p className="text-base font-semibold text-text-primary">
                {latestBackup ? formatDate(latestBackup.createdAt) : t('overview.never')}
              </p>
              {latestBackup && (
                <p className="text-xs text-text-muted mt-1">
                  {latestBackup.backupType === 'AUTOMATIC' ? t('overview.automatic_type') : t('overview.manual_type')}
                </p>
              )}
            </div>

            {/* Storage Used */}
            <div className="bg-background/50 rounded-lg p-4 border border-border">
              <div className="flex items-center gap-2 mb-2">
                <HardDrive className="h-4 w-4 text-primary" />
                <span className="text-xs text-text-secondary">{t('overview.storageUsed')}</span>
              </div>
              <p className="text-2xl font-bold text-text-primary">
                {formattedTotalSize}
              </p>
              {completedBackups.length > 0 && (
                <p className="text-xs text-text-muted mt-1">
                  {t('overview.average', { size: formatBytes(totalSize / completedBackups.length) })}
                </p>
              )}
            </div>
          </div>
        )}

        {/* Info Box */}
        <div className="bg-blue-500/10 border border-blue-500/20 rounded-lg p-4 mb-6">
          <p className="text-sm text-blue-400">
            <strong>{t('overview.tip')}:</strong> {t('overview.tipText')}
          </p>
        </div>

        {/* Action Button */}
        <Button
          variant="primary"
          onClick={() => navigate(ROUTES.BACKUP)}
          className="w-full flex items-center justify-center gap-2"
        >
          <Database className="h-4 w-4" />
          {t('overview.manageBackups')}
          <ArrowRight className="h-4 w-4" />
        </Button>
      </div>

      {/* Additional Information */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <h3 className="text-sm font-medium text-text-primary mb-3">{t('overview.aboutTitle')}</h3>
        <ul className="space-y-2 text-sm text-text-secondary">
          <li className="flex items-start gap-2">
            <span className="text-primary mt-1">•</span>
            <span>{t('overview.aboutGzip')}</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-primary mt-1">•</span>
            <span>{t('overview.aboutChecksum')}</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-primary mt-1">•</span>
            <span>{t('overview.aboutSafety')}</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-primary mt-1">•</span>
            <span>{t('overview.aboutRetention')}</span>
          </li>
          <li className="flex items-start gap-2">
            <span className="text-primary mt-1">•</span>
            <span>{t('overview.aboutDownload')}</span>
          </li>
        </ul>
      </div>
    </div>
  );
}
