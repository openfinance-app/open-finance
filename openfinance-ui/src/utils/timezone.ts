import { TZDateMini } from '@date-fns/tz';

/** Device timezone is the default; an account override must be a supported IANA identifier. */
export function deviceTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

/** A financial calendar date, independent of display formatting and the device's date parts. */
export function calendarDate(timeZone: string, instant: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(instant);
  const part = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find(value => value.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

export function resolveTimezone(timezone?: string, useDeviceTimezone = true): string {
  if (!useDeviceTimezone && timezone) {
    try {
      new Intl.DateTimeFormat('en', { timeZone: timezone }).format(0);
      return timezone;
    } catch {
      // A preference from an older client must not prevent the page from rendering.
    }
  }
  return deviceTimezone();
}

export function availableTimezones(current?: string): string[] {
  return [
    ...new Set([
      'UTC',
      deviceTimezone(),
      ...(current ? [current] : []),
      ...(typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : []),
    ]),
  ].sort();
}
/** Inclusive start or exclusive end of a calendar day in the selected display timezone. */
export function dateBoundary(value: string, timeZone: string, end = false): string | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return undefined;
  const [, year, month, day] = match.map(Number);
  const date = new TZDateMini(year, month - 1, day, timeZone);
  if (year < 100) date.setFullYear(year);
  if (!Number.isFinite(date.getTime()) || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return undefined;
  }
  if (end) date.setDate(date.getDate() + 1);
  return new Date(date.getTime()).toISOString();
}
