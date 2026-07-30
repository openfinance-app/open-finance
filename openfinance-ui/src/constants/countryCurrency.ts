/**
 * Shared country → default-currency reference data.
 *
 * Single source of truth for country-code-to-currency lookups. Previously `OnboardingPage.tsx`
 * (32-entry locale-detection heuristic) and `configs/tools/buyVsRentConfig.ts` (5-entry
 * calculator country configs) each hand-maintained an independent country→currency mapping, with
 * no cross-reference — a currency change for a shared country (e.g. a Euro-zone country) would
 * need editing in two unrelated places to stay consistent.
 */
export const COUNTRY_CURRENCY_MAP: Record<string, string> = {
  US: 'USD',
  CA: 'CAD',
  GB: 'GBP',
  AU: 'AUD',
  NZ: 'NZD',
  JP: 'JPY',
  CN: 'CNY',
  IN: 'INR',
  BR: 'BRL',
  MX: 'MXN',
  KR: 'KRW',
  SG: 'SGD',
  HK: 'HKD',
  CH: 'CHF',
  NO: 'NOK',
  SE: 'SEK',
  DK: 'DKK',
  // Euro-zone
  FR: 'EUR',
  DE: 'EUR',
  IT: 'EUR',
  ES: 'EUR',
  PT: 'EUR',
  NL: 'EUR',
  BE: 'EUR',
  AT: 'EUR',
  FI: 'EUR',
  IE: 'EUR',
  LU: 'EUR',
  GR: 'EUR',
  SK: 'EUR',
  SI: 'EUR',
  EE: 'EUR',
  LV: 'EUR',
  LT: 'EUR',
  CY: 'EUR',
  MT: 'EUR',
};
