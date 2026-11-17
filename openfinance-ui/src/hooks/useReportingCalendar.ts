import { useEffect, useMemo, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { parseISO } from 'date-fns';
import { useUserSettings } from '@/hooks/useUserSettings';
import { calendarDate, dateBoundary, resolveTimezone } from '@/utils/timezone';

/** The same calendar used by the API: device by default, account override when selected. */
export function useReportingCalendar() {
  const { data: settings } = useUserSettings();
  const [now, setNow] = useState(() => Date.now());
  const timeZone = resolveTimezone(settings?.timezone, settings?.useDeviceTimezone ?? true);
  const today = calendarDate(timeZone, new Date(now));

  useEffect(() => {
    let timer: number;
    const refresh = (): void => {
      const instant = new Date();
      setNow(instant.getTime());
      window.clearTimeout(timer);
      const currentDay = calendarDate(timeZone, instant);
      const midnight = dateBoundary(currentDay, timeZone, true);
      // Re-arm even if the device clock moved backwards or the browser delayed the timer.
      timer = window.setTimeout(
        refresh,
        midnight ? Math.max(50, new Date(midnight).getTime() - instant.getTime() + 50) : 60_000
      );
    };
    refresh();
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, [today, timeZone]);

  // A calendar Date for date-fns arithmetic, never an instant to send to the API.
  const date = useMemo(() => parseISO(today), [today]);
  return { today, timeZone, date };
}

export function useToday(): string {
  return useReportingCalendar().today;
}

/** Refresh derived balances/statuses too, even when their query has no explicit date range. */
export function ReportingCalendarRefresh() {
  const { today, timeZone } = useReportingCalendar();
  const client = useQueryClient();
  const previous = useRef(`${timeZone}:${today}`);
  useEffect(() => {
    const calendar = `${timeZone}:${today}`;
    if (calendar === previous.current) return;
    previous.current = calendar;
    const predicate = (query: { queryKey: readonly unknown[] }): boolean =>
      !['user', 'currencies', 'institutions', 'categories', 'rssFeeds'].includes(
        String(query.queryKey[0])
      );
    // Cancel old-calendar requests so a late response cannot overwrite the refreshed results.
    void client.cancelQueries({ predicate }).then(() => client.invalidateQueries({ predicate }));
  }, [today, timeZone, client]);
  return null;
}
