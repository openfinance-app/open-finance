import { useCallback } from 'react';
import { useUserSettings } from '@/hooks/useUserSettings';
import { useLocale } from '@/context/LocaleContext';
import { formatDate, formatIsoToDisplay } from '@/utils/date';
import { resolveTimezone } from '@/utils/timezone';

/** Calendar dates stay fixed; timestamps use the device timezone or an account override. */
export function useDateFormatter() {
  const { data: settings } = useUserSettings();
  const { locale } = useLocale();
  const dateFormat = settings?.dateFormat ?? (locale === 'fr' ? 'DD/MM/YYYY' : 'MM/DD/YYYY');
  const timeZone = resolveTimezone(settings?.timezone, settings?.useDeviceTimezone ?? true);
  const date = useCallback(
    (value: string | Date): string =>
      typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
        ? formatIsoToDisplay(value, dateFormat)
        : formatDate(value, dateFormat, timeZone),
    [dateFormat, timeZone]
  );
  const time = useCallback(
    (value: string | Date): string =>
      new Intl.DateTimeFormat(locale, {
        timeZone,
        hour: '2-digit',
        minute: '2-digit',
      }).format(typeof value === 'string' ? new Date(value) : value),
    [locale, timeZone]
  );
  const dateTime = useCallback(
    (value: string): string => {
      if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return date(value);
      const instant = new Date(value);
      return `${date(instant)} ${time(instant)}`;
    },
    [date, time]
  );
  return { date, dateTime, time, timeZone };
}
