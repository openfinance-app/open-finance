import { describe, it, expect } from 'vitest';
import { CurrencyBadge } from './CurrencyBadge';

describe('CurrencyBadge', () => {
  it('renders null (deprecated stub)', () => {
    const result = CurrencyBadge({
      fromCurrency: 'USD',
      toCurrency: 'EUR',
    });
    expect(result).toBeNull();
  });
});
