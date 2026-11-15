import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useViewStateStore } from '@/stores/viewState';
import type {
  AdvancedSearchRequest,
  GlobalSearchResponse,
  SavedSearch,
  SearchResult,
} from '@/types/search';
import { getResultRoute } from '@/types/search';
import i18n from '@/test/i18n-test';
import SearchResultsPage from '@/pages/SearchResultsPage';

const state = vi.hoisted(() => ({
  simple: undefined as GlobalSearchResponse | undefined,
  advanced: undefined as GlobalSearchResponse | undefined,
  simpleError: null as unknown,
  advancedError: null as unknown,
  pending: false,
  saved: [] as SavedSearch[],
  simpleSearch: vi.fn(),
  mutate: vi.fn(),
  save: vi.fn(),
  remove: vi.fn(),
  used: vi.fn(),
}));
vi.mock('@/hooks/useSearch', () => ({
  useGlobalSearch: (...args: unknown[]) => {
    state.simpleSearch(...args);
    return { data: state.simple, error: state.simpleError, isLoading: false };
  },
  useAdvancedSearch: () => ({
    data: state.advanced,
    error: state.advancedError,
    isPending: state.pending,
    mutate: state.mutate,
  }),
  useSavedSearches: () => ({
    savedSearches: state.saved,
    saveSearch: state.save,
    deleteSearch: state.remove,
    updateLastUsed: state.used,
  }),
}));
vi.mock('@/components/search/AdvancedFilterPanel', () => ({
  AdvancedFilterPanel: ({
    filters,
    onFiltersChange,
    onApply,
    onSaveSearch,
  }: ComponentProps<
    typeof import('@/components/search/AdvancedFilterPanel').AdvancedFilterPanel
  >) => (
    <section aria-label="Advanced filters">
      <label>
        Filter query
        <input
          value={filters.query}
          onChange={event =>
            onFiltersChange({ ...filters, query: event.target.value, minAmount: '1.01' })
          }
        />
      </label>
      <button onClick={() => onApply()}>Apply filters</button>
      <button onClick={() => onApply({ ...filters, maxAmount: '9.99' })}>Apply override</button>
      <button onClick={onSaveSearch}>Save filters</button>
    </section>
  ),
}));
vi.mock('@/components/search/SavedSearchesDropdown', () => ({
  SavedSearchesDropdown: ({
    savedSearches,
    onLoad,
    onDelete,
  }: ComponentProps<
    typeof import('@/components/search/SavedSearchesDropdown').SavedSearchesDropdown
  >) => (
    <div>
      {savedSearches.map(saved => (
        <div key={saved.id}>
          <button onClick={() => onLoad(saved)}>Load {saved.name}</button>
          <button onClick={() => onDelete(saved.id)}>Delete {saved.name}</button>
        </div>
      ))}
    </div>
  ),
}));
vi.mock('@/components/search/SaveSearchDialog', () => ({
  SaveSearchDialog: ({
    isOpen,
    onSave,
    onClose,
  }: ComponentProps<typeof import('@/components/search/SaveSearchDialog').SaveSearchDialog>) =>
    isOpen ? (
      <div role="dialog">
        <button onClick={() => onSave('Expenses')}>Confirm save</button>
        <button onClick={onClose}>Cancel save</button>
      </div>
    ) : null,
}));

const result = (changes: Partial<SearchResult> = {}): SearchResult => ({
  id: 3,
  resultType: 'ACCOUNT',
  title: 'Matching account',
  createdAt: '',
  ...changes,
});
function response(
  rows: SearchResult[],
  extras: Partial<GlobalSearchResponse> = {}
): GlobalSearchResponse {
  const resultsByType: Record<string, SearchResult[]> = {};
  for (const row of rows) (resultsByType[row.resultType] ??= []).push(row);
  return {
    query: 'account',
    resultsByType,
    countsPerType: {},
    totalResults: rows.length,
    executionTimeMs: 2,
    hasMore: false,
    limit: 100,
    ...extras,
  };
}
const mount = () => {
  window.history.replaceState(null, '', '/search?q=account');
  return renderWithProviders(<SearchResultsPage />);
};

describe('Search query transitions and advanced results', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    useViewStateStore.getState().clear();
    state.simple = undefined;
    state.advanced = undefined;
    state.pending = false;
    state.simpleError = null;
    state.advancedError = null;
    state.saved = [];
  });

  it.each(['', 'a'])('does not execute incomplete advanced query %s', query => {
    mount();
    fireEvent.change(screen.getByLabelText('Filter query'), { target: { value: query } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(state.mutate).not.toHaveBeenCalled();
  });

  it.each([false, true])(
    'applies edited filters with an optional immediate override %s',
    override => {
      state.simple = response([result()]);
      state.advanced = response([result({ title: 'Filtered account' })]);
      mount();
      fireEvent.change(screen.getByLabelText('Filter query'), { target: { value: 'rent' } });
      fireEvent.change(screen.getByLabelText('Filter query'), { target: { value: 'account' } });
      fireEvent.click(
        screen.getByRole('button', { name: override ? 'Apply override' : 'Apply filters' })
      );
      expect(state.mutate).toHaveBeenCalledWith({
        query: 'account',
        limit: 100,
        minAmount: '1.01',
        ...(override ? { maxAmount: '9.99' } : {}),
      });
      expect(screen.getByRole('button', { name: 'Filtered account' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Matching account' })).not.toBeInTheDocument();
      expect(state.simpleSearch).toHaveBeenLastCalledWith('account', 100, false, false);
    }
  );

  it.each(['pending', 'empty', 'error', 'unexpected-error'])(
    'renders advanced-search %s independently of simple results',
    mode => {
      state.simple = response([result()]);
      if (mode === 'pending') state.pending = true;
      if (mode === 'empty') state.advanced = response([]);
      if (mode === 'error') state.advancedError = new Error('Filtered request failed');
      if (mode === 'unexpected-error') state.advancedError = 'Unexpected response';
      mount();
      fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
      expect(screen.queryByRole('button', { name: 'Matching account' })).not.toBeInTheDocument();
      if (mode === 'empty')
        expect(screen.getByText(i18n.t('errors:search.noResults'))).toBeInTheDocument();
      if (mode === 'error') expect(screen.getByText('Filtered request failed')).toBeInTheDocument();
      if (mode === 'unexpected-error')
        expect(screen.getByText(i18n.t('errors:generic.description'))).toBeInTheDocument();
    }
  );

  it('returning to a text query clears prior advanced filters and trims URL input', () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    fireEvent.change(screen.getByPlaceholderText(i18n.t('errors:search.placeholder')), {
      target: { value: '  new account  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: i18n.t('common:search') }));
    expect(window.location.search).toBe('?q=new+account');
    expect(state.simpleSearch).toHaveBeenLastCalledWith('new account', 100, true, false);
  });

  it('passes regex mode to searches and can switch it off again', () => {
    mount();
    const toggle = screen.getByRole('button', { name: i18n.t('common:regexSearch.toggleLabel') });
    fireEvent.click(toggle);
    expect(state.simpleSearch).toHaveBeenLastCalledWith('account', 100, true, true);
    fireEvent.click(toggle);
    expect(state.simpleSearch).toHaveBeenLastCalledWith('account', 100, true, false);
  });

  it('saves the edited filters and can cancel a subsequent save without creating another search', () => {
    mount();
    fireEvent.change(screen.getByLabelText('Filter query'), { target: { value: 'rent' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save filters' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm save' }));
    expect(state.save).toHaveBeenCalledWith('Expenses', {
      query: 'rent',
      minAmount: '1.01',
      limit: 100,
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Save filters' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel save' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(state.save).toHaveBeenCalledTimes(1);
  });

  it('loads a saved query and all its financial filters and updates its last-used timestamp', () => {
    const filters: AdvancedSearchRequest = {
      query: 'rent',
      accountIds: [4],
      minAmount: '10.01',
      regex: true,
    };
    state.saved = [{ id: 'rent-search', name: 'Rent', filters, createdAt: '' }];
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Load Rent' }));
    expect(window.location.search).toBe('?q=rent&regex=true');
    expect(state.mutate).toHaveBeenCalledWith(filters);
    expect(state.used).toHaveBeenCalledWith('rent-search');
    fireEvent.click(screen.getByRole('button', { name: 'Delete Rent' }));
    expect(state.remove).toHaveBeenCalledWith('rent-search');
  });

  it('uses result counts when the server omitted per-type counts and ignores empty groups', () => {
    state.simple = response([result()], { resultsByType: { ACCOUNT: [result()], ASSET: [] } });
    mount();
    expect(screen.getByRole('button', { name: /Accounts.*1 result/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Assets.*result/i })).not.toBeInTheDocument();
  });

  it.each([
    ['CATEGORY', 'EXPENSE', 'transactions:form.types.EXPENSE'],
    ['CATEGORY', 'custom-category-label', 'custom-category-label'],
    ['BUDGET', 'MONTHLY', 'budgets:form.periods.MONTHLY'],
    ['BUDGET', 'custom-period-label', 'custom-period-label'],
  ] as const)(
    'localizes %s subtitle %s with a fallback for unknown enums',
    (resultType, subtitle, key) => {
      state.simple = response([result({ resultType, subtitle })]);
      mount();
      expect(screen.getByText(key.includes(':') ? i18n.t(key) : key)).toBeInTheDocument();
    }
  );

  it('supports optional result metadata, zero amounts, unknown icons, tags and navigation', async () => {
    const row = result({
      resultType: 'TRANSACTION',
      amount: 0,
      currency: 'EUR',
      subtitle: 'Checking',
      snippet: 'Invoice details',
      icon: 'NotAnIcon',
      color: '#112233',
      tags: ['one', 'two', 'three', 'four'],
      date: '2025-01-01',
    });
    state.simple = response([row]);
    mount();
    expect(screen.getByText('Invoice details')).toBeInTheDocument();
    expect(screen.getByText('+1')).toBeInTheDocument();
    expect(screen.queryByText('four')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Matching account.*0\.00/ }));
    await waitFor(() =>
      expect(window.location.pathname + window.location.search).toBe(getResultRoute(row))
    );
  });
  it('loads a regular-expression query received from the global search URL', () => {
    window.history.replaceState({}, '', '/search?q=%5Erent%24&regex=true');
    renderWithProviders(<SearchResultsPage />);
    expect(state.simpleSearch).toHaveBeenLastCalledWith('^rent$', 100, true, true);
  });

  it('keeps regular-expression mode when submitting an edited query', () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: i18n.t('common:regexSearch.toggleLabel') }));
    fireEvent.change(screen.getByPlaceholderText(i18n.t('errors:search.placeholder')), {
      target: { value: '^rent$' },
    });
    fireEvent.click(screen.getByRole('button', { name: i18n.t('common:search') }));
    expect(new URLSearchParams(window.location.search).get('regex')).toBe('true');
    expect(state.simpleSearch).toHaveBeenLastCalledWith('^rent$', 100, true, true);
  });
});
