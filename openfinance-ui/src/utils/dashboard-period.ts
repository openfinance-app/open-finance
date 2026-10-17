import {
  differenceInCalendarDays,
  differenceInCalendarMonths,
  endOfMonth,
  endOfYear,
  isValid,
  parseISO,
  startOfMonth,
  startOfYear,
  subDays,
  subMonths,
  subYears,
} from 'date-fns';
import { toLocalISODate } from '@/utils/date';

export interface DateRange {
  from: string;
  to: string;
}

export type PresetPeriod = '1D' | '7D' | '30D' | 'MTD' | 'YTD' | '365D' | 'ALL';
export type Period = PresetPeriod | 'CUSTOM';
export type PeriodSelection = { period: PresetPeriod } | { period: 'CUSTOM'; range: DateRange };

export interface ResolvedPeriod {
  range: DateRange;
  comparisonRange: DateRange | null;
  days: number;
}

function dateRange(from: Date, to: Date): DateRange {
  return { from: toLocalISODate(from), to: toLocalISODate(to) };
}

export function dateRangeToDays(range: DateRange): number {
  return differenceInCalendarDays(parseISO(range.to), parseISO(range.from)) + 1;
}

export function previousEqualPeriod(range: DateRange): DateRange {
  const start = parseISO(range.from);
  return dateRange(subDays(start, dateRangeToDays(range)), subDays(start, 1));
}

/** Whole calendar months (including whole years) retain their calendar boundaries. */
export function previousCustomPeriod(range: DateRange): DateRange {
  const start = parseISO(range.from);
  const end = parseISO(range.to);
  if (start.getDate() === 1 && range.to === toLocalISODate(endOfMonth(end))) {
    const months = differenceInCalendarMonths(end, start) + 1;
    return dateRange(subMonths(start, months), subDays(start, 1));
  }
  return previousEqualPeriod(range);
}

function calendarPeriod(start: Date, unit: 'MONTH' | 'YEAR', today: Date): ResolvedPeriod {
  const fullEnd = unit === 'MONTH' ? endOfMonth(start) : endOfYear(start);
  const range = dateRange(start, fullEnd > today ? today : fullEnd);
  const previousStart = unit === 'MONTH' ? subMonths(start, 1) : subYears(start, 1);
  // Completed periods compare whole months/years; ongoing periods compare matching dates.
  const previousEnd =
    range.to === toLocalISODate(fullEnd)
      ? unit === 'MONTH'
        ? endOfMonth(previousStart)
        : endOfYear(previousStart)
      : unit === 'MONTH'
        ? subMonths(parseISO(range.to), 1)
        : subYears(parseISO(range.to), 1);
  return {
    range,
    comparisonRange: dateRange(previousStart, previousEnd),
    days: dateRangeToDays(range),
  };
}

/** Resolve once so requests, chart boundaries, comparisons and drill-downs agree. */
export function resolveDashboardPeriod(
  selection: PeriodSelection,
  today: Date = new Date()
): ResolvedPeriod {
  switch (selection.period) {
    case 'MTD':
      return calendarPeriod(startOfMonth(today), 'MONTH', today);
    case 'YTD':
      return calendarPeriod(startOfYear(today), 'YEAR', today);
    case 'CUSTOM':
      return {
        range: selection.range,
        comparisonRange: previousCustomPeriod(selection.range),
        days: dateRangeToDays(selection.range),
      };
    default: {
      const days = { '1D': 1, '7D': 7, '30D': 30, '365D': 365, ALL: 36500 }[selection.period];
      const range = dateRange(subDays(today, days - 1), today);
      return {
        range,
        comparisonRange: selection.period === 'ALL' ? null : previousEqualPeriod(range),
        days,
      };
    }
  }
}

export function defaultDashboardPeriod(): PeriodSelection {
  return { period: 'MTD' };
}

function isDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && isValid(parseISO(value));
}

/** Persist choices, never derived day counts; migrate old rolling and custom selections. */
export function restoreDashboardPeriod(
  saved: string | null,
  today: Date = new Date()
): PeriodSelection {
  const fallback = defaultDashboardPeriod();
  if (!saved) return fallback;
  try {
    const value: unknown = JSON.parse(saved);
    if (!value || typeof value !== 'object') return fallback;
    const data = value as Record<string, unknown>;
    const period = data.period ?? data.selectedPeriod;
    if (period === '1M') return { period: '30D' };
    if (period === '1Y') return { period: '365D' };
    if (
      typeof period === 'string' &&
      ['1D', '7D', '30D', 'MTD', 'YTD', '365D', 'ALL'].includes(period)
    ) {
      return { period: period as PresetPeriod };
    }
    const todayISO = toLocalISODate(today);
    if (
      period === 'MONTH' &&
      typeof data.month === 'string' &&
      isDate(`${data.month}-01`) &&
      data.month >= '0001-01' &&
      data.month <= todayISO.slice(0, 7)
    ) {
      if (data.month === todayISO.slice(0, 7)) return { period: 'MTD' };
      const start = parseISO(`${data.month}-01`);
      return { period: 'CUSTOM', range: dateRange(start, endOfMonth(start)) };
    }
    if (
      period === 'YEAR' &&
      typeof data.year === 'number' &&
      Number.isInteger(data.year) &&
      data.year >= 1 &&
      data.year <= today.getFullYear()
    ) {
      if (data.year === today.getFullYear()) return { period: 'YTD' };
      const start = parseISO(`${String(data.year).padStart(4, '0')}-01-01`);
      return { period: 'CUSTOM', range: dateRange(start, endOfYear(start)) };
    }
    const range = (data.range ?? data.activeDateRange) as Partial<DateRange> | undefined;
    if (
      period === 'CUSTOM' &&
      range &&
      isDate(range.from) &&
      isDate(range.to) &&
      range.from <= range.to
    ) {
      return { period, range: { from: range.from, to: range.to } };
    }
  } catch {
    // A malformed or obsolete saved selection must not prevent the dashboard loading.
  }
  return fallback;
}
