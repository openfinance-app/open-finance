/**
 * BudgetFilters Component
 *
 * Filter controls for budgets (keyword search + period dropdown).
 * Mirrors the AccountFilters pattern with a collapsible panel layout.
 */
import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input, Button } from '@/components/ui';
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
  const handleChange = (
    key: keyof BudgetFiltersState,
    value: string | boolean | undefined
  ) => {
    onFiltersChange({
      ...filters,
      [key]: value || value === false ? value : undefined,
    });
  };

  const clearFilters = () => {
    onFiltersChange({});
  };

  /** True when any meaningful filter is active (ignores empty / undefined values). */
  const hasActiveFilters =
    (filters.keyword !== undefined && filters.keyword !== '') ||
    (filters.period !== undefined && filters.period !== '');

  const periodOptions: { value: BudgetPeriod | ''; label: string }[] = [
    { value: '', label: t('filters.allPeriods') },
    { value: 'WEEKLY', label: t('form.periods.WEEKLY') },
    { value: 'MONTHLY', label: t('form.periods.MONTHLY') },
    { value: 'QUARTERLY', label: t('form.periods.QUARTERLY') },
    { value: 'YEARLY', label: t('form.periods.YEARLY') },
  ];

  return (
    <div className="space-y-4 p-4 bg-surface rounded-lg border border-border">
      {/* Search keyword */}
      <div>
        <label htmlFor="budget-keyword" className="block text-sm font-medium text-text-primary mb-1.5">
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
            onChange={(val) => handleChange('keywordRegex', val || undefined)}
            className="absolute right-2 top-1/2 -translate-y-1/2"
          />
        </div>
      </div>

      {/* Filters grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Period filter */}
        <div>
          <label htmlFor="budget-period" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filters.period')}
          </label>
          <select
            id="budget-period"
            value={filters.period || ''}
            onChange={(e) =>
              handleChange('period', e.target.value || undefined)
            }
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {periodOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        {/* Clear button — aligned to bottom of grid cell */}
        <div className="flex items-end">
          <Button
            variant="ghost"
            onClick={clearFilters}
            disabled={!hasActiveFilters}
            className="w-full"
          >
            <X className="h-4 w-4 mr-2" />
            {t('filters.clear')}
          </Button>
        </div>
      </div>
    </div>
  );
}
