import { describe, expect, it } from 'vitest';
import { decimalField, distributeExact, equalDecimals, exactSum } from '@/utils/exact-decimal';

describe('exact decimal values', () => {
  it('survives JSON parsing without losing the original account balance', () => {
    const response = JSON.parse(
      '{"ownBalance":10006858.9112567483,"exactValues":{"ownBalance":"10006858.9112567483"}}'
    );
    expect(String(response.ownBalance)).toBe('10006858.91125675');
    expect(decimalField(response, 'ownBalance')).toBe('10006858.9112567483');
  });

  it('writes tiny legacy numbers as plain decimals accepted by the forms', () => {
    expect(decimalField({ amount: 1e-18 }, 'amount')).toBe('0.000000000000000001');
    expect(equalDecimals('1.00', 1)).toBe(true);
    expect(equalDecimals('10006858.9112567483', '10006858.9112567484')).toBe(false);
  });

  it('distributes without rounding large or fractional split amounts through JS numbers', () => {
    const amounts = distributeExact('10006858.9112567483', ['10006858.9112567482', '0'], 16);
    expect(amounts).toEqual(['10006858.9112567483', '0']);
    expect(exactSum(amounts)).toBe('10006858.9112567483');
    expect(exactSum(['99999999999999999999999998.123456789012345678', '1'])).toBe(
      '99999999999999999999999999.123456789012345678'
    );
  });
});
