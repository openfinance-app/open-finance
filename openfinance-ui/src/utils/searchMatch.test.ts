import { describe, it, expect } from 'vitest';
import { matchesQuery, isValidRegex } from './searchMatch';

describe('matchesQuery', () => {
    it('returns false for null/undefined text or query', () => {
        expect(matchesQuery(null, 'foo', false)).toBe(false);
        expect(matchesQuery('foo', null, false)).toBe(false);
        expect(matchesQuery(undefined, undefined, false)).toBe(false);
        expect(matchesQuery('foo', '', false)).toBe(false);
    });

    it('performs case-insensitive substring match when regex is disabled', () => {
        expect(matchesQuery('Grocery Store', 'grocery', false)).toBe(true);
        expect(matchesQuery('Grocery Store', 'STORE', false)).toBe(true);
        expect(matchesQuery('Grocery Store', 'restaurant', false)).toBe(false);
    });

    it('performs regex match when regex is enabled', () => {
        expect(matchesQuery('Bank of America', 'boa|taxi', true)).toBe(false);
        expect(matchesQuery('Taxi ride', 'boa|taxi', true)).toBe(true);
        expect(matchesQuery('BOA transfer', 'boa|taxi', true)).toBe(true);
    });

    it('supports basic wildcards like .* and character classes', () => {
        expect(matchesQuery('Invoice #12345', '\\d+', true)).toBe(true);
        expect(matchesQuery('No digits here', '\\d+', true)).toBe(false);
        expect(matchesQuery('Coffee Shop', 'co.*shop', true)).toBe(true);
    });

    it('treats invalid regex patterns as no match rather than throwing', () => {
        expect(() => matchesQuery('anything', '(unclosed', true)).not.toThrow();
        expect(matchesQuery('anything', '(unclosed', true)).toBe(false);
    });
});

describe('isValidRegex', () => {
    it('returns true for valid patterns', () => {
        expect(isValidRegex('boa|taxi')).toBe(true);
        expect(isValidRegex('^start')).toBe(true);
        expect(isValidRegex('')).toBe(true);
    });

    it('returns false for invalid patterns', () => {
        expect(isValidRegex('(unclosed')).toBe(false);
        expect(isValidRegex('[a-')).toBe(false);
    });
});
