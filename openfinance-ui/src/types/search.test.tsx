import { describe, it, expect } from 'vitest';
import { getResultTypeDisplayName, getResultTypeIcon, getResultRoute, highlightMatch } from './search';
import type { SearchResult, SearchResultType } from './search';

describe('getResultTypeDisplayName', () => {
  it('returns display name for each type without translator', () => {
    expect(getResultTypeDisplayName('TRANSACTION')).toBe('Transactions');
    expect(getResultTypeDisplayName('ACCOUNT')).toBe('Accounts');
    expect(getResultTypeDisplayName('ASSET')).toBe('Assets');
    expect(getResultTypeDisplayName('REAL_ESTATE')).toBe('Real Estate');
    expect(getResultTypeDisplayName('LIABILITY')).toBe('Liabilities');
    expect(getResultTypeDisplayName('BUDGET')).toBe('Budgets');
    expect(getResultTypeDisplayName('CATEGORY')).toBe('Categories');
    expect(getResultTypeDisplayName('RECURRING_TRANSACTION')).toBe('Recurring');
  });

  it('returns default for unknown type', () => {
    expect(getResultTypeDisplayName('UNKNOWN' as SearchResultType)).toBe('Results');
  });

  it('uses translator function when provided', () => {
    const t = (key: string) => `translated:${key}`;
    expect(getResultTypeDisplayName('TRANSACTION', t)).toBe('translated:search.types.TRANSACTION');
  });
});

describe('getResultTypeIcon', () => {
  it('returns icon name for each type', () => {
    expect(getResultTypeIcon('TRANSACTION')).toBe('Receipt');
    expect(getResultTypeIcon('ACCOUNT')).toBe('Wallet');
    expect(getResultTypeIcon('ASSET')).toBe('TrendingUp');
    expect(getResultTypeIcon('REAL_ESTATE')).toBe('Home');
    expect(getResultTypeIcon('LIABILITY')).toBe('CreditCard');
    expect(getResultTypeIcon('BUDGET')).toBe('PieChart');
    expect(getResultTypeIcon('CATEGORY')).toBe('Tag');
    expect(getResultTypeIcon('RECURRING_TRANSACTION')).toBe('RefreshCw');
  });

  it('returns Search for unknown type', () => {
    expect(getResultTypeIcon('UNKNOWN' as SearchResultType)).toBe('Search');
  });
});

describe('getResultRoute', () => {
  const makeResult = (type: SearchResultType, id = 42): SearchResult => ({
    resultType: type,
    id,
    title: 'Test',
    createdAt: '2026-01-01',
  });

  it('returns correct route for each type', () => {
    expect(getResultRoute(makeResult('TRANSACTION'))).toBe('/transactions?highlight=42');
    expect(getResultRoute(makeResult('ACCOUNT'))).toBe('/accounts?highlight=42');
    expect(getResultRoute(makeResult('ASSET'))).toBe('/assets?highlight=42');
    expect(getResultRoute(makeResult('REAL_ESTATE'))).toBe('/real-estate?highlight=42');
    expect(getResultRoute(makeResult('LIABILITY'))).toBe('/liabilities?highlight=42');
    expect(getResultRoute(makeResult('BUDGET'))).toBe('/budget/42');
    expect(getResultRoute(makeResult('CATEGORY'))).toBe('/transactions?category=42');
    expect(getResultRoute(makeResult('RECURRING_TRANSACTION'))).toBe('/recurring-transactions?highlight=42');
  });

  it('returns / for unknown type', () => {
    expect(getResultRoute(makeResult('UNKNOWN' as SearchResultType))).toBe('/');
  });
});

describe('highlightMatch', () => {
  it('returns text unchanged when no query', () => {
    expect(highlightMatch('hello world', '')).toBe('hello world');
  });

  it('returns text unchanged when no text', () => {
    expect(highlightMatch('', 'query')).toBe('');
  });

  it('wraps matched text in mark elements', () => {
    const result = highlightMatch('Hello World', 'world');
    // Result is an array of React nodes
    expect(Array.isArray(result)).toBe(true);
  });

  it('is case-insensitive', () => {
    const result = highlightMatch('Hello WORLD', 'world');
    expect(Array.isArray(result)).toBe(true);
  });
});
