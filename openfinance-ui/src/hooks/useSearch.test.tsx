import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useGlobalSearch,
  useAdvancedSearch,
  useSavedSearches,
  useSearchWithDebounce,
} from './useSearch';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

const mockSessionStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
  key: vi.fn(),
  length: 0,
};
Object.defineProperty(window, 'sessionStorage', { value: mockSessionStorage });

// Mock localStorage for saved/recent searches
const localStorageData: Record<string, string> = {};
const mockLocalStorage = {
  getItem: vi.fn((key: string) => localStorageData[key] ?? null),
  setItem: vi.fn((key: string, value: string) => { localStorageData[key] = value; }),
  removeItem: vi.fn((key: string) => { delete localStorageData[key]; }),
  clear: vi.fn(() => { Object.keys(localStorageData).forEach(k => delete localStorageData[k]); }),
  key: vi.fn(),
  length: 0,
};
Object.defineProperty(window, 'localStorage', { value: mockLocalStorage });

describe('useSearch hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
    // Clear localStorage mock data
    Object.keys(localStorageData).forEach(k => delete localStorageData[k]);
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  // ── useGlobalSearch ─────────────────────────────────────────────────
  describe('useGlobalSearch', () => {
    const mockSearchResponse = {
      query: 'test',
      totalResults: 2,
      resultsByType: {
        TRANSACTION: [{ id: 1, title: 'Test Transaction', resultType: 'TRANSACTION', createdAt: '2024-01-01' }],
        ACCOUNT: [{ id: 2, title: 'Test Account', resultType: 'ACCOUNT', createdAt: '2024-01-01' }],
      },
      countsPerType: { TRANSACTION: 1, ACCOUNT: 1 },
      executionTimeMs: 50,
      hasMore: false,
      limit: 50,
    };

    it('should search when query is at least 2 characters', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockSearchResponse });

      const { result } = renderHook(() => useGlobalSearch('test'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockSearchResponse);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/search', {
        params: { q: 'test', limit: 50 },
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should not search when query is less than 2 characters', () => {
      const { result } = renderHook(() => useGlobalSearch('t'), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
      expect(mockedApiClient.get).not.toHaveBeenCalled();
    });

    it('should not search when disabled', () => {
      const { result } = renderHook(() => useGlobalSearch('test', 50, false), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });

    it('should pass custom limit parameter', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockSearchResponse });

      const { result } = renderHook(() => useGlobalSearch('test', 10), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/search', {
        params: { q: 'test', limit: 10 },
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useGlobalSearch('test'), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toContain('Encryption key not found');
    });
  });

  // ── useAdvancedSearch ─────────────────────────────────────────────────
  describe('useAdvancedSearch', () => {
    it('should perform advanced search mutation', async () => {
      const mockResponse = {
        query: 'rent',
        totalResults: 1,
        resultsByType: { TRANSACTION: [{ id: 1, title: 'Rent', resultType: 'TRANSACTION', createdAt: '2024-01-01' }] },
        countsPerType: { TRANSACTION: 1 },
        executionTimeMs: 30,
        hasMore: false,
        limit: 50,
      };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useAdvancedSearch(), { wrapper });

      await act(async () => {
        result.current.mutate({
          query: 'rent',
          entityTypes: ['TRANSACTION'],
          minAmount: 500,
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockResponse);
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/search/advanced',
        { query: 'rent', entityTypes: ['TRANSACTION'], minAmount: 500 },
        { headers: { 'X-Encryption-Session': 'test-encryption-key' } },
      );
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useAdvancedSearch(), { wrapper });

      await act(async () => {
        result.current.mutate({ query: 'test' });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toContain('Encryption key not found');
    });
  });

  // ── useSavedSearches ─────────────────────────────────────────────────
  describe('useSavedSearches', () => {
    it('should return empty array when no saved searches exist', () => {
      const { result } = renderHook(() => useSavedSearches(), { wrapper });
      expect(result.current.savedSearches).toEqual([]);
    });

    it('should load saved searches from localStorage', () => {
      const existing = [{ id: '1', name: 'My Search', filters: { query: 'test' }, createdAt: '2024-01-01' }];
      localStorageData['saved-searches'] = JSON.stringify(existing);

      const { result } = renderHook(() => useSavedSearches(), { wrapper });
      expect(result.current.savedSearches).toEqual(existing);
    });

    it('should save a new search', () => {
      const { result } = renderHook(() => useSavedSearches(), { wrapper });

      act(() => {
        result.current.saveSearch('My Filter', { query: 'rent', minAmount: 100 });
      });

      expect(result.current.savedSearches).toHaveLength(1);
      expect(result.current.savedSearches[0].name).toBe('My Filter');
      expect(result.current.savedSearches[0].filters).toEqual({ query: 'rent', minAmount: 100 });
      expect(mockLocalStorage.setItem).toHaveBeenCalledWith(
        'saved-searches',
        expect.any(String),
      );
    });

    it('should delete a saved search', () => {
      const existing = [
        { id: '1', name: 'Search 1', filters: { query: 'a' }, createdAt: '2024-01-01' },
        { id: '2', name: 'Search 2', filters: { query: 'b' }, createdAt: '2024-01-02' },
      ];
      localStorageData['saved-searches'] = JSON.stringify(existing);

      const { result } = renderHook(() => useSavedSearches(), { wrapper });

      act(() => {
        result.current.deleteSearch('1');
      });

      expect(result.current.savedSearches).toHaveLength(1);
      expect(result.current.savedSearches[0].id).toBe('2');
    });

    it('should update lastUsed timestamp', () => {
      const existing = [{ id: '1', name: 'Search 1', filters: { query: 'a' }, createdAt: '2024-01-01' }];
      localStorageData['saved-searches'] = JSON.stringify(existing);

      const { result } = renderHook(() => useSavedSearches(), { wrapper });

      act(() => {
        result.current.updateLastUsed('1');
      });

      expect(result.current.savedSearches[0].lastUsed).toBeDefined();
    });

    it('should handle corrupted localStorage gracefully', () => {
      localStorageData['saved-searches'] = 'invalid-json{{{';

      const { result } = renderHook(() => useSavedSearches(), { wrapper });
      expect(result.current.savedSearches).toEqual([]);
    });
  });

  // ── useSearchWithDebounce ─────────────────────────────────────────────────
  describe('useSearchWithDebounce', () => {
    it('should initialize with empty query by default', () => {
      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      expect(result.current.query).toBe('');
      expect(result.current.debouncedQuery).toBe('');
      expect(result.current.isDebouncing).toBe(false);
    });

    it('should initialize with provided initial query', () => {
      const { result } = renderHook(() => useSearchWithDebounce('initial'), { wrapper });

      expect(result.current.query).toBe('initial');
      expect(result.current.debouncedQuery).toBe('initial');
    });

    it('should update query immediately and set debouncing', () => {
      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      act(() => {
        result.current.updateQuery('search term');
      });

      expect(result.current.query).toBe('search term');
      expect(result.current.isDebouncing).toBe(true);
    });

    it('should update debounced query after delay', async () => {
      vi.useFakeTimers();

      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      act(() => {
        result.current.updateQuery('delayed');
      });

      expect(result.current.debouncedQuery).toBe('');

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(result.current.debouncedQuery).toBe('delayed');
      expect(result.current.isDebouncing).toBe(false);

      vi.useRealTimers();
    });

    it('should save recent search to localStorage', () => {
      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      act(() => {
        result.current.saveRecentSearch('my search');
      });

      expect(mockLocalStorage.setItem).toHaveBeenCalledWith(
        'recent-searches',
        expect.stringContaining('my search'),
      );
    });

    it('should not save searches shorter than 2 characters', () => {
      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      act(() => {
        result.current.saveRecentSearch('a');
      });

      expect(mockLocalStorage.setItem).not.toHaveBeenCalledWith(
        'recent-searches',
        expect.any(String),
      );
    });

    it('should get recent searches from localStorage', () => {
      localStorageData['recent-searches'] = JSON.stringify(['search1', 'search2']);

      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      const recent = result.current.getRecentSearches();
      expect(recent).toEqual(['search1', 'search2']);
    });

    it('should limit recent searches to 10', () => {
      const existingRecent = Array.from({ length: 10 }, (_, i) => `search${i}`);
      localStorageData['recent-searches'] = JSON.stringify(existingRecent);

      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      act(() => {
        result.current.saveRecentSearch('new search');
      });

      const savedData = JSON.parse(localStorageData['recent-searches']);
      expect(savedData).toHaveLength(10);
      expect(savedData[0]).toBe('new search');
    });

    it('should deduplicate recent searches', () => {
      localStorageData['recent-searches'] = JSON.stringify(['existing', 'other']);

      const { result } = renderHook(() => useSearchWithDebounce(), { wrapper });

      act(() => {
        result.current.saveRecentSearch('existing');
      });

      const savedData = JSON.parse(localStorageData['recent-searches']);
      // 'existing' should appear only once, at the front
      expect(savedData.filter((s: string) => s === 'existing')).toHaveLength(1);
      expect(savedData[0]).toBe('existing');
    });
  });
});
