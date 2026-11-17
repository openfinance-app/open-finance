import { useToday } from '@/hooks/useReportingCalendar';
import { FilterPanel } from '@/components/ui/FilterPanel';
import { countActiveFilters } from '@/utils/filter-utils';
import { useState } from 'react';
/**
 * TransactionFilters Component
 * Task 3.2.16: Create TransactionFilters component
 * Task 12.3.6: Add tag filtering to TransactionsPage
 *
 * Filter controls for transactions (date range, account, category, type, tags, payee)
 */
import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input, Button } from '@/components/ui';
import { DateInput } from '@/components/ui/DateInput';
import { AmountFilterControls } from '@/components/ui/AmountFilterControls';
import { RegexToggle } from '@/components/ui/RegexToggle';
import { PayeeSelector } from '@/components/ui/PayeeSelector';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { useTransactionTags } from '@/hooks/useTransactionTags';
import type { TransactionFilters as Filters, TransactionType } from '@/types/transaction';
import { getDaysAgo, getStartOfMonth, getStartOfYear } from '@/utils/date';

interface TransactionFiltersProps {
  filters: Filters;
  onFiltersChange: (filters: Filters) => void;
}

export function TransactionFilters({ filters, onFiltersChange }: TransactionFiltersProps) {
  const today = useToday();
  const { t } = useTranslation('transactions');
  const [showAllTags, setShowAllTags] = useState(false);

  const transactionTypes: { value: TransactionType; label: string }[] = [
    { value: 'INCOME', label: t('form.types.INCOME') },
    { value: 'EXPENSE', label: t('form.types.EXPENSE') },
    { value: 'TRANSFER', label: t('form.types.TRANSFER') },
  ];

  const datePresets = [
    { label: t('filterKeys.today'), getValue: () => ({ from: today, to: today }) },
    {
      label: t('filterKeys.last7Days'),
      getValue: () => ({ from: getDaysAgo(7, today), to: today }),
    },
    {
      label: t('filterKeys.last30Days'),
      getValue: () => ({ from: getDaysAgo(30, today), to: today }),
    },
    {
      label: t('filterKeys.thisMonth'),
      getValue: () => ({ from: getStartOfMonth(today), to: today }),
    },
    {
      label: t('filterKeys.thisYear'),
      getValue: () => ({ from: getStartOfYear(today), to: today }),
    },
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

  const additionalCount = countActiveFilters([
    filters.payee,
    filters.tag,
    filters.minAmount,
    filters.maxAmount,
    filters.exactAmount,
    filters.dateFrom,
    filters.dateTo,
    filters.amountCurrencyMode === 'NATIVE',
  ]);
  const activeCount =
    additionalCount +
    countActiveFilters([
      filters.keyword,
      filters.keywordRegex,
      filters.accountId,
      filters.type,
      filters.categoryId,
      filters.noCategory,
      filters.noPayee,
      filters.excludeTransfers,
      filters.includeSubcategories,
      filters.assetId,
      filters.realEstateId,
      filters.liabilityId,
      filters.sort && filters.sort !== 'date,desc',
    ]);

  return (
    <FilterPanel
      onClear={clearFilters}
      activeCount={activeCount}
      additionalCount={additionalCount}
      additionalDescription={t('common:formLayout.transactionFiltersHint')}
      additional={
        <>
          {' '}
          {/* Payee filter - using PayeeSelector */}
          <div>
            <label
              htmlFor="filter-payee"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('form.payee')}
            </label>
            <PayeeSelector
              id="filter-payee"
              value={filters.payee}
              onValueChange={val => handleChange('payee', val)}
              placeholder={t('filterKeys.allPayees')}
              noneLabel={t('filterKeys.allPayees')}
              allowNone={true}
              allowNewPayee={false}
            />
          </div>
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-text-primary">
              {t('common:formLayout.amount')}
            </h3>{' '}
            <AmountFilterControls
              min={filters.minAmount}
              max={filters.maxAmount}
              exact={filters.exactAmount}
              amountCurrencyMode={filters.amountCurrencyMode}
              minLabel={t('filterKeys.minAmount')}
              maxLabel={t('filterKeys.maxAmount')}
              testIdSuffix="amount"
              onChange={value =>
                onFiltersChange({
                  ...filters,
                  minAmount: value.min,
                  maxAmount: value.max,
                  exactAmount: value.exact,
                  amountCurrencyMode: value.amountCurrencyMode,
                })
              }
            />
          </div>
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-text-primary">
              {t('common:formLayout.dateRange')}
            </h3>{' '}
            {/* Custom date range */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="dateFrom" className="block text-xs text-text-secondary mb-1">
                  {t('filterKeys.from')}
                </label>
                <DateInput
                  id="dateFrom"
                  value={filters.dateFrom || ''}
                  onChange={val => handleChange('dateFrom', val || undefined)}
                />
              </div>
              <div>
                <label htmlFor="dateTo" className="block text-xs text-text-secondary mb-1">
                  {t('filterKeys.to')}
                </label>
                <DateInput
                  id="dateTo"
                  value={filters.dateTo || ''}
                  onChange={val => handleChange('dateTo', val || undefined)}
                />
              </div>
            </div>
          </div>{' '}
          {/* Tag Filter */}
          {allTags.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-text-primary mb-2">
                {t('filterKeys.filterByTag')}
              </label>
              <div className="flex flex-wrap gap-2">
                {allTags.slice(0, showAllTags ? undefined : 8).map(tagInfo => (
                  <button
                    type="button"
                    key={tagInfo.tag}
                    aria-pressed={filters.tag === tagInfo.tag}
                    className="min-h-9 rounded-md border border-border px-3 text-xs text-text-secondary transition-colors hover:border-border-strong aria-pressed:border-primary aria-pressed:bg-primary/10 aria-pressed:text-primary"
                    onClick={() =>
                      handleChange('tag', filters.tag === tagInfo.tag ? undefined : tagInfo.tag)
                    }
                  >
                    {tagInfo.tag} ({tagInfo.count})
                  </button>
                ))}
                {allTags.length > 8 && (
                  <button
                    type="button"
                    onClick={() => setShowAllTags(value => !value)}
                    className="min-h-9 px-2 text-xs text-text-secondary underline underline-offset-4"
                  >
                    {t(showAllTags ? 'common:formLayout.fewerTags' : 'common:formLayout.moreTags')}
                  </button>
                )}
              </div>
              {filters.tag && (
                <div className="mt-2 flex items-center gap-2 text-sm text-text-secondary">
                  <span>
                    {t('filterKeys.filteredByTag')} <strong>{filters.tag}</strong>
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleChange('tag', undefined)}
                    className="h-9 px-2"
                    aria-label={t('common:formLayout.removeFilter', { name: filters.tag })}
                  >
                    <X className="h-3 w-3" />
                  </Button>
                </div>
              )}
            </div>
          )}
        </>
      }
    >
      <div className="space-y-4">
        {' '}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-6">
          {' '}
          {/* Search keyword */}
          <div className="col-span-full lg:col-span-2">
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
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  handleChange('keyword', e.target.value)
                }
                className="pl-10 pr-10"
              />
              <RegexToggle
                enabled={!!filters.keywordRegex}
                onChange={val => handleChange('keywordRegex', val || undefined)}
                className="absolute right-2 top-1/2 -translate-y-1/2"
              />
            </div>
          </div>{' '}
          {/* Account filter - using AccountSelector */}
          <div>
            <label
              htmlFor="filter-account"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('form.account')}
            </label>
            <AccountSelector
              id="filter-account"
              value={filters.accountId}
              onValueChange={val => handleChange('accountId', val)}
              placeholder={t('filterKeys.allAccounts')}
              noneLabel={t('filterKeys.allAccounts')}
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
              onChange={e => handleChange('type', e.target.value || undefined)}
              className="w-full h-10 px-3 pr-8 rounded-lg bg-background border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
            >
              <option value="">{t('filterKeys.allTypes')}</option>
              {transactionTypes.map(type => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </div>
          {/* Category filter - using CategorySelect */}
          <div>
            <label
              htmlFor="filter-category"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('form.category')}
            </label>
            <CategorySelect
              id="filter-category"
              value={filters.categoryId}
              onValueChange={val => handleChange('categoryId', val)}
              placeholder={t('filterKeys.allCategories')}
              allowNone={true}
            />
            {filters.categoryId && (
              <label className="mt-2 flex items-center gap-2 text-xs text-text-secondary">
                <input
                  type="checkbox"
                  checked={!!filters.includeSubcategories}
                  onChange={event => handleChange('includeSubcategories', event.target.checked)}
                />
                {t('filterKeys.includeSubcategories')}
              </label>
            )}
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
              onChange={e => handleChange('sort', e.target.value || undefined)}
              className="w-full h-10 px-3 pr-8 rounded-lg bg-background border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
            >
              {sortOptions.map(option => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        {/* Date presets */}
        <div className="flex flex-wrap gap-2">
          {datePresets.map(preset => (
            <Button
              key={preset.label}
              variant={
                filters.dateFrom === preset.getValue().from &&
                filters.dateTo === preset.getValue().to
                  ? 'secondary'
                  : 'ghost'
              }
              aria-pressed={
                filters.dateFrom === preset.getValue().from &&
                filters.dateTo === preset.getValue().to
              }
              size="sm"
              onClick={() => handleDatePreset(preset.getValue)}
            >
              {preset.label}
            </Button>
          ))}
        </div>
        {/* Tag Filter */}
        {(filters.noCategory || filters.noPayee || filters.excludeTransfers) && (
          <div className="flex flex-wrap gap-2">
            {filters.noCategory && (
              <button
                type="button"
                className="inline-flex min-h-9 items-center gap-1.5 rounded-md border border-border px-3 text-xs text-text-secondary hover:text-text-primary"
                onClick={() => handleChange('noCategory', undefined)}
              >
                <span>{t('filterKeys.noCategory')}</span>
                <X className="w-3 h-3 ml-0.5" />
              </button>
            )}
            {filters.noPayee && (
              <button
                type="button"
                className="inline-flex min-h-9 items-center gap-1.5 rounded-md border border-border px-3 text-xs text-text-secondary hover:text-text-primary"
                onClick={() => handleChange('noPayee', undefined)}
              >
                <span>{t('filterKeys.noPayee')}</span>
                <X className="w-3 h-3 ml-0.5" />
              </button>
            )}
            {filters.excludeTransfers && (
              <button
                type="button"
                onClick={() => handleChange('excludeTransfers', undefined)}
                className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1 text-xs text-primary"
              >
                <span>{t('filterKeys.excludeTransfers')}</span>
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
        )}
      </div>
    </FilterPanel>
  );
}
