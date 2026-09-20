import { useCallback } from 'react';
import { useUserSettings } from '@/hooks/useUserSettings';
import { useLocale } from '@/context/LocaleContext';
import { formatDate, formatIsoToDisplay } from '@/utils/date';

/** Calendar dates stay in their calendar; timestamp offsets are converted to local time. */
export function useDateFormatter() {
  const { data: settings } = useUserSettings();
  const { locale } = useLocale();
  const dateFormat = settings?.dateFormat ?? (locale === 'fr' ? 'DD/MM/YYYY' : 'MM/DD/YYYY');
  const date = useCallback(
    (value: string | Date): string =>
      typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
        ? formatIsoToDisplay(value, dateFormat)
        : formatDate(value, dateFormat),
    [dateFormat]
  );
  const dateTime = useCallback(
    (value: string): string => {
      const instant = new Date(value);
      return `${date(instant)} ${new Intl.DateTimeFormat(locale, {
        hour: '2-digit',
        minute: '2-digit',
      }).format(instant)}`;
    },
    [date, locale]
  );
  return { date, dateTime };
}
