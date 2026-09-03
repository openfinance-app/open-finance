import { describe, it, expect } from 'vitest';
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
    const range = periodToDateRange(30);
    expect(range.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(range.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(range.to).toBe(new Date().toISOString().slice(0, 10));
  });
});
