import { describe, it, expect } from 'vitest';
import { COUNTRY_CURRENCY_MAP } from './countryCurrency';

describe('COUNTRY_CURRENCY_MAP', () => {
  it('maps major economies to their ISO 4217 currency', () => {
    expect(COUNTRY_CURRENCY_MAP.US).toBe('USD');
    expect(COUNTRY_CURRENCY_MAP.GB).toBe('GBP');
    expect(COUNTRY_CURRENCY_MAP.JP).toBe('JPY');
  });

  it('maps every Euro-zone country used by the app to EUR', () => {
    for (const code of ['FR', 'DE', 'IT', 'ES', 'BE', 'NL', 'AT', 'IE']) {
      expect(COUNTRY_CURRENCY_MAP[code]).toBe('EUR');
    }
  });

  it('contains exactly the previously-hardcoded country codes', () => {
    expect(Object.keys(COUNTRY_CURRENCY_MAP)).toHaveLength(36);
  });
});
