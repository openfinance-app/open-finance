/**
 * Utility for translating backend-provided category names (always English)
 * into the user's current locale using the 'categories' i18n namespace.
 *
 * The backend returns English strings like "Food & Dining", "Healthcare", etc.
 * This module maps those strings to camelCase i18n keys and falls back to the
 * original English string when no mapping is found.
 */

/**
 * Maps known English category names (from the backend) to camelCase keys in
 * the `categories.names.*` i18n namespace.
 */
const CATEGORY_NAME_TO_KEY: Record<string, string> = {
  'Food & Dining': 'foodAndDining',
  Housing: 'housing',
  Transportation: 'transportation',
  Entertainment: 'entertainment',
  Healthcare: 'healthcare',
  Shopping: 'shopping',
  Education: 'education',
  'Personal Care': 'personalCare',
  Travel: 'travel',
  Utilities: 'utilities',
  Income: 'income',
  Salary: 'salary',
  Business: 'business',
  Investment: 'investment',
  Other: 'other',
  Groceries: 'groceries',
  Restaurants: 'restaurants',
  Rent: 'rent',
  Insurance: 'insurance',
  Subscriptions: 'subscriptions',
  Savings: 'savings',
  Taxes: 'taxes',
  Gifts: 'gifts',
  'Cash & Savings': 'cashAndSavings',
};

/**
 * Returns the i18n key for a given English backend category name, or null if
 * there is no mapping.
 *
 * @example
 *   getCategoryKey('Food & Dining') // → 'names.foodAndDining'
 *   getCategoryKey('Unknown')       // → null
 */
export function getCategoryKey(englishName: string): string | null {
  const key = CATEGORY_NAME_TO_KEY[englishName];
  return key ? `names.${key}` : null;
}

/**
 * Translates a backend category name using the provided `t` function from
 * `useTranslation('categories')`. Falls back to the original name if no
 * mapping exists.
 *
 * @param t - The `t` function bound to the 'categories' namespace.
 * @param categoryName - The English category name returned by the backend.
 * @returns The translated category name, or the original if not found.
 *
 * @example
 *   const { t } = useTranslation('categories');
 *   translateCategoryName(t, 'Food & Dining') // → 'Alimentation & Restauration' (fr)
 */
export function translateCategoryName(
  t: (key: string, options?: Record<string, unknown>) => string,
  categoryName: string
): string {
  // First try exact match
  const key = getCategoryKey(categoryName);
  if (key) return t(key, { defaultValue: categoryName });

  // Handle hierarchical QIF paths like "Food:Groceries" — try translating the last segment
  if (categoryName.includes(':')) {
    const lastSegment = categoryName.split(':').pop()!;
    const segmentKey = getCategoryKey(lastSegment);
    if (segmentKey) return t(segmentKey, { defaultValue: lastSegment });
  }

  return categoryName;
}
