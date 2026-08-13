/**
 * AssetFilters Component
 * 
 * Filter controls for assets (keyword, type, currency, value range, etc.)
 */
import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input, Button } from '@/components/ui';
import { RegexToggle } from '@/components/ui/RegexToggle';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import type { AssetFilters, AssetType } from '@/types/asset';

interface AssetFiltersProps {
  filters: AssetFilters;
  onFiltersChange: (filters: AssetFilters) => void;
}

const assetTypes: { value: AssetType | ''; labelKey: string }[] = [
  { value: '', labelKey: 'filtersPanel.allTypes' },
  { value: 'STOCK', labelKey: 'types.STOCK' },
  { value: 'ETF', labelKey: 'types.ETF' },
  { value: 'CRYPTO', labelKey: 'types.CRYPTO' },
  { value: 'BOND', labelKey: 'types.BOND' },
  { value: 'MUTUAL_FUND', labelKey: 'types.MUTUAL_FUND' },
  { value: 'REAL_ESTATE', labelKey: 'types.REAL_ESTATE' },
  { value: 'COMMODITY', labelKey: 'types.COMMODITY' },
  { value: 'VEHICLE', labelKey: 'types.VEHICLE' },
  { value: 'JEWELRY', labelKey: 'types.JEWELRY' },
  { value: 'COLLECTIBLE', labelKey: 'types.COLLECTIBLE' },
  { value: 'ELECTRONICS', labelKey: 'types.ELECTRONICS' },
  { value: 'FURNITURE', labelKey: 'types.FURNITURE' },
  { value: 'OTHER', labelKey: 'types.OTHER' },
];

const sortOptions: { value: string; labelKey: string }[] = [
  { value: 'name,asc', labelKey: 'filtersPanel.sortOptions.nameAsc' },
  { value: 'name,desc', labelKey: 'filtersPanel.sortOptions.nameDesc' },
  { value: 'totalValue,desc', labelKey: 'filtersPanel.sortOptions.valueHighToLow' },
  { value: 'totalValue,asc', labelKey: 'filtersPanel.sortOptions.valueLowToHigh' },
  { value: 'unrealizedGain,desc', labelKey: 'filtersPanel.sortOptions.gainHighToLow' },
  { value: 'unrealizedGain,asc', labelKey: 'filtersPanel.sortOptions.gainLowToHigh' },
  { value: 'purchaseDate,desc', labelKey: 'filtersPanel.sortOptions.newestFirst' },
  { value: 'purchaseDate,asc', labelKey: 'filtersPanel.sortOptions.oldestFirst' },
];

export function AssetFilters({
  filters,
  onFiltersChange,
}: AssetFiltersProps) {
  const { t } = useTranslation('assets');
  const handleChange = (key: keyof AssetFilters, value: string | number | boolean | undefined) => {
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
      filters[key as keyof AssetFilters] !== undefined &&
      key !== 'page' &&
      key !== 'size' &&
      key !== 'sort'
  );

  return (
    <div className="space-y-4 p-4 bg-surface rounded-lg border border-border">
      {/* Search keyword */}
      <div>
        <label htmlFor="keyword" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('filtersPanel.search')}
        </label>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
          <Input
            id="keyword"
            type="text"
            placeholder={t('form.searchPlaceholder')}
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
        {/* Type filter */}
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filtersPanel.assetType')}
          </label>
          <select
            id="type"
            value={filters.type || ''}
            onChange={(e) => handleChange('type', e.target.value || undefined)}
            className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {assetTypes.map((type) => (
              <option key={type.value} value={type.value}>
                {t(type.labelKey)}
              </option>
            ))}
          </select>
        </div>

        {/* Currency filter */}
        <div>
          <label htmlFor="currency" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filtersPanel.currency')}
          </label>
          <CurrencySelector
            value={filters.currency}
            onValueChange={(val) => handleChange('currency', val)}
            allowNone={true}
          />
        </div>

        {/* Symbol filter */}
        <div>
          <label htmlFor="symbol" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filtersPanel.symbol')}
          </label>
          <Input
            id="symbol"
            type="text"
            placeholder={t('form.symbolFilterPlaceholder')}
            value={filters.symbol || ''}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange('symbol', e.target.value)}
          />
        </div>

        {/* Sort selector */}
        <div>
          <label htmlFor="sort" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filtersPanel.sortBy')}
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
                {t(option.labelKey)}
              </option>
            ))}
          </select>
        </div>

        {/* Value Range */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="valueMin" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filtersPanel.minValue')}
            </label>
            <input
              id="valueMin"
              data-testid="filter-min-value"
              type="number"
              min="0"
              step="any"
              placeholder="0.00"
              value={filters.valueMin ?? ''}
              onChange={(e) =>
                handleChange('valueMin', e.target.value ? parseFloat(e.target.value) : undefined)
              }
              className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            />
          </div>
          <div>
            <label htmlFor="valueMax" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filtersPanel.maxValue')}
            </label>
            <input
              id="valueMax"
              data-testid="filter-max-value"
              type="number"
              min="0"
              step="any"
              placeholder="0.00"
              value={filters.valueMax ?? ''}
              onChange={(e) =>
                handleChange('valueMax', e.target.value ? parseFloat(e.target.value) : undefined)
              }
              className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            />
          </div>
        </div>

        {/* Purchase Date Range */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="purchaseDateFrom" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filtersPanel.purchasedFrom')}
            </label>
            <input
              id="purchaseDateFrom"
              type="date"
              value={filters.purchaseDateFrom || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange('purchaseDateFrom', e.target.value || undefined)}
              className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            />
          </div>
          <div>
            <label htmlFor="purchaseDateTo" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('filtersPanel.purchasedTo')}
            </label>
            <input
              id="purchaseDateTo"
              type="date"
              value={filters.purchaseDateTo || ''}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => handleChange('purchaseDateTo', e.target.value || undefined)}
              className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
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
            {t('filtersPanel.clear')}
          </Button>
        </div>
      </div>
    </div>
  );
}
