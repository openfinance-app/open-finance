/**
 * TransactionFilters Component
 * Task 3.2.16: Create TransactionFilters component
 * Task 12.3.6: Add tag filtering to TransactionsPage
 * 
 * Filter controls for transactions (date range, account, category, type, tags, payee)
 */
import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input, Button, Badge } from '@/components/ui';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { RegexToggle } from '@/components/ui/RegexToggle';
import { PayeeSelector } from '@/components/ui/PayeeSelector';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { useTransactionTags } from '@/hooks/useTransactionTags';
import type { TransactionFilters as Filters, TransactionType } from '@/types/transaction';
import { getToday, getDaysAgo, getStartOfMonth, getStartOfYear } from '@/utils/date';

interface TransactionFiltersProps {
  filters: Filters;
  onFiltersChange: (filters: Filters) => void;
}

export function TransactionFilters({
  filters,
  onFiltersChange,
}: TransactionFiltersProps) {
  const { t } = useTranslation('transactions');

  const transactionTypes: { value: TransactionType; label: string }[] = [
    { value: 'INCOME', label: t('form.types.INCOME') },
    { value: 'EXPENSE', label: t('form.types.EXPENSE') },
    { value: 'TRANSFER', label: t('form.types.TRANSFER') },
  ];

  const datePresets = [
    { label: t('filterKeys.today'), getValue: () => ({ from: getToday(), to: getToday() }) },
    { label: t('filterKeys.last7Days'), getValue: () => ({ from: getDaysAgo(7), to: getToday() }) },
    { label: t('filterKeys.last30Days'), getValue: () => ({ from: getDaysAgo(30), to: getToday() }) },
    { label: t('filterKeys.thisMonth'), getValue: () => ({ from: getStartOfMonth(), to: getToday() }) },
    { label: t('filterKeys.thisYear'), getValue: () => ({ from: getStartOfYear(), to: getToday() }) },
  ];

  const sortOptions = [
    { value: 'date,desc', label: t('filterKeys.sort.dateNewest') },
    { value: 'date,asc', label: t('filterKeys.sort.dateOldest') },
    { value: 'amount,desc', label: t('filterKeys.sort.amountHigh') },
    { value: 'amount,asc', label: t('filterKeys.sort.amountLow') },
  ];

  // Fetch all tags with counts for the tag filter
  const { data: allTags = [] } = useTransactionTags();

  const handleChange = (key: keyof Filters, value: string | number | boolean | undefined) => {
    onFiltersChange({
      ...filters,
      [key]: value === '' ? undefined : value,
    });
  };

  const handleDatePreset = (getValue: () => { from: string; to: string }) => {
    const { from, to } = getValue();
    onFiltersChange({
      ...filters,
      dateFrom: from,
      dateTo: to,
    });
  };

  const clearFilters = () => {
    onFiltersChange({});
  };

  const hasActiveFilters = Object.keys(filters).some(key => filters[key as keyof Filters]);

  return (
    <div className="space-y-4 p-4 bg-surface rounded-lg border border-border">
      {/* Search keyword */}
      <div>
        <label htmlFor="keyword" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('filterKeys.search')}
        </label>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
          <Input
            id="keyword"
            type="text"
            placeholder={t('filterKeys.searchPlaceholder')}
            value={filters.keyword || ''}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange('keyword', e.target.value)}
            className="pl-10 pr-10"
          />
          <RegexToggle
            enabled={!!filters.keywordRegex}
            onChange={(val) => handleChange('keywordRegex', val || undefined)}
            className="absolute right-2 top-1/2 -translate-y-1/2"
          />
        </div>
      </div>

      {/* Filters grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Account filter - using AccountSelector */}
        <div>
          <label className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.account')}
          </label>
          <AccountSelector
            value={filters.accountId}
            onValueChange={(val) => handleChange('accountId', val)}
            placeholder={t('filterKeys.allAccounts')}
            allowNone={true}
          />
        </div>

        {/* Type filter */}
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.type')}
          </label>
          <select
            id="type"
            value={filters.type || ''}
            onChange={(e) => handleChange('type', e.target.value || undefined)}
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            <option value="">{t('filterKeys.allTypes')}</option>
            {transactionTypes.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        {/* Category filter - using CategorySelect */}
        <div>
          <label className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.category')}
          </label>
          <CategorySelect
            value={filters.categoryId}
            onValueChange={(val) => handleChange('categoryId', val)}
            placeholder={t('filterKeys.allCategories')}
            allowNone={true}
          />
        </div>

        {/* Payee filter - using PayeeSelector */}
        <div>
          <label className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.payee')}
          </label>
          <PayeeSelector
            value={filters.payee}
            onValueChange={(val) => handleChange('payee', val)}
            placeholder={t('filterKeys.allPayees')}
            allowNone={true}
            allowNewPayee={false}
          />
        </div>

        {/* Sort selector */}
        <div>
          <label htmlFor="sort" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filterKeys.sortBy')}
          </label>
          <select
            id="sort"
            data-testid="filter-sort"
            value={filters.sort || 'date,desc'}
            onChange={(e) => handleChange('sort', e.target.value || undefined)}
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {sortOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        {/* Amount Range */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="minAmount" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filterKeys.minAmount')}
            </label>
            <NumberInput
              id="minAmount"
              data-testid="filter-min-amount"
              value={filters.minAmount !== undefined ? String(filters.minAmount) : ''}
              onChange={(val) => handleChange('minAmount', val ? parseFloat(val) : undefined)}
              placeholder="0.00"
              min="0"
            />
          </div>
          <div>
            <label htmlFor="maxAmount" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filterKeys.maxAmount')}
            </label>
            <NumberInput
              id="maxAmount"
              data-testid="filter-max-amount"
              value={filters.maxAmount !== undefined ? String(filters.maxAmount) : ''}
              onChange={(val) => handleChange('maxAmount', val ? parseFloat(val) : undefined)}
              placeholder="0.00"
              min="0"
            />
          </div>
        </div>

        {/* Clear button */}
        <div className="flex items-end">
          <Button
            variant="ghost"
            onClick={clearFilters}
            disabled={!hasActiveFilters}
            className="w-full"
          >
            <X className="h-4 w-4 mr-2" />
            {t('filterKeys.clear')}
          </Button>
        </div>
      </div>

      {/* Tag Filter */}
      {(filters.noCategory || filters.noPayee) && (
        <div className="flex flex-wrap gap-2">
          {filters.noCategory && (
            <Badge
              variant="info"
              className="cursor-pointer flex items-center gap-1 pr-1.5"
              onClick={() => handleChange('noCategory', undefined)}
            >
              <span>{t('filterKeys.noCategory')}</span>
              <X className="w-3 h-3 ml-0.5" />
            </Badge>
          )}
          {filters.noPayee && (
            <Badge
              variant="info"
              className="cursor-pointer flex items-center gap-1 pr-1.5"
              onClick={() => handleChange('noPayee', undefined)}
            >
              <span>{t('filterKeys.noPayee')}</span>
              <X className="w-3 h-3 ml-0.5" />
            </Badge>
          )}
        </div>
      )}

      {/* Tag Filter */}
      {allTags.length > 0 && (
        <div>
          <label className="block text-sm font-medium text-text-primary mb-2">
            {t('filterKeys.filterByTag')}
          </label>
          <div className="flex flex-wrap gap-2">
            {allTags.slice(0, 15).map((tagInfo) => (
              <Badge
                key={tagInfo.tag}
                variant={filters.tag === tagInfo.tag ? 'info' : 'default'}
                className="cursor-pointer hover:bg-primary/20 transition-colors"
                onClick={() => handleChange('tag', filters.tag === tagInfo.tag ? undefined : tagInfo.tag)}
              >
                {tagInfo.tag} ({tagInfo.count})
              </Badge>
            ))}
            {allTags.length > 15 && (
              <Badge variant="default" className="text-text-tertiary">
                +{allTags.length - 15} more
              </Badge>
            )}
          </div>
          {filters.tag && (
            <div className="mt-2 flex items-center gap-2 text-sm text-text-secondary">
              <span>{t('filterKeys.filteredByTag')} <strong>{filters.tag}</strong></span>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleChange('tag', undefined)}
                className="h-6 px-2"
              >
                <X className="h-3 w-3" />
              </Button>
            </div>
          )}
        </div>
      )}

      {/* Date range */}
      <div className="space-y-2">
        <label className="block text-sm font-medium text-text-primary">
          {t('filterKeys.dateRange')}
        </label>

        {/* Date presets */}
        <div className="flex flex-wrap gap-2">
          {datePresets.map((preset) => (
            <Button
              key={preset.label}
              variant="ghost"
              size="sm"
              onClick={() => handleDatePreset(preset.getValue)}
            >
              {preset.label}
            </Button>
          ))}
        </div>

        {/* Custom date range */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label htmlFor="dateFrom" className="block text-xs text-text-secondary mb-1">
              {t('filterKeys.from')}
            </label>
            <DateInput
              id="dateFrom"
              value={filters.dateFrom || ''}
              onChange={(val) => handleChange('dateFrom', val || undefined)}
            />
          </div>
          <div>
            <label htmlFor="dateTo" className="block text-xs text-text-secondary mb-1">
              {t('filterKeys.to')}
            </label>
            <DateInput
              id="dateTo"
              value={filters.dateTo || ''}
              onChange={(val) => handleChange('dateTo', val || undefined)}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
