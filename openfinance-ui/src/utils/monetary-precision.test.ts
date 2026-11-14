import { afterEach, describe, expect, it } from 'vitest';
import { formatCurrency, setDecimalPlacesOverride } from '@/utils/currency';
import {
  calculateCompoundInterest,
  calculateMinimumResalePrice,
} from '@/utils/realEstateCalculations';

afterEach(() => {
  setDecimalPlacesOverride(null);
  localStorage.removeItem('open_finance_number_format');
});

describe('monetary precision boundaries', () => {
  it('rounds exact API text only once, including values beyond JavaScript numeric precision', () => {
    localStorage.setItem('open_finance_number_format', '1,234.56');
    setDecimalPlacesOverride(4);
    expect(formatCurrency('12345678901234567890.123449999999999999', 'EUR')).toBe(
      '€12,345,678,901,234,567,890.1234'
    );
    expect(formatCurrency('-1.475349999999999999', 'EUR')).toBe('-€1.4753');
    expect(formatCurrency('-1.475350000000000000', 'EUR')).toBe('-€1.4754');
  });

  it('retains fractions in property forecasts independently of display preferences', () => {
    for (const decimals of [null, 1, 4, 8]) {
      setDecimalPlacesOverride(decimals);
      expect(calculateCompoundInterest(100, 1.47536328, 1)).toBeCloseTo(101.47536328, 10);
      expect(calculateMinimumResalePrice(1.23456789, 0, 0, 0)).toBe(1.23456789);
    }
  });
});
