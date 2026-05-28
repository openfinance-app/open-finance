import { describe, it, expect } from 'vitest';
import { formatCurrency, formatPercentage, formatNumber, formatCompactCurrency, getGainLossClass, formatGainLoss } from './format';

describe('formatCurrency', () => {
  it('formats EUR by default', () => {
    const result = formatCurrency(1048396);
    expect(result).toBeTruthy();
    expect(result.replace(/\s/g, '')).toContain('1');
  });

  it('formats USD', () => {
    const result = formatCurrency(1234.56, 'USD');
    expect(result).toBeTruthy();
  });

  it('formats compact mode', () => {
    const result = formatCurrency(1048396, 'EUR', { compact: true });
    expect(result).toBeTruthy();
  });

  it('handles zero', () => {
    const result = formatCurrency(0, 'EUR');
    expect(result).toBeTruthy();
  });

  it('handles negative', () => {
    const result = formatCurrency(-500, 'USD');
    expect(result).toBeTruthy();
  });
});

describe('formatPercentage', () => {
  it('formats percentage with 2 decimals by default', () => {
    expect(formatPercentage(72.45)).toBe('72.45%');
  });

  it('formats with custom decimals', () => {
    expect(formatPercentage(72.456, 1)).toBe('72.5%');
  });

  it('formats zero', () => {
    expect(formatPercentage(0)).toBe('0.00%');
  });

  it('formats negative', () => {
    expect(formatPercentage(-5.5)).toBe('-5.50%');
  });
});

describe('formatNumber', () => {
  it('formats large numbers with separators', () => {
    const result = formatNumber(1048396);
    expect(result).toBeTruthy();
    expect(result.length).toBeGreaterThan(5);
  });

  it('formats small numbers', () => {
    const result = formatNumber(42);
    expect(result).toBe('42');
  });

  it('formats decimals', () => {
    const result = formatNumber(3.14);
    expect(result).toBeTruthy();
  });
});

describe('formatCompactCurrency', () => {
  it('formats large amounts compactly', () => {
    const result = formatCompactCurrency(1048396, 'EUR');
    expect(result).toBeTruthy();
  });
});

describe('getGainLossClass', () => {
  it('returns gain-positive for positive values', () => {
    expect(getGainLossClass(100)).toBe('gain-positive');
  });

  it('returns gain-negative for negative values', () => {
    expect(getGainLossClass(-100)).toBe('gain-negative');
  });

  it('returns gain-positive for zero', () => {
    expect(getGainLossClass(0)).toBe('gain-positive');
  });
});

describe('formatGainLoss', () => {
  it('formats positive gain with plus sign', () => {
    const result = formatGainLoss(25385, 72.45, 'EUR');
    expect(result).toContain('+');
    expect(result).toContain('72.45%');
  });

  it('formats negative loss without plus sign', () => {
    const result = formatGainLoss(-1000, -5.5, 'EUR');
    expect(result).not.toMatch(/^\+/);
  });

  it('formats zero amount with plus sign', () => {
    const result = formatGainLoss(0, 0, 'EUR');
    expect(result).toContain('+');
  });
});
