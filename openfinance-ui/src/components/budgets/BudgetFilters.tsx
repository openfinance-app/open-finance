import { FilterPanel } from '@/components/ui/FilterPanel';
import { countActiveFilters } from '@/utils/filter-utils';
/**
 * BudgetFilters Component
 *
 * Filter controls for budgets (keyword search + period dropdown).
 * Mirrors the AccountFilters pattern with a collapsible panel layout.
 */
import { Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui';
import { RegexToggle } from '@/components/ui/RegexToggle';
import type { BudgetPeriod } from '@/types/budget';

/** Shape of the active budget filters. */
export interface BudgetFiltersState {
  keyword?: string;
  /** When true, `keyword` is treated as a regular expression instead of a plain substring. */
  keywordRegex?: boolean;
  period?: BudgetPeriod | '';
}

interface BudgetFiltersProps {
  filters: BudgetFiltersState;
  onFiltersChange: (filters: BudgetFiltersState) => void;
}

/**
 * Budget filter panel with keyword search and period dropdown.
 * Rendered conditionally by BudgetsPage when the user toggles the Filters button.
 */
export function BudgetFilters({ filters, onFiltersChange }: BudgetFiltersProps) {
  const { t } = useTranslation('budgets');
  const handleChange = (key: keyof BudgetFiltersState, value: string | boolean | undefined) => {
    onFiltersChange({
      ...filters,
      [key]: value || value === false ? value : undefined,
    });
  };

  const clearFilters = () => {
    onFiltersChange({});
  };

  /** True when any meaningful filter is active (ignores empty / undefined values). */
  const activeCount = countActiveFilters([filters.keyword, filters.period, filters.keywordRegex]);

  const periodOptions: { value: BudgetPeriod | ''; label: string }[] = [
    { value: '', label: t('filters.allPeriods') },
    { value: 'WEEKLY', label: t('form.periods.WEEKLY') },
    { value: 'MONTHLY', label: t('form.periods.MONTHLY') },
    { value: 'QUARTERLY', label: t('form.periods.QUARTERLY') },
    { value: 'YEARLY', label: t('form.periods.YEARLY') },
  ];

  return (
    <FilterPanel activeCount={activeCount} onClear={clearFilters}>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-[2fr_1fr]">
        {' '}
        {/* Search keyword */}
        <div>
          <label
            htmlFor="budget-keyword"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('filters.search')}
          </label>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
            <Input
              id="budget-keyword"
              type="text"
              placeholder={t('form.searchPlaceholder')}
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
        </div>
        {/* Period filter */}
        <div>
          <label
            htmlFor="budget-period"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('filters.period')}
          </label>
          <select
            id="budget-period"
            value={filters.period || ''}
            onChange={e => handleChange('period', e.target.value || undefined)}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-background border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            {periodOptions.map(opt => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>
    </FilterPanel>
  );
}
