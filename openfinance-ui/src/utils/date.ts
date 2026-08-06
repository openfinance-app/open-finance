/**
 * Date formatting utilities for transactions and financial data
 * Task 3.2.20: Add date formatting utilities
 * Task 1.4.1 (i18n): Add useRelativeDateFormatter hook; remove hardcoded English from formatRelativeDate
 */
import { formatDistanceToNow } from 'date-fns';
import type { Locale as DateFnsLocale } from 'date-fns';
import { useLocale } from '@/context/LocaleContext';
import i18next from 'i18next';

/**
 * Format a date string or Date object to display format.
 *
 * The locale used here intentionally matches the date-format pattern, not the
 * user's UI locale, because the caller provides an explicit format string that
 * dictates the separator style (e.g. MM/DD/YYYY → en-US separators).
 *
 * Example: "2024-01-15" => "15 Jan 2024"
 */
export function formatDate(date: string | Date, dateFormat?: string): string {
  const d = typeof date === 'string' ? new Date(date) : date;

  if (dateFormat === 'YYYY-MM-DD') {
    return d.toISOString().split('T')[0];
  }

  if (dateFormat === 'MM/DD/YYYY') {
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d);
  }

  if (dateFormat === 'DD/MM/YYYY') {
    return new Intl.DateTimeFormat('en-GB', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d);
  }

  // Default fallback — use a locale-aware short date based on current active language
  // Ensures default rendering is aware of the user's localized session (e.g. FR)
  const inferredLocale = (() => {
    try {
      return i18next.language?.startsWith('fr') ? 'fr-FR' : 'en-US';
    } catch {
      return 'en-US';
    }
  })();
  
  return new Intl.DateTimeFormat(inferredLocale, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(d);
}

/**
 * Format a date string to ISO date format for inputs (YYYY-MM-DD)
 */
export function formatDateForInput(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toISOString().split('T')[0];
}

/**
 * Format a date string to a relative time string using date-fns.
 *
 * @param date - The date to format
 * @param dateFnsLocale - Optional date-fns Locale; defaults to enUS when omitted.
 *                        Prefer the `useRelativeDateFormatter` hook in React components.
 * Example: "2 hours ago", "3 days ago" / "il y a 3 jours" in French
 */
export function formatRelativeDate(date: string | Date, dateFnsLocale?: DateFnsLocale): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  return formatDistanceToNow(d, { addSuffix: true, locale: dateFnsLocale });
}

/**
 * React hook that returns a locale-aware `formatRelativeDate` function.
 * Must be called inside a component tree wrapped by LocaleProvider.
 *
 * Usage:
 *   const formatRelative = useRelativeDateFormatter();
 *   return <span>{formatRelative(transaction.date)}</span>;
 */
export function useRelativeDateFormatter(): (date: string | Date) => string {
  const { dateFnsLocale } = useLocale();
  return (date: string | Date) => formatRelativeDate(date, dateFnsLocale);
}

/**
 * Format a Date using its LOCAL calendar components as YYYY-MM-DD.
 *
 * Unlike {@link formatDateForInput} (which serialises via `toISOString()` in UTC), this preserves
 * the local date. Use it for "now"-derived values so a user in a non-UTC timezone gets their own
 * calendar date rather than a UTC-shifted one — otherwise, at certain times of day, the UTC date
 * rolls to the previous/next day (e.g. local "1st of the month" 00:30 becomes the prior month's
 * last day in UTC+n).
 */
function toLocalISODate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Get the start of today in ISO format (YYYY-MM-DD), in the user's local timezone.
 */
export function getToday(): string {
  return toLocalISODate(new Date());
}

/**
 * Get a date N days ago in ISO format (YYYY-MM-DD), in the user's local timezone.
 */
export function getDaysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return toLocalISODate(date);
}

/**
 * Get the first day of the current month in ISO format, in the user's local timezone.
 */
export function getStartOfMonth(): string {
  const date = new Date();
  date.setDate(1);
  return toLocalISODate(date);
}

/**
 * Get the first day of the current year in ISO format, in the user's local timezone.
 */
export function getStartOfYear(): string {
  const date = new Date();
  date.setMonth(0, 1);
  return toLocalISODate(date);
}

/**
 * Group transactions by date for display
 * Returns a map of date string => items
 */
export function groupByDate<T extends { date: string }>(items: T[], dateFormat?: string): Map<string, T[]> {
  const groups = new Map<string, T[]>();

  items.forEach(item => {
    const dateKey = formatDate(item.date, dateFormat);
    const existing = groups.get(dateKey) || [];
    groups.set(dateKey, [...existing, item]);
  });

  return groups;
}
