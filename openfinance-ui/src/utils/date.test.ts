import { describe, it, expect, vi } from 'vitest';
import { formatDate, formatDateForInput, getToday, getDaysAgo, getStartOfMonth, getStartOfYear, formatRelativeDate } from './date';

describe('formatDate', () => {
  it('formats with YYYY-MM-DD format', () => {
    expect(formatDate('2024-01-15T10:00:00Z', 'YYYY-MM-DD')).toBe('2024-01-15');
  });

  it('formats with MM/DD/YYYY format', () => {
    const result = formatDate('2024-01-15T10:00:00Z', 'MM/DD/YYYY');
    expect(result).toBe('01/15/2024');
  });

  it('formats with DD/MM/YYYY format', () => {
    const result = formatDate('2024-01-15T10:00:00Z', 'DD/MM/YYYY');
    expect(result).toBe('15/01/2024');
  });

  it('handles Date objects', () => {
    const result = formatDate(new Date('2024-01-15T10:00:00Z'), 'YYYY-MM-DD');
    expect(result).toBe('2024-01-15');
  });

  it('uses default locale-aware format when no format specified', () => {
    const result = formatDate('2024-06-15T10:00:00Z');
    expect(result).toBeTruthy();
    // Should contain year
    expect(result).toContain('2024');
  });
});

describe('formatDateForInput', () => {
  it('formats string date to YYYY-MM-DD', () => {
    expect(formatDateForInput('2024-01-15T10:00:00Z')).toBe('2024-01-15');
  });

  it('formats Date object to YYYY-MM-DD', () => {
    expect(formatDateForInput(new Date('2024-06-20T00:00:00Z'))).toBe('2024-06-20');
  });
});

describe('getToday', () => {
  it('returns today in YYYY-MM-DD format', () => {
    const today = getToday();
    expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('getDaysAgo', () => {
  it('returns date N days ago in YYYY-MM-DD format', () => {
    const result = getDaysAgo(7);
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    const d = new Date(result);
    const now = new Date();
    const diff = Math.round((now.getTime() - d.getTime()) / (1000 * 60 * 60 * 24));
    expect(diff).toBeGreaterThanOrEqual(6);
    expect(diff).toBeLessThanOrEqual(8);
  });
});

describe('getStartOfMonth', () => {
  it('returns 1st of current month in YYYY-MM-DD format', () => {
    const result = getStartOfMonth();
    expect(result).toMatch(/^\d{4}-\d{2}-01$/);
  });
});

describe('getStartOfYear', () => {
  it('returns Jan 1st of current year', () => {
    const result = getStartOfYear();
    const year = new Date().getFullYear();
    expect(result).toBe(`${year}-01-01`);
  });
});

describe('formatRelativeDate', () => {
  it('returns a relative time string', () => {
    const recent = new Date(Date.now() - 3600 * 1000).toISOString(); // 1 hour ago
    const result = formatRelativeDate(recent);
    expect(result).toBeTruthy();
    expect(typeof result).toBe('string');
  });
});
