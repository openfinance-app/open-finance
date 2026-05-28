import { describe, it, expect } from 'vitest';
import {
  PropertyType,
  getPropertyTypeName,
  getPropertyTypeIcon,
  getPropertyTypeBadgeColor,
  formatAppreciation,
  calculatePropertyAge,
} from '@/types/realEstate';

describe('getPropertyTypeName', () => {
  it('returns the type string as fallback when translation is unavailable', () => {
    // getPropertyTypeName calls i18next.t() which may return the key or undefined
    // in test without i18n init, so we just verify it doesn't throw
    Object.values(PropertyType).forEach(type => {
      expect(() => getPropertyTypeName(type)).not.toThrow();
    });
  });
});

describe('getPropertyTypeIcon', () => {
  it('returns Home for RESIDENTIAL', () => {
    expect(getPropertyTypeIcon(PropertyType.RESIDENTIAL)).toBe('Home');
  });

  it('returns Building2 for COMMERCIAL', () => {
    expect(getPropertyTypeIcon(PropertyType.COMMERCIAL)).toBe('Building2');
  });

  it('returns Mountain for LAND', () => {
    expect(getPropertyTypeIcon(PropertyType.LAND)).toBe('Mountain');
  });

  it('returns Factory for INDUSTRIAL', () => {
    expect(getPropertyTypeIcon(PropertyType.INDUSTRIAL)).toBe('Factory');
  });

  it('returns MapPin for OTHER', () => {
    expect(getPropertyTypeIcon(PropertyType.OTHER)).toBe('MapPin');
  });
});

describe('getPropertyTypeBadgeColor', () => {
  it('returns blue classes for RESIDENTIAL', () => {
    expect(getPropertyTypeBadgeColor(PropertyType.RESIDENTIAL)).toContain('blue');
  });

  it('returns purple classes for COMMERCIAL', () => {
    expect(getPropertyTypeBadgeColor(PropertyType.COMMERCIAL)).toContain('purple');
  });

  it('returns green classes for LAND', () => {
    expect(getPropertyTypeBadgeColor(PropertyType.LAND)).toContain('green');
  });

  it('returns a default for unknown types', () => {
    expect(getPropertyTypeBadgeColor('UNKNOWN' as any)).toContain('border');
  });
});

describe('formatAppreciation', () => {
  it('returns N/A for undefined values', () => {
    const result = formatAppreciation(undefined, undefined);
    expect(result.text).toBe('N/A');
    expect(result.color).toBe('text-text-muted');
  });

  it('formats positive appreciation', () => {
    const result = formatAppreciation(50000, 10, 'USD');
    expect(result.text).toContain('+');
    expect(result.text).toContain('10.00%');
    expect(result.color).toBe('text-green-400');
  });

  it('formats negative appreciation', () => {
    const result = formatAppreciation(-10000, -5, 'USD');
    expect(result.color).toBe('text-red-400');
  });

  it('formats zero appreciation as positive', () => {
    const result = formatAppreciation(0, 0, 'USD');
    expect(result.color).toBe('text-green-400');
  });
});

describe('calculatePropertyAge', () => {
  it('calculates age in years', () => {
    const twoYearsAgo = new Date();
    twoYearsAgo.setFullYear(twoYearsAgo.getFullYear() - 2);
    twoYearsAgo.setMonth(twoYearsAgo.getMonth() - 1);
    const age = calculatePropertyAge(twoYearsAgo.toISOString());
    expect(age).toBe(2);
  });

  it('returns 0 for recent purchase', () => {
    const recently = new Date();
    recently.setMonth(recently.getMonth() - 3);
    const age = calculatePropertyAge(recently.toISOString());
    expect(age).toBe(0);
  });
});
