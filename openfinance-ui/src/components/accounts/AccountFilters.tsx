import { FilterChip } from '@/components/ui/FilterChip';
import { FilterPanel } from '@/components/ui/FilterPanel';
import { getAccountFilterCounts } from '@/utils/filter-utils';
/**
 * AccountFilters Component
 *
 * Filter controls for accounts (keyword, type, currency, balance range, institution)
 */
import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Search } from 'lucide-react';
import { Input } from '@/components/ui';
import { RegexToggle } from '@/components/ui/RegexToggle';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { AmountFilterControls } from '@/components/ui/AmountFilterControls';
import type { AccountFilters, AccountType } from '@/types/account';

interface AccountFiltersProps {
  filters: AccountFilters;
  onFiltersChange: (filters: AccountFilters) => void;
}

export function AccountFilters({ filters, onFiltersChange }: AccountFiltersProps) {
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
  const latestFilters = useRef(filters);

  useEffect(() => {
    latestFilters.current = filters;
  }, [filters]);

  useEffect(
    () => () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    },
    []
  );

  // Sync if external filter changes (e.g., cleared by the 'Clear' button)
  useEffect(() => {
    if (filters.keyword === undefined) {
      setLocalKeyword('');
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    }
  }, [filters.keyword]);

  const handleKeywordChange = (val: string) => {
    setLocalKeyword(val);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => {
      timeoutRef.current = null;
      onFiltersChange({
        ...latestFilters.current,
        keyword: val || undefined,
        page: 0,
      });
    }, 400);
  };

  const handleChange = (
    key: keyof AccountFilters,
    value: string | number | boolean | undefined
  ) => {
    onFiltersChange({
      ...filters,
      [key]: value || value === false ? value : undefined,
    });
  };

  const clearFilters = () => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    setLocalKeyword('');
    onFiltersChange({
      sort: 'name,asc',
      page: 0,
    });
  };

  const { activeCount, additionalCount } = getAccountFilterCounts({
    ...filters,
    keyword: localKeyword,
  });

  return (
    <FilterPanel
      onClear={clearFilters}
      activeFilters={
        filters.lowBalance && (
          <FilterChip
            label={t('filters.lowBalance')}
            onRemove={() => handleChange('lowBalance', undefined)}
          />
        )
      }
      activeCount={activeCount}
      additionalCount={additionalCount}
      additionalDescription={t('common:formLayout.accountFiltersHint')}
      additional={
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-2">
            {' '}
            {/* Currency filter */}
            <div>
              <label
                htmlFor="currency"
                className="block text-sm font-medium text-text-primary mb-1.5"
              >
                {t('filters.currency')}
              </label>
              <CurrencySelector
                id="currency"
                value={filters.currency}
                onValueChange={val => handleChange('currency', val)}
                allowNone={true}
              />
            </div>
            {/* Institution filter */}
            <div>
              <label
                htmlFor="institution"
                className="block text-sm font-medium text-text-primary mb-1.5"
              >
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
          </div>
          <AmountFilterControls
            min={filters.balanceMin}
            max={filters.balanceMax}
            exact={filters.balanceExact}
            amountCurrencyMode={filters.amountCurrencyMode}
            minLabel={t('filters.minBalance')}
            maxLabel={t('filters.maxBalance')}
            allowNegative
            testIdSuffix="balance"
            onChange={value =>
              onFiltersChange({
                ...filters,
                balanceMin: value.min,
                balanceMax: value.max,
                balanceExact: value.exact,
                amountCurrencyMode: value.amountCurrencyMode,
              })
            }
          />
        </>
      }
    >
      <div className="space-y-4">
        {' '}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {' '}
          {/* Search keyword */}
          <div className="col-span-full lg:col-span-2">
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
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  handleKeywordChange(e.target.value)
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
          {/* Type filter */}
          <div>
            <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filters.accountType')}
            </label>
            <select
              id="type"
              value={filters.type || ''}
              onChange={e => handleChange('type', e.target.value || undefined)}
              className="w-full h-10 px-3 pr-8 rounded-lg bg-background border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
            >
              {accountTypes.map(type => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </div>
          {/* Status filter */}
          <div>
            <label
              htmlFor="isActive"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('filters.status')}
            </label>
            <select
              id="isActive"
              value={filters.isActive === undefined ? '' : filters.isActive ? 'active' : 'closed'}
              onChange={e => {
                if (e.target.value === '') {
                  handleChange('isActive', undefined);
                } else {
                  handleChange('isActive', e.target.value === 'active');
                }
              }}
              className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
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
      </div>
    </FilterPanel>
  );
}
