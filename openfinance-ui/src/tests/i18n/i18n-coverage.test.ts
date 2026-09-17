/**
 * i18n Coverage Test
 *
 * Verifies that all English translation keys have corresponding French translations.
 * This test ensures translation parity across all 20 namespaces.
 *
 * Related: .spec/i18n-localization/tasks.md (Task 4.1.1)
 */

import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'fs';
import { join } from 'path';

const LOCALES_DIR = join(__dirname, '../../../public/locales');

/**
 * Recursively extracts all keys from a nested translation object
 */
function extractKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  let keys: string[] = [];

  for (const [key, value] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;

    if (value && typeof value === 'object' && !Array.isArray(value)) {
      keys = keys.concat(extractKeys(value as Record<string, unknown>, fullKey));
    } else {
      keys.push(fullKey);
    }
  }

  return keys;
}

/**
 * Loads and parses a translation JSON file
 */
function loadTranslations(locale: string, namespace: string): Record<string, unknown> {
  const filePath = join(LOCALES_DIR, locale, `${namespace}.json`);
  const content = readFileSync(filePath, 'utf-8');
  return JSON.parse(content);
}

/**
 * Gets all namespace files from a locale directory
 */
function getNamespaces(locale: string): string[] {
  const localeDir = join(LOCALES_DIR, locale);
  return readdirSync(localeDir)
    .filter(file => file.endsWith('.json'))
    .map(file => file.replace('.json', ''));
}

describe('i18n Translation Coverage', () => {
  const supportedLocales = ['en', 'fr'];

  it('should have both English and French locale directories', () => {
    const enDir = join(LOCALES_DIR, 'en');
    const frDir = join(LOCALES_DIR, 'fr');

    expect(() => readdirSync(enDir)).not.toThrow();
    expect(() => readdirSync(frDir)).not.toThrow();
  });

  it('should have the same namespaces in English and French', () => {
    const enNamespaces = getNamespaces('en').sort();
    const frNamespaces = getNamespaces('fr').sort();

    expect(enNamespaces).toEqual(frNamespaces);
  });

  describe('Key Parity Across Locales', () => {
    const namespaces = getNamespaces('en');

    namespaces.forEach(namespace => {
      it(`should have matching keys in ${namespace}.json for all locales`, () => {
        const enTranslations = loadTranslations('en', namespace);
        const frTranslations = loadTranslations('fr', namespace);

        const enKeys = extractKeys(enTranslations).sort();
        const frKeys = extractKeys(frTranslations).sort();

        // Check for missing keys in French
        const missingInFrench = enKeys.filter(key => !frKeys.includes(key));
        if (missingInFrench.length > 0) {
          console.error(`Missing French translations in ${namespace}:`, missingInFrench);
        }

        // Check for extra keys in French (typos or orphaned keys)
        const extraInFrench = frKeys.filter(key => !enKeys.includes(key));
        if (extraInFrench.length > 0) {
          console.warn(`Extra French keys in ${namespace} (not in English):`, extraInFrench);
        }

        expect(missingInFrench).toEqual([]);
        expect(extraInFrench).toEqual([]);
      });
    });
  });

  describe('Translation Value Validation', () => {
    const namespaces = getNamespaces('en');

    namespaces.forEach(namespace => {
      it(`should not have empty translation values in ${namespace}.json`, () => {
        supportedLocales.forEach(locale => {
          const translations = loadTranslations(locale, namespace);
          const keys = extractKeys(translations);

          keys.forEach(key => {
            const keyParts = key.split('.');
            let value: unknown = translations;

            for (const part of keyParts) {
              value = (value as Record<string, unknown>)[part];
            }

            expect(
              value,
              `Empty value for key "${key}" in ${locale}/${namespace}.json`
            ).toBeTruthy();

            if (typeof value === 'string') {
              expect(
                value.trim(),
                `Whitespace-only value for key "${key}" in ${locale}/${namespace}.json`
              ).not.toBe('');
            }
          });
        });
      });
    });
  });

  describe('Plural Key Convention', () => {
    const namespaces = getNamespaces('en');

    namespaces.forEach(namespace => {
      it(`should have matching plural keys in ${namespace}.json`, () => {
        const enTranslations = loadTranslations('en', namespace);
        const frTranslations = loadTranslations('fr', namespace);

        const enKeys = extractKeys(enTranslations);
        const frKeys = extractKeys(frTranslations);

        // Find all plural keys in English (ending with _one, _other, _zero)
        const enPluralKeys = enKeys.filter(
          key => key.endsWith('_one') || key.endsWith('_other') || key.endsWith('_zero')
        );

        // Verify French has the same plural keys
        enPluralKeys.forEach(pluralKey => {
          expect(frKeys, `Missing plural key "${pluralKey}" in French ${namespace}.json`).toContain(
            pluralKey
          );
        });
      });
    });
  });

  describe('Interpolation Pattern Validation', () => {
    const namespaces = getNamespaces('en');

    namespaces.forEach(namespace => {
      it(`should preserve interpolation patterns in ${namespace}.json`, () => {
        const enTranslations = loadTranslations('en', namespace);
        const frTranslations = loadTranslations('fr', namespace);

        const enKeys = extractKeys(enTranslations);

        enKeys.forEach(key => {
          const keyParts = key.split('.');
          let enValue: unknown = enTranslations;
          let frValue: unknown = frTranslations;

          for (const part of keyParts) {
            enValue = (enValue as Record<string, unknown>)[part];
            frValue = (frValue as Record<string, unknown>)?.[part];
          }

          if (typeof enValue === 'string' && typeof frValue === 'string') {
            // Extract interpolation patterns like {{variable}}, {count}, etc.
            const enPatterns = enValue.match(/\{\{?\w+\}?\}/g) || [];
            const frPatterns = frValue.match(/\{\{?\w+\}?\}/g) || [];

            // Verify French has the same interpolation variables
            const enVariables = enPatterns.map(p => p.replace(/[{}]/g, '')).sort();
            const frVariables = frPatterns.map(p => p.replace(/[{}]/g, '')).sort();

            expect(
              frVariables,
              `Interpolation mismatch for key "${key}" in ${namespace}.json\nEN: ${enValue}\nFR: ${frValue}`
            ).toEqual(enVariables);
          }
        });
      });
    });
  });
});
