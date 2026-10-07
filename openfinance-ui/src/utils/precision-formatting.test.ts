import { describe, it, expect, afterEach } from 'vitest';
import { formatExactNumber } from '@/utils/format';
import { formatExchangeRate, setDecimalPlacesOverride } from '@/utils/currency';

afterEach(() => {
  localStorage.removeItem('open_finance_number_format');
  setDecimalPlacesOverride(null);
});

describe('precision and separator preferences', () => {
  it('formats recorded values without losing significant digits or applying a display rounding override', () => {
    localStorage.setItem('open_finance_number_format', '1 234,56');
    setDecimalPlacesOverride(1);
    expect(formatExactNumber('1234567890123456.123456789012345678')).toBe(
      '1\u202f234\u202f567\u202f890\u202f123\u202f456,123456789012345678'
    );
    expect(formatExactNumber(1234.5678)).toBe('1\u202f234,5678');
    expect(formatExchangeRate(0.000000078125)).toBe('0,000000078125');
  });
  it('uses European and US separators independently of the interface language', () => {
    localStorage.setItem('open_finance_number_format', '1.234,56');
    expect(formatExactNumber('-1234.005')).toBe('-1.234,005');
    localStorage.setItem('open_finance_number_format', '1,234.56');
    expect(formatExactNumber('-1234.005')).toBe('-1,234.005');
  });
});
