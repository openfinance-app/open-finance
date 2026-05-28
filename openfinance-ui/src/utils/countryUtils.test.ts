import { describe, it, expect } from 'vitest';
import { countryFlagClass, ALL_COUNTRIES } from '@/utils/countryUtils';

describe('countryFlagClass', () => {
  it('returns lowercase flag class for uppercase code', () => {
    expect(countryFlagClass('FR')).toBe('fi fi-fr');
  });

  it('handles already lowercase codes', () => {
    expect(countryFlagClass('us')).toBe('fi fi-us');
  });

  it('handles mixed case', () => {
    expect(countryFlagClass('Gb')).toBe('fi fi-gb');
  });
});

describe('ALL_COUNTRIES', () => {
  it('contains a reasonable number of countries', () => {
    expect(ALL_COUNTRIES.length).toBeGreaterThan(180);
  });

  it('contains United States', () => {
    const us = ALL_COUNTRIES.find(c => c.code === 'US');
    expect(us).toBeDefined();
    expect(us!.name).toBe('United States');
  });

  it('contains France', () => {
    const fr = ALL_COUNTRIES.find(c => c.code === 'FR');
    expect(fr).toBeDefined();
    expect(fr!.name).toBe('France');
  });

  it('has unique country codes', () => {
    const codes = ALL_COUNTRIES.map(c => c.code);
    expect(new Set(codes).size).toBe(codes.length);
  });

  it('has proper ISO 3166-1 alpha-2 format', () => {
    ALL_COUNTRIES.forEach(c => {
      expect(c.code).toMatch(/^[A-Z]{2}$/);
      expect(c.name.length).toBeGreaterThan(0);
    });
  });
});
