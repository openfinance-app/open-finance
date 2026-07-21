/**
 * GlobalSearch - Search bar component for top navigation with autocomplete
 * 
 * Features:
 * - Real-time search with debounce (300ms)
 * - Dropdown with grouped results (max 5 per type)
 * - Keyboard navigation (arrow keys, enter, escape)
 * - Recent searches
 * - Navigate to full results page
 * - Loading and empty states
 */

import React, { useState, useRef, useEffect, forwardRef, useImperativeHandle } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { Search, X, Clock, TrendingUp, Loader, ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useSearchWithDebounce } from '../../hooks/useSearch';
import { useNumberFormat } from '../../context/NumberFormatContext';
import { DEFAULT_CURRENCY, formatCurrency } from '@/utils/currency';
import type { SearchResult } from '../../types/search';
import {
  getResultTypeDisplayName,
  getResultRoute,
} from '../../types/search';
import { Icon } from '../icons/Icon';

export interface GlobalSearchHandle {
  /** Focuses the search input and opens the dropdown */
  focus: () => void;
}

export const GlobalSearch = forwardRef<GlobalSearchHandle>((_, ref) => {
  const { t } = useTranslation(['navigation', 'errors', 'transactions', 'budgets']);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [isOpen, setIsOpen] = useState(false);
  const [isFocused, setIsFocused] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const searchRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useImperativeHandle(ref, () => ({
    focus() {
      inputRef.current?.focus();
      setIsOpen(true);
      setIsFocused(true);
    },
  }));

  const {
    query,
    debouncedQuery: _debouncedQuery,
    isDebouncing,
    updateQuery,
    searchResult,
    saveRecentSearch,
    getRecentSearches,
  } = useSearchWithDebounce();

  // Get user's number format preference (e.g., '1,234.56', '1.234,56', '1 234,56')
  const { numberFormat } = useNumberFormat();

  // Format amount with currency symbol respecting user's number format preference
  const formatAmount = (amount?: number, currencyCode?: string): string => {
    if (amount === undefined || amount === null) return '';
    return formatCurrency(amount, currencyCode || DEFAULT_CURRENCY, {
      showSymbol: true,
      numberFormat,
    });
  };

  const recentSearches = getRecentSearches();

  // BUG-005: Sync the top-bar input with the ?q= URL param when navigating to /search
  useEffect(() => {
    const q = searchParams.get('q');
    if (q && q.trim().length >= 2 && q !== query) {
      updateQuery(q);
      setIsOpen(false);
    }
  // Only run on searchParams change, not on every query change
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  // Localize raw enum subtitles returned by the backend for categories and budgets
  const localizeSubtitle = (result: SearchResult): string | undefined => {
    if (!result.subtitle) return result.subtitle;
    if (result.resultType === 'CATEGORY') {
      return t(`transactions:types.${result.subtitle}`, result.subtitle);
    }
    if (result.resultType === 'BUDGET') {
      return t(`budgets:form.periods.${result.subtitle}`, result.subtitle);
    }
    return result.subtitle;
  };

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) {
        setIsOpen(false);
        setIsFocused(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Flatten results for keyboard navigation
  const flatResults: SearchResult[] = [];
  if (searchResult.data?.resultsByType) {
    Object.entries(searchResult.data.resultsByType).forEach(([_type, results]) => {
      flatResults.push(...results.slice(0, 5)); // Max 5 per type
    });
  }

  // BUG-006: Use a unified navigable list — recent searches when no query, results otherwise
  const showingRecent = !searchResult.isLoading && query.length === 0 && recentSearches.length > 0;
  const navigableLength = showingRecent ? Math.min(recentSearches.length, 5) : flatResults.length;

  // Handle keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!isOpen) {
      if (e.key === 'Enter' && query.length >= 2) {
        handleViewAllResults();
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setSelectedIndex((prev) =>
          prev < navigableLength - 1 ? prev + 1 : prev
        );
        break;
      case 'ArrowUp':
        e.preventDefault();
        setSelectedIndex((prev) => (prev > 0 ? prev - 1 : -1));
        break;
      case 'Enter':
        e.preventDefault();
        if (selectedIndex >= 0) {
          if (showingRecent) {
            handleRecentSearchClick(recentSearches[selectedIndex]);
          } else if (flatResults[selectedIndex]) {
            handleResultClick(flatResults[selectedIndex]);
          }
        } else if (query.length >= 2) {
          handleViewAllResults();
        }
        break;
      case 'Escape':
        e.preventDefault();
        setIsOpen(false);
        inputRef.current?.blur();
        break;
    }
  };

  // Handle search input change
  const handleSearchChange = (value: string) => {
    updateQuery(value);
    setIsOpen(true);
    setSelectedIndex(-1);
  };

  // Handle result click - navigate to detail page
  const handleResultClick = (result: SearchResult) => {
    saveRecentSearch(query);
    setIsOpen(false);
    updateQuery('');
    const route = getResultRoute(result);
    navigate(route);
  };

  // Handle view all results
  const handleViewAllResults = () => {
    if (query.length < 2) return;
    saveRecentSearch(query);
    setIsOpen(false);
    navigate(`/search?q=${encodeURIComponent(query)}`);
  };

  // Handle recent search click
  const handleRecentSearchClick = (recentQuery: string) => {
    updateQuery(recentQuery);
    setIsOpen(true);
  };

  // Handle clear search
  const handleClear = () => {
    updateQuery('');
    setIsOpen(false);
    inputRef.current?.focus();
  };

  // Show dropdown when there are results, loading, or some query text/recent searches and focused
  const showDropdown =
    isOpen &&
    (flatResults.length > 0 ||
      searchResult.isLoading ||
      query.length > 0 ||
      (isFocused && query.length === 0 && recentSearches.length > 0));

  return (
    <div ref={searchRef} className="relative w-full max-w-2xl">
      {/* Search Input */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-text-muted" />
        <input
          ref={inputRef}
          type="text"
          placeholder={t('search.placeholder')}
          value={query}
          onChange={(e) => handleSearchChange(e.target.value)}
          onFocus={() => { setIsOpen(true); setIsFocused(true); }}
          onBlur={() => {
            // Need a slight delay to allow clicks to register inside the dropdown
            setTimeout(() => setIsFocused(false), 200);
          }}
          onKeyDown={handleKeyDown}
          className="w-full pl-10 pr-10 py-2 border border-border rounded-lg 
                     bg-surface text-text-primary
                     focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                     placeholder:text-text-muted"
        />
        {(isDebouncing || searchResult.isLoading) && (
          <Loader className="absolute right-3 top-1/2 -translate-y-1/2 h-5 w-5 text-text-muted animate-spin" />
        )}
        {!isDebouncing && !searchResult.isLoading && query && (
          <button
            onClick={handleClear}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-secondary"
          >
            <X className="h-5 w-5" />
          </button>
        )}
      </div>

      {/* Dropdown */}
      {showDropdown && (
        <div className="absolute z-50 mt-2 w-full bg-surface rounded-lg shadow-lg border border-border max-h-96 overflow-y-auto">
          {/* Query Too Short or Loading */}
          {query.length > 0 && query.length < 2 ? (
            <div className="p-4 text-center text-text-secondary">
              <p className="text-sm">{t('search.typeMinChars')}</p>
            </div>
          ) : searchResult.isLoading ? (
            <div className="p-4 text-center text-text-secondary flex items-center justify-center gap-2">
              <Loader className="h-4 w-4 animate-spin" />
              <p className="text-sm">{t('search.searching')}</p>
            </div>
          ) : null}

          {/* Recent Searches (when no query) */}
          {!searchResult.isLoading && query.length === 0 && recentSearches.length > 0 && (
            <div className="p-2">
              <div className="px-3 py-2 text-xs font-semibold text-text-secondary uppercase">
                {t('search.recentSearches')}
              </div>
              {recentSearches.slice(0, 5).map((recentQuery, index) => (
                <button
                  key={index}
                  onClick={() => handleRecentSearchClick(recentQuery)}
                  className={`w-full flex items-center gap-3 px-3 py-2 rounded-md text-left ${
                    index === selectedIndex ? 'bg-blue-50' : 'hover:bg-surface-elevated'
                  }`}
                >
                  <Clock className="h-4 w-4 text-text-muted flex-shrink-0" />
                  <span className="text-sm text-text-secondary truncate">
                    {recentQuery}
                  </span>
                </button>
              ))}
            </div>
          )}

          {/* Search Results */}
          {!searchResult.isLoading && flatResults.length > 0 && (
            <>
              {Object.entries(searchResult.data?.resultsByType || {}).map(
                ([type, results]) => (
                  <div key={type} className="p-2">
                    <div className="px-3 py-2 text-xs font-semibold text-text-secondary uppercase flex items-center gap-2">
                      <Icon name={results[0]?.icon || 'Search'} className="h-4 w-4" />
                      {getResultTypeDisplayName(type as any, (key) => t(`errors:${key}`))}
                      <span className="text-text-muted">
                        ({searchResult.data?.countsPerType[type] || 0})
                      </span>
                    </div>
                    {results.slice(0, 5).map((result, _index) => {
                      const globalIndex = flatResults.indexOf(result);
                      const isSelected = globalIndex === selectedIndex;
                      return (
                        <button
                          key={`${result.resultType}-${result.id}`}
                          onClick={() => handleResultClick(result)}
                          className={`w-full flex items-start gap-3 px-3 py-2 rounded-md text-left transition-colors group ${isSelected
                            ? 'bg-blue-50'
                            : 'hover:bg-surface-elevated'
                            }`}
                        >
                          <div
                            className={`flex-shrink-0 h-8 w-8 rounded-full flex items-center justify-center`}
                            style={{ backgroundColor: result.color || '#6b7280' }}
                          >
                            <Icon
                              name={result.icon || 'Search'}
                              className="h-4 w-4 text-white"
                            />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between gap-2">
                              <div className="text-sm font-medium text-text-primary truncate">
                                {result.title}
                              </div>
                              {result.amount !== undefined && (
                                <span className="text-sm font-semibold text-text-primary whitespace-nowrap">
                                  {formatAmount(result.amount, result.currency)}
                                </span>
                              )}
                            </div>
                            {result.subtitle && (
                              <div className="text-xs text-text-secondary truncate">
                                {localizeSubtitle(result)}
                              </div>
                            )}
                            {result.snippet && (
                              <div className="text-xs text-text-muted mt-1 line-clamp-1">
                                {result.snippet}
                              </div>
                            )}
                          </div>
                          <ArrowRight className="h-4 w-4 text-text-muted group-hover:text-blue-500 flex-shrink-0 self-center opacity-0 group-hover:opacity-100 transition-opacity" />
                        </button>
                      );
                    })}
                  </div>
                )
              )}

              {/* View All Results Button */}
              <div className="p-2 border-t border-border">
                <button
                  onClick={handleViewAllResults}
                  className="w-full flex items-center justify-center gap-2 px-3 py-2 text-sm font-medium text-blue-600 hover:bg-surface-elevated rounded-md"
                >
                  <TrendingUp className="h-4 w-4" />
                  {t('search.viewAllResults', { count: searchResult.data?.totalResults })}
                </button>
              </div>
            </>
          )}

          {/* No Results Fallback */}
          {!searchResult.isLoading &&
            query.length >= 2 &&
            flatResults.length === 0 && (
              <div className="p-8 text-center" aria-live="polite">
                <Search className="h-12 w-12 text-text-muted mx-auto mb-3" />
                <p className="text-text-secondary font-medium">
                  {t('search.noResults', { query })}
                </p>
                <p className="text-sm text-text-muted mt-1">
                  {t('search.tryDifferent')}
                </p>
              </div>
            )}
        </div>
      )}
    </div>
  );
});

GlobalSearch.displayName = 'GlobalSearch';
