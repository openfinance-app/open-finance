import { describe, it, expect, vi } from 'vitest';
import { buildTransactionsLink, periodToDateRange } from './navigation';

describe('buildTransactionsLink', () => {
  it('builds full query string with all params', () => {
    expect(
      buildTransactionsLink({
        accountId: 3,
        type: 'INCOME',
        dateRange: { from: '2026-08-01', to: '2026-08-31' },
      })
    ).toBe('/transactions?accountId=3&type=INCOME&dateFrom=2026-08-01&dateTo=2026-08-31');
  });

  it('supports categoryId and noCategory', () => {
    expect(buildTransactionsLink({ categoryId: 7 })).toBe('/transactions?categoryId=7');
    expect(buildTransactionsLink({ noCategory: true })).toBe('/transactions?noCategory=1');
  });

  it('omits absent params', () => {
    expect(buildTransactionsLink({})).toBe('/transactions');
    expect(buildTransactionsLink({ categoryId: undefined })).toBe('/transactions');
  });
});

describe('periodToDateRange', () => {
  it('returns ISO yyyy-MM-dd from/to spanning days back from today', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date(2026, 9, 1, 0, 30));
      expect(periodToDateRange(1)).toEqual({ from: '2026-10-01', to: '2026-10-01' });
      expect(periodToDateRange(7)).toEqual({ from: '2026-09-25', to: '2026-10-01' });
      expect(periodToDateRange(30)).toEqual({ from: '2026-09-02', to: '2026-10-01' });
    } finally {
      vi.useRealTimers();
    }
  });
});
