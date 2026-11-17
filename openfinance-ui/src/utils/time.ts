/**
 * Time formatting utilities
 * Task 5.4.6: Timestamp display helpers
 */
import i18next from 'i18next';

/**
 * Format a date as relative time (e.g., "2 minutes ago", "3 hours ago")
 * Uses Intl.RelativeTimeFormat for locale-aware output.
 */
export function formatRelativeTime(
  date: string | Date | null | undefined,
  timeZone?: string
): string {
  if (!date) {
    return i18next.t('common:lastUpdated.never');
  }
  const now = new Date();
  const target = typeof date === 'string' ? new Date(date) : date;
  const diffMs = now.getTime() - target.getTime();
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffSeconds < 60) {
    return i18next.t('common:lastUpdated.justNow');
  }

  const locale = i18next.language || 'en';
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });

  if (diffMinutes < 60) {
    return rtf.format(-diffMinutes, 'minute');
  } else if (diffHours < 24) {
    return rtf.format(-diffHours, 'hour');
  } else if (diffDays < 7) {
    return rtf.format(-diffDays, 'day');
  } else {
    const yearFormat = new Intl.DateTimeFormat(locale, { timeZone, year: 'numeric' });
    return target.toLocaleDateString(locale, {
      timeZone,
      month: 'short',
      day: 'numeric',
      year: yearFormat.format(target) !== yearFormat.format(now) ? 'numeric' : undefined,
    });
  }
}

/**
 * Check if a timestamp is stale (older than 1 day)
 */
export function isStalePrice(lastUpdated: string | Date | null | undefined): boolean {
  if (!lastUpdated) {
    return true; // Treat null/undefined as stale
  }
  const now = new Date();
  const target = typeof lastUpdated === 'string' ? new Date(lastUpdated) : lastUpdated;
  const diffMs = now.getTime() - target.getTime();
  const diffHours = diffMs / (1000 * 60 * 60);
  return diffHours > 24;
}

/**
 * Get color class for timestamp freshness
 */
export function getTimestampColor(lastUpdated: string | Date | null | undefined): string {
  if (isStalePrice(lastUpdated)) {
    return 'text-yellow-500'; // Stale (>1 day)
  }
  return 'text-muted-foreground'; // Fresh
}

/**
 * Format absolute timestamp
 */
export function formatAbsoluteTime(date: string | Date, timeZone?: string): string {
  const target = typeof date === 'string' ? new Date(date) : date;
  return target.toLocaleString(i18next.language || 'en', {
    timeZone,
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}
