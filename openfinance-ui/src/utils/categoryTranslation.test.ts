import { describe, it, expect } from 'vitest';
import { getCategoryKey, translateCategoryName } from './categoryTranslation';

describe('getCategoryKey', () => {
  it('returns key for known category', () => {
    expect(getCategoryKey('Food & Dining')).toBe('names.foodAndDining');
  });

  it('returns key for single-word category', () => {
    expect(getCategoryKey('Housing')).toBe('names.housing');
    expect(getCategoryKey('Healthcare')).toBe('names.healthcare');
  });

  it('returns null for unknown category', () => {
    expect(getCategoryKey('Unknown Category')).toBeNull();
  });

  it('handles all mapped categories', () => {
    const knownCategories = [
      'Food & Dining', 'Housing', 'Transportation', 'Entertainment',
      'Healthcare', 'Shopping', 'Education', 'Personal Care',
      'Travel', 'Utilities', 'Income', 'Salary', 'Business',
      'Investment', 'Other', 'Groceries', 'Restaurants', 'Rent',
      'Insurance', 'Subscriptions', 'Savings', 'Taxes', 'Gifts',
      'Cash & Savings',
    ];
    for (const cat of knownCategories) {
      expect(getCategoryKey(cat)).not.toBeNull();
    }
  });
});

describe('translateCategoryName', () => {
  const mockT = (key: string, options?: Record<string, unknown>) =>
    options?.defaultValue as string ?? key;

  it('translates known category', () => {
    const result = translateCategoryName(mockT, 'Food & Dining');
    expect(result).toBe('Food & Dining');
  });

  it('returns original for unknown category', () => {
    const result = translateCategoryName(mockT, 'Random Category');
    expect(result).toBe('Random Category');
  });

  it('handles hierarchical QIF paths', () => {
    const result = translateCategoryName(mockT, 'Food:Groceries');
    expect(result).toBe('Groceries');
  });

  it('returns original for unknown hierarchical path', () => {
    const result = translateCategoryName(mockT, 'Unknown:StillUnknown');
    expect(result).toBe('Unknown:StillUnknown');
  });
});
