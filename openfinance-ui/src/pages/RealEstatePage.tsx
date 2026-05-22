/**
 * RealEstatePage Component
 * Task 9.1.8: Create RealEstatePage component
 * 
 * Main page for managing real estate properties and physical assets with filters and pagination
 */
import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Building2, Calculator, Filter, Search, X, AlertTriangle } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Pagination } from '@/components/ui/Pagination';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { Card } from '@/components/ui/Card';
import { PropertyCard } from '@/components/real-estate/PropertyCard';
import { RealEstateForm } from '@/components/real-estate/RealEstateForm';
import { PropertyDetailView } from '@/components/real-estate/PropertyDetailView';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { usePropertiesSearch, useCreateProperty, useUpdateProperty } from '@/hooks/useRealEstate';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useAuthContext } from '@/context/AuthContext';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import type { RealEstateProperty, PropertySearchFilters, RealEstatePropertyRequest } from '@/types/realEstate';
import { PropertyType as PropertyTypeEnum } from '@/types/realEstate';

const DEFAULT_PAGE_SIZE = 20;

export default function RealEstatePage() {
  const { t } = useTranslation('realEstate');
  useDocumentTitle(t('title'));
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const highlightId = searchParams.get('highlight') ? parseInt(searchParams.get('highlight')!) : null;
  const { baseCurrency } = useAuthContext();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(baseCurrency);

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingProperty, setEditingProperty] = useState<RealEstateProperty | null>(null);
  const [viewingPropertyId, setViewingPropertyId] = useState<number | null>(null);
  const [showFilters, setShowFilters] = useState(false);

  // Search filters state
  const [searchFilters, setSearchFilters] = useState<PropertySearchFilters>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sort: 'name,asc',
    isActive: true,
  });

  // Build filters for API call
  const apiFilters: PropertySearchFilters = {
    ...searchFilters,
  };

  const { data: propertiesPage, isLoading, error } = usePropertiesSearch(apiFilters);
  // Fetch all active properties (unfiltered) for global summary totals
  const { data: allPropertiesPage } = usePropertiesSearch({ page: 0, size: 10000, sort: 'name,asc', isActive: true });

  const properties = propertiesPage?.content || [];
  const allProperties = allPropertiesPage?.content || [];

  /** Compute summary statistics for a set of properties.
   *
   * When a property has been converted to the user's base currency
   * (isConverted=true, valueInBaseCurrency is present) we use the
   * converted value so that every card shows amounts in a single,
   * consistent currency.
   *
   * When a property is already in the base currency (isConverted=false,
   * currency === baseCurrency), we use its native value directly.
   *
   * When conversion has failed for a foreign-currency property
   * (isConverted=false, currency !== baseCurrency), we EXCLUDE it
   * from the totals to avoid summing incompatible currencies. The
   * returned `excludedCount` tracks how many properties were skipped
   * so the UI can display a warning.
   */
  const computeSummary = (list: typeof properties) => {
    if (!list || list.length === 0) {
      return { totalValue: 0, totalEquity: 0, totalMortgageDebt: 0, totalRentalIncome: 0, currency: baseCurrency, excludedCount: 0 };
    }

    let excludedCount = 0;

    // Determine whether a property can be safely included in base-currency totals.
    const isIncludable = (p: (typeof properties)[number]) => {
      // Already in base currency — always safe to include.
      if (!p.currency || p.currency === baseCurrency) return true;
      // Conversion succeeded — use valueInBaseCurrency.
      if (p.isConverted && p.valueInBaseCurrency != null) return true;
      // Foreign currency with no usable conversion — exclude.
      return false;
    };

    const totalValue = list.reduce((sum, p) => {
      if (!isIncludable(p)) { excludedCount++; return sum; }
      const value = p.isConverted && p.valueInBaseCurrency != null
        ? p.valueInBaseCurrency
        : p.currentValue;
      return sum + value;
    }, 0);

    // Reset excludedCount — it was inflated once per metric above; track once.
    excludedCount = list.filter(p => !isIncludable(p)).length;

    // Equity = currentValue - mortgageBalance; apply the same conversion logic.
    const totalEquity = list.reduce((sum, p) => {
      if (!isIncludable(p)) return sum;
      const value = p.isConverted && p.valueInBaseCurrency != null
        ? p.valueInBaseCurrency
        : p.currentValue;
      const debt = p.mortgageBalance || 0;
      // Mortgage balance conversion: multiply by the same exchange rate used for value.
      const rate = p.exchangeRate ?? 1;
      const convertedDebt = p.isConverted ? debt * rate : debt;
      return sum + Math.max(0, value - convertedDebt);
    }, 0);

    const totalMortgageDebt = list.reduce((sum, p) => {
      if (!isIncludable(p)) return sum;
      const debt = p.mortgageBalance || 0;
      const rate = p.exchangeRate ?? 1;
      return sum + (p.isConverted ? debt * rate : debt);
    }, 0);

    const totalRentalIncome = list.reduce((sum, p) => {
      if (!isIncludable(p)) return sum;
      const income = p.rentalIncome || 0;
      const rate = p.exchangeRate ?? 1;
      return sum + (p.isConverted ? income * rate : income);
    }, 0);

    return {
      totalValue,
      totalEquity,
      totalMortgageDebt,
      totalRentalIncome,
      // Always show summary totals in the user's base currency
      currency: baseCurrency,
      excludedCount,
    };
  };

  // Calculate summary statistics
  const summary = useMemo(() => computeSummary(allProperties), [allProperties, baseCurrency]);
  const filteredSummary = useMemo(() => computeSummary(properties), [properties, baseCurrency]);

  const handleFiltersChange = (key: keyof PropertySearchFilters, value: string | number | boolean | undefined) => {
    setSearchFilters(prev => ({
      ...prev,
      [key]: value || value === false ? value : undefined,
      page: key !== 'page' ? 0 : prev.page, // Reset to page 0 when filter changes
    }));
  };

  const clearFilters = () => {
    setSearchFilters({
      page: 0,
      size: DEFAULT_PAGE_SIZE,
      sort: 'name,asc',
      isActive: true,
    });
  };

  const handlePageChange = (page: number) => {
    setSearchFilters(prev => ({ ...prev, page }));
  };

  const handlePageSizeChange = (size: number) => {
    setSearchFilters(prev => ({ ...prev, page: 0, size }));
  };

  const handleCreate = () => {
    setEditingProperty(null);
    setIsFormOpen(true);
  };

  const handleEdit = (property: RealEstateProperty) => {
    setEditingProperty(property);
    setIsFormOpen(true);
  };

  const handleView = (propertyId: number) => {
    setViewingPropertyId(propertyId);
  };

  const createMutation = useCreateProperty();
  const updateMutation = useUpdateProperty();

  const handleFormSubmit = async (data: RealEstatePropertyRequest) => {
    try {
      if (editingProperty) {
        await updateMutation.mutateAsync({ id: editingProperty.id, data });
      } else {
        await createMutation.mutateAsync(data);
      }
      handleFormClose();
    } catch (error) {
      console.error('Failed to save property:', error);
    }
  };

  const handleFormClose = () => {
    setIsFormOpen(false);
    setEditingProperty(null);
  };

  const hasActiveFilters = Object.keys(searchFilters).some(
    (key) =>
      searchFilters[key as keyof PropertySearchFilters] !== undefined &&
      key !== 'page' &&
      key !== 'size' &&
      key !== 'sort' &&
      key !== 'isActive' &&
      key !== 'propertyType'
  );

  const sortOptions = [
    { value: 'name,asc', label: t('sort.nameAsc') },
    { value: 'name,desc', label: t('sort.nameDesc') },
    { value: 'currentValue,desc', label: t('sort.valueDesc') },
    { value: 'currentValue,asc', label: t('sort.valueAsc') },
    { value: 'purchaseDate,desc', label: t('sort.newest') },
    { value: 'purchaseDate,asc', label: t('sort.oldest') },
  ];

  if (error) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} />
        <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error">
          {t('loadError')}
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <PageHeader
          title={t('title')}
          description={t('description')}
        />
        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <Button
            variant={showFilters ? 'primary' : 'outline'}
            onClick={() => setShowFilters(!showFilters)}
          >
            <Filter className="h-4 w-4 mr-2" />
            {t('filters.label')}
          </Button>
          <Button variant="outline" onClick={() => navigate('/real-estate/tools')}>
            <Calculator className="h-4 w-4 mr-2" />
            {t('tools')}
          </Button>
          <Button variant="primary" onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addProperty')}
          </Button>
        </div>
      </div>

      {/* Advanced Filters Panel */}
      {showFilters && (
        <div className="mb-6 p-4 bg-surface rounded-lg border border-border">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium text-text-primary">{t('filters.searchAndFilter')}</h3>
            {hasActiveFilters && (
              <Button variant="ghost" size="sm" onClick={clearFilters}>
                <X className="h-4 w-4 mr-1" />
                {t('filters.clear')}
              </Button>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Keyword Search */}
            <div>
              <label htmlFor="keyword" className="block text-sm font-medium text-text-primary mb-1.5">
                {t('filters.search')}
              </label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
                <input
                  id="keyword"
                  type="text"
                  placeholder={t('filters.searchPlaceholder')}
                  value={searchFilters.keyword || ''}
                  onChange={(e) => handleFiltersChange('keyword', e.target.value)}
                  className="w-full h-10 pl-10 pr-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                />
              </div>
            </div>

            {/* Currency Filter */}
            <div>
              <label htmlFor="currency" className="block text-sm font-medium text-text-primary mb-1.5">
                {t('filters.currency')}
              </label>
              <CurrencySelector
                value={searchFilters.currency}
                onValueChange={(val) => handleFiltersChange('currency', val)}
                allowNone
                className="w-full h-10"
              />
            </div>

            {/* Property Type Filter */}
            <div>
              <label htmlFor="propertyType" className="block text-sm font-medium text-text-primary mb-1.5">
                {t('filters.propertyType')}
              </label>
              <select
                id="propertyType"
                value={searchFilters.propertyType || ''}
                onChange={(e) => handleFiltersChange('propertyType', e.target.value || undefined)}
                className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                <option value="">{t('filters.allTypes')}</option>
                <option value={PropertyTypeEnum.RESIDENTIAL}>{t('filters.residential')}</option>
                <option value={PropertyTypeEnum.COMMERCIAL}>{t('filters.commercial')}</option>
                <option value={PropertyTypeEnum.LAND}>{t('filters.land')}</option>
                <option value={PropertyTypeEnum.MIXED_USE}>{t('filters.mixedUse')}</option>
                <option value={PropertyTypeEnum.INDUSTRIAL}>{t('filters.industrial')}</option>
                <option value={PropertyTypeEnum.OTHER}>{t('filters.other')}</option>
              </select>
            </div>

            {/* Value Range */}
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label htmlFor="valueMin" className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('filters.minValue')}
                </label>
                <input
                  id="valueMin"
                  type="number"
                  min="0"
                  step="1000"
                  placeholder="0"
                  value={searchFilters.valueMin ?? ''}
                  onChange={(e) => handleFiltersChange('valueMin', e.target.value ? parseFloat(e.target.value) : undefined)}
                  className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                />
              </div>
              <div>
                <label htmlFor="valueMax" className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('filters.maxValue')}
                </label>
                <input
                  id="valueMax"
                  type="number"
                  min="0"
                  step="1000"
                  placeholder="0"
                  value={searchFilters.valueMax ?? ''}
                  onChange={(e) => handleFiltersChange('valueMax', e.target.value ? parseFloat(e.target.value) : undefined)}
                  className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                />
              </div>
            </div>

            {/* Sort */}
            <div>
              <label htmlFor="sort" className="block text-sm font-medium text-text-primary mb-1.5">
                {t('filters.sortBy')}
              </label>
              <select
                id="sort"
                value={searchFilters.sort || 'name,asc'}
                onChange={(e) => handleFiltersChange('sort', e.target.value)}
                className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                {sortOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      )}

      {/* Summary Cards */}
      {!isLoading && allProperties && allProperties.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          {/* Total Portfolio Value */}
          <Card className="p-4">
            <p className="text-sm text-text-secondary mb-1">{t('summary.totalPortfolioValue')}</p>
            <ConvertedAmount
              amount={summary.totalValue}
              currency={summary.currency}
              className="text-2xl font-bold text-text-primary"
            />
            {hasActiveFilters && (
              <p className="text-xs text-text-tertiary mt-1">
                {t('filtered')} <ConvertedAmount
                  amount={filteredSummary.totalValue}
                  currency={filteredSummary.currency}
                  isConverted={false}
                  secondaryAmount={convert(filteredSummary.totalValue)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
              </p>
            )}
          </Card>

          {/* Total Equity */}
          <Card className="p-4">
            <p className="text-sm text-text-secondary mb-1">{t('summary.totalEquity')}</p>
            <ConvertedAmount
              amount={summary.totalEquity}
              currency={summary.currency}
              className="text-2xl font-bold text-green-400"
            />
            {hasActiveFilters && (
              <p className="text-xs text-text-tertiary mt-1">
                {t('filtered')} <ConvertedAmount
                  amount={filteredSummary.totalEquity}
                  currency={filteredSummary.currency}
                  isConverted={false}
                  secondaryAmount={convert(filteredSummary.totalEquity)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
              </p>
            )}
          </Card>

          {/* Total Mortgage Debt */}
          <Card className="p-4">
            <p className="text-sm text-text-secondary mb-1">{t('summary.totalMortgageDebt')}</p>
            <ConvertedAmount
              amount={summary.totalMortgageDebt}
              currency={summary.currency}
              className="text-2xl font-bold text-error"
            />
            {hasActiveFilters && (
              <p className="text-xs text-text-tertiary mt-1">
                {t('filtered')} <ConvertedAmount
                  amount={filteredSummary.totalMortgageDebt}
                  currency={filteredSummary.currency}
                  isConverted={false}
                  secondaryAmount={convert(filteredSummary.totalMortgageDebt)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
              </p>
            )}
          </Card>

          {/* Total Rental Income */}
          <Card className="p-4">
            <p className="text-sm text-text-secondary mb-1">{t('summary.monthlyRentalIncome')}</p>
            <div className="flex items-baseline gap-1">
              <ConvertedAmount
                amount={summary.totalRentalIncome}
                currency={summary.currency}
                className="text-2xl font-bold text-primary"
              />
              <span className="text-sm text-text-secondary">/mo</span>
            </div>
            {hasActiveFilters && (
              <p className="text-xs text-text-tertiary mt-1">
                {t('filtered')} <ConvertedAmount
                  amount={filteredSummary.totalRentalIncome}
                  currency={filteredSummary.currency}
                  isConverted={false}
                  secondaryAmount={convert(filteredSummary.totalRentalIncome)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
              </p>
            )}
          </Card>
        </div>
      )}

      {/* Currency conversion warning */}
      {!isLoading && summary.excludedCount > 0 && (
        <div className="flex items-start gap-2 mb-6 p-3 rounded-lg bg-warning/10 border border-warning/20 text-warning text-sm">
          <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0" />
          <span>
            {t('conversionWarning.property', { count: summary.excludedCount })}{' '}
            {t('conversionWarning.message', {
              baseCurrency,
              verb: summary.excludedCount === 1 ? t('conversionWarning.verbSingular') : t('conversionWarning.verbPlural'),
            })}
          </span>
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[...Array(6)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-64" />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && properties && properties.length === 0 && (
        <EmptyState
          icon={Building2}
          title={t('empty.noResults')}
          description={hasActiveFilters ? t('empty.noMatch') : t('empty.noProperties')}
          action={{
            label: t('addFirstProperty'),
            onClick: handleCreate,
          }}
        />
      )}

      {/* Properties Grid */}
      {!isLoading && properties && properties.length > 0 && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-6">
            {properties.map((property) => (
              <PropertyCard
                key={property.id}
                property={property}
                onEdit={handleEdit}
                onView={handleView}
                isHighlighted={highlightId === property.id}
              />
            ))}
          </div>

          {/* Pagination */}
          {propertiesPage && propertiesPage.totalPages > 1 && (
            <Pagination
              currentPage={propertiesPage.number}
              totalPages={propertiesPage.totalPages}
              pageSize={propertiesPage.size}
              totalElements={propertiesPage.totalElements}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          )}
        </>
      )}

      {/* Property Detail Modal */}
      {viewingPropertyId && (
        <PropertyDetailView
          propertyId={viewingPropertyId}
          onClose={() => setViewingPropertyId(null)}
        />
      )}

      {/* Create/Edit Dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="sm:max-w-[700px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingProperty ? t('dialogs.editTitle') : t('dialogs.createTitle')}
            </DialogTitle>
          </DialogHeader>
          <RealEstateForm
            property={editingProperty || undefined}
            onSubmit={handleFormSubmit}
            onCancel={handleFormClose}
            isLoading={createMutation.isPending || updateMutation.isPending}
          />
        </DialogContent>
      </Dialog>
    </div>
  );
}
