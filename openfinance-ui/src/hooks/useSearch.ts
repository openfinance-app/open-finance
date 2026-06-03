/**
 * Custom hooks for global search functionality
 */

import { useQuery, useMutation } from '@tanstack/react-query';
import { useState, useCallback } from 'react';
import apiClient from '@/services/apiClient';
import type {
  GlobalSearchResponse,
  AdvancedSearchRequest,
  SavedSearch,
} from '../types/search';

const SEARCH_KEYS = {
  all: ['search'] as const,
  global: (query: string) => [...SEARCH_KEYS.all, 'global', query] as const,
  advanced: (request: AdvancedSearchRequest) =>
    [...SEARCH_KEYS.all, 'advanced', request] as const,
  saved: () => [...SEARCH_KEYS.all, 'saved'] as const,
};

/**
 * Hook for global search with debounce
 */
export const useGlobalSearch = (query: string, limit: number = 50, enabled: boolean = true) => {
  return useQuery({
    queryKey: SEARCH_KEYS.global(query),
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<GlobalSearchResponse>('/search', {
        params: { q: query, limit },
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });

      return response.data;
    },
    enabled: enabled && query.length >= 2, // Only search when query is at least 2 characters
    staleTime: 30000, // 30 seconds
    gcTime: 300000, // 5 minutes
  });
};

/**
 * Hook for advanced search with filters
 */
export const useAdvancedSearch = () => {
  return useMutation({
    mutationFn: async (request: AdvancedSearchRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<GlobalSearchResponse>(
        '/search/advanced',
        request,
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );

      return response.data;
    },
  });
};

/**
 * Hook for managing saved searches (localStorage)
 */
export const useSavedSearches = () => {
  const STORAGE_KEY = 'saved-searches';

  const getSavedSearches = useCallback((): SavedSearch[] => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch (error) {
      console.error('Failed to load saved searches:', error);
      return [];
    }
  }, []);

  const [savedSearches, setSavedSearches] = useState<SavedSearch[]>(getSavedSearches);

  const saveSearch = useCallback(
    (name: string, filters: AdvancedSearchRequest): SavedSearch => {
      const newSearch: SavedSearch = {
        id: Date.now().toString(),
        name,
        filters,
        createdAt: new Date().toISOString(),
      };

      const updated = [...savedSearches, newSearch];
      localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      setSavedSearches(updated);

      return newSearch;
    },
    [savedSearches]
  );

  const deleteSearch = useCallback(
    (id: string) => {
      const updated = savedSearches.filter((s) => s.id !== id);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      setSavedSearches(updated);
    },
    [savedSearches]
  );

  const updateLastUsed = useCallback(
    (id: string) => {
      const updated = savedSearches.map((s) =>
        s.id === id ? { ...s, lastUsed: new Date().toISOString() } : s
      );
      localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      setSavedSearches(updated);
    },
    [savedSearches]
  );

  return {
    savedSearches,
    saveSearch,
    deleteSearch,
    updateLastUsed,
  };
};

/**
 * Hook for search with debounce and recent searches
 */
export const useSearchWithDebounce = (initialQuery: string = '') => {
  const [query, setQuery] = useState(initialQuery);
  const [debouncedQuery, setDebouncedQuery] = useState(initialQuery);
  const [isDebouncing, setIsDebouncing] = useState(false);

  // Debounce query changes
  const updateQuery = useCallback((newQuery: string) => {
    setQuery(newQuery);
    setIsDebouncing(true);

    const timer = setTimeout(() => {
      setDebouncedQuery(newQuery);
      setIsDebouncing(false);
    }, 300); // 300ms debounce

    return () => clearTimeout(timer);
  }, []);

  // Search with debounced query
  const searchResult = useGlobalSearch(
    debouncedQuery.trim(),
    50,
    debouncedQuery.trim().length >= 2
  );

  // Save recent searches to localStorage
  const saveRecentSearch = useCallback((searchQuery: string) => {
    if (!searchQuery || searchQuery.length < 2) return;

    try {
      const RECENT_KEY = 'recent-searches';
      const stored = localStorage.getItem(RECENT_KEY);
      const recent: string[] = stored ? JSON.parse(stored) : [];

      // Add to front, remove duplicates, limit to 10
      const updated = [
        searchQuery,
        ...recent.filter((q) => q !== searchQuery),
      ].slice(0, 10);

      localStorage.setItem(RECENT_KEY, JSON.stringify(updated));
    } catch (error) {
      console.error('Failed to save recent search:', error);
    }
  }, []);

  // Get recent searches from localStorage
  const getRecentSearches = useCallback((): string[] => {
    try {
      const RECENT_KEY = 'recent-searches';
      const stored = localStorage.getItem(RECENT_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch (error) {
      console.error('Failed to load recent searches:', error);
      return [];
    }
  }, []);

  return {
    query,
    debouncedQuery,
    isDebouncing,
    updateQuery,
    searchResult,
    saveRecentSearch,
    getRecentSearches,
  };
};
