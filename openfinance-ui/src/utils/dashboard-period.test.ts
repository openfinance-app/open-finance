import { describe, expect, it } from 'vitest';
import {
  defaultDashboardPeriod,
  resolveDashboardPeriod,
  restoreDashboardPeriod,
  type PeriodSelection,
} from '@/utils/dashboard-period';

const today = new Date(2026, 9, 17, 12);

describe('Dashboard calendar and rolling periods', () => {
  it.each<[PeriodSelection, string, string, string, string]>([
    [{ period: '1D' }, '2026-10-17', '2026-10-17', '2026-10-16', '2026-10-16'],
    [{ period: '7D' }, '2026-10-11', '2026-10-17', '2026-10-04', '2026-10-10'],
    [{ period: '30D' }, '2026-09-18', '2026-10-17', '2026-08-19', '2026-09-17'],
    [{ period: '365D' }, '2025-10-18', '2026-10-17', '2024-10-18', '2025-10-17'],
    [{ period: 'MTD' }, '2026-10-01', '2026-10-17', '2026-09-01', '2026-09-17'],
    [{ period: 'YTD' }, '2026-01-01', '2026-10-17', '2025-01-01', '2025-10-17'],
    [
      { period: 'CUSTOM', range: { from: '2026-10-01', to: '2026-10-31' } },
      '2026-10-01',
      '2026-10-31',
      '2026-09-01',
      '2026-09-30',
    ],
    [
      { period: 'CUSTOM', range: { from: '2026-01-01', to: '2026-12-31' } },
      '2026-01-01',
      '2026-12-31',
      '2025-01-01',
      '2025-12-31',
    ],
    [
      { period: 'CUSTOM', range: { from: '2026-10-01', to: '2026-10-17' } },
      '2026-10-01',
      '2026-10-17',
      '2026-09-14',
      '2026-09-30',
    ],
    [
      { period: 'CUSTOM', range: { from: '2026-07-01', to: '2026-07-31' } },
      '2026-07-01',
      '2026-07-31',
      '2026-06-01',
      '2026-06-30',
    ],
    [
      { period: 'CUSTOM', range: { from: '2024-01-01', to: '2024-12-31' } },
      '2024-01-01',
      '2024-12-31',
      '2023-01-01',
      '2023-12-31',
    ],
    [
      { period: 'CUSTOM', range: { from: '2026-07-01', to: '2026-09-30' } },
      '2026-07-01',
      '2026-09-30',
      '2026-04-01',
      '2026-06-30',
    ],
    [
      { period: 'CUSTOM', range: { from: '2026-07-10', to: '2026-07-20' } },
      '2026-07-10',
      '2026-07-20',
      '2026-06-29',
      '2026-07-09',
    ],
    [
      { period: 'CUSTOM', range: { from: '2026-03-28', to: '2026-03-30' } },
      '2026-03-28',
      '2026-03-30',
      '2026-03-25',
      '2026-03-27',
    ],
  ])(
    'resolves %j to exact selected and comparison dates',
    (selection, from, to, previousFrom, previousTo) => {
      expect(resolveDashboardPeriod(selection, today)).toMatchObject({
        range: { from, to },
        comparisonRange: { from: previousFrom, to: previousTo },
      });
    }
  );

  it.each([
    ['2026-03-01', '2026-03-31', '2026-02-01', '2026-02-28'],
    ['2024-03-01', '2024-03-31', '2024-02-01', '2024-02-29'],
    ['2026-01-01', '2026-01-31', '2025-12-01', '2025-12-31'],
    ['2026-11-01', '2026-11-30', '2026-10-01', '2026-10-31'],
  ])('compares the full custom month %s through %s', (from, to, previousFrom, previousTo) => {
    expect(resolveDashboardPeriod({ period: 'CUSTOM', range: { from, to } }, today)).toMatchObject({
      range: { from, to },
      comparisonRange: { from: previousFrom, to: previousTo },
    });
  });

  it('keeps 30D rolling even when it coincides with a full calendar month', () => {
    expect(resolveDashboardPeriod({ period: '30D' }, new Date(2026, 5, 30))).toEqual({
      range: { from: '2026-06-01', to: '2026-06-30' },
      comparisonRange: { from: '2026-05-02', to: '2026-05-31' },
      days: 30,
    });
  });

  it.each([
    [new Date(2026, 2, 30), '2026-02-01', '2026-02-28'],
    [new Date(2026, 5, 30), '2026-05-01', '2026-05-31'],
    [new Date(2024, 1, 28), '2024-01-01', '2024-01-28'],
    [new Date(2024, 1, 29), '2024-01-01', '2024-01-31'],
  ])('handles partial and completed months on %s', (date, from, to) => {
    expect(resolveDashboardPeriod({ period: 'MTD' }, date).comparisonRange).toEqual({ from, to });
  });

  it('clamps leap-day YTD to February 28 in the previous year', () => {
    expect(
      resolveDashboardPeriod({ period: 'YTD' }, new Date(2024, 1, 29)).comparisonRange
    ).toEqual({
      from: '2023-01-01',
      to: '2023-02-28',
    });
  });

  it('counts calendar days correctly across daylight-saving changes', () => {
    expect(
      resolveDashboardPeriod(
        { period: 'CUSTOM', range: { from: '2026-03-01', to: '2026-03-31' } },
        today
      ).days
    ).toBe(31);
    expect(
      resolveDashboardPeriod(
        { period: 'CUSTOM', range: { from: '2026-10-24', to: '2026-10-26' } },
        today
      ).days
    ).toBe(3);
  });

  it('disables all-time comparison', () => {
    expect(resolveDashboardPeriod({ period: 'ALL' }, today).comparisonRange).toBeNull();
  });
});

describe('Dashboard period persistence', () => {
  it('defaults to This month and follows the current month after rollover', () => {
    expect(defaultDashboardPeriod()).toEqual({ period: 'MTD' });
    expect(restoreDashboardPeriod(null, today)).toEqual(defaultDashboardPeriod());
    expect(resolveDashboardPeriod(defaultDashboardPeriod(), new Date(2026, 10, 1)).range).toEqual({
      from: '2026-11-01',
      to: '2026-11-01',
    });
  });

  it.each([
    ['1M', '30D'],
    ['1Y', '365D'],
    ['7D', '7D'],
    ['YTD', 'YTD'],
  ])('migrates %s without retaining old day counts or expanded chart windows', (legacy, period) => {
    expect(
      restoreDashboardPeriod(
        JSON.stringify({ selectedPeriod: legacy, periodDays: 99, historyPeriod: 90 }),
        today
      )
    ).toEqual({ period });
  });

  it('restores a historical custom range from the old format', () => {
    const range = { from: '2026-07-01', to: '2026-07-31' };
    expect(
      restoreDashboardPeriod(
        JSON.stringify({ selectedPeriod: 'CUSTOM', activeDateRange: range }),
        today
      )
    ).toEqual({ period: 'CUSTOM', range });
  });

  it.each<PeriodSelection>([
    { period: 'MTD' },
    { period: 'CUSTOM', range: { from: '2026-10-01', to: '2026-10-31' } },
    { period: 'CUSTOM', range: { from: '2026-01-01', to: '2026-12-31' } },
    { period: 'CUSTOM', range: { from: '2027-01-01', to: '2027-12-31' } },
  ])('restores %j', selection => {
    expect(restoreDashboardPeriod(JSON.stringify(selection), today)).toEqual(selection);
  });

  it.each([
    [{ period: 'MONTH', month: '2026-10' }, { period: 'MTD' }],
    [{ period: 'YEAR', year: 2026 }, { period: 'YTD' }],
    [
      { period: 'MONTH', month: '2026-07' },
      { period: 'CUSTOM', range: { from: '2026-07-01', to: '2026-07-31' } },
    ],
    [
      { period: 'YEAR', year: 2024 },
      { period: 'CUSTOM', range: { from: '2024-01-01', to: '2024-12-31' } },
    ],
  ])('migrates removed calendar selection %j preserving its meaning', (saved, expected) => {
    expect(restoreDashboardPeriod(JSON.stringify(saved), today)).toEqual(expected);
  });

  it.each([
    'bad JSON',
    'null',
    '[]',
    '{"period":["1D"]}',
    '{"period":"MONTH","month":"2026-13"}',
    '{"period":"MONTH","month":"2027-01"}',
    '{"period":"YEAR","year":2027}',
    '{"period":"YEAR","year":2025.5}',
    '{"period":"CUSTOM","range":{"from":"2026-02-30","to":"2026-03-01"}}',
    '{"period":"CUSTOM","range":{"from":"2026-07-31","to":"2026-07-01"}}',
  ])('recovers safely from invalid saved data: %s', saved => {
    expect(restoreDashboardPeriod(saved, today)).toEqual(defaultDashboardPeriod());
  });

  it('recomputes YTD on reload instead of restoring a stale day count', () => {
    const selection = restoreDashboardPeriod('{"selectedPeriod":"YTD","periodDays":1}', today);
    expect(resolveDashboardPeriod(selection, today).range.to).toBe('2026-10-17');
    expect(resolveDashboardPeriod(selection, new Date(2027, 0, 2)).range).toEqual({
      from: '2027-01-01',
      to: '2027-01-02',
    });
  });
});
