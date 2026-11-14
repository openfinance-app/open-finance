import { afterEach, describe, expect, it } from 'vitest';
import { formatCurrency, setDecimalPlacesOverride } from '@/utils/currency';
import { formatCurrency as formatLiabilityCurrency } from '@/hooks/useLiabilities';
import { formatCurrency as formatPortfolioCurrency } from '@/utils/portfolio';
import { formatCurrency as formatLegacyCurrency } from '@/utils/format';
import { formatCurrency as formatFinancialCurrency } from '@/utils/financialCalculations';
import { ariaLabel } from '@/utils/accessibility';

afterEach(() => {
  setDecimalPlacesOverride(null);
  localStorage.removeItem('open_finance_number_format');
});

describe.each([
  ['shared', formatCurrency],
  ['liability', formatLiabilityCurrency],
  ['portfolio', formatPortfolioCurrency],
  ['legacy', formatLegacyCurrency],
  ['accessible', ariaLabel.money],
] as const)('%s monetary formatting', (_name, format) => {
  it('keeps rounded display and follows the selected separators and decimal count', () => {
    localStorage.setItem('open_finance_number_format', '1 234,56');
    expect(format(1234.478, 'EUR')).toBe('€1\u202f234,48');
    setDecimalPlacesOverride(4);
    expect(format(1234.47856, 'EUR')).toBe('€1\u202f234,4786');
  });

  it('formats exact decimal text without going through a JavaScript number', () => {
    setDecimalPlacesOverride(4);
    expect(format('1.475349999999999999', 'EUR')).toBe('€1.4753');
  });

  it('respects currency precision and uses consistent halfway rounding', () => {
    localStorage.setItem('open_finance_number_format', '1,234.56');
    expect(format(1.005, 'EUR')).toBe('€1.01');
    expect(format(1234.56, 'JPY')).toBe('¥1,235');
    expect(format(1.2345, 'KWD')).toContain('1.235');
  });
});

it('keeps monetary decimals in the financial-calculation formatter', () => {
  expect(formatFinancialCurrency(1234.478, 'fr-FR', 'EUR')).toBe('€1\u202f234,48');
  setDecimalPlacesOverride(3);
  expect(formatFinancialCurrency(1234.4786, 'fr-FR', 'EUR')).toBe('€1\u202f234,479');
});
