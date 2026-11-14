import { describe, it, expect, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import React from 'react';
import testI18n from '@/test/i18n-test';
import {
  getMonetaryScale,
  getCurrencySymbol,
  getCurrencyName,
  isValidCurrency,
  getCurrencyDecimals,
  setDecimalPlacesOverride,
  setCryptoCurrencyCodes,
  isCryptoCurrency,
  formatCurrency,
  formatCurrencyCompact,
  formatCurrencyWithColor,
  formatExchangeRate,
  parseCurrency,
  applyNumberFormat,
  useCurrencyName,
  CURRENCIES,
} from './currency';

it('keeps posting precision independent of display preferences', () => {
  setDecimalPlacesOverride(2);
  try {
    expect(getMonetaryScale('BTC')).toBe(18);
    expect(getMonetaryScale('KWD')).toBe(3);
    expect(getMonetaryScale('JPY')).toBe(0);
    expect(getMonetaryScale('EUR')).toBe(2);
  } finally {
    setDecimalPlacesOverride(null);
  }
});

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

describe('decimal places override', () => {
  afterEach(() => {
    // Always reset the module-level override so other tests are unaffected.
    setDecimalPlacesOverride(null);
  });

  it('overrides decimals for every currency when set', () => {
    setDecimalPlacesOverride(3);
    expect(getCurrencyDecimals('USD')).toBe(3);
    expect(getCurrencyDecimals('JPY')).toBe(3);
    expect(getCurrencyDecimals('BTC')).toBe(3);
  });

  it('falls back to smart per-currency decimals when override is null', () => {
    setDecimalPlacesOverride(null);
    expect(getCurrencyDecimals('USD')).toBe(2);
    expect(getCurrencyDecimals('JPY')).toBe(0);
    expect(getCurrencyDecimals('BTC')).toBe(8);
  });

  it('ignores values below 1 (treats them as no override)', () => {
    setDecimalPlacesOverride(0);
    expect(getCurrencyDecimals('BTC')).toBe(8);
  });

  it('explicit options.decimals still wins over the override in formatCurrency', () => {
    setDecimalPlacesOverride(5);
    // '$1,234.00' — the explicit decimals:2 beats the override of 5.
    expect(formatCurrency(1234, 'USD', { decimals: 2 })).toBe('$1,234.00');
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

describe('dynamic crypto currency codes', () => {
  afterEach(() => {
    // Reset module state so the hardcoded bootstrap list is used by other tests.
    setCryptoCurrencyCodes([]);
  });

  it('treats codes from setCryptoCurrencyCodes as crypto (8 decimals)', () => {
    setCryptoCurrencyCodes(['SUI', 'PEPE']);
    expect(isCryptoCurrency('SUI')).toBe(true);
    expect(isCryptoCurrency('pepe')).toBe(true);
    expect(getCurrencyDecimals('SUI')).toBe(8);
  });

  it('once set, the dynamic list overrides the bootstrap fallback', () => {
    setCryptoCurrencyCodes(['SUI']);
    expect(isCryptoCurrency('SUI')).toBe(true);
    // BTC is in the bootstrap list but NOT the dynamic set -> a true override must exclude it
    expect(isCryptoCurrency('BTC')).toBe(false);
    expect(isCryptoCurrency('DOGE')).toBe(false);
  });

  it('falls back to the bootstrap list (BTC/ETH) before the API loads', () => {
    // no setCryptoCurrencyCodes call -> module state is null
    expect(isCryptoCurrency('BTC')).toBe(true);
    expect(isCryptoCurrency('ETH')).toBe(true);
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
    expect(result).toBe('—');
  });
  it('handles NaN', () => {
    const result = formatCurrency(NaN, 'USD');
    expect(result).toBe('—');
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
  it('rounds halfway values consistently with standard currency formatting', () => {
    expect(formatCurrency(1.005, 'EUR')).toBe('€1.01');
    expect(formatCurrencyCompact(1.005, 'EUR')).toBe('€1.01');
    expect(formatCurrencyCompact(1005, 'EUR', { decimals: 2 })).toBe('€1.01K');
  });

  it('uses the same compact precision through either entry point', () => {
    expect(formatCurrency(1234, 'EUR', { compact: true })).toBe('€1.23K');
    expect(formatCurrencyCompact(1234, 'EUR')).toBe('€1.23K');
    setDecimalPlacesOverride(3);
    try {
      expect(formatCurrency(1234, 'EUR', { compact: true })).toBe('€1.234K');
      expect(formatCurrencyCompact(1234, 'EUR')).toBe('€1.234K');
      expect(formatCurrencyCompact(1.23456, 'EUR')).toBe('€1.235');
    } finally {
      setDecimalPlacesOverride(null);
    }
  });

  it('formats thousands as K', () => {
    const result = formatCurrencyCompact(1500, 'USD');
    expect(result).toContain('1.50K');
  });
  it('formats millions as M', () => {
    const result = formatCurrencyCompact(2300000, 'USD');
    expect(result).toContain('2.30M');
  });
  it('formats billions as B', () => {
    const result = formatCurrencyCompact(1200000000, 'USD');
    expect(result).toContain('1.20B');
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
    expect(result).toBe('—');
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

it.each([
  ['KWD', 3],
  ['BHD', 3],
  ['CLF', 4],
  ['JPY', 0],
  ['XOF', 0],
  ['USD', 2],
] as const)('uses the natural minor-unit precision for %s', (code, scale) => {
  setDecimalPlacesOverride(null);
  expect(getCurrencyDecimals(code)).toBe(scale);
});

it('preserves KWD precision in both supported number formats', () => {
  setDecimalPlacesOverride(null);
  expect(formatCurrency(1.234, 'KWD')).toBe('KWD1.234');
  expect(formatCurrency(1.234, 'KWD', { numberFormat: '1 234,56' })).toBe('KWD1,234');
  setDecimalPlacesOverride(1);
  expect(formatCurrency(1.234, 'KWD')).toBe('KWD1.2');
  expect(getMonetaryScale('KWD')).toBe(3);
  setDecimalPlacesOverride(null);
});

describe('getMonetaryScale invalid codes', () => {
  it('falls back to 18 for codes Intl rejects instead of throwing', () => {
    expect(() => getMonetaryScale('XX')).not.toThrow();
    expect(getMonetaryScale('XX')).toBe(18);
    expect(getMonetaryScale('')).toBe(18);
  });
});

describe('formatCurrencyWithColor', () => {
  afterEach(() => setDecimalPlacesOverride(null));

  it('marks positive amounts green and forces a leading sign', () => {
    const result = formatCurrencyWithColor('12.5', 'EUR');
    expect(result.className).toBe('text-green-500');
    expect(result.formatted).toContain('+');
    expect(result.formatted).toContain('12.50');
  });

  it('marks negative amounts red', () => {
    expect(formatCurrencyWithColor(-3, 'EUR').className).toBe('text-red-500');
  });

  it('marks exact zero as muted in every representation', () => {
    expect(formatCurrencyWithColor(0, 'EUR').className).toBe('text-text-muted');
    expect(formatCurrencyWithColor('0.00', 'EUR').className).toBe('text-text-muted');
    expect(formatCurrencyWithColor('-0.00', 'EUR').className).toBe('text-text-muted');
  });

  it('colours sub-cent amounts by their true sign, not the rounded display', () => {
    // Displays as -0.00 at 2dp but must still read as a loss.
    expect(formatCurrencyWithColor('-0.001', 'EUR').className).toBe('text-red-500');
    expect(formatCurrencyWithColor('0.001', 'EUR').className).toBe('text-green-500');
  });

  it('defaults the currency when omitted, null or undefined', () => {
    expect(formatCurrencyWithColor(5).formatted).toContain('5.00');
    expect(formatCurrencyWithColor(5, null).className).toBe('text-green-500');
    expect(formatCurrencyWithColor(5, undefined).className).toBe('text-green-500');
  });

  it('honours caller formatting options but always shows the sign', () => {
    const result = formatCurrencyWithColor('1234.5', 'EUR', { showPositiveSign: false });
    expect(result.formatted).toContain('+');
  });
});

describe('formatExchangeRate', () => {
  it('returns 0 for zero and non-finite rates instead of NaN/Infinity text', () => {
    expect(formatExchangeRate(0)).toBe('0');
    expect(formatExchangeRate('0')).toBe('0');
    expect(formatExchangeRate(NaN)).toBe('0');
    expect(formatExchangeRate(Infinity)).toBe('0');
  });

  it('keeps 6 significant figures for very small rates', () => {
    expect(formatExchangeRate('0.00176385510712862')).toBe('0.00176386');
    expect(formatExchangeRate(1)).toBe('1');
  });
});

describe('useCurrencyName', () => {
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(I18nextProvider, { i18n: testI18n }, children);

  it('resolves localised names and falls back to the English name/code', () => {
    const { result } = renderHook(() => useCurrencyName(), { wrapper });
    expect(result.current('USD')).toBe('US Dollar');
    expect(result.current('XYZ')).toBe('XYZ');
  });

  it('defaults to EUR for nullish codes', () => {
    const { result } = renderHook(() => useCurrencyName(), { wrapper });
    expect(result.current(null)).toBe('Euro');
    expect(result.current(undefined)).toBe('Euro');
    expect(result.current('')).toBe('Euro');
  });
});
