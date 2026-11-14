import { describe, it, expect } from 'vitest';
import {
  alpha2FromNumericId,
  countryCentroid,
  countryDisplayName,
  countryFromAddress,
  countryOfPoint,
  normalizeAlpha2,
} from './countryGeo';

describe('alpha2FromNumericId', () => {
  it('maps zero-padded string ids', () => {
    expect(alpha2FromNumericId('250')).toBe('FR');
    expect(alpha2FromNumericId('004')).toBe('AF');
  });

  it('maps numeric ids given as numbers', () => {
    expect(alpha2FromNumericId(250)).toBe('FR');
  });

  it('maps ids below 100 given as numbers (no zero padding in source data)', () => {
    expect(alpha2FromNumericId(4)).toBe('AF');
    expect(alpha2FromNumericId(32)).toBe('AR');
  });

  it('maps ids below 100 given as unpadded strings', () => {
    expect(alpha2FromNumericId('4')).toBe('AF');
  });

  it('returns undefined for unknown ids', () => {
    expect(alpha2FromNumericId('999')).toBeUndefined();
    expect(alpha2FromNumericId('-99')).toBeUndefined();
    expect(alpha2FromNumericId('')).toBeUndefined();
  });
});

describe('normalizeAlpha2', () => {
  it('upper-cases standard codes', () => {
    expect(normalizeAlpha2('fr')).toBe('FR');
  });

  it('resolves common non-standard codes', () => {
    expect(normalizeAlpha2('UK')).toBe('GB');
    expect(normalizeAlpha2('EL')).toBe('GR');
  });
});

describe('countryCentroid', () => {
  it('returns a plausible centroid for France', () => {
    const centroid = countryCentroid('FR');
    expect(centroid).not.toBeNull();
    const [lon, lat] = centroid!;
    // The 110m France feature spans overseas territories, pulling lon west.
    expect(lon).toBeGreaterThan(-10);
    expect(lon).toBeLessThan(10);
    expect(lat).toBeGreaterThan(41);
    expect(lat).toBeLessThan(51);
  });

  it('uses the manual centroid for jurisdictions missing from the atlas', () => {
    expect(countryCentroid('HK')).toEqual([114.17, 22.32]);
  });

  it('returns null for an unknown country code', () => {
    expect(countryCentroid('XX')).toBeNull();
  });

  it('accepts lower-case codes', () => {
    expect(countryCentroid('hk')).toEqual([114.17, 22.32]);
  });
});

describe('countryOfPoint', () => {
  it('reverse-geocodes Paris to FR', () => {
    expect(countryOfPoint(2.3522, 48.8566)).toBe('FR');
  });

  it('reverse-geocodes New York to US', () => {
    expect(countryOfPoint(-74.006, 40.7128)).toBe('US');
  });

  it('returns undefined for open ocean', () => {
    expect(countryOfPoint(-30, -30)).toBeUndefined();
  });
});

describe('countryDisplayName', () => {
  it('returns the country name for a known code', () => {
    expect(countryDisplayName('FR')).toBe('France');
    expect(countryDisplayName('fr')).toBe('France');
  });

  it('falls back to the upper-cased code for unknown codes', () => {
    expect(countryDisplayName('xx')).toBe('XX');
  });
});

describe('countryFromAddress', () => {
  it('returns undefined for missing addresses', () => {
    expect(countryFromAddress(undefined)).toBeUndefined();
    expect(countryFromAddress(null)).toBeUndefined();
    expect(countryFromAddress('')).toBeUndefined();
  });

  it('matches a country name in the trailing segment', () => {
    expect(countryFromAddress('12 Rue de la Paix, Paris, France')).toBe('FR');
  });

  it('matches a bare alpha-2 code in the trailing segment', () => {
    expect(countryFromAddress('12 Rue de la Paix, Paris, FR')).toBe('FR');
  });

  it('resolves aliases', () => {
    expect(countryFromAddress('221B Baker Street, London, UK')).toBe('GB');
    expect(countryFromAddress('1 Infinite Loop, Cupertino, USA')).toBe('US');
  });

  it('handles diacritics in country names', () => {
    expect(countryFromAddress("Plateau, Abidjan, Côte d'Ivoire")).toBe('CI');
  });

  it('prefers the trailing segment over an earlier country mention', () => {
    expect(countryFromAddress('France Exports Ltd, Berlin, Germany')).toBe('DE');
  });

  it('ignores punctuation-only segments', () => {
    expect(countryFromAddress('Somewhere Street, ---')).toBeUndefined();
  });

  it('returns undefined when no country is present', () => {
    expect(countryFromAddress('123 Nowhere Lane, Springfield')).toBeUndefined();
  });

  describe('whole-address fallback scan', () => {
    it('does not confuse Nigeria with Niger', () => {
      expect(countryFromAddress('Lagos Nigeria office')).toBe('NG');
    });

    it('does not confuse Somalia with Mali', () => {
      expect(countryFromAddress('Mogadishu Somalia port')).toBe('SO');
    });

    it('does not confuse Guinea-Bissau with Guinea', () => {
      expect(countryFromAddress('Bissau Guinea-Bissau docks')).toBe('GW');
    });

    it('does not match India inside Indiana', () => {
      expect(countryFromAddress('Indianapolis Indiana')).toBeUndefined();
    });

    it('still finds a genuine whole-word country mention', () => {
      expect(countryFromAddress('Khartoum Sudan branch')).toBe('SD');
      expect(countryFromAddress('Juba South Sudan office')).toBe('SS');
    });
  });
});
