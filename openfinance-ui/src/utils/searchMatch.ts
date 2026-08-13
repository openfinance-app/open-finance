/**
 * Shared helper for optional-regex text matching used by client-side search/filter
 * features (Budgets, Categories) and by search inputs that need to preview/validate
 * a pattern before sending it to the backend.
 *
 * Mirrors the backend's `RegexSearchUtil`: case-insensitive partial match by default,
 * or a case-insensitive regex `find`-style match when `isRegex` is true. Invalid
 * patterns are treated as "no match" rather than throwing, so mid-typing malformed
 * regex simply yields an empty result set.
 */
export function matchesQuery(
    text: string | null | undefined,
    query: string | null | undefined,
    isRegex: boolean
): boolean {
    if (!text || !query) return false;

    if (isRegex) {
        try {
            return new RegExp(query, 'i').test(text);
        } catch {
            return false;
        }
    }

    return text.toLowerCase().includes(query.toLowerCase());
}

/**
 * Returns true if `pattern` is a syntactically valid regular expression.
 * Useful for showing inline validation feedback on a regex-enabled search input.
 */
export function isValidRegex(pattern: string): boolean {
    try {
        new RegExp(pattern);
        return true;
    } catch {
        return false;
    }
}
