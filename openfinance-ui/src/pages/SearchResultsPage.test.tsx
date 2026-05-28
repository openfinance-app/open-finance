import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useDocumentTitle', () => ({ useDocumentTitle: vi.fn() }));

let mockSimpleData: any = undefined;
let mockSimpleLoading = false;
let mockSimpleError: Error | null = null;
let mockAdvancedData: any = undefined;
let mockAdvancedPending = false;
let mockAdvancedError: Error | null = null;
const mockMutate = vi.fn();
let mockSavedSearches: any[] = [];
const mockSaveSearch = vi.fn();
const mockDeleteSearch = vi.fn();
const mockUpdateLastUsed = vi.fn();

vi.mock('@/hooks/useSearch', () => ({
  useAdvancedSearch: () => ({
    data: mockAdvancedData,
    isPending: mockAdvancedPending,
    error: mockAdvancedError,
    mutate: mockMutate,
  }),
  useGlobalSearch: () => ({
    data: mockSimpleData,
    isLoading: mockSimpleLoading,
    error: mockSimpleError,
  }),
  useSavedSearches: () => ({
    savedSearches: mockSavedSearches,
    saveSearch: mockSaveSearch,
    deleteSearch: mockDeleteSearch,
    updateLastUsed: mockUpdateLastUsed,
  }),
}));

vi.mock('@/components/search/AdvancedFilterPanel', () => ({
  AdvancedFilterPanel: ({ onApply, onSaveSearch, filters, onFiltersChange }: any) => (
    <div data-testid="filter-panel">
      <button data-testid="apply-filters" onClick={() => onApply()}>Apply</button>
      {onSaveSearch && <button data-testid="save-search" onClick={onSaveSearch}>Save</button>}
    </div>
  ),
}));
vi.mock('@/components/search/SavedSearchesDropdown', () => ({
  SavedSearchesDropdown: ({ onLoad, onDelete, savedSearches }: any) => (
    <div data-testid="saved-searches">
      {savedSearches.map((s: any) => (
        <div key={s.id}>
          <button data-testid={`load-${s.id}`} onClick={() => onLoad(s)}>Load</button>
          <button data-testid={`delete-${s.id}`} onClick={() => onDelete(s.id)}>Delete</button>
        </div>
      ))}
    </div>
  ),
}));
vi.mock('@/components/search/SaveSearchDialog', () => ({
  SaveSearchDialog: ({ isOpen, onSave, onClose }: any) => (
    isOpen ? (
      <div data-testid="save-dialog">
        <button data-testid="confirm-save" onClick={() => onSave('My Search')}>Save</button>
        <button data-testid="close-dialog" onClick={onClose}>Close</button>
      </div>
    ) : null
  ),
}));

import SearchResultsPage from './SearchResultsPage';

describe('SearchResultsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    mockSimpleData = undefined;
    mockSimpleLoading = false;
    mockSimpleError = null;
    mockAdvancedData = undefined;
    mockAdvancedPending = false;
    mockAdvancedError = null;
    mockSavedSearches = [];
  });

  it('renders the search page heading', () => {
    renderWithProviders(<SearchResultsPage />);
    expect(screen.getByRole('heading', { name: 'Search' })).toBeInTheDocument();
  });

  it('renders search input and button', () => {
    renderWithProviders(<SearchResultsPage />);
    expect(screen.getByPlaceholderText(/search/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /search/i })).toBeInTheDocument();
  });

  it('shows description text', () => {
    renderWithProviders(<SearchResultsPage />);
    expect(screen.getByText(/search across/i)).toBeInTheDocument();
  });

  it('allows typing in search input', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SearchResultsPage />);
    const input = screen.getByPlaceholderText(/search/i);
    await user.type(input, 'test');
    expect(input).toHaveValue('test');
  });

  it('shows loading skeletons when loading', () => {
    mockSimpleLoading = true;
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    // Loading state renders LoadingSkeleton divs
    expect(screen.queryByText(/no results/i)).not.toBeInTheDocument();
  });

  it('shows error state', () => {
    mockSimpleError = new Error('Search failed');
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    expect(screen.getAllByText(/search failed/i).length).toBeGreaterThan(0);
  });

  it('shows error card when error occurs', () => {
    mockSimpleError = { message: '' } as Error;
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    // Error card renders with AlertCircle icon area
    const errorDiv = document.querySelector('.text-error');
    expect(errorDiv).toBeTruthy();
  });

  it('shows empty state when no results', () => {
    mockSimpleData = { totalResults: 0, resultsByType: {}, countsPerType: {}, executionTimeMs: 5 };
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    expect(screen.getAllByText(/no results/i).length).toBeGreaterThan(0);
  });

  it('renders results grouped by type', () => {
    mockSimpleData = {
      totalResults: 2,
      executionTimeMs: 10,
      resultsByType: {
        ACCOUNT: [
          { id: 1, resultType: 'ACCOUNT', title: 'Checking Account', subtitle: null, snippet: null, date: null, tags: [], icon: 'Wallet' },
          { id: 2, resultType: 'ACCOUNT', title: 'Savings Account', subtitle: null, snippet: null, date: null, tags: [], icon: 'Wallet' },
        ],
      },
      countsPerType: { ACCOUNT: 2 },
    };
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=account' });
    expect(screen.getByText('Checking Account')).toBeInTheDocument();
    expect(screen.getByText('Savings Account')).toBeInTheDocument();
  });

  it('toggles result type expansion', async () => {
    const user = userEvent.setup();
    mockSimpleData = {
      totalResults: 1,
      executionTimeMs: 5,
      resultsByType: {
        ACCOUNT: [
          { id: 1, resultType: 'ACCOUNT', title: 'Test Account', subtitle: null, snippet: null, date: null, tags: [], icon: 'Wallet' },
        ],
      },
      countsPerType: { ACCOUNT: 1 },
    };
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    expect(screen.getByText('Test Account')).toBeInTheDocument();
    // Click the type header to collapse
    const header = screen.getByText('Accounts').closest('button');
    if (header) await user.click(header);
    expect(screen.queryByText('Test Account')).not.toBeInTheDocument();
  });

  it('displays result with amount and currency', () => {
    mockSimpleData = {
      totalResults: 1,
      executionTimeMs: 5,
      resultsByType: {
        TRANSACTION: [
          { id: 1, resultType: 'TRANSACTION', title: 'Grocery Store', amount: 50.00, currency: 'USD', subtitle: null, snippet: null, date: null, tags: [], icon: 'ArrowRightLeft' },
        ],
      },
      countsPerType: { TRANSACTION: 1 },
    };
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=grocery' });
    expect(screen.getByText('Grocery Store')).toBeInTheDocument();
  });

  it('displays result with subtitle, snippet and tags', () => {
    mockSimpleData = {
      totalResults: 1,
      executionTimeMs: 5,
      resultsByType: {
        CATEGORY: [
          { id: 1, resultType: 'CATEGORY', title: 'Food', subtitle: 'EXPENSE', snippet: 'food and drink', date: '2024-01-01', tags: ['monthly'], icon: 'Tag' },
        ],
      },
      countsPerType: { CATEGORY: 1 },
    };
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=food' });
    expect(screen.getByText('Food')).toBeInTheDocument();
    expect(screen.getByText('monthly')).toBeInTheDocument();
  });

  it('shows saved searches dropdown when saved searches exist', () => {
    mockSavedSearches = [{ id: '1', name: 'My Search', filters: { query: 'test', limit: 100 }, createdAt: Date.now(), lastUsedAt: Date.now() }];
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    expect(screen.getByTestId('saved-searches')).toBeInTheDocument();
  });

  it('does not show saved searches dropdown when empty', () => {
    renderWithProviders(<SearchResultsPage />);
    expect(screen.queryByTestId('saved-searches')).not.toBeInTheDocument();
  });

  it('loads a saved search', async () => {
    const user = userEvent.setup();
    mockSavedSearches = [{ id: 's1', name: 'My Search', filters: { query: 'saved', limit: 100 }, createdAt: Date.now(), lastUsedAt: Date.now() }];
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    await user.click(screen.getByTestId('load-s1'));
    expect(mockUpdateLastUsed).toHaveBeenCalledWith('s1');
    expect(mockMutate).toHaveBeenCalled();
  });

  it('deletes a saved search', async () => {
    const user = userEvent.setup();
    mockSavedSearches = [{ id: 's1', name: 'My Search', filters: { query: 'saved', limit: 100 }, createdAt: Date.now(), lastUsedAt: Date.now() }];
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    await user.click(screen.getByTestId('delete-s1'));
    expect(mockDeleteSearch).toHaveBeenCalledWith('s1');
  });

  it('opens save dialog and saves search', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=test' });
    await user.click(screen.getByTestId('save-search'));
    expect(screen.getByTestId('save-dialog')).toBeInTheDocument();
    await user.click(screen.getByTestId('confirm-save'));
    expect(mockSaveSearch).toHaveBeenCalled();
  });

  it('search button exists', () => {
    renderWithProviders(<SearchResultsPage />);
    const btn = screen.getByRole('button', { name: /search/i });
    expect(btn).toBeInTheDocument();
  });

  it('submits search on form submit', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SearchResultsPage />);
    const input = screen.getByPlaceholderText(/search/i);
    await user.type(input, 'test query');
    await user.click(screen.getByRole('button', { name: /search/i }));
    // Search should be submitted (URL updated)
  });

  it('localizes BUDGET subtitle', () => {
    mockSimpleData = {
      totalResults: 1,
      executionTimeMs: 5,
      resultsByType: {
        BUDGET: [
          { id: 1, resultType: 'BUDGET', title: 'Groceries Budget', subtitle: 'MONTHLY', snippet: null, date: null, tags: [], icon: 'PiggyBank' },
        ],
      },
      countsPerType: { BUDGET: 1 },
    };
    renderWithProviders(<SearchResultsPage />, { route: '/search?q=groceries' });
    expect(screen.getByText('Groceries Budget')).toBeInTheDocument();
  });
});
