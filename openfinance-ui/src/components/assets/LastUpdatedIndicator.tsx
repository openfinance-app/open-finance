/**
 * LastUpdatedIndicator Component
 * Task 5.4.6: Display last updated timestamp
 *
 * Shows when asset prices were last updated with visual indicators for stale data
 */
import { Clock, AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { formatRelativeTime, isStalePrice, getTimestampColor } from '@/utils/time';

interface LastUpdatedIndicatorProps {
  lastUpdated: string;
  showWarning?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export function LastUpdatedIndicator({
  lastUpdated,
  showWarning = true,
  size = 'sm',
}: LastUpdatedIndicatorProps) {
  const { t } = useTranslation('common');
  const isStale = isStalePrice(lastUpdated);
  const relativeTime = formatRelativeTime(lastUpdated);
  const colorClass = getTimestampColor(lastUpdated);

  const iconSize = size === 'sm' ? 12 : size === 'md' ? 14 : 16;
  const textSize = size === 'sm' ? 'text-xs' : size === 'md' ? 'text-sm' : 'text-base';

  return (
    <div className="flex items-center gap-1.5">
      {isStale && showWarning ? (
        <AlertTriangle className="flex-shrink-0 text-yellow-500" size={iconSize} />
      ) : (
        <Clock className="flex-shrink-0 text-muted-foreground" size={iconSize} />
      )}
      <span className={`${textSize} ${colorClass}`}>
        {t('lastUpdated.updated', { time: relativeTime })}
      </span>
    </div>
  );
}

/**
 * Batch update indicator for multiple assets
 */
interface BatchUpdateIndicatorProps {
  assets: Array<{ lastUpdated: string }>;
  size?: 'sm' | 'md' | 'lg';
}

export function BatchUpdateIndicator({ assets, size = 'md' }: BatchUpdateIndicatorProps) {
  const { t } = useTranslation('common');
  if (!assets || assets.length === 0) {
    return null;
  }

  // Find the most recent update
  const mostRecent = assets.reduce((latest, asset) => {
    const assetDate = new Date(asset.lastUpdated);
    const latestDate = new Date(latest);
    return assetDate > latestDate ? asset.lastUpdated : latest;
  }, assets[0].lastUpdated);

  // Count stale assets
  const staleCount = assets.filter(a => isStalePrice(a.lastUpdated)).length;

  const textSize = size === 'sm' ? 'text-xs' : size === 'md' ? 'text-sm' : 'text-base';

  return (
    <div className="flex items-center gap-3">
      <LastUpdatedIndicator lastUpdated={mostRecent} size={size} showWarning={false} />
      {staleCount > 0 && (
        <div className="flex items-center gap-1.5">
          <AlertTriangle
            className="flex-shrink-0 text-yellow-500"
            size={size === 'sm' ? 12 : size === 'md' ? 14 : 16}
          />
          <span className={`${textSize} text-yellow-500`}>
            {t('lastUpdated.stalePrice', { count: staleCount })}
          </span>
        </div>
      )}
    </div>
  );
}
