/**
 * Tests for GlobalSearch component
 * Verifies search functionality, keyboard navigation, and goto actions
 */

import { screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { renderWithProviders } from '@/test/test-utils';
import { GlobalSearch } from './GlobalSearch';
import * as useSearchHook from '../../hooks/useSearch';
import type { GlobalSearchResponse } from '../../types/search';

const mockNavigate = vi.fn();

vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

describe('GlobalSearch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders search input with correct placeholder', () => {
    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    expect(input).toBeInTheDocument();
  });

  it('shows recent searches when input is focused with no query', async () => {
    // Add recent searches to localStorage
    localStorage.setItem('recent-searches', JSON.stringify(['investment', 'groceries']));
    
    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);
    
    await waitFor(() => {
      expect(screen.getByText('Recent Searches')).toBeInTheDocument();
      expect(screen.getByText('investment')).toBeInTheDocument();
      expect(screen.getByText('groceries')).toBeInTheDocument();
    });
  });

  it('navigates to account detail page when account result is clicked', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'checking',
      totalResults: 1,
      resultsByType: {
        ACCOUNT: [
          {
            resultType: 'ACCOUNT',
            id: 123,
            title: 'Checking Account',
            subtitle: 'Bank of America',
            amount: 5000.00,
            currency: 'USD',
            icon: 'Wallet',
            color: '#3b82f6',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: {
        ACCOUNT: 1,
      },
      executionTimeMs: 10,
      hasMore: false,
      limit: 50,
    };

    const mockUpdateQuery = vi.fn();
    const mockSaveRecentSearch = vi.fn();

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'checking',
      debouncedQuery: 'checking',
      isDebouncing: false,
      updateQuery: mockUpdateQuery,
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: mockSaveRecentSearch,
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    
    // Focus to open dropdown
    fireEvent.focus(input);
    
    await waitFor(() => {
      expect(screen.getByText('Checking Account')).toBeInTheDocument();
    });

    const resultButton = screen.getByText('Checking Account').closest('button');
    fireEvent.click(resultButton!);

    expect(mockNavigate).toHaveBeenCalledWith('/accounts?highlight=123');
    expect(mockSaveRecentSearch).toHaveBeenCalledWith('checking');
  });

  it('navigates to budget detail page when budget result is clicked', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'monthly',
      totalResults: 1,
      resultsByType: {
        BUDGET: [
          {
            resultType: 'BUDGET',
            id: 456,
            title: 'Monthly Budget',
            subtitle: 'January 2024',
            amount: 3000.00,
            currency: 'USD',
            icon: 'PieChart',
            color: '#10b981',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: {
        BUDGET: 1,
      },
      executionTimeMs: 10,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'monthly',
      debouncedQuery: 'monthly',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input); // Focus to open dropdown
    fireEvent.change(input, { target: { value: 'monthly' } });
    
    await waitFor(() => {
      expect(screen.getByText('Monthly Budget')).toBeInTheDocument();
    });

    const resultButton = screen.getByText('Monthly Budget').closest('button');
    fireEvent.click(resultButton!);

    expect(mockNavigate).toHaveBeenCalledWith('/budget/456');
  });

  it('navigates to assets page when asset result is clicked', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'stock',
      totalResults: 1,
      resultsByType: {
        ASSET: [
          {
            resultType: 'ASSET',
            id: 789,
            title: 'Apple Stock',
            subtitle: 'AAPL',
            amount: 15000.00,
            currency: 'USD',
            icon: 'TrendingUp',
            color: '#8b5cf6',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: {
        ASSET: 1,
      },
      executionTimeMs: 10,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'stock',
      debouncedQuery: 'stock',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input); // Focus to open dropdown
    fireEvent.change(input, { target: { value: 'stock' } });
    
    await waitFor(() => {
      expect(screen.getByText('Apple Stock')).toBeInTheDocument();
    });

    const resultButton = screen.getByText('Apple Stock').closest('button');
    fireEvent.click(resultButton!);

    expect(mockNavigate).toHaveBeenCalledWith('/assets?highlight=789');
  });

  it('navigates to real estate page when real estate result is clicked', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'apartment',
      totalResults: 1,
      resultsByType: {
        REAL_ESTATE: [
          {
            resultType: 'REAL_ESTATE',
            id: 101,
            title: 'Downtown Apartment',
            subtitle: '123 Main St',
            amount: 350000.00,
            currency: 'USD',
            icon: 'Home',
            color: '#f59e0b',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: {
        REAL_ESTATE: 1,
      },
      executionTimeMs: 10,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'apartment',
      debouncedQuery: 'apartment',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input); // Focus to open dropdown
    fireEvent.change(input, { target: { value: 'apartment' } });
    
    await waitFor(() => {
      expect(screen.getByText('Downtown Apartment')).toBeInTheDocument();
    });

    const resultButton = screen.getByText('Downtown Apartment').closest('button');
    fireEvent.click(resultButton!);

    expect(mockNavigate).toHaveBeenCalledWith('/real-estate?highlight=101');
  });

  it('navigates to liabilities page when liability result is clicked', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'mortgage',
      totalResults: 1,
      resultsByType: {
        LIABILITY: [
          {
            resultType: 'LIABILITY',
            id: 202,
            title: 'Home Mortgage',
            subtitle: 'Wells Fargo',
            amount: 250000.00,
            currency: 'USD',
            icon: 'CreditCard',
            color: '#ef4444',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: {
        LIABILITY: 1,
      },
      executionTimeMs: 10,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'mortgage',
      debouncedQuery: 'mortgage',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input); // Focus to open dropdown
    fireEvent.change(input, { target: { value: 'mortgage' } });
    
    await waitFor(() => {
      expect(screen.getByText('Home Mortgage')).toBeInTheDocument();
    });

    const resultButton = screen.getByText('Home Mortgage').closest('button');
    fireEvent.click(resultButton!);

    expect(mockNavigate).toHaveBeenCalledWith('/liabilities?highlight=202');
  });

  it('navigates to transactions page when transaction result is clicked', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'grocery',
      totalResults: 1,
      resultsByType: {
        TRANSACTION: [
          {
            resultType: 'TRANSACTION',
            id: 303,
            title: 'Whole Foods',
            subtitle: 'Groceries',
            amount: 85.50,
            currency: 'USD',
            date: '2024-01-15',
            icon: 'ShoppingCart',
            color: '#10b981',
            createdAt: '2024-01-15T00:00:00',
          },
        ],
      },
      countsPerType: {
        TRANSACTION: 1,
      },
      executionTimeMs: 10,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'grocery',
      debouncedQuery: 'grocery',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input); // Focus to open dropdown
    fireEvent.change(input, { target: { value: 'grocery' } });
    
    await waitFor(() => {
      expect(screen.getByText('Whole Foods')).toBeInTheDocument();
    });

    const resultButton = screen.getByText('Whole Foods').closest('button');
    fireEvent.click(resultButton!);

    expect(mockNavigate).toHaveBeenCalledWith('/transactions?highlight=303');
  });

  it('supports keyboard navigation with arrow keys', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'test',
      totalResults: 2,
      resultsByType: {
        ACCOUNT: [
          {
            resultType: 'ACCOUNT',
            id: 1,
            title: 'Account 1',
            createdAt: '2024-01-01T00:00:00',
          },
          {
            resultType: 'ACCOUNT',
            id: 2,
            title: 'Account 2',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: {
        ACCOUNT: 2,
      },
      executionTimeMs: 10,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'test',
      debouncedQuery: 'test',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input); // Focus to open dropdown
    fireEvent.change(input, { target: { value: 'test' } });
    
    await waitFor(() => {
      expect(screen.getByText('Account 1')).toBeInTheDocument();
    });

    // Press arrow down to select first result
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    
    // Press enter to navigate
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(mockNavigate).toHaveBeenCalledWith('/accounts?highlight=1');
  });

  it('clears search and closes dropdown when clear button is clicked', async () => {
    const mockUpdateQuery = vi.fn();
    
    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'test query',
      debouncedQuery: 'test query',
      isDebouncing: false,
      updateQuery: mockUpdateQuery,
      searchResult: {
        data: undefined,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: false,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    
    const clearButton = screen.getByRole('button', { name: '' });
    fireEvent.click(clearButton);

    expect(mockUpdateQuery).toHaveBeenCalledWith('');
  });

  it('shows loading state while searching', async () => {
    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'test',
      debouncedQuery: 'test',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: undefined,
        isLoading: true,
        error: null,
        isError: false,
        isSuccess: false,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });

    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    // A loading indicator should be present (spinner/loader)
    await waitFor(() => {
      const loader = document.querySelector('.animate-spin');
      expect(loader).toBeInTheDocument();
    });
  });

  it('shows no results message when search returns empty', async () => {
    const emptyResult: GlobalSearchResponse = {
      query: 'xyz',
      totalResults: 0,
      resultsByType: {},
      countsPerType: {},
      executionTimeMs: 5,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'xyz',
      debouncedQuery: 'xyz',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: emptyResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });

    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    await waitFor(() => {
      expect(screen.getByText(/no results/i)).toBeInTheDocument();
    });
  });

  it('closes dropdown on Escape key', async () => {
    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: '',
      debouncedQuery: '',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: undefined,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: false,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => ['recent-test'],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });

    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    await waitFor(() => {
      expect(screen.getByText('recent-test')).toBeInTheDocument();
    });

    fireEvent.keyDown(input, { key: 'Escape' });

    await waitFor(() => {
      expect(screen.queryByText('recent-test')).not.toBeInTheDocument();
    });
  });

  it('shows "type at least 2 characters" message for short queries', async () => {
    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'a',
      debouncedQuery: 'a',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: undefined,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: false,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    await waitFor(() => {
      expect(screen.getByText(/type at least/i)).toBeInTheDocument();
    });
  });

  it('navigates to /search page when Enter is pressed with query >= 2 chars', async () => {
    const mockSaveRecentSearch = vi.fn();
    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'test',
      debouncedQuery: 'test',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: { query: 'test', totalResults: 5, resultsByType: {}, countsPerType: {}, executionTimeMs: 5, hasMore: false, limit: 50 },
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: mockSaveRecentSearch,
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    // Press Enter without selecting a result to navigate to search page
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(mockNavigate).toHaveBeenCalledWith('/search?q=test');
    expect(mockSaveRecentSearch).toHaveBeenCalledWith('test');
  });

  it('navigates to search page via view all results button', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'payment',
      totalResults: 10,
      resultsByType: {
        TRANSACTION: [
          { resultType: 'TRANSACTION', id: 1, title: 'Payment 1', createdAt: '2024-01-01T00:00:00' },
          { resultType: 'TRANSACTION', id: 2, title: 'Payment 2', createdAt: '2024-01-01T00:00:00' },
        ],
      },
      countsPerType: { TRANSACTION: 10 },
      executionTimeMs: 10,
      hasMore: true,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'payment',
      debouncedQuery: 'payment',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    await waitFor(() => {
      expect(screen.getByText('Payment 1')).toBeInTheDocument();
    });

    const viewAllButton = screen.getByText(/view all/i).closest('button');
    fireEvent.click(viewAllButton!);

    expect(mockNavigate).toHaveBeenCalledWith('/search?q=payment');
  });

  it('selects recent search with arrow keys and Enter', async () => {
    const mockUpdateQuery = vi.fn();
    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: '',
      debouncedQuery: '',
      isDebouncing: false,
      updateQuery: mockUpdateQuery,
      searchResult: {
        data: undefined,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: false,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => ['recent query'],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    await waitFor(() => {
      expect(screen.getByText('recent query')).toBeInTheDocument();
    });

    // Arrow down to select the recent search
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    // Enter to select it
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(mockUpdateQuery).toHaveBeenCalledWith('recent query');
  });

  it('displays category result with translated subtitle', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'food',
      totalResults: 1,
      resultsByType: {
        CATEGORY: [
          {
            resultType: 'CATEGORY',
            id: 10,
            title: 'Food',
            subtitle: 'EXPENSE',
            icon: 'ShoppingCart',
            color: '#22c55e',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: { CATEGORY: 1 },
      executionTimeMs: 5,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'food',
      debouncedQuery: 'food',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    await waitFor(() => {
      expect(screen.getByText('Food')).toBeInTheDocument();
    });
  });

  it('displays result with amount and currency', async () => {
    const mockSearchResult: GlobalSearchResponse = {
      query: 'savings',
      totalResults: 1,
      resultsByType: {
        ACCOUNT: [
          {
            resultType: 'ACCOUNT',
            id: 50,
            title: 'Savings Account',
            amount: 12345.67,
            currency: 'USD',
            icon: 'Wallet',
            color: '#3b82f6',
            createdAt: '2024-01-01T00:00:00',
          },
        ],
      },
      countsPerType: { ACCOUNT: 1 },
      executionTimeMs: 5,
      hasMore: false,
      limit: 50,
    };

    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'savings',
      debouncedQuery: 'savings',
      isDebouncing: false,
      updateQuery: vi.fn(),
      searchResult: {
        data: mockSearchResult,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: true,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    const input = screen.getByPlaceholderText(/search accounts/i);
    fireEvent.focus(input);

    await waitFor(() => {
      expect(screen.getByText('Savings Account')).toBeInTheDocument();
      // Should display the formatted amount
      expect(screen.getByText(/12.*345/)).toBeInTheDocument();
    });
  });

  it('shows debouncing spinner', async () => {
    vi.spyOn(useSearchHook, 'useSearchWithDebounce').mockReturnValue({
      query: 'test',
      debouncedQuery: '',
      isDebouncing: true,
      updateQuery: vi.fn(),
      searchResult: {
        data: undefined,
        isLoading: false,
        error: null,
        isError: false,
        isSuccess: false,
      } as any,
      saveRecentSearch: vi.fn(),
      getRecentSearches: () => [],
    });

    renderWithProviders(<GlobalSearch />, { queryClient });
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });
});
