import { describe, it, expect, vi } from 'vitest';
import { isStalePrice, getTimestampColor, formatAbsoluteTime, formatRelativeTime } from './time';

// Mock i18next for formatRelativeTime
vi.mock('i18next', () => ({
  default: {
    t: (key: string) => key,
    language: 'en',
  },
}));

describe('isStalePrice', () => {
  it('returns true for null', () => {
    expect(isStalePrice(null)).toBe(true);
  });

  it('returns true for undefined', () => {
    expect(isStalePrice(undefined)).toBe(true);
  });

  it('returns true for date older than 24 hours', () => {
    const old = new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString();
    expect(isStalePrice(old)).toBe(true);
  });

  it('returns false for recent date', () => {
    const recent = new Date(Date.now() - 1 * 60 * 60 * 1000).toISOString();
    expect(isStalePrice(recent)).toBe(false);
  });

  it('handles Date objects', () => {
    const recent = new Date(Date.now() - 1000);
    expect(isStalePrice(recent)).toBe(false);
  });
});

describe('getTimestampColor', () => {
  it('returns yellow for stale prices', () => {
    expect(getTimestampColor(null)).toBe('text-yellow-500');
  });

  it('returns muted for fresh prices', () => {
    const recent = new Date().toISOString();
    expect(getTimestampColor(recent)).toBe('text-muted-foreground');
  });
});

describe('formatAbsoluteTime', () => {
  it('formats string date', () => {
    const result = formatAbsoluteTime('2024-06-15T14:30:00Z');
    expect(result).toBeTruthy();
    expect(result).toContain('2024');
  });

  it('formats Date object', () => {
    const result = formatAbsoluteTime(new Date('2024-01-01T00:00:00Z'));
    expect(result).toBeTruthy();
  });
});

describe('formatRelativeTime', () => {
  it('returns never key for null', () => {
    expect(formatRelativeTime(null)).toBe('common:lastUpdated.never');
  });

  it('returns never key for undefined', () => {
    expect(formatRelativeTime(undefined)).toBe('common:lastUpdated.never');
  });

  it('returns justNow for very recent dates', () => {
    const now = new Date().toISOString();
    expect(formatRelativeTime(now)).toBe('common:lastUpdated.justNow');
  });

  it('returns relative time for minutes ago', () => {
    const fiveMinAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    const result = formatRelativeTime(fiveMinAgo);
    expect(result).toContain('5');
  });

  it('returns relative time for hours ago', () => {
    const threeHoursAgo = new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString();
    const result = formatRelativeTime(threeHoursAgo);
    expect(result).toContain('3');
  });

  it('returns relative time for days ago', () => {
    const twoDaysAgo = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString();
    const result = formatRelativeTime(twoDaysAgo);
    expect(result).toContain('2');
  });

  it('returns formatted date for dates older than a week', () => {
    const old = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
    const result = formatRelativeTime(old);
    expect(result).toBeTruthy();
  });

  it('accepts Date objects', () => {
    const result = formatRelativeTime(new Date());
    expect(result).toBe('common:lastUpdated.justNow');
  });
});
