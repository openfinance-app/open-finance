import { describe, it, expect } from 'vitest';
import {
  getCurrencySymbol,
  getCurrencyName,
  isValidCurrency,
  getCurrencyDecimals,
  isCryptoCurrency,
  formatCurrency,
  formatCurrencyCompact,
  parseCurrency,
  applyNumberFormat,
  CURRENCIES,
} from './currency';

describe('getCurrencySymbol', () => {
  it('returns $ for USD', () => {
    expect(getCurrencySymbol('USD')).toBe('$');
  });
  it('returns € for EUR', () => {
    expect(getCurrencySymbol('EUR')).toBe('€');
  });
  it('returns code for unknown currency', () => {
    expect(getCurrencySymbol('XYZ')).toBe('XYZ');
  });
  it('defaults to EUR when null', () => {
    expect(getCurrencySymbol(null)).toBe('€');
  });
  it('defaults to EUR when undefined', () => {
    expect(getCurrencySymbol(undefined)).toBe('€');
  });
});

describe('getCurrencyName', () => {
  it('returns US Dollar for USD', () => {
    expect(getCurrencyName('USD')).toBe('US Dollar');
  });
  it('returns code for unknown currency', () => {
    expect(getCurrencyName('XYZ')).toBe('XYZ');
  });
  it('defaults to EUR when null', () => {
    expect(getCurrencyName(null)).toBe('Euro');
  });
});

describe('isValidCurrency', () => {
  it('returns true for known currencies', () => {
    expect(isValidCurrency('USD')).toBe(true);
    expect(isValidCurrency('EUR')).toBe(true);
    expect(isValidCurrency('BTC')).toBe(true);
  });
  it('returns false for unknown currencies', () => {
    expect(isValidCurrency('XYZ')).toBe(false);
  });
  it('returns false for null/undefined', () => {
    expect(isValidCurrency(null)).toBe(false);
    expect(isValidCurrency(undefined)).toBe(false);
  });
});

describe('getCurrencyDecimals', () => {
  it('returns 2 for standard fiat', () => {
    expect(getCurrencyDecimals('USD')).toBe(2);
    expect(getCurrencyDecimals('EUR')).toBe(2);
  });
  it('returns 0 for zero-decimal currencies', () => {
    expect(getCurrencyDecimals('JPY')).toBe(0);
    expect(getCurrencyDecimals('KRW')).toBe(0);
  });
  it('returns 8 for crypto', () => {
    expect(getCurrencyDecimals('BTC')).toBe(8);
    expect(getCurrencyDecimals('ETH')).toBe(8);
  });
  it('defaults to EUR when null', () => {
    expect(getCurrencyDecimals(null)).toBe(2);
  });
});

describe('isCryptoCurrency', () => {
  it('returns true for crypto', () => {
    expect(isCryptoCurrency('BTC')).toBe(true);
    expect(isCryptoCurrency('ETH')).toBe(true);
  });
  it('returns false for fiat', () => {
    expect(isCryptoCurrency('USD')).toBe(false);
  });
  it('is case insensitive', () => {
    expect(isCryptoCurrency('btc')).toBe(true);
  });
  it('defaults to EUR (not crypto) when null', () => {
    expect(isCryptoCurrency(null)).toBe(false);
  });
});

describe('applyNumberFormat', () => {
  it('returns unchanged for en-US format', () => {
    expect(applyNumberFormat('1,234.56', '1,234.56')).toBe('1,234.56');
  });
  it('returns unchanged when format is undefined', () => {
    expect(applyNumberFormat('1,234.56', undefined)).toBe('1,234.56');
  });
  it('converts to de-DE format', () => {
    expect(applyNumberFormat('1,234.56', '1.234,56')).toBe('1.234,56');
  });
  it('converts to fr-FR format', () => {
    const result = applyNumberFormat('1,234.56', '1 234,56');
    expect(result).toContain('234');
    expect(result).toContain(',56');
  });
});

describe('formatCurrency', () => {
  it('formats USD correctly', () => {
    const result = formatCurrency(1234.56, 'USD');
    expect(result).toContain('$');
    expect(result).toContain('1,234.56');
  });
  it('handles negative amounts', () => {
    const result = formatCurrency(-500, 'USD');
    expect(result).toContain('-');
    expect(result).toContain('500');
  });
  it('handles zero', () => {
    const result = formatCurrency(0, 'USD');
    expect(result).toContain('$');
    expect(result).toContain('0.00');
  });
  it('handles Infinity', () => {
    const result = formatCurrency(Infinity, 'USD');
    expect(result).toContain('$');
    expect(result).toContain('0.00');
  });
  it('handles NaN', () => {
    const result = formatCurrency(NaN, 'USD');
    expect(result).toContain('0.00');
  });
  it('shows positive sign when requested', () => {
    const result = formatCurrency(100, 'USD', { showPositiveSign: true });
    expect(result).toContain('+');
  });
  it('hides symbol when requested', () => {
    const result = formatCurrency(100, 'USD', { showSymbol: false });
    expect(result).not.toContain('$');
  });
  it('uses accounting format for negative', () => {
    const result = formatCurrency(-100, 'USD', { accounting: true });
    expect(result).toContain('(');
    expect(result).toContain(')');
  });
  it('formats CHF with symbol after', () => {
    const result = formatCurrency(100, 'CHF');
    expect(result).toContain('CHF');
  });
  it('applies compact mode', () => {
    const result = formatCurrency(1500, 'USD', { compact: true });
    expect(result).toContain('K');
  });
  it('overrides decimal places', () => {
    const result = formatCurrency(100.123, 'USD', { decimals: 3 });
    expect(result).toContain('100.123');
  });
  it('defaults to EUR when currencyCode is null', () => {
    const result = formatCurrency(100, null);
    expect(result).toContain('€');
  });
  it('applies number format de-DE', () => {
    const result = formatCurrency(1234.56, 'USD', { numberFormat: '1.234,56' });
    expect(result).toContain('1.234,56');
  });
});

describe('formatCurrencyCompact', () => {
  it('formats thousands as K', () => {
    const result = formatCurrencyCompact(1500, 'USD');
    expect(result).toContain('1.5K');
  });
  it('formats millions as M', () => {
    const result = formatCurrencyCompact(2300000, 'USD');
    expect(result).toContain('2.3M');
  });
  it('formats billions as B', () => {
    const result = formatCurrencyCompact(1200000000, 'USD');
    expect(result).toContain('1.2B');
  });
  it('shows full value for small amounts', () => {
    const result = formatCurrencyCompact(42.5, 'USD');
    expect(result).toContain('42.50');
  });
  it('handles negative amounts', () => {
    const result = formatCurrencyCompact(-5000, 'USD');
    expect(result).toContain('-');
    expect(result).toContain('K');
  });
  it('handles Infinity', () => {
    const result = formatCurrencyCompact(Infinity, 'USD');
    expect(result).toContain('0');
  });
  it('defaults to EUR when null', () => {
    const result = formatCurrencyCompact(100, null);
    expect(result).toContain('€');
  });
});

describe('parseCurrency', () => {
  it('parses simple number', () => {
    expect(parseCurrency('1234.56')).toBe(1234.56);
  });
  it('parses currency with symbol', () => {
    expect(parseCurrency('$1,234.56')).toBe(1234.56);
  });
  it('parses negative', () => {
    expect(parseCurrency('-$500')).toBe(-500);
  });
  it('parses accounting format with parens', () => {
    expect(parseCurrency('($500.00)')).toBe(-500);
  });
  it('returns null for empty string', () => {
    expect(parseCurrency('')).toBeNull();
  });
  it('returns null for invalid input', () => {
    expect(parseCurrency('abc')).toBeNull();
  });
});

describe('CURRENCIES constant', () => {
  it('contains common currencies', () => {
    const codes = CURRENCIES.map(c => c.code);
    expect(codes).toContain('USD');
    expect(codes).toContain('EUR');
    expect(codes).toContain('GBP');
    expect(codes).toContain('BTC');
  });
});
