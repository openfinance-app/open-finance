/**
 * AccountFilters Component
 * 
 * Filter controls for accounts (keyword, type, currency, balance range, institution)
 */
import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, X } from 'lucide-react';
import { Input, Button, Badge } from '@/components/ui';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import type { AccountFilters, AccountType } from '@/types/account';

interface AccountFiltersProps {
  filters: AccountFilters;
  onFiltersChange: (filters: AccountFilters) => void;
}

export function AccountFilters({
  filters,
  onFiltersChange,
}: AccountFiltersProps) {
  const { t } = useTranslation('accounts');

  const accountTypes: { value: AccountType | ''; label: string }[] = [
    { value: '', label: t('filters.allTypes') },
    { value: 'CHECKING', label: t('filters.types.CHECKING') },
    { value: 'SAVINGS', label: t('filters.types.SAVINGS') },
    { value: 'CREDIT_CARD', label: t('filters.types.CREDIT_CARD') },
    { value: 'INVESTMENT', label: t('filters.types.INVESTMENT') },
    { value: 'CASH', label: t('filters.types.CASH') },
    { value: 'OTHER', label: t('filters.types.OTHER') },
  ];

  const sortOptions = [
    { value: 'name,asc', label: t('filters.sort.nameAsc') },
    { value: 'name,desc', label: t('filters.sort.nameDesc') },
    { value: 'balance,desc', label: t('filters.sort.balanceDesc') },
    { value: 'balance,asc', label: t('filters.sort.balanceAsc') },
    { value: 'createdAt,desc', label: t('filters.sort.newestFirst') },
    { value: 'createdAt,asc', label: t('filters.sort.oldestFirst') },
  ];
  const [localKeyword, setLocalKeyword] = useState<string>(filters.keyword || '');
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Sync if external filter changes (e.g., cleared by the 'Clear' button)
  useEffect(() => {
    if (filters.keyword === undefined) {
      setLocalKeyword('');
    }
  }, [filters.keyword]);

  const handleKeywordChange = (val: string) => {
    setLocalKeyword(val);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => {
      onFiltersChange({
        ...filters,
        keyword: val || undefined,
        page: 0,
      });
    }, 400);
  };

  const handleChange = (key: keyof AccountFilters, value: string | number | boolean | undefined) => {
    onFiltersChange({
      ...filters,
      [key]: value || value === false ? value : undefined,
    });
  };

  const clearFilters = () => {
    onFiltersChange({
      sort: 'name,asc',
      page: 0,
    });
  };

  const hasActiveFilters = Object.keys(filters).some(
    (key) =>
      filters[key as keyof AccountFilters] !== undefined &&
      key !== 'page' &&
      key !== 'size' &&
      key !== 'sort'
  );

  return (
    <div className="space-y-4 p-4 bg-surface rounded-lg border border-border">
      {/* Search keyword */}
      <div>
        <label htmlFor="keyword" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('filters.search')}
        </label>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
          <Input
            id="keyword"
            type="text"
            placeholder={t('form.searchPlaceholder')}
            value={localKeyword}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleKeywordChange(e.target.value)}
            className="pl-10"
          />
        </div>
      </div>

      {/* Filters grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Type filter */}
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filters.accountType')}
          </label>
          <select
            id="type"
            value={filters.type || ''}
            onChange={(e) => handleChange('type', e.target.value || undefined)}
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {accountTypes.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        {/* Currency filter */}
        <div>
          <label htmlFor="currency" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filters.currency')}
          </label>
          <CurrencySelector
            value={filters.currency}
            onValueChange={(val) => handleChange('currency', val)}
            allowNone={true}
          />
        </div>

        {/* Status filter */}
        <div>
          <label htmlFor="isActive" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filters.status')}
          </label>
          <select
            id="isActive"
            value={
              filters.isActive === undefined
                ? ''
                : filters.isActive
                  ? 'active'
                  : 'closed'
            }
            onChange={(e) => {
              if (e.target.value === '') {
                handleChange('isActive', undefined);
              } else {
                handleChange('isActive', e.target.value === 'active');
              }
            }}
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            <option value="">{t('filters.allAccounts')}</option>
            <option value="active">{t('filters.active')}</option>
            <option value="closed">{t('filters.closed')}</option>
          </select>
        </div>

        {/* Sort selector */}
        <div>
          <label htmlFor="sort" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filters.sortBy')}
          </label>
          <select
            id="sort"
            data-testid="filter-sort"
            value={filters.sort || 'name,asc'}
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

        {/* Balance Range */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="balanceMin" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filters.minBalance')}
            </label>
            <input
              id="balanceMin"
              data-testid="filter-min-balance"
              type="number"
              min="0"
              step="any"
              placeholder="0.00"
              value={filters.balanceMin ?? ''}
              onChange={(e) =>
                handleChange('balanceMin', e.target.value ? parseFloat(e.target.value) : undefined)
              }
              className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            />
          </div>
          <div>
            <label htmlFor="balanceMax" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filters.maxBalance')}
            </label>
            <input
              id="balanceMax"
              data-testid="filter-max-balance"
              type="number"
              min="0"
              step="any"
              placeholder="0.00"
              value={filters.balanceMax ?? ''}
              onChange={(e) =>
                handleChange('balanceMax', e.target.value ? parseFloat(e.target.value) : undefined)
              }
              className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            />
          </div>
        </div>

        {/* Institution filter */}
        <div>
          <label htmlFor="institution" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filters.institution')}
          </label>
          <Input
            id="institution"
            type="text"
            placeholder={t('form.searchInstitutionPlaceholder')}
            value={filters.institution || ''}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              handleChange('institution', e.target.value)
            }
          />
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
            {t('filters.clear')}
          </Button>
        </div>
      </div>

      {/* Active deep-link filter pills */}
      {filters.lowBalance && (
        <div className="flex flex-wrap gap-2 pt-1">
          <Badge
            variant="warning"
            className="cursor-pointer flex items-center gap-1 pr-1.5"
            onClick={() => handleChange('lowBalance', undefined)}
          >
            <span>{t('filters.lowBalance')}</span>
            <X className="w-3 h-3 ml-0.5" />
          </Badge>
        </div>
      )}
    </div>
  );
}
