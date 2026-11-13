import { useViewState } from '@/hooks/useViewState';
/**
 * SearchResultsPage Component
 * Task 12.4.7: Create SearchResultsPage component
 * Task 12.4.8: Add saved searches UI
 *
 * Display search results with advanced filters and result grouping
 */
import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Search, ChevronDown, ChevronUp, ExternalLink, AlertCircle } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { RegexToggle } from '@/components/ui/RegexToggle';
import { AdvancedFilterPanel } from '@/components/search/AdvancedFilterPanel';
import { SavedSearchesDropdown } from '@/components/search/SavedSearchesDropdown';
import { SaveSearchDialog } from '@/components/search/SaveSearchDialog';
import { useAdvancedSearch, useGlobalSearch, useSavedSearches } from '@/hooks/useSearch';
import { SearchResultAmount } from '@/components/search/SearchResultAmount';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  type SearchResult,
  type AdvancedSearchRequest,
  type GlobalSearchResponse,
  type SavedSearch,
  getResultTypeDisplayName,
  getResultTypeIcon,
  getResultRoute,
  highlightMatch,
} from '@/types/search';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import * as Icons from 'lucide-react';

export default function SearchResultsPage() {
  const { t } = useTranslation(['errors', 'common', 'transactions', 'budgets']);
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryParam = searchParams.get('q') || '';

  useDocumentTitle(queryParam ? `${t('common:search')}: ${queryParam}` : t('common:search'));

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

  // State
  const [query, setQuery] = useViewState('query', queryParam);
  const [filters, setFilters] = useViewState<AdvancedSearchRequest>('filters', {
    query: queryParam,
    limit: 100,
  });
  const [expandedTypes, setExpandedTypes] = useViewState<Set<string> | null>(
    'expandedTypes',
    null,
    Boolean(queryParam)
  );
  const [appliedFilters, setAppliedFilters] = useViewState<AdvancedSearchRequest | null>(
    'appliedFilters',
    null
  );
  const hasAppliedFilters = appliedFilters?.query === queryParam;
  const [searchResults, setSearchResults] = useState<GlobalSearchResponse | null>(null);
  const [isSaveDialogOpen, setIsSaveDialogOpen] = useState(false);

  // Saved searches
  const { savedSearches, saveSearch, deleteSearch, updateLastUsed } = useSavedSearches();

  // Use simple search for initial load from URL, advanced search for filtered results
  const simpleSearch = useGlobalSearch(
    queryParam,
    100,
    queryParam.length >= 2 && !hasAppliedFilters,
    !!filters.regex
  );
  const advancedSearch = useAdvancedSearch();

  // Re-run the last applied search after returning; keep result data in TanStack Query.
  const runAdvancedSearch = advancedSearch.mutate;
  useEffect(() => {
    if (hasAppliedFilters && appliedFilters) runAdvancedSearch(appliedFilters);
  }, [hasAppliedFilters, appliedFilters, runAdvancedSearch]);

  // Update query from URL param
  useEffect(() => {
    const q = searchParams.get('q') || '';
    setQuery(q);
    setFilters(prev => ({ ...prev, query: q }));
  }, [searchParams, setQuery, setFilters]);

  // Set initial results from simple search
  useEffect(() => {
    if (!hasAppliedFilters && simpleSearch.data) {
      setSearchResults(simpleSearch.data);
      // Expand all result types by default
      const types = Object.keys(simpleSearch.data.resultsByType);
      if (expandedTypes === null) setExpandedTypes(new Set(types));
    }
  }, [simpleSearch.data, hasAppliedFilters, expandedTypes, setExpandedTypes]);

  // Handle advanced search results
  useEffect(() => {
    if (hasAppliedFilters && advancedSearch.data) {
      setSearchResults(advancedSearch.data);
      // Expand all result types by default
      const types = Object.keys(advancedSearch.data.resultsByType);
      if (expandedTypes === null) setExpandedTypes(new Set(types));
    }
  }, [advancedSearch.data, hasAppliedFilters, expandedTypes, setExpandedTypes]);

  // Handlers
  const handleQueryChange = (newQuery: string) => {
    setQuery(newQuery);
  };

  const handleQuerySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim().length >= 2) {
      setAppliedFilters(null);
      setExpandedTypes(null);
      setSearchParams({ q: query.trim() });
      setFilters(prev => ({ ...prev, query: query.trim() }));
    }
  };

  const handleFiltersChange = (newFilters: AdvancedSearchRequest) => {
    setFilters(newFilters);
  };

  const handleApplyFilters = (override?: AdvancedSearchRequest) => {
    const toSearch = override ?? filters;
    if (toSearch.query && toSearch.query.length >= 2) {
      setExpandedTypes(null);
      setAppliedFilters({ ...toSearch });
    }
  };

  const handleSaveSearch = () => {
    setIsSaveDialogOpen(true);
  };

  const handleSaveSearchConfirm = (name: string) => {
    saveSearch(name, filters);
    setIsSaveDialogOpen(false);
  };

  const handleLoadSavedSearch = (search: SavedSearch) => {
    // Update filters with saved search
    setFilters(search.filters);
    setQuery(search.filters.query);

    // Update URL if query changed
    if (search.filters.query) {
      setSearchParams({ q: search.filters.query });
    }

    // Execute search
    setExpandedTypes(null);
    setAppliedFilters({ ...search.filters });

    // Update last used timestamp
    updateLastUsed(search.id);
  };

  const handleDeleteSavedSearch = (id: string) => {
    deleteSearch(id);
  };

  const toggleTypeExpanded = (type: string) => {
    const newExpanded = new Set(expandedTypes);
    if (newExpanded.has(type)) {
      newExpanded.delete(type);
    } else {
      newExpanded.add(type);
    }
    setExpandedTypes(newExpanded);
  };

  const handleResultClick = (result: SearchResult) => {
    const route = getResultRoute(result);
    navigate(route);
  };

  // Loading state
  const isLoading = hasAppliedFilters ? advancedSearch.isPending : simpleSearch.isLoading;
  const error = hasAppliedFilters ? advancedSearch.error : simpleSearch.error;

  // Get icon component dynamically
  const getIcon = (iconName: string) => {
    const IconComponent = (Icons as any)[iconName] || Icons.Search;
    return IconComponent;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <PageHeader
          title={t('common:search')}
          description={
            searchResults
              ? t('errors:search.foundWithTime', {
                  count: searchResults.totalResults,
                  ms: searchResults.executionTimeMs,
                })
              : t('errors:search.placeholder')
          }
        />

        {/* Saved Searches Dropdown */}
        {savedSearches.length > 0 && (
          <SavedSearchesDropdown
            savedSearches={savedSearches}
            onLoad={handleLoadSavedSearch}
            onDelete={handleDeleteSavedSearch}
          />
        )}
      </div>

      {/* Search Bar */}
      <Card className="p-4">
        <form onSubmit={handleQuerySubmit} className="flex gap-2">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-text-tertiary" />
            <Input
              type="text"
              placeholder={t('errors:search.placeholder')}
              value={query}
              onChange={e => handleQueryChange(e.target.value)}
              className="pl-10 pr-10"
              autoFocus
            />
            <RegexToggle
              enabled={!!filters.regex}
              onChange={val => setFilters(prev => ({ ...prev, regex: val || undefined }))}
              className="absolute right-2 top-1/2 -translate-y-1/2"
            />
          </div>
          <button
            type="submit"
            className="px-6 py-2 bg-primary text-white rounded-lg hover:bg-primary/90 transition-colors font-medium disabled:opacity-50 disabled:cursor-not-allowed"
            disabled={query.trim().length < 2}
          >
            {t('common:search')}
          </button>
        </form>
      </Card>

      {/* Advanced Filters */}
      <AdvancedFilterPanel
        filters={filters}
        onFiltersChange={handleFiltersChange}
        onApply={handleApplyFilters}
        onSaveSearch={handleSaveSearch}
        isLoading={isLoading}
      />

      {/* Results */}
      <div className="space-y-4">
        {/* Loading State */}
        {isLoading && (
          <div className="space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <LoadingSkeleton key={i} height={80} />
            ))}
          </div>
        )}

        {/* Error State */}
        {error && (
          <Card className="p-6">
            <div className="flex items-center gap-3 text-error">
              <AlertCircle className="h-5 w-5 flex-shrink-0" />
              <div>
                <h3 className="font-semibold">{t('errors:search.failed')}</h3>
                <p className="text-sm text-text-secondary mt-1">
                  {error instanceof Error ? error.message : t('errors:generic.description')}
                </p>
              </div>
            </div>
          </Card>
        )}

        {/* Empty State */}
        {!isLoading && !error && searchResults && searchResults.totalResults === 0 && (
          <EmptyState
            icon={Search}
            title={t('errors:search.noResults')}
            description={t('errors:search.noResultsDescription', { query })}
          />
        )}

        {/* Results by Type */}
        {!isLoading &&
          !error &&
          searchResults &&
          searchResults.totalResults > 0 &&
          Object.entries(searchResults.resultsByType).map(([type, results]) => {
            if (results.length === 0) return null;

            const isExpanded = expandedTypes?.has(type);
            const Icon = getIcon(getResultTypeIcon(type as any));
            const displayName = getResultTypeDisplayName(type as any, key => t(`errors:${key}`));
            const count = searchResults.countsPerType[type] || results.length;

            return (
              <Card key={type} className="overflow-hidden">
                {/* Type Header */}
                <button
                  onClick={() => toggleTypeExpanded(type)}
                  className="w-full px-6 py-4 flex items-center justify-between bg-surface-elevated hover:bg-surface-elevated/80 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center">
                      <Icon className="h-5 w-5 text-primary" />
                    </div>
                    <div className="text-left">
                      <h3 className="font-semibold text-text-primary">{displayName}</h3>
                      <p className="text-sm text-text-secondary">
                        {t('errors:search.found', { count })}
                      </p>
                    </div>
                  </div>
                  {isExpanded ? (
                    <ChevronUp className="h-5 w-5 text-text-tertiary" />
                  ) : (
                    <ChevronDown className="h-5 w-5 text-text-tertiary" />
                  )}
                </button>

                {/* Results List */}
                {isExpanded && (
                  <div className="divide-y divide-border">
                    {results.map(result => (
                      <ResultCard
                        key={`${result.resultType}-${result.id}`}
                        result={result}
                        query={query}
                        onClick={() => handleResultClick(result)}
                        localizeSubtitle={localizeSubtitle}
                      />
                    ))}
                  </div>
                )}
              </Card>
            );
          })}
      </div>

      {/* Save Search Dialog */}
      <SaveSearchDialog
        isOpen={isSaveDialogOpen}
        onClose={() => setIsSaveDialogOpen(false)}
        onSave={handleSaveSearchConfirm}
      />
    </div>
  );
}

/**
 * Individual result card component
 */
interface ResultCardProps {
  result: SearchResult;
  query: string;
  onClick: () => void;
  localizeSubtitle: (result: SearchResult) => string | undefined;
}

function ResultCard({ result, query, onClick, localizeSubtitle }: ResultCardProps) {
  const { date: formatDate } = useDateFormatter();

  const getIcon = (iconName: string) => {
    const IconComponent = (Icons as any)[iconName] || Icons.Search;
    return IconComponent;
  };

  const Icon = result.icon ? getIcon(result.icon) : getIcon(getResultTypeIcon(result.resultType));

  return (
    <button
      onClick={onClick}
      className="w-full px-6 py-4 flex items-center gap-4 hover:bg-surface-elevated transition-colors text-left group"
    >
      {/* Icon */}
      <div
        className="flex-shrink-0 h-12 w-12 rounded-lg flex items-center justify-center"
        style={{
          backgroundColor: result.color ? `${result.color}20` : 'rgba(59, 130, 246, 0.1)',
        }}
      >
        <Icon className="h-6 w-6" style={{ color: result.color || '#3b82f6' }} />
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex flex-col items-stretch gap-1 mb-1 sm:flex-row sm:items-start sm:justify-between sm:gap-2">
          <h4 className="min-w-0 font-semibold text-text-primary truncate group-hover:text-primary transition-colors">
            {highlightMatch(result.title, query)}
          </h4>
          {result.amount !== undefined && result.amount !== null && (
            <span className="font-semibold text-text-primary flex-shrink-0">
              <SearchResultAmount result={result} />
            </span>
          )}
        </div>

        {result.subtitle && (
          <div className="text-sm text-text-secondary truncate mb-1">
            {highlightMatch(localizeSubtitle(result) ?? '', query)}
          </div>
        )}

        {result.snippet && (
          <div className="text-sm text-text-tertiary truncate mb-2">
            {highlightMatch(result.snippet, query)}
          </div>
        )}

        <div className="flex items-center gap-2 flex-wrap">
          {result.date && (
            <span className="text-xs text-text-tertiary">{formatDate(result.date)}</span>
          )}
          {result.tags && result.tags.length > 0 && (
            <div className="flex gap-1 flex-wrap">
              {result.tags.slice(0, 3).map(tag => (
                <Badge key={tag} variant="default" className="text-xs">
                  {tag}
                </Badge>
              ))}
              {result.tags.length > 3 && (
                <Badge variant="default" className="text-xs">
                  +{result.tags.length - 3}
                </Badge>
              )}
            </div>
          )}
        </div>
      </div>

      {/* External link icon */}
      <ExternalLink className="h-4 w-4 text-text-tertiary opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0" />
    </button>
  );
}
