import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen } from '@testing-library/react';
import { createRef } from 'react';
import { GlobalSearch, type GlobalSearchHandle } from '@/components/search/GlobalSearch';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { GlobalSearchResponse, SearchResult } from '@/types/search';
const state = vi.hoisted(() => ({
  initial: 'rent',
  regex: false,
  recent: [] as string[],
  loading: false,
  result: undefined as GlobalSearchResponse | undefined,
  navigate: vi.fn(),
  update: vi.fn(),
  save: vi.fn(),
  regexChange: vi.fn(),
}));
vi.mock('react-router', async original => ({
  ...(await original<typeof import('react-router')>()),
  useNavigate: () => state.navigate,
}));
vi.mock('@/hooks/useSearch', async () => {
  const React = await import('react');
  return {
    useSearchWithDebounce: () => {
      const [query, setQuery] = React.useState(state.initial);
      const [isRegex, setIsRegex] = React.useState(state.regex);
      return {
        query,
        debouncedQuery: query,
        isRegex,
        isDebouncing: false,
        updateQuery: (value: string) => {
          state.update(value);
          setQuery(value);
        },
        setIsRegex: (value: boolean) => {
          state.regexChange(value);
          setIsRegex(value);
        },
        searchResult: { data: state.result, isLoading: state.loading },
        saveRecentSearch: state.save,
        getRecentSearches: () => state.recent,
      };
    },
  };
});
const result = (id: number, changes: Partial<SearchResult> = {}): SearchResult => ({
  id,
  resultType: 'ACCOUNT',
  title: `Account ${id}`,
  createdAt: '2024-01-01',
  ...changes,
});
function results(rows: SearchResult[]) {
  state.result = {
    query: 'rent',
    totalResults: rows.length,
    resultsByType: { ACCOUNT: rows },
    countsPerType: { ACCOUNT: rows.length },
    executionTimeMs: 1,
    hasMore: false,
    limit: 50,
  };
}
function mount(url = '/') {
  window.history.replaceState({}, '', url);
  return renderWithProviders(<GlobalSearch />);
}
function focus() {
  fireEvent.focus(screen.getByRole('textbox'));
}
function key(key: string) {
  fireEvent.keyDown(screen.getByRole('textbox'), { key });
}
beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.initial = 'rent';
  state.regex = false;
  state.recent = [];
  state.loading = false;
  state.result = undefined;
});
describe('Global search keyboard and navigation boundaries', () => {
  it('imperative focus makes recent searches available', () => {
    state.initial = '';
    state.recent = ['Groceries'];
    const ref = createRef<GlobalSearchHandle>();
    window.history.replaceState({}, '', '/');
    renderWithProviders(<GlobalSearch ref={ref} />);
    act(() => ref.current!.focus());
    expect(screen.getByRole('textbox')).toHaveFocus();
    expect(screen.getByText('Groceries')).toBeInTheDocument();
  });
  it('viewing all results retains regular-expression mode', () => {
    state.initial = '^rent|tax$';
    state.regex = true;
    mount();
    key('Enter');
    expect(state.navigate).toHaveBeenCalledWith('/search?q=%5Erent%7Ctax%24&regex=true');
    expect(state.save).toHaveBeenCalledWith('^rent|tax$');
  });
  it('the clear action is accessible and restores focus without navigation', () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.getByRole('textbox')).toHaveValue('');
    expect(screen.getByRole('textbox')).toHaveFocus();
    expect(state.navigate).not.toHaveBeenCalled();
  });
  it.each(['ArrowUp', 'ArrowDown', 'Escape', 'x'])('a closed dropdown ignores %s', value => {
    mount();
    key(value);
    expect(state.navigate).not.toHaveBeenCalled();
    expect(state.update).not.toHaveBeenCalled();
  });
  it('a one-character query cannot navigate with Enter', () => {
    state.initial = 'x';
    mount();
    key('Enter');
    focus();
    key('Enter');
    expect(screen.getByText('Type at least 2 characters to search')).toBeInTheDocument();
    expect(state.navigate).not.toHaveBeenCalled();
  });
  it('ArrowDown stops at the fifth visible result rather than reaching hidden rows', () => {
    results(Array.from({ length: 7 }, (_, index) => result(index + 1)));
    mount();
    focus();
    expect(screen.queryByText('Account 6')).not.toBeInTheDocument();
    for (let i = 0; i < 9; i++) key('ArrowDown');
    key('Enter');
    expect(state.navigate).toHaveBeenCalledWith('/accounts?highlight=5');
    expect(state.update).toHaveBeenCalledWith('');
  });
  it('ArrowUp can clear result selection and Enter then opens all results', () => {
    results([result(1), result(2)]);
    mount();
    focus();
    key('ArrowDown');
    key('ArrowDown');
    key('ArrowUp');
    key('ArrowUp');
    key('ArrowUp');
    key('Enter');
    expect(state.navigate).toHaveBeenCalledWith('/search?q=rent');
  });
  it('results that disappear during navigation cannot open a stale entity', () => {
    results([result(1), result(2)]);
    const view = mount();
    focus();
    key('ArrowDown');
    key('ArrowDown');
    results([result(1)]);
    view.rerender(<GlobalSearch />);
    key('Enter');
    expect(state.navigate).not.toHaveBeenCalled();
  });
  it('recent searches are bounded to five and selecting one updates the query', () => {
    state.initial = '';
    state.recent = ['one', 'two', 'three', 'four', 'five', 'six'];
    mount();
    focus();
    for (let i = 0; i < 8; i++) key('ArrowDown');
    key('Enter');
    expect(screen.getByRole('textbox')).toHaveValue('five');
    expect(state.navigate).not.toHaveBeenCalled();
  });
  it('typing a new query clears the previously selected result', () => {
    results([result(1)]);
    mount();
    focus();
    key('ArrowDown');
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'new search' } });
    key('Enter');
    expect(state.navigate).toHaveBeenCalledWith('/search?q=new%20search');
  });
  it('outside clicks close results while inside clicks preserve them', () => {
    results([result(1)]);
    mount();
    focus();
    fireEvent.mouseDown(screen.getByRole('textbox'));
    expect(screen.getByText('Account 1')).toBeInTheDocument();
    fireEvent.mouseDown(document.body);
    expect(screen.queryByText('Account 1')).not.toBeInTheDocument();
  });
  it.each(['/search?q=x', '/search?q=rent', '/search?q=%20%20'])(
    'short, blank or unchanged URL query does not overwrite the input: %s',
    url => {
      mount(url);
      expect(screen.getByRole('textbox')).toHaveValue('rent');
      expect(state.update).not.toHaveBeenCalled();
    }
  );
  it('URL navigation updates the input and closes its autocomplete', () => {
    results([result(1)]);
    mount('/search?q=taxes');
    expect(screen.getByRole('textbox')).toHaveValue('taxes');
    expect(screen.queryByText('Account 1')).not.toBeInTheDocument();
  });
  it('shows loading without exposing stale results or recent searches', () => {
    state.loading = true;
    results([result(1)]);
    state.recent = ['Old search'];
    mount();
    focus();
    expect(screen.getByText('Searching...')).toBeInTheDocument();
    expect(screen.queryByText('Account 1')).not.toBeInTheDocument();
    expect(screen.queryByText('Old search')).not.toBeInTheDocument();
  });
  it('localizes budget/category subtitles and preserves other metadata with missing icon/color', () => {
    state.result = {
      query: 'rent',
      totalResults: 3,
      resultsByType: {
        BUDGET: [
          result(1, {
            resultType: 'BUDGET',
            title: 'Allowance',
            subtitle: 'MONTHLY',
            snippet: 'Budget note',
          }),
        ],
        CATEGORY: [result(2, { resultType: 'CATEGORY', title: 'Food', subtitle: 'INCOME' })],
        ASSET: [result(3, { resultType: 'ASSET', title: 'Holding', subtitle: 'STOCK' })],
        ACCOUNT: [],
      },
      countsPerType: {},
      executionTimeMs: 1,
      hasMore: false,
      limit: 50,
    };
    mount();
    focus();
    expect(screen.getByText('Monthly')).toBeInTheDocument();
    expect(screen.getByText('Income')).toBeInTheDocument();
    expect(screen.getByText('STOCK')).toBeInTheDocument();
    expect(screen.getByText('Budget note')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Allowance'));
    expect(state.navigate).toHaveBeenCalledWith('/budget/1');
  });
});
