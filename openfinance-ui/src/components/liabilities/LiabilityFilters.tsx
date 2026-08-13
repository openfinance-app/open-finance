/**
 * LiabilityFilters Component
 * 
 * Filter controls for liabilities (type, search, sort)
 */
import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input, Button } from '@/components/ui';
import { RegexToggle } from '@/components/ui/RegexToggle';
import type { LiabilityFilters as Filters, LiabilityType } from '@/types/liability';

interface LiabilityFiltersProps {
  filters: Filters;
  onFiltersChange: (filters: Filters) => void;
}

const liabilityTypes: { value: LiabilityType }[] = [
  { value: 'MORTGAGE' },
  { value: 'LOAN' },
  { value: 'CREDIT_CARD' },
  { value: 'STUDENT_LOAN' },
  { value: 'AUTO_LOAN' },
  { value: 'PERSONAL_LOAN' },
  { value: 'OTHER' },
];

const sortOptions: { value: string; labelKey: string }[] = [
  { value: 'createdAt,desc', labelKey: 'filterOptions.sort.newestFirst' },
  { value: 'createdAt,asc', labelKey: 'filterOptions.sort.oldestFirst' },
  { value: 'name,asc', labelKey: 'filterOptions.sort.nameAZ' },
  { value: 'name,desc', labelKey: 'filterOptions.sort.nameZA' },
  { value: 'currentBalance,desc', labelKey: 'filterOptions.sort.balanceHighLow' },
  { value: 'currentBalance,asc', labelKey: 'filterOptions.sort.balanceLowHigh' },
  { value: 'interestRate,desc', labelKey: 'filterOptions.sort.interestHighLow' },
  { value: 'interestRate,asc', labelKey: 'filterOptions.sort.interestLowHigh' },
];

export function LiabilityFilters({
  filters,
  onFiltersChange,
}: LiabilityFiltersProps) {
  const { t } = useTranslation('liabilities');
  const handleChange = (key: keyof Filters, value: string | number | boolean | undefined) => {
    onFiltersChange({
      ...filters,
      [key]: value || value === false ? value : undefined,
    });
  };

  const clearFilters = () => {
    onFiltersChange({
      page: 0,
      size: filters.size || 20,
      sort: filters.sort || 'createdAt,desc',
    });
  };

  const hasActiveFilters = filters.type || filters.search;

  return (
    <div className="space-y-4 p-4 bg-surface rounded-lg border border-border">
      {/* Search keyword */}
      <div>
        <label htmlFor="search" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('filterOptions.searchLabel')}
        </label>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
          <Input
            id="search"
            type="text"
            placeholder={t('form.searchPlaceholder')}
            value={filters.search || ''}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange('search', e.target.value)}
            className="pl-10 pr-10"
          />
          <RegexToggle
            enabled={!!filters.searchRegex}
            onChange={(val) => handleChange('searchRegex', val || undefined)}
            className="absolute right-2 top-1/2 -translate-y-1/2"
          />
        </div>
      </div>

      {/* Filters grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Type filter */}
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filterOptions.typeLabel')}
          </label>
          <select
            id="type"
            value={filters.type || ''}
            onChange={(e) => handleChange('type', e.target.value || undefined)}
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            <option value="">{t('filterOptions.allTypes')}</option>
            {liabilityTypes.map((type) => (
              <option key={type.value} value={type.value}>
                {t(`types.${type.value}`)}
              </option>
            ))}
          </select>
        </div>

        {/* Sort selector */}
        <div>
          <label htmlFor="sort" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filterOptions.sortByLabel')}
          </label>
          <select
            id="sort"
            value={filters.sort || 'createdAt,desc'}
            onChange={(e) => handleChange('sort', e.target.value || undefined)}
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {sortOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {t(option.labelKey)}
              </option>
            ))}
          </select>
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
            {t('filterOptions.clear')}
          </Button>
        </div>
      </div>
    </div>
  );
}
