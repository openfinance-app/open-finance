/**
 * AdvancedFilterPanel Component
 * Task 12.4.6: Create AdvancedFilterPanel component
 * Task 12.4.8: Add saved searches UI
 * 
 * Expandable filter panel for advanced search with multiple filter types
 */
import { useState } from 'react';
import { ChevronDown, ChevronUp, X, Filter, Save } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { useAccounts } from '@/hooks/useAccounts';
import { useCategories } from '@/hooks/useCategories';
import { useTransactionTags } from '@/hooks/useTransactionTags';
import type { AdvancedSearchRequest, SearchResultType } from '@/types/search';
import type { TransactionType } from '@/types/transaction';
import { getToday, getDaysAgo, getStartOfMonth, getStartOfYear } from '@/utils/date';

interface AdvancedFilterPanelProps {
  filters: AdvancedSearchRequest;
  onFiltersChange: (filters: AdvancedSearchRequest) => void;
  onApply: (override?: AdvancedSearchRequest) => void;
  onSaveSearch?: () => void;
  isLoading?: boolean;
}

const entityTypeValues: SearchResultType[] = [
  'TRANSACTION', 'ACCOUNT', 'ASSET', 'REAL_ESTATE', 'LIABILITY', 'BUDGET', 'CATEGORY',
];

const transactionTypeValues: TransactionType[] = ['INCOME', 'EXPENSE', 'TRANSFER'];

type DatePresetKey = 'today' | 'last7days' | 'last30days' | 'thisMonth' | 'thisYear';

const datePresetKeys: { key: DatePresetKey; getValue: () => { from: string; to: string } }[] = [
  { key: 'today', getValue: () => ({ from: getToday(), to: getToday() }) },
  { key: 'last7days', getValue: () => ({ from: getDaysAgo(7), to: getToday() }) },
  { key: 'last30days', getValue: () => ({ from: getDaysAgo(30), to: getToday() }) },
  { key: 'thisMonth', getValue: () => ({ from: getStartOfMonth(), to: getToday() }) },
  { key: 'thisYear', getValue: () => ({ from: getStartOfYear(), to: getToday() }) },
];

export function AdvancedFilterPanel({
  filters,
  onFiltersChange,
  onApply,
  onSaveSearch,
  isLoading = false,
}: AdvancedFilterPanelProps) {
  const { t } = useTranslation(['navigation', 'common', 'dashboard', 'errors', 'transactions']);
  const [isExpanded, setIsExpanded] = useState(false);

  // Fetch data for filter options
  const { data: accounts = [] } = useAccounts();
  const { data: categories = [] } = useCategories();
  const { data: allTags = [] } = useTransactionTags();

  // Helper to update a single filter field
  const updateFilter = <K extends keyof AdvancedSearchRequest>(
    key: K,
    value: AdvancedSearchRequest[K]
  ) => {
    onFiltersChange({
      ...filters,
      [key]: value,
    });
  };

  // Helper to toggle array values (multi-select)
  const toggleArrayValue = <K extends keyof AdvancedSearchRequest>(
    key: K,
    value: any
  ) => {
    const currentArray = (filters[key] as any[]) || [];
    const newArray = currentArray.includes(value)
      ? currentArray.filter((v) => v !== value)
      : [...currentArray, value];

    updateFilter(key, (newArray.length > 0 ? newArray : undefined) as any);
  };

  // Clear all filters (BUG-010: also triggers a new search with cleared filters)
  const clearFilters = () => {
    const cleared: AdvancedSearchRequest = {
      query: filters.query, // Keep the search query
    };
    onFiltersChange(cleared);
    onApply(cleared);
  };

  // Apply date preset
  const applyDatePreset = (getValue: () => { from: string; to: string }) => {
    const { from, to } = getValue();
    onFiltersChange({
      ...filters,
      dateFrom: from,
      dateTo: to,
    });
  };

  // Count active filters (excluding query)
  const activeFilterCount = Object.entries(filters).filter(
    ([key, value]) => key !== 'query' && key !== 'limit' && value !== undefined && value !== null && (Array.isArray(value) ? value.length > 0 : true)
  ).length;

  return (
    <Card className="mb-6">
      {/* Header - Always visible */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-surface-elevated transition-colors"
      >
        <div className="flex items-center gap-3">
          <Filter className="h-5 w-5 text-primary" />
          <div>
            <h3 className="font-semibold text-text-primary">{t('navigation:search.advancedFilters')}</h3>
            <p className="text-sm text-text-secondary mt-0.5">
              {activeFilterCount > 0
                ? t('navigation:search.filtersActive', { count: activeFilterCount })
                : t('navigation:search.filtersSubtitle')}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {activeFilterCount > 0 && (
            <Badge variant="info" className="mr-2">
              {activeFilterCount}
            </Badge>
          )}
          {isExpanded ? (
            <ChevronUp className="h-5 w-5 text-text-tertiary" />
          ) : (
            <ChevronDown className="h-5 w-5 text-text-tertiary" />
          )}
        </div>
      </button>

      {/* Filters - Shown when expanded */}
      {isExpanded && (
        <div className="px-6 pb-6 space-y-6 border-t border-border pt-6">
          {/* Entity Types */}
          <div>
            <label className="block text-sm font-medium text-text-primary mb-3">
              {t('navigation:search.filters.searchIn')}
            </label>
            <div className="flex flex-wrap gap-2">
              {entityTypeValues.map((value) => (
                <Badge
                  key={value}
                  variant={
                    filters.entityTypes?.includes(value)
                      ? 'info'
                      : 'default'
                  }
                  className="cursor-pointer hover:bg-primary/20 transition-colors"
                  onClick={() => toggleArrayValue('entityTypes', value)}
                >
                  {t(`errors:search.types.${value}`, value)}
                </Badge>
              ))}
            </div>
          </div>

          {/* Amount Range */}
          <div>
            <label className="block text-sm font-medium text-text-primary mb-3">
              {t('navigation:search.filters.amountRange')}
            </label>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="minAmount" className="block text-xs text-text-secondary mb-1.5">
                  {t('navigation:search.filters.minimum')}
                </label>
                <Input
                  id="minAmount"
                  type="number"
                  placeholder="0.00"
                  step="any"
                  min="0"
                  value={filters.minAmount ?? ''}
                  onChange={(e) =>
                    updateFilter('minAmount', e.target.value ? parseFloat(e.target.value) : undefined)
                  }
                />
              </div>
              <div>
                <label htmlFor="maxAmount" className="block text-xs text-text-secondary mb-1.5">
                  {t('navigation:search.filters.maximum')}
                </label>
                <Input
                  id="maxAmount"
                  type="number"
                  placeholder="0.00"
                  step="any"
                  min="0"
                  value={filters.maxAmount ?? ''}
                  onChange={(e) =>
                    updateFilter('maxAmount', e.target.value ? parseFloat(e.target.value) : undefined)
                  }
                />
              </div>
            </div>
          </div>

          {/* Date Range */}
          <div>
            <label className="block text-sm font-medium text-text-primary mb-3">
              {t('navigation:search.filters.dateRange')}
            </label>

            {/* Date presets */}
            <div className="flex flex-wrap gap-2 mb-4">
              {datePresetKeys.map((preset) => (
                <Button
                  key={preset.key}
                  variant="ghost"
                  size="sm"
                  onClick={() => applyDatePreset(preset.getValue)}
                >
                  {t(`navigation:search.filters.datePresets.${preset.key}`)}
                </Button>
              ))}
            </div>

            {/* Custom date range */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="dateFrom" className="block text-xs text-text-secondary mb-1.5">
                  {t('navigation:search.filters.from')}
                </label>
                <Input
                  id="dateFrom"
                  type="date"
                  value={filters.dateFrom || ''}
                  onChange={(e) => updateFilter('dateFrom', e.target.value || undefined)}
                />
              </div>
              <div>
                <label htmlFor="dateTo" className="block text-xs text-text-secondary mb-1.5">
                  {t('navigation:search.filters.to')}
                </label>
                <Input
                  id="dateTo"
                  type="date"
                  value={filters.dateTo || ''}
                  onChange={(e) => updateFilter('dateTo', e.target.value || undefined)}
                />
              </div>
            </div>
          </div>

          {/* Accounts */}
          {accounts.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-text-primary mb-3">
                {t('navigation:search.filters.accounts')}
              </label>
              <div className="flex flex-wrap gap-2 max-h-48 overflow-y-auto">
                {accounts.map((account) => (
                  <Badge
                    key={account.id}
                    variant={
                      filters.accountIds?.includes(account.id)
                        ? 'info'
                        : 'default'
                    }
                    className="cursor-pointer hover:bg-primary/20 transition-colors"
                    onClick={() => toggleArrayValue('accountIds', account.id)}
                  >
                    {account.name}
                  </Badge>
                ))}
              </div>
            </div>
          )}

          {/* Categories */}
          {categories.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-text-primary mb-3">
                {t('navigation:search.filters.categories')}
              </label>
              <div className="flex flex-wrap gap-2 max-h-48 overflow-y-auto">
                {categories.map((category) => (
                  <Badge
                    key={category.id}
                    variant={
                      filters.categoryIds?.includes(category.id)
                        ? 'info'
                        : 'default'
                    }
                    className="cursor-pointer hover:bg-primary/20 transition-colors"
                    onClick={() => toggleArrayValue('categoryIds', category.id)}
                  >
                    {category.name}
                  </Badge>
                ))}
              </div>
            </div>
          )}

          {/* Tags */}
          {allTags.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-text-primary mb-3">
                {t('navigation:search.filters.tags')}
              </label>
              <div className="flex flex-wrap gap-2 max-h-48 overflow-y-auto">
                {allTags.slice(0, 20).map((tagInfo) => (
                  <Badge
                    key={tagInfo.tag}
                    variant={
                      filters.tags?.includes(tagInfo.tag)
                        ? 'info'
                        : 'default'
                    }
                    className="cursor-pointer hover:bg-primary/20 transition-colors"
                    onClick={() => toggleArrayValue('tags', tagInfo.tag)}
                  >
                    {tagInfo.tag} ({tagInfo.count})
                  </Badge>
                ))}
              </div>
            </div>
          )}

          {/* Transaction-specific filters */}
          <div className="space-y-4">
            <label className="block text-sm font-medium text-text-primary">
              {t('navigation:search.filters.transactionFilters')}
            </label>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Transaction Type */}
              <div>
                <label htmlFor="transactionType" className="block text-xs text-text-secondary mb-1.5">
                  {t('navigation:search.filters.type')}
                </label>
                <select
                  id="transactionType"
                  value={filters.transactionType || ''}
                  onChange={(e) =>
                    updateFilter('transactionType', (e.target.value || undefined) as TransactionType | undefined)
                  }
                  className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                >
                  <option value="">{t('navigation:search.filters.allTypes')}</option>
                  {transactionTypeValues.map((value) => (
                    <option key={value} value={value}>
                      {t(`transactions:types.${value}`, value)}
                    </option>
                  ))}
                </select>
              </div>

              {/* Reconciled Status */}
              <div>
                <label htmlFor="isReconciled" className="block text-xs text-text-secondary mb-1.5">
                  {t('navigation:search.filters.reconciledStatus')}
                </label>
                <select
                  id="isReconciled"
                  value={
                    filters.isReconciled === undefined
                      ? ''
                      : filters.isReconciled
                        ? 'true'
                        : 'false'
                  }
                  onChange={(e) => {
                    const value = e.target.value;
                    updateFilter(
                      'isReconciled',
                      value === '' ? undefined : value === 'true'
                    );
                  }}
                  className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                >
                  <option value="">{t('navigation:search.filters.all')}</option>
                  <option value="true">{t('navigation:search.filters.reconciled')}</option>
                  <option value="false">{t('navigation:search.filters.notReconciled')}</option>
                </select>
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center justify-between pt-4 border-t border-border">
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                onClick={clearFilters}
                disabled={activeFilterCount === 0}
              >
                <X className="h-4 w-4 mr-2" />
                {t('common:buttons.clearFilters', 'Clear All Filters')}
              </Button>
              {onSaveSearch && activeFilterCount > 0 && (
                <Button
                  variant="ghost"
                  onClick={onSaveSearch}
                  disabled={isLoading}
                  title={t('common:buttons.saveSearchHint', 'Save this search for quick access later')}
                >
                  <Save className="h-4 w-4 mr-2" />
                  {t('common:buttons.saveSearch', 'Save Search')}
                </Button>
              )}
            </div>
            <Button
              variant="primary"
              onClick={() => onApply()}
              disabled={isLoading}
            >
              {isLoading ? t('common:status.searching', 'Searching...') : t('common:buttons.applyFilters', 'Apply Filters')}
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}
