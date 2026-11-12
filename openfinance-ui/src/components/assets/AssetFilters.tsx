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
import { DateInput } from '@/components/ui/DateInput';
import { AmountFilterControls } from '@/components/ui/AmountFilterControls';
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

export function AssetFilters({ filters, onFiltersChange }: AssetFiltersProps) {
  const { t } = useTranslation('assets');
  const handleChange = (key: keyof AssetFilters, value: string | number | boolean | undefined) => {
    onFiltersChange({
      ...filters,
      [key]: value || value === false ? value : undefined,
    });
  };

  const clearFilters = () => {
    onFiltersChange({
      group: filters.group,
      ownershipStatus: 'OWNED',
      sort: 'name,asc',
      page: 0,
    });
  };

  const hasActiveFilters =
    filters.ownershipStatus !== 'OWNED' ||
    Object.keys(filters).some(
      key =>
        filters[key as keyof AssetFilters] !== undefined &&
        key !== 'page' &&
        key !== 'size' &&
        key !== 'sort' &&
        key !== 'group' &&
        key !== 'ownershipStatus'
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

      {/* Filters grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div>
          <label
            htmlFor="asset-status"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('statuses.label')}
          </label>
          <select
            id="asset-status"
            value={filters.ownershipStatus ?? ''}
            onChange={event => handleChange('ownershipStatus', event.target.value)}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-background border border-border text-text-primary text-sm hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background transition-colors duration-150"
          >
            {(['OWNED', 'PLANNED', 'CLOSED', ''] as const).map(status => (
              <option key={status} value={status}>
                {t(`statuses.${status || 'ALL'}`)}
              </option>
            ))}
          </select>
        </div>

        {/* Type filter */}
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('filtersPanel.assetType')}
          </label>
          <select
            id="type"
            value={filters.type || ''}
            onChange={e => handleChange('type', e.target.value || undefined)}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-background border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            {assetTypes
              .filter(
                type =>
                  !type.value ||
                  !filters.group ||
                  (filters.group === 'PHYSICAL'
                    ? ['VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS', 'FURNITURE'].includes(
                        type.value
                      )
                    : ![
                        'VEHICLE',
                        'JEWELRY',
                        'COLLECTIBLE',
                        'ELECTRONICS',
                        'FURNITURE',
                        'REAL_ESTATE',
                      ].includes(type.value))
              )
              .map(type => (
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
            onValueChange={val => handleChange('currency', val)}
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
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              handleChange('symbol', e.target.value)
            }
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
            onChange={e => handleChange('sort', e.target.value || undefined)}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-background border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            {sortOptions.map(option => (
              <option key={option.value} value={option.value}>
                {t(option.labelKey)}
              </option>
            ))}
          </select>
        </div>

        <AmountFilterControls
          min={filters.valueMin}
          max={filters.valueMax}
          exact={filters.valueExact}
          amountCurrencyMode={filters.amountCurrencyMode}
          minLabel={t('filtersPanel.minValue')}
          maxLabel={t('filtersPanel.maxValue')}
          testIdSuffix="value"
          onChange={value =>
            onFiltersChange({
              ...filters,
              valueMin: value.min,
              valueMax: value.max,
              valueExact: value.exact,
              amountCurrencyMode: value.amountCurrencyMode,
            })
          }
        />

        {/* Purchase Date Range */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label
              htmlFor="purchaseDateFrom"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('filtersPanel.purchasedFrom')}
            </label>
            <DateInput
              id="purchaseDateFrom"
              value={filters.purchaseDateFrom || ''}
              onChange={val => handleChange('purchaseDateFrom', val || undefined)}
            />
          </div>
          <div>
            <label
              htmlFor="purchaseDateTo"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('filtersPanel.purchasedTo')}
            </label>
            <DateInput
              id="purchaseDateTo"
              value={filters.purchaseDateTo || ''}
              onChange={val => handleChange('purchaseDateTo', val || undefined)}
            />
          </div>
        </div>

        {/* Clear button */}
        <div className="flex items-end justify-end md:col-start-2 lg:col-start-4">
          <Button
            variant="outline"
            size="sm"
            onClick={clearFilters}
            disabled={!hasActiveFilters}
            className="w-fit gap-1.5 border-border text-text-secondary hover:border-border-strong hover:bg-surface-elevated"
          >
            <X className="h-3.5 w-3.5" />
            {t('filtersPanel.clear')}
          </Button>
        </div>
      </div>
    </div>
  );
}
